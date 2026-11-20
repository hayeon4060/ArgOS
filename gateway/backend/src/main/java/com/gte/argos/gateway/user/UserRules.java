package com.gte.argos.gateway.user;

import com.gte.argos.gateway.access.UserScope;
import com.gte.argos.gateway.central.CentralDto;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.common.api.ApiException;
import com.gte.argos.gateway.user.UserRepository.Assignment;
import com.gte.argos.gateway.user.UserRepository.UserRow;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * 사용자 관리 규칙 (화면설계서 G1 '권한별로 할 수 있는 범위'). DB 없이 시험할 수 있게 따로 둔다.
 *
 * <pre>
 * 본사 관리자  모든 사용자. 줄 수 있는 권한: 본사 관리자 · 공장 담당자 · 공정 담당자
 * 공장 담당자  담당 공장의 공장 담당자 · 공정 담당자만. 줄 수 있는 권한: 공장 담당자 · 공정 담당자 (담당 공장 안에서)
 * 공정 담당자  사용자 관리 없음 (PUT /me 만)
 * 자기 자신은 권한 변경 · 정지 · 삭제 불가
 * </pre>
 */
final class UserRules {

    static final List<String> ROLES = List.of(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN, UserScope.WORKER);
    static final Map<String, String> ROLE_LABEL = Map.of(
            UserScope.HQ_ADMIN, "본사 관리자", UserScope.PLANT_ADMIN, "공장 담당자", UserScope.WORKER, "공정 담당자");

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TEMP_CHARS = "abcdefghjkmnpqrstuvwxyz23456789";

    private UserRules() {
    }

    /** 정리한 담당 범위 */
    record Scope(Long siteId, List<Long> factoryIds, List<Long> processIds) {

        static final Scope NONE = new Scope(null, List.of(), List.of());

        List<Assignment> assignments(MasterCache.Snapshot m) {
            List<Assignment> out = new ArrayList<>();
            if (!processIds.isEmpty()) {
                for (Long p : processIds) {
                    out.add(new Assignment(siteId, m.factoryOfProcess(p), p));
                }
            } else {
                for (Long f : factoryIds) {
                    out.add(new Assignment(siteId, f, null));
                }
            }
            return out;
        }
    }

    /** 저장된 배정 → 담당 범위 (공정만 배정된 줄도 그 공정의 공장을 담당 공장으로 본다) */
    static Scope scopeOf(UserRow u, MasterCache.Snapshot m) {
        Set<Long> factories = new LinkedHashSet<>();
        Set<Long> processes = new LinkedHashSet<>();
        Long site = u.plantId();
        for (Assignment a : u.assignments()) {
            if (site == null) {
                site = a.plantId();
            }
            if (a.factoryId() != null) {
                factories.add(a.factoryId());
            }
            if (a.processId() != null) {
                processes.add(a.processId());
                Long f = m.factoryOfProcess(a.processId());
                if (f != null) {
                    factories.add(f);
                }
            }
        }
        return new Scope(site, List.copyOf(factories), List.copyOf(processes));
    }

    /**
     * 요청한 권한 · 담당 범위를 검사하고 정리한다.
     * 본사 관리자: 범위 없음 / 공장 담당자: 공장 1개 이상 / 공정 담당자: 공정 1개 이상 (공장은 공정에서 정함)
     */
    static Scope normalize(String role, List<Long> factoryIds, List<Long> processIds, MasterCache.Snapshot m) {
        if (!ROLES.contains(role)) {
            throw new IllegalArgumentException("role");
        }
        if (UserScope.HQ_ADMIN.equals(role)) {
            return Scope.NONE;
        }
        Set<Long> factories = new LinkedHashSet<>();
        Set<Long> processes = new LinkedHashSet<>();
        if (UserScope.WORKER.equals(role)) {
            if (processIds == null || processIds.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SCOPE_REQUIRED", "공정을 한 개 이상 선택해 주세요.");
            }
            for (Long p : processIds) {
                Long f = p == null ? null : m.factoryOfProcess(p);
                if (f == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "없는 공정입니다: " + p);
                }
                processes.add(p);
                factories.add(f);
            }
        } else {
            if (factoryIds == null || factoryIds.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SCOPE_REQUIRED", "공장을 선택해 주세요.");
            }
            for (Long f : factoryIds) {
                if (f == null || m.factory(f) == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "없는 공장입니다: " + f);
                }
                factories.add(f);
            }
        }
        Set<Long> sites = new LinkedHashSet<>();
        for (Long f : factories) {
            sites.add(m.plantOfFactory(f));
        }
        if (sites.size() != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "담당 범위는 한 사업장 안에서만 정할 수 있습니다.");
        }
        return new Scope(sites.iterator().next(), List.copyOf(factories), List.copyOf(processes));
    }

    /** 이 사람(actor)이 볼 · 고칠 수 있는 사용자인가 */
    static boolean canSee(UserScope actor, UserRow u, Scope target, MasterCache.Snapshot m) {
        if (actor.isHq()) {
            return true;
        }
        if (!actor.isPlantAdmin()) {
            return u.id() == actor.userId();
        }
        if (u.id() == actor.userId()) {
            return true;
        }
        if (UserScope.HQ_ADMIN.equals(u.role())) {
            return false;
        }
        if (target.factoryIds().isEmpty()) {
            // 공장 배정 없이 사업장만 있는 사람은 사업장 전체를 맡은 공장 담당자만 본다
            return target.siteId() != null && actor.plantIds().contains(target.siteId());
        }
        return target.factoryIds().stream().anyMatch(f -> actor.canFactory(f, m));
    }

    /** 줄 수 있는 권한과 담당 범위인가 */
    static void checkGrant(UserScope actor, String role, Scope scope, MasterCache.Snapshot m) {
        if (UserScope.HQ_ADMIN.equals(role) && !actor.isHq()) {
            throw ApiException.forbidden("ROLE_NOT_ALLOWED", "줄 수 없는 권한입니다.");
        }
        if (actor.isHq()) {
            return;
        }
        boolean ok = scope.factoryIds().stream().allMatch(f -> actor.canFactory(f, m))
                && scope.processIds().stream().allMatch(p -> actor.canProcess(p, m));
        if (!ok) {
            throw ApiException.forbidden("OUT_OF_SCOPE", "담당 공장 사용자만 다룰 수 있습니다.");
        }
    }

    /** 담당 범위 문구: 모든 사업장 / 5공장 전체 / 5공장 · 도장 공정 */
    static String scopeText(String role, Scope s, MasterCache.Snapshot m) {
        if (UserScope.HQ_ADMIN.equals(role)) {
            return "모든 사업장";
        }
        List<String> factories = s.factoryIds().stream().map(m::factory).filter(f -> f != null)
                .map(CentralDto.Factory::name).toList();
        if (factories.isEmpty()) {
            CentralDto.Plant p = s.siteId() == null ? null : m.plant(s.siteId());
            return p != null && UserScope.PLANT_ADMIN.equals(role) ? p.name() + " 전체" : "공장 미배정";
        }
        if (UserScope.PLANT_ADMIN.equals(role)) {
            return String.join(", ", factories) + " 전체";
        }
        List<String> processes = s.processIds().stream().map(m::process).filter(p -> p != null)
                .map(CentralDto.Process::name).toList();
        return String.join(", ", factories) + " · " + (processes.isEmpty() ? "공정 미배정" : String.join(", ", processes));
    }

    /** 이 공장 담당자를 빼면 담당 공장 담당자가 아무도 안 남는 공장이 있는가 */
    static boolean isLastPlantAdmin(UserRow target, Scope targetScope, List<UserRow> all, MasterCache.Snapshot m) {
        if (!UserScope.PLANT_ADMIN.equals(target.role()) || !target.active()) {
            return false;
        }
        for (Long f : coveredFactories(targetScope, m)) {
            boolean other = all.stream().anyMatch(u -> u.id() != target.id() && u.active()
                    && UserScope.PLANT_ADMIN.equals(u.role())
                    && coveredFactories(scopeOf(u, m), m).contains(f));
            if (!other) {
                return true;
            }
        }
        return false;
    }

    /** 공장 담당자가 맡는 공장 (공장 배정이 없으면 사업장의 모든 공장) */
    private static Set<Long> coveredFactories(Scope s, MasterCache.Snapshot m) {
        Set<Long> out = new LinkedHashSet<>(s.factoryIds());
        if (out.isEmpty() && s.siteId() != null) {
            m.factoriesOf(s.siteId()).forEach(f -> out.add(f.id()));
        }
        return out;
    }

    /** 임시 비밀번호 (예: Arg-k7m2qx4). 비밀번호 규칙(영문 · 숫자 · 특수문자, 8자 이상)에 맞춘다 */
    static String tempPassword() {
        StringBuilder sb = new StringBuilder("Arg-");
        for (int i = 0; i < 6; i++) {
            sb.append(TEMP_CHARS.charAt(RANDOM.nextInt(TEMP_CHARS.length())));
        }
        sb.append(2 + RANDOM.nextInt(8));
        return sb.toString();
    }
}
