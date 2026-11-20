package com.gte.argos.factory.query;

import com.gte.argos.factory.common.api.ApiException;
import com.gte.argos.factory.common.time.DemoClock;
import com.gte.argos.factory.live.LiveState;
import com.gte.argos.factory.query.InternalDto.EquipmentStatus;
import com.gte.argos.factory.query.InternalDto.SensorSeries;
import com.gte.argos.factory.query.InternalDto.SensorStatus;
import com.gte.argos.factory.query.InternalQueryRepository.EquipmentRow;
import com.gte.argos.factory.query.InternalQueryRepository.SensorRow;
import com.gte.argos.factory.query.InternalQueryRepository.TrendValue;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 현재 상태 계산과 추이 조회 */
@Service
public class InternalQueryService {

    private static final Duration MAX_RANGE = Duration.ofDays(31);

    private final InternalQueryRepository repo;
    private final LiveState live;
    private final DemoClock clock;

    public InternalQueryService(InternalQueryRepository repo, LiveState live, DemoClock clock) {
        this.repo = repo;
        this.live = live;
        this.clock = clock;
    }

    public InternalDto.Master master() {
        return new InternalDto.Master(repo.plants(), repo.factories(), repo.processes(), repo.equipment(),
                repo.sensors(), repo.units());
    }

    /**
     * 지금 값과 단계. 단계는 진행 중 이벤트로 계산해 설비 → 공정 → 공장 → 사업장으로 올려 묶는다.
     *
     * @param processId 주면 그 공정의 센서·설비만 (levels 는 항상 전체)
     */
    public InternalDto.Status status(Long processId) {
        List<SensorRow> sensors = repo.sensorRows();
        Map<Long, Integer> open = repo.openStages();
        Map<Long, TrendValue> trends = repo.lastTrends();

        Map<Long, Integer> byEquipment = new HashMap<>();
        Map<Long, Integer> byProcess = new HashMap<>();
        Map<Long, Integer> byFactory = new HashMap<>();
        Map<Long, Integer> byPlant = new HashMap<>();
        Map<Long, Integer> bySensor = new HashMap<>();
        List<SensorStatus> sensorOut = new ArrayList<>();

        for (SensorRow s : sensors) {
            int stage = open.getOrDefault(s.sensorId(), 1);
            bySensor.put(s.sensorId(), stage);
            byEquipment.merge(s.equipmentId(), stage, Math::max);
            byProcess.merge(s.processId(), stage, Math::max);
            byFactory.merge(s.factoryId(), stage, Math::max);
            byPlant.merge(s.plantId(), stage, Math::max);
            if (processId != null && processId != s.processId()) {
                continue;
            }
            Optional<LiveState.Point> latest = live.latest(s.sensorId());
            Double value = null;
            java.time.OffsetDateTime at = null;
            boolean isLive = false;
            if (latest.isPresent()) {
                value = latest.get().value();
                at = latest.get().ts().atZone(clock.zone()).toOffsetDateTime();
                isLive = true;
            } else if (trends.containsKey(s.sensorId())) {
                value = trends.get(s.sensorId()).avg();
                at = trends.get(s.sensorId()).ts();
            }
            sensorOut.add(new SensorStatus(s.sensorId(), s.tag(), s.type(), s.unit(), s.equipmentId(), s.processId(),
                    value, at, isLive, stage, s.warn(), s.alarm(), s.lowWarn(), s.lowAlarm()));
        }

        Map<Long, Integer> byUnit = new HashMap<>();
        repo.unitSensors().forEach((unitId, members) -> {
            int max = 1;
            for (Long sensorId : members) {
                max = Math.max(max, bySensor.getOrDefault(sensorId, 1));
            }
            byUnit.put(unitId, max);
        });

        List<EquipmentStatus> equipmentOut = new ArrayList<>();
        for (EquipmentRow e : repo.equipmentRows()) {
            // 센서가 없는 공정·공장·사업장도 정상(1)으로 보이게
            byProcess.putIfAbsent(e.processId(), 1);
            byFactory.putIfAbsent(e.factoryId(), 1);
            byPlant.putIfAbsent(e.plantId(), 1);
            if (processId != null && processId != e.processId()) {
                continue;
            }
            equipmentOut.add(new EquipmentStatus(e.equipmentId(), e.processId(), e.runState(), e.fanRpm(),
                    byEquipment.getOrDefault(e.equipmentId(), 1)));
        }

        Map<String, Map<Long, Integer>> levels = new LinkedHashMap<>();
        levels.put("plants", byPlant);
        levels.put("factories", byFactory);
        levels.put("processes", byProcess);
        levels.put("units", byUnit);
        return new InternalDto.Status(clock.nowOffset(), sensorOut, equipmentOut, levels);
    }

    /**
     * 추이 그래프.
     *
     * @param step 5s | 1m | 10m. 비우면 1시간 이하 5s, 1일 이하 1m, 그 이상 10m.
     *             5s 는 실시간 메모리(최근 1시간)에만 있으므로, 없으면 1m 으로 바꿔 준다.
     */
    public InternalDto.Series series(List<Long> sensorIds, Instant from, Instant to, String step) {
        if (sensorIds == null || sensorIds.isEmpty() || !from.isBefore(to)) {
            throw new IllegalArgumentException("센서와 기간을 확인하세요");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RANGE_TOO_LARGE", "한 번에 31일까지 조회할 수 있습니다.");
        }
        Duration range = Duration.between(from, to);
        String s = step;
        if (s == null || s.isBlank()) {
            s = range.compareTo(Duration.ofHours(1)) <= 0 ? "5s" : range.compareTo(Duration.ofDays(1)) <= 0 ? "1m" : "10m";
        }

        Map<Long, SensorRow> meta = new HashMap<>();
        for (SensorRow r : repo.sensorRows()) {
            meta.put(r.sensorId(), r);
        }

        Map<Long, List<Object[]>> points = new LinkedHashMap<>();
        if ("5s".equals(s)) {
            boolean any = false;
            for (Long id : sensorIds) {
                List<Object[]> list = new ArrayList<>();
                for (LiveState.Point p : live.range(id, from, to)) {
                    list.add(new Object[] {p.ts().atZone(clock.zone()).toOffsetDateTime(), p.value(), p.value(), p.value()});
                }
                any |= !list.isEmpty();
                points.put(id, list);
            }
            if (!any) {
                s = "1m";
            }
        }
        if ("1m".equals(s)) {
            points = repo.trend1m(sensorIds, from, to);
        } else if ("10m".equals(s)) {
            points = repo.trend10m(sensorIds, from, to);
        } else if (!"5s".equals(s)) {
            throw new IllegalArgumentException("step 은 5s, 1m, 10m 중 하나");
        }

        List<SensorSeries> out = new ArrayList<>();
        for (Long id : sensorIds) {
            SensorRow m = meta.get(id);
            if (m == null) {
                continue;
            }
            out.add(new SensorSeries(id, m.tag(), m.unit(), m.warn(), m.alarm(), m.lowWarn(), m.lowAlarm(),
                    points.getOrDefault(id, List.of())));
        }
        return new InternalDto.Series(s, out);
    }
}
