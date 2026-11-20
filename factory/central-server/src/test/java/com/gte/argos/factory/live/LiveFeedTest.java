package com.gte.argos.factory.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiveFeedTest {

    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-16T09:00:00+09:00");

    @Test
    void after_다음_번호부터_가져가고_next_로_이어_받는다() {
        LiveFeed feed = new LiveFeed();
        feed.append("ALARM_OPENED", AT, Map.of("alarmId", 1));
        feed.append("ALARM_STATUS", AT, Map.of("alarmId", 1));
        feed.append("EVENT_CLOSED", AT, Map.of("eventId", 7));

        LiveFeed.Page first = feed.after(0, 2);
        assertThat(first.items()).extracting(LiveFeed.Item::type).containsExactly("ALARM_OPENED", "ALARM_STATUS");
        assertThat(first.next()).isEqualTo(2);
        assertThat(first.gap()).isEqualTo(false);

        LiveFeed.Page second = feed.after(first.next(), 10);
        assertThat(second.items()).extracting(LiveFeed.Item::type).containsExactly("EVENT_CLOSED");

        LiveFeed.Page empty = feed.after(second.next(), 10);
        assertThat(empty.items()).isEmpty();
        assertThat(empty.next()).isEqualTo(3);
    }

    @Test
    void 오래_끊겨서_지워진_구간이_있으면_gap() {
        LiveFeed feed = new LiveFeed();
        for (int i = 0; i < 20_010; i++) {
            feed.append("SENSOR_VALUES", AT, Map.of());
        }

        assertThat(feed.after(5, 10).gap()).isEqualTo(true);
        assertThat(feed.after(20_000, 10).gap()).isEqualTo(false);
    }
}
