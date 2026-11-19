package com.gte.argos.gateway.user;

import com.gte.argos.gateway.access.CurrentUser;
import com.gte.argos.gateway.access.UserScope;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.common.ActivityLogger;
import com.gte.argos.gateway.common.api.ApiException;
import com.gte.argos.gateway.user.UserRepository.UserRow;
import com.gte.argos.gateway.user.UserRules.Scope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 관리 (API 명세서 G1 · A1 PUT /me) */
@Service
public class UserService {

    private static final Pattern LOGIN_ID = Pattern.compile("^[a-z0-9_]{4,20}$");
    private static final String LOG_TYPE = "USER";
    private static final String TABLE = "app_user";

    private final UserRepository repo;
    private final CurrentUser current;
    private final MasterCache master;
    private final PasswordEncoder passwords;
    private final ActivityLogger activity;

    public UserService(UserRepository repo, CurrentUser current, MasterCache master, PasswordEncoder passwords,
                       ActivityLogger activity) {
        this.repo = repo;
        this.current = current;
        this.master = master;
        this.passwords = passwords;
        this.activity = activity;
    }

    public record ScopeBody(Long siteId, List<Long> factoryIds, List<Long> processIds) {
    }

    /** POST /users · PUT /users/{id} 본문 (수정 때 loginId 는 무시) */
    public record UserBody(String name, String loginId, String phone, String role, ScopeBody scope) {
    }

    public record ProfileBody(String name, String phone) {
    }

    /* ───────── 조회 ───────── */

    @Transactional(readOnly = true)
    public Map<String, Object> list(String role, Long factoryId, Boolean active, String q, int page, int size) {
        UserScope actor = admin();
        MasterCache.Snapshot m = master.get();
        String query = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        int pageSize = Math.max(1, Math.min(size, 100));
        int pageNo = Math.max(0, page);

        Map<String, Integer> counts = new LinkedHashMap<>();
        UserRules.ROLES.forEach(r -> counts.put(r, 0));
        int inactive = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        List<UserRow> all = new ArrayList<>(repo.findAll());
        all.sort(Comparator.comparingInt((UserRow u) -> UserRules.ROLES.indexOf(u.role())).thenComparingLong(UserRow::id));
        for (UserRow u : all) {
            Scope s = UserRules.scopeOf(u, m);
            if (!UserRules.canSee(actor, u, s, m)) {
                continue;
            }
            counts.merge(u.role(), 1, Integer::sum);
            if (!u.active()) {
                inactive++;
            }
            boolean match = (role == null || role.isBlank() || role.equals(u.role()))
                    && (factoryId == null || s.factoryIds().contains(factoryId))
                    && (active == null || active == u.active())
                    && (query == null || u.name().toLowerCase(Locale.ROOT).contains(query)
                    || u.loginId().toLowerCase(Locale.ROOT).contains(query));
            if (match) {
                rows.add(row(u, s, m));
            }
        }
        int from = Math.min(rows.size(), pageNo * pageSize);
        int to = Math.min(rows.size(), from + pageSize);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("counts", counts);
        out.put("inactiveCount", inactive);
        out.put("items", rows.subList(from, to));
        out.put("page", pageNo);
        out.put("size", pageSize);
        out.put("total", rows.size());
        return out;
    }

    /** 내 정보(GET /me)의 담당 범위 문구. 사용자가 없으면 null */
    @Transactional(readOnly = true)
    public String scopeTextOf(long userId) {
        UserRow u = repo.find(userId);
        if (u == null) {
            return null;
        }
        MasterCache.Snapshot m = master.get();
        return UserRules.scopeText(u.role(), UserRules.scopeOf(u, m), m);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(long id) {
        UserScope actor = admin();
        MasterCache.Snapshot m = master.get();
        UserRow u = visible(actor, id, m);
        Scope s = UserRules.scopeOf(u, m);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", u.id());
        out.put("name", u.name());
        out.put("loginId", u.loginId());
        out.put("role", u.role());
        out.put("phone", u.phone());
        out.put("active", u.active());
        out.put("scope", scopeView(s));
        out.put("scopeText", UserRules.scopeText(u.role(), s, m));
        out.put("createdAt", u.createdAt());
        out.put("lastLoginAt", u.lastLoginAt());
        out.put("pushReady", u.pushReady());
        List<Map<String, Object>> history = new ArrayList<>();
        for (UserRepository.HistoryRow h : repo.history(id, 50)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("at", h.at());
            item.put("by", h.by());
            item.put("text", historyText(h.summary()));
            history.add(item);
        }
        out.put("history", history);
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> checkLoginId(String loginId) {
        admin();
        String id = loginId == null ? "" : loginId.trim();
        return Map.of("available", LOGIN_ID.matcher(id).matches() && !repo.loginIdTaken(id));
    }

    /* ───────── 변경 ───────── */

    @Transactional
    public Map<String, Object> create(UserBody body) {
        UserScope actor = admin();
        MasterCache.Snapshot m = master.get();
        String loginId = body.loginId() == null ? "" : body.loginId().trim();
        if (!LOGIN_ID.matcher(loginId).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "아이디는 영문 소문자, 숫자, 밑줄로 4~20자입니다.");
        }
        if (repo.loginIdTaken(loginId)) {
            throw ApiException.conflict("LOGIN_ID_TAKEN", "이미 쓰는 아이디입니다.");
        }
        String name = name(body.name());
        String phone = phone(body.phone());
        Scope scope = scopeFromBody(body);
        UserRules.checkGrant(actor, body.role(), scope, m);

        String temp = UserRules.tempPassword();
        long id = repo.insert(scope.siteId(), body.role(), loginId, passwords.encode(temp), name, phone);
        repo.replaceAssignments(id, scope.assignments(m));
        log(actor, id, scope.siteId(), name, loginId,
                "계정 등록 (" + UserRules.ROLE_LABEL.get(body.role()) + ", " + UserRules.scopeText(body.role(), scope, m) + ")");
        return Map.of("id", id, "tempPassword", temp);
    }

    @Transactional
    public Map<String, Object> update(long id, UserBody body) {
        UserScope actor = admin();
        MasterCache.Snapshot m = master.get();
        UserRow u = visible(actor, id, m);
        Scope before = UserRules.scopeOf(u, m);
        String name = name(body.name());
        String phone = body.phone() == null ? u.phone() : phone(body.phone());
        Scope next = scopeFromBody(body);
        boolean roleChanged = !u.role().equals(body.role());
        if (roleChanged && u.id() == actor.userId()) {
            throw ApiException.forbidden("ROLE_NOT_ALLOWED", "내 권한은 직접 바꿀 수 없습니다.");
        }
        UserRules.checkGrant(actor, body.role(), next, m);

        boolean scopeChanged = !before.factoryIds().equals(next.factoryIds())
                || !before.processIds().equals(next.processIds());
        boolean dropsProcess = roleChanged || !next.processIds().containsAll(before.processIds());
        if (dropsProcess && repo.activeChecklists(id) > 0) {
            throw ApiException.conflict("HAS_ACTIVE_CHECKLIST", "진행 중인 체크리스트가 있어 공정을 뺄 수 없습니다.");
        }
        if ((roleChanged || !next.factoryIds().containsAll(before.factoryIds()))
                && UserRules.isLastPlantAdmin(u, before, repo.findAll(), m)) {
            throw ApiException.conflict("LAST_PLANT_ADMIN", "이 공장에 공장 담당자가 한 명은 있어야 합니다.");
        }

        repo.update(id, next.siteId(), body.role(), name, phone);
        if (roleChanged || scopeChanged) {
            repo.replaceAssignments(id, next.assignments(m));
        }
        String text = roleChanged ? UserRules.ROLE_LABEL.get(body.role()) + "로 변경"
                : scopeChanged ? "담당 범위 변경 (" + UserRules.scopeText(body.role(), next, m) + ")" : "정보 수정";
        log(actor, id, next.siteId(), name, u.loginId(), text);
        return Map.of("id", id, "role", body.role(), "scopeText", UserRules.scopeText(body.role(), next, m));
    }

    @Transactional
    public Map<String, Object> setActive(long id, Boolean active) {
        if (active == null) {
            throw new IllegalArgumentException("active");
        }
        UserScope actor = admin();
        MasterCache.Snapshot m = master.get();
        if (id == actor.userId()) {
            throw ApiException.conflict("CANNOT_DISABLE_SELF", "자기 자신은 정지할 수 없습니다.");
        }
        UserRow u = visible(actor, id, m);
        if (u.active() == active) {
            return Map.of("id", id, "active", active);
        }
        if (!active) {
            checkRemovable(u, m);
        }
        repo.setActive(id, active);
        log(actor, id, UserRules.scopeOf(u, m).siteId(), u.name(), u.loginId(), active ? "사용 재개" : "사용 정지");
        return Map.of("id", id, "active", active);
    }

    @Transactional
    public void delete(long id) {
        UserScope actor = admin();
        MasterCache.Snapshot m = master.get();
        if (id == actor.userId()) {
            throw ApiException.conflict("CANNOT_DELETE_SELF", "자기 자신은 삭제할 수 없습니다.");
        }
        UserRow u = visible(actor, id, m);
        checkRemovable(u, m);
        repo.markDeleted(id);
        log(actor, id, UserRules.scopeOf(u, m).siteId(), u.name(), u.loginId(), "계정 삭제");
    }

    @Transactional
    public Map<String, Object> resetPassword(long id) {
        UserScope actor = admin();
        MasterCache.Snapshot m = master.get();
        UserRow u = visible(actor, id, m);
        String temp = UserRules.tempPassword();
        repo.resetPassword(id, passwords.encode(temp));
        log(actor, id, UserRules.scopeOf(u, m).siteId(), u.name(), u.loginId(), "비밀번호 초기화");
        return Map.of("tempPassword", temp);
    }

    /** PUT /me : 내 이름 · 연락처 (모든 권한) */
    @Transactional
    public Map<String, Object> updateMe(ProfileBody body) {
        UserScope actor = current.scope();
        UserRow u = repo.find(actor.userId());
        if (u == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "다시 로그인해 주세요.");
        }
        String name = name(body.name());
        String phone = body.phone() == null ? u.phone() : phone(body.phone());
        repo.updateProfile(u.id(), name, phone);
        activity.log(actor.userId(), LOG_TYPE, u.plantId(), null, TABLE, u.id(), label(name, u.loginId()) + " · 내 정보 수정");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", u.id());
        out.put("name", name);
        out.put("phone", phone);
        return out;
    }

    /* ───────── 도움 ───────── */

    private UserScope admin() {
        return current.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
    }

    private UserRow visible(UserScope actor, long id, MasterCache.Snapshot m) {
        UserRow u = repo.find(id);
        if (u == null) {
            throw ApiException.notFound("NOT_FOUND", "없는 사용자입니다.");
        }
        if (!UserRules.canSee(actor, u, UserRules.scopeOf(u, m), m)) {
            throw ApiException.forbidden("OUT_OF_SCOPE", "다른 공장 사용자는 다룰 수 없습니다.");
        }
        return u;
    }

    /** 정지 · 삭제 전에: 진행 중 체크리스트, 마지막 공장 담당자 */
    private void checkRemovable(UserRow u, MasterCache.Snapshot m) {
        int checklists = repo.activeChecklists(u.id());
        if (checklists > 0) {
            throw ApiException.conflict("HAS_ACTIVE_CHECKLIST",
                    "진행 중인 체크리스트 " + checklists + "건이 있습니다. 담당자를 바꾼 뒤 처리하세요.");
        }
        if (UserRules.isLastPlantAdmin(u, UserRules.scopeOf(u, m), repo.findAll(), m)) {
            throw ApiException.conflict("LAST_PLANT_ADMIN", "이 공장에 공장 담당자가 한 명은 있어야 합니다.");
        }
    }

    private Scope scopeFromBody(UserBody body) {
        if (body.role() == null || !UserRules.ROLES.contains(body.role())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "권한을 선택해 주세요.");
        }
        ScopeBody s = body.scope();
        return UserRules.normalize(body.role(), s == null ? null : s.factoryIds(), s == null ? null : s.processIds(),
                master.get());
    }

    private static String name(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "이름을 1~100자로 입력해 주세요.");
        }
        return n;
    }

    private static String phone(String phone) {
        String p = phone == null ? null : phone.trim();
        if (p != null && p.length() > 30) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "연락처가 너무 깁니다.");
        }
        return p == null || p.isEmpty() ? null : p;
    }

    private Map<String, Object> row(UserRow u, Scope s, MasterCache.Snapshot m) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", u.id());
        r.put("name", u.name());
        r.put("loginId", u.loginId());
        r.put("role", u.role());
        r.put("scopeText", UserRules.scopeText(u.role(), s, m));
        r.put("factoryId", s.factoryIds().isEmpty() ? null : s.factoryIds().get(0));
        r.put("lastLoginAt", u.lastLoginAt());
        r.put("active", u.active());
        return r;
    }

    private static Map<String, Object> scopeView(Scope s) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("siteId", s.siteId());
        v.put("factoryIds", s.factoryIds());
        v.put("processIds", s.processIds());
        return v;
    }

    /** 이용 로그 한 줄: "김도현(u5_paint01) · 계정 등록 …" (이용 로그 화면에서 누구 얘기인지 보이게) */
    private void log(UserScope actor, long targetId, Long plantId, String name, String loginId, String text) {
        activity.log(actor.userId(), LOG_TYPE, plantId, null, TABLE, targetId, label(name, loginId) + " · " + text);
    }

    private static String label(String name, String loginId) {
        return name + "(" + loginId + ")";
    }

    /** 상세 화면 변경 기록에는 "누구 ·" 앞부분을 빼고 보여 준다 */
    static String historyText(String summary) {
        int i = summary == null ? -1 : summary.indexOf(" · ");
        return i < 0 ? summary : summary.substring(i + 3);
    }
}
