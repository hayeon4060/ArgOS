package com.gte.argos.factory.live;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * 실시간 센서값 메모리 보관. 5초 원시값은 DB 에 넣지 않으므로
 * "지금 값"과 "최근 1시간 5초 그래프"는 여기서 꺼낸다.
 */
@Component
public class LiveState {

    private static final Duration KEEP = Duration.ofHours(1);

    private final Map<Long, Deque<Point>> recent = new ConcurrentHashMap<>();
    private final List<Sink> sinks = new CopyOnWriteArrayList<>();

    /** 값이 들어올 때마다 불린다 (Redis 로 내보내기) */
    public void addSink(Sink sink) {
        sinks.add(sink);
    }

    public void record(long sensorId, Instant ts, double value) {
        for (Sink sink : sinks) {
            sink.onValue(sensorId, ts, value);
        }
        Deque<Point> q = recent.computeIfAbsent(sensorId, k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(new Point(ts, value));
            Instant oldest = ts.minus(KEEP);
            while (!q.isEmpty() && q.peekFirst().ts().isBefore(oldest)) {
                q.pollFirst();
            }
        }
    }

    /** 가장 최근 값 */
    public Optional<Point> latest(long sensorId) {
        Deque<Point> q = recent.get(sensorId);
        if (q == null) {
            return Optional.empty();
        }
        synchronized (q) {
            return Optional.ofNullable(q.peekLast());
        }
    }

    /** from 이상 to 미만의 5초 값 */
    public List<Point> range(long sensorId, Instant from, Instant to) {
        Deque<Point> q = recent.get(sensorId);
        List<Point> out = new ArrayList<>();
        if (q == null) {
            return out;
        }
        synchronized (q) {
            for (Point p : q) {
                if (!p.ts().isBefore(from) && p.ts().isBefore(to)) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    public void clear() {
        recent.clear();
    }

    public record Point(Instant ts, double value) {
    }

    @FunctionalInterface
    public interface Sink {
        void onValue(long sensorId, Instant ts, double value);
    }
}
