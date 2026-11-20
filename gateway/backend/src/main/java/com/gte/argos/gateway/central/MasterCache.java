package com.gte.argos.gateway.central;

import com.gte.argos.gateway.central.CentralDto.Equipment;
import com.gte.argos.gateway.central.CentralDto.Factory;
import com.gte.argos.gateway.central.CentralDto.Master;
import com.gte.argos.gateway.central.CentralDto.Plant;
import com.gte.argos.gateway.central.CentralDto.Process;
import com.gte.argos.gateway.central.CentralDto.Sensor;
import com.gte.argos.gateway.central.CentralDto.Unit;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 중앙 서버 마스터(사업장·공장·공정·설비·센서·관제 단위)를 30초 동안 메모리에 둔다.
 * 연계 DB 에는 마스터 테이블을 두지 않는다 (중앙이 원본).
 */
@Component
public class MasterCache {

    private static final Duration TTL = Duration.ofSeconds(30);

    private final CentralClient central;
    private volatile Snapshot snapshot;
    private volatile Instant loadedAt = Instant.EPOCH;

    public MasterCache(CentralClient central) {
        this.central = central;
    }

    public Snapshot get() {
        Snapshot s = snapshot;
        if (s == null || Instant.now().isAfter(loadedAt.plus(TTL))) {
            synchronized (this) {
                if (snapshot == null || Instant.now().isAfter(loadedAt.plus(TTL))) {
                    snapshot = new Snapshot(central.master());
                    loadedAt = Instant.now();
                }
                s = snapshot;
            }
        }
        return s;
    }

    /** 설비 추가·센서 변경 뒤 바로 다시 읽게 */
    public void invalidate() {
        loadedAt = Instant.EPOCH;
    }

    /** 마스터 한 벌과 찾기용 색인 */
    public static final class Snapshot {
        public final Master master;
        private final Map<Long, Plant> plants = new HashMap<>();
        private final Map<Long, Factory> factories = new HashMap<>();
        private final Map<Long, Process> processes = new HashMap<>();
        private final Map<Long, Equipment> equipment = new HashMap<>();
        private final Map<Long, Sensor> sensors = new HashMap<>();
        private final Map<Long, Unit> units = new HashMap<>();

        public Snapshot(Master m) {
            this.master = m;
            m.plants().forEach(x -> plants.put(x.id(), x));
            m.factories().forEach(x -> factories.put(x.id(), x));
            m.processes().forEach(x -> processes.put(x.id(), x));
            m.equipment().forEach(x -> equipment.put(x.id(), x));
            m.sensors().forEach(x -> sensors.put(x.id(), x));
            m.units().forEach(x -> units.put(x.id(), x));
        }

        public Plant plant(long id) {
            return plants.get(id);
        }

        public Factory factory(long id) {
            return factories.get(id);
        }

        public Process process(long id) {
            return processes.get(id);
        }

        public Equipment equipment(long id) {
            return equipment.get(id);
        }

        public Sensor sensor(long id) {
            return sensors.get(id);
        }

        public Unit unit(long id) {
            return units.get(id);
        }

        /** 관제 단위 전부 */
        public java.util.Collection<Unit> units() {
            return units.values();
        }

        public List<Plant> plants() {
            return master.plants();
        }

        public List<Factory> factoriesOf(long plantId) {
            return master.factories().stream().filter(f -> f.plantId() == plantId).toList();
        }

        public List<Process> processesOf(long factoryId) {
            return master.processes().stream().filter(p -> p.factoryId() == factoryId).toList();
        }

        public List<Equipment> equipmentOf(long processId) {
            return master.equipment().stream().filter(e -> e.processId() == processId).toList();
        }

        public List<Sensor> sensorsOf(long equipmentId) {
            return master.sensors().stream().filter(s -> s.equipmentId() == equipmentId).toList();
        }

        /** 공정 안의 센서 전부 */
        public List<Sensor> sensorsOfProcess(long processId) {
            List<Sensor> out = new ArrayList<>();
            for (Equipment e : equipmentOf(processId)) {
                out.addAll(sensorsOf(e.id()));
            }
            return out;
        }

        /** 센서가 속한 공정 id, 없으면 null */
        public Long processOfSensor(long sensorId) {
            Sensor s = sensors.get(sensorId);
            Equipment e = s == null ? null : equipment.get(s.equipmentId());
            return e == null ? null : e.processId();
        }

        /** 공정이 속한 공장 id */
        public Long factoryOfProcess(long processId) {
            Process p = processes.get(processId);
            return p == null ? null : p.factoryId();
        }

        /** 공장이 속한 사업장 id */
        public Long plantOfFactory(long factoryId) {
            Factory f = factories.get(factoryId);
            return f == null ? null : f.plantId();
        }

        /** "5공장 · 도장 공정 · 상도 건조로" */
        public String location(long processId, Long equipmentId) {
            Process p = processes.get(processId);
            Factory f = p == null ? null : factories.get(p.factoryId());
            Equipment e = equipmentId == null ? null : equipment.get(equipmentId);
            StringBuilder sb = new StringBuilder();
            if (f != null) {
                sb.append(f.name());
            }
            if (p != null) {
                sb.append(sb.isEmpty() ? "" : " · ").append(p.name());
            }
            if (e != null) {
                sb.append(sb.isEmpty() ? "" : " · ").append(e.name());
            }
            return sb.toString();
        }
    }
}
