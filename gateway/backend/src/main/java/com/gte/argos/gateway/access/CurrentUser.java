package com.gte.argos.gateway.access;

import com.gte.argos.gateway.auth.AuthRepository;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.common.api.ApiException;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 지금 요청의 사용자와 담당 범위. 로그인 쪽(auth)이 넣어 준 인증 정보를 읽기만 한다.
 * 인증 이름 = user_id, 권한 = ROLE_HQ_ADMIN 같은 값, 담당 범위 = user_assignment.
 */
@Component
public class CurrentUser {

    private final AuthRepository auth;
    private final MasterCache master;

    public CurrentUser(AuthRepository auth, MasterCache master) {
        this.auth = auth;
        this.master = master;
    }

    public UserScope scope() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a.getName() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "로그인이 필요합니다.");
        }
        long userId;
        try {
            userId = Long.parseLong(a.getName());
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "로그인이 필요합니다.");
        }
        String role = null;
        for (GrantedAuthority g : a.getAuthorities()) {
            String s = g.getAuthority();
            if (s != null && s.startsWith("ROLE_")) {
                role = s.substring(5);
            }
        }
        AuthRepository.User user = auth.findActiveUser(userId);
        Set<Long> plants = new HashSet<>();
        Set<Long> factories = new HashSet<>();
        Set<Long> processes = new HashSet<>();
        Long siteId = user == null ? null : user.siteId();
        for (AuthRepository.Assignment as : auth.assignments(userId)) {
            if (as.processId() != null) {
                processes.add(as.processId());
            } else if (as.factoryId() != null) {
                factories.add(as.factoryId());
            } else if (as.siteId() != null) {
                plants.add(as.siteId());
            }
        }
        // 공장 배정이 하나도 없는 공장 담당자는 소속 사업장 전체를 맡는 것으로 본다
        if (UserScope.PLANT_ADMIN.equals(role) && factories.isEmpty() && plants.isEmpty() && siteId != null) {
            plants.add(siteId);
        }
        if (UserScope.WORKER.equals(role)) {
            plants.clear(); // 공정 담당자는 배정 공정만
        }
        return new UserScope(userId, role, siteId, plants, factories, processes);
    }

    /** 권한 확인. 아니면 403 FORBIDDEN */
    public UserScope require(String... roles) {
        UserScope s = scope();
        for (String r : roles) {
            if (r.equals(s.role())) {
                return s;
            }
        }
        throw ApiException.forbidden("FORBIDDEN", "권한이 없습니다.");
    }

    public void checkSite(UserScope s, long plantId) {
        if (!s.canSite(plantId, master.get())) {
            throw ApiException.outOfScope();
        }
    }

    public void checkFactory(UserScope s, long factoryId) {
        if (!s.canFactory(factoryId, master.get())) {
            throw ApiException.outOfScope();
        }
    }

    public void checkProcess(UserScope s, long processId) {
        if (!s.canProcess(processId, master.get())) {
            throw ApiException.outOfScope();
        }
    }
}
