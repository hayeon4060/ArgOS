package com.gte.argos.factory.threshold;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FillSeasonsTest {

    @Test
    void 계절_줄이_없으면_ALL_값으로_채우고_있는_줄은_그대로_둔다() {
        Map<String, Object> seasons = new LinkedHashMap<>();
        seasons.put("ALL", new LinkedHashMap<>(Map.of("warn", 19.8, "alarm", 22.5)));
        seasons.put("SUMMER", new LinkedHashMap<>(Map.of("warn", 21.0, "alarm", 24.0)));

        ThresholdService.fillSeasons(seasons);

        assertThat(seasons).containsKeys("ALL", "SPRING_FALL", "SUMMER", "WINTER");
        @SuppressWarnings("unchecked")
        Map<String, Object> winter = (Map<String, Object>) seasons.get("WINTER");
        assertThat(winter).containsEntry("warn", 19.8).containsEntry("alarm", 22.5).containsEntry("inherited", true);
        @SuppressWarnings("unchecked")
        Map<String, Object> summer = (Map<String, Object>) seasons.get("SUMMER");
        assertThat(summer).containsEntry("warn", 21.0).doesNotContainKey("inherited");
        @SuppressWarnings("unchecked")
        Map<String, Object> all = (Map<String, Object>) seasons.get("ALL");
        assertThat(all).doesNotContainKey("inherited");
    }

    @Test
    void ALL_도_없으면_채우지_않는다() {
        Map<String, Object> seasons = new LinkedHashMap<>();
        ThresholdService.fillSeasons(seasons);
        assertThat(seasons).isEmpty();
    }
}
