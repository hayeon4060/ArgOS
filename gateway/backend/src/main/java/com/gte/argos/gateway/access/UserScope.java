package com.gte.argos.gateway.access;

import com.gte.argos.gateway.central.MasterCache;
import java.util.Set;

/**
 * 로그인한 사용자의 권한과 담당 범위.
 *
 * <pre>
 * HQ_ADMIN    모든 사업장
 * PLANT_ADMIN 담당 공장 (배정에 사업장만 있으면 그 사업장의 모든 공장)
 * WORKER      배정된 공정
 * </pre>
 * 화면에서 메뉴를 숨기는 것과 별개로, 서버는 요청마다 이 범위를 확인한다.
 */
public record UserScope(long userId, String role, Long siteId, Set<Long> plantIds, Set<Long> factoryIds,
                        Set<Long> processIds) {

    public static final String HQ_ADMIN = "HQ_ADMIN";
    public static final String PLANT_ADMIN = "PLANT_ADMIN";
    public static final String WORKER = "WORKER";

    public boolean isHq() {
        return HQ_ADMIN.equals(role);
    }

    public boolean isPlantAdmin() {
        return PLANT_ADMIN.equals(role);
    }

    public boolean isWorker() {
        return WORKER.equals(role);
    }

    public boolean canSite(long plantId, MasterCache.Snapshot m) {
        if (isHq()) {
            return true;
        }
        if (plantIds.contains(plantId) || (siteId != null && siteId == plantId)) {
            return true;
        }
        for (Long f : factoryIds) {
            Long p = m.plantOfFactory(f);
            if (p != null && p == plantId) {
                return true;
            }
        }
        return false;
    }

    public boolean canFactory(long factoryId, MasterCache.Snapshot m) {
        if (isHq() || factoryIds.contains(factoryId)) {
            return true;
        }
        Long plant = m.plantOfFactory(factoryId);
        if (plant != null && plantIds.contains(plant) && !isWorker()) {
            return true;
        }
        if (isWorker()) {
            for (Long p : processIds) {
                Long f = m.factoryOfProcess(p);
                if (f != null && f == factoryId) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean canProcess(long processId, MasterCache.Snapshot m) {
        if (isHq() || processIds.contains(processId)) {
            return true;
        }
        if (isWorker()) {
            return false;
        }
        Long f = m.factoryOfProcess(processId);
        return f != null && canFactory(f, m);
    }
}
