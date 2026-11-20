package com.gte.argos.factory.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gte.argos.factory.common.time.DemoClock;
import com.gte.argos.factory.live.LiveFeed;
import com.gte.argos.factory.live.LiveState;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 실시간 변화(LiveFeed)와 센서값(LiveState)을 Redis 로 내보낸다 (ARGOS_REDIS_ENABLED=true 일 때만).
 *
 * <pre>
 * - 판정 스레드를 막지 않도록 큐에 넣고, 전용 스레드가 모아서 파이프라인으로 쓴다
 * - Redis 가 꺼져 있으면 그 묶음은 버린다. 중앙 서버는 번호가 비는 것을 보고(gap) 전체를 다시 읽는다
 * - 큐가 가득 차면(20만 건) 새 항목을 버린다
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "argos.redis.enabled", havingValue = "true")
public class RedisPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisPublisher.class);
    private static final int BATCH = 1_000;

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final LiveFeed feed;
    private final LiveState state;
    private final DemoClock clock;
    private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>(200_000);

    private volatile boolean running;
    private Thread writer;
    private boolean failing;
    private long dropped;

    public RedisPublisher(StringRedisTemplate redis, ObjectMapper json, LiveFeed feed, LiveState state,
                          DemoClock clock) {
        this.clock = clock;
        this.redis = redis;
        this.json = json;
        this.feed = feed;
        this.state = state;
    }

    @PostConstruct
    void start() {
        feed.addSink(this::offer);
        state.addSink((sensorId, ts, value) -> offer(new Value(sensorId, ts, value)));
        try {
            redis.opsForHash().putAll(RedisKeys.FEED_META, Map.of("bootId", feed.bootId(),
                    "bootMs", Long.toString(feed.bootMillis()), "latest", "0"));
        } catch (RuntimeException e) {
            log.warn("Redis 에 연결하지 못했습니다 (값이 들어오면 다시 시도): {}", e.getMessage());
        }
        running = true;
        writer = new Thread(this::run, "redis-publisher");
        writer.setDaemon(true);
        writer.start();
        log.info("Redis 내보내기 켜짐: {} · {} · {}*", RedisKeys.FEED, RedisKeys.LIVE, RedisKeys.RAW_PREFIX);
    }

    @PreDestroy
    void stop() {
        running = false;
        if (writer != null) {
            writer.interrupt();
        }
    }

    private void offer(Object o) {
        if (!queue.offer(o) && dropped++ % 10_000 == 0) {
            log.warn("Redis 내보내기 큐가 가득 차 버림 (누적 {}건)", dropped);
        }
    }

    private void run() {
        List<Object> batch = new ArrayList<>();
        long latest = 0;
        while (running) {
            try {
                Object first = queue.poll(500, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, BATCH - 1);
                latest = write(batch, latest);
                if (failing) {
                    log.info("Redis 다시 연결됨");
                    failing = false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                if (!failing) {
                    log.warn("Redis 에 쓰지 못했습니다 ({}건 버림): {}", batch.size(), e.getMessage());
                    failing = true;
                }
                pause();
            } finally {
                batch.clear();
            }
        }
    }

    /** 한 묶음을 파이프라인으로 쓴다. 마지막 피드 번호를 돌려준다 */
    private long write(List<Object> batch, long latest) {
        long[] last = {latest};
        redis.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection c = (StringRedisConnection) connection;
            for (Object o : batch) {
                if (o instanceof LiveFeed.Item item) {
                    Map<String, String> f = new LinkedHashMap<>();
                    f.put("bootId", feed.bootId());
                    f.put("seq", Long.toString(item.seq()));
                    f.put("type", item.type());
                    f.put("at", item.at() == null ? "" : item.at().toString());
                    f.put("data", write(item.data()));
                    c.xAdd(StreamRecords.string(f).withStreamKey(RedisKeys.FEED)
                                    .withId(RecordId.of(feed.bootMillis(), item.seq())),
                            XAddOptions.maxlen(RedisKeys.FEED_MAXLEN).approximateTrimming(true));
                    last[0] = item.seq();
                } else if (o instanceof Value v) {
                    String ts = v.ts().atZone(clock.zone()).toOffsetDateTime().toString();
                    String value = Double.toString(v.value());
                    c.hSet(RedisKeys.LIVE, Long.toString(v.sensorId()),
                            "{\"ts\":\"" + ts + "\",\"value\":" + value + "}");
                    c.xAdd(StreamRecords.string(Map.of("ts", ts, "value", value))
                                    .withStreamKey(RedisKeys.RAW_PREFIX + v.sensorId()),
                            XAddOptions.maxlen(RedisKeys.RAW_MAXLEN).approximateTrimming(true));
                }
            }
            // Redis 가 다시 켜져 비어 있어도 중앙 서버가 알 수 있게 매번 같이 쓴다
            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("bootId", feed.bootId());
            meta.put("bootMs", Long.toString(feed.bootMillis()));
            meta.put("latest", Long.toString(last[0]));
            c.hMSet(RedisKeys.FEED_META, meta);
            return null;
        });
        return last[0];
    }

    private String write(Object data) {
        try {
            return json.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static void pause() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record Value(long sensorId, Instant ts, double value) {
    }
}
