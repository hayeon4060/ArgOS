package com.gte.argos.factory.live;

import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * 실시간 변화 기록. 연계 서버가 1초마다 GET /internal/feed?after=마지막번호 로 가져가서 SSE 로 화면에 뿌린다.
 *
 * <pre>
 * - 항목마다 1씩 커지는 번호(seq). 서버를 껐다 켜도 이어지지 않으므로 bootId 로 구분한다
 * - 최근 20,000건만 보관. 연계 서버가 그보다 오래 끊겼으면 gap=true → 연계 서버는 전체를 다시 조회
 * </pre>
 */
@Component
public class LiveFeed {

    private static final int CAPACITY = 20_000;

    private final Deque<Item> items = new ArrayDeque<>();
    private final long bootMillis = System.currentTimeMillis();
    private final String bootId = Long.toString(bootMillis, 36);
    private final List<Consumer<Item>> sinks = new CopyOnWriteArrayList<>();
    private long seq;

    /** 항목이 생길 때마다 불린다 (Redis 로 내보내기). 순서대로, 잠금 안에서 불리므로 오래 걸리면 안 됨 */
    public void addSink(Consumer<Item> sink) {
        sinks.add(sink);
    }

    public String bootId() {
        return bootId;
    }

    /** 켜진 시각(ms). Redis 스트림 번호 앞자리로 쓴다 ({bootMillis}-{seq}) */
    public long bootMillis() {
        return bootMillis;
    }

    public synchronized long append(String type, OffsetDateTime at, Map<String, Object> data) {
        seq++;
        Item item = new Item(seq, type, at, data);
        items.addLast(item);
        for (Consumer<Item> sink : sinks) {
            sink.accept(item);
        }
        while (items.size() > CAPACITY) {
            items.pollFirst();
        }
        return seq;
    }

    /** after 다음 번호부터 최대 limit 건 */
    public synchronized Page after(long after, int limit) {
        long oldest = items.isEmpty() ? seq + 1 : items.peekFirst().seq();
        boolean gap = after > 0 && after + 1 < oldest;
        List<Item> out = new ArrayList<>();
        for (Item item : items) {
            if (item.seq() > after) {
                out.add(item);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        long next = out.isEmpty() ? Math.max(after, seq) : out.get(out.size() - 1).seq();
        return new Page(bootId, out, next, seq, gap);
    }

    /**
     * @param type SENSOR_VALUES · EVENT_OPENED · EVENT_ESCALATED · EVENT_CLOSED ·
     *             ALARM_OPENED · ALARM_UPDATED · ALARM_CLEARED · ALARM_STATUS · RESET
     */
    public record Item(long seq, String type, OffsetDateTime at, Map<String, Object> data) {
    }

    /**
     * @param bootId 서버가 켜질 때마다 바뀜. 바뀌면 연계 서버는 after=0 으로 다시 시작
     * @param items  변화 목록
     * @param next   다음 요청의 after 값
     * @param latest 지금 가장 큰 번호
     * @param gap    요청한 번호 이후 일부가 이미 지워짐
     */
    public record Page(String bootId, List<Item> items, long next, long latest, boolean gap) {
    }
}
