package com.gte.argos.gateway.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gte.argos.gateway.access.UserScope;
import com.gte.argos.gateway.central.CentralDto;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.common.api.ApiException;
import com.gte.argos.gateway.user.UserRepository.Assignment;
import com.gte.argos.gateway.user.UserRepository.UserRow;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserRulesTest {

    // 울산(1) → 5공장(5) → 도장(12), 차체(11) / 울산 4공장(4) → 도장(9) / 아산(2) → 1공장(21) → 도장(30)
    private final MasterCache.Snapshot m = new MasterCache.Snapshot(new CentralDto.Master(
            List.of(new CentralDto.Plant(1, "USN", "울산공장", null, null, null),
                    new CentralDto.Plant(2, "ASN", "아산공장", null, null, null)),
            List.of(new CentralDto.Factory(5, 1, "P5", "5공장", 5, null),
                    new CentralDto.Factory(4, 1, "P4", "4공장", 4, null),
                    new CentralDto.Factory(21, 2, "P1", "1공장", 1, null)),
            List.of(new CentralDto.Process(12, 5, "PNT", "도장 공정", "3", null),
                    new CentralDto.Process(11, 5, "BDY", "차체 공정", "2", null),
                    new CentralDto.Process(9, 4, "PNT", "도장 공정", "3", null),
                    new CentralDto.Process(30, 21, "PNT", "도장 공정", "3", null)),
            List.of(), List.of(), List.of()));

    private final UserScope hq = new UserScope(1, UserScope.HQ_ADMIN, null, Set.of(), Set.of(), Set.of());
    private final UserScope plant5 = new UserScope(7, UserScope.PLANT_ADMIN, 1L, Set.of(), Set.of(5L), Set.of());

    private static UserRow user(long id, String role, boolean active, Assignment... as) {
        return new UserRow(id, role.equals(UserScope.HQ_ADMIN) ? null : 1L, "u" + id, "사람" + id, null, role, active,
                null, null, false, List.of(as));
    }

    private final UserRow hqUser = user(2, UserScope.HQ_ADMIN, true);
    private final UserRow worker5 = user(21, UserScope.WORKER, true, new Assignment(1L, 5L, 12L));
    private final UserRow worker4 = user(37, UserScope.WORKER, true, new Assignment(1L, null, 9L));
    private final UserRow admin5 = user(7, UserScope.PLANT_ADMIN, true, new Assignment(1L, 5L, null));

    private boolean sees(UserScope actor, UserRow u) {
        return UserRules.canSee(actor, u, UserRules.scopeOf(u, m), m);
    }

    @Test
    void 공장_담당자는_담당_공장_사람만_본다() {
        assertThat(sees(plant5, worker5)).isTrue();
        assertThat(sees(plant5, worker4)).isFalse();
        assertThat(sees(plant5, hqUser)).isFalse();
        assertThat(sees(hq, worker4)).isTrue();
        assertThat(sees(hq, hqUser)).isTrue();
    }

    @Test
    void 공정만_배정돼도_공장을_찾는다() {
        assertThat(UserRules.scopeOf(worker4, m).factoryIds()).containsExactly(4L);
        assertThat(UserRules.scopeText(UserScope.WORKER, UserRules.scopeOf(worker5, m), m)).isEqualTo("5공장 · 도장 공정");
        assertThat(UserRules.scopeText(UserScope.PLANT_ADMIN, UserRules.scopeOf(admin5, m), m)).isEqualTo("5공장 전체");
        assertThat(UserRules.scopeText(UserScope.HQ_ADMIN, UserRules.Scope.NONE, m)).isEqualTo("모든 사업장");
    }

    @Test
    void 공정_담당자는_공정이_있어야_하고_공장은_공정에서_정한다() {
        UserRules.Scope s = UserRules.normalize(UserScope.WORKER, null, List.of(12L, 11L), m);
        assertThat(s.siteId()).isEqualTo(1L);
        assertThat(s.factoryIds()).containsExactly(5L);
        assertThat(s.assignments(m)).hasSize(2);
        assertThatThrownBy(() -> UserRules.normalize(UserScope.WORKER, List.of(5L), List.of(), m))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("SCOPE_REQUIRED");
        assertThatThrownBy(() -> UserRules.normalize(UserScope.PLANT_ADMIN, List.of(5L, 21L), null, m))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void 공장_담당자는_본사_권한이나_다른_공장을_줄_수_없다() {
        UserRules.Scope p4 = UserRules.normalize(UserScope.WORKER, null, List.of(9L), m);
        assertThatThrownBy(() -> UserRules.checkGrant(plant5, UserScope.HQ_ADMIN, UserRules.Scope.NONE, m))
                .extracting("code").isEqualTo("ROLE_NOT_ALLOWED");
        assertThatThrownBy(() -> UserRules.checkGrant(plant5, UserScope.WORKER, p4, m))
                .extracting("code").isEqualTo("OUT_OF_SCOPE");
        UserRules.checkGrant(plant5, UserScope.WORKER, UserRules.normalize(UserScope.WORKER, null, List.of(12L), m), m);
        UserRules.checkGrant(hq, UserScope.HQ_ADMIN, UserRules.Scope.NONE, m);
    }

    @Test
    void 마지막_공장_담당자() {
        UserRow other5 = user(8, UserScope.PLANT_ADMIN, true, new Assignment(1L, 5L, null));
        UserRow stopped5 = user(9, UserScope.PLANT_ADMIN, false, new Assignment(1L, 5L, null));
        UserRow wholeSite = user(10, UserScope.PLANT_ADMIN, true); // 공장 배정 없음 = 울산 전체
        UserRules.Scope s = UserRules.scopeOf(admin5, m);
        assertThat(UserRules.isLastPlantAdmin(admin5, s, List.of(admin5, stopped5), m)).isTrue();
        assertThat(UserRules.isLastPlantAdmin(admin5, s, List.of(admin5, other5), m)).isFalse();
        assertThat(UserRules.isLastPlantAdmin(admin5, s, List.of(admin5, wholeSite), m)).isFalse();
        assertThat(UserRules.isLastPlantAdmin(worker5, UserRules.scopeOf(worker5, m), List.of(worker5), m)).isFalse();
    }

    @Test
    void 임시_비밀번호는_비밀번호_규칙에_맞다() {
        for (int i = 0; i < 50; i++) {
            assertThat(UserRules.tempPassword()).matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");
        }
    }

    @Test
    void 변경_기록에서는_대상_이름을_뺀다() {
        assertThat(UserService.historyText("김도현(u5_paint01) · 사용 정지")).isEqualTo("사용 정지");
        assertThat(UserService.historyText("본인 비밀번호 변경")).isEqualTo("본인 비밀번호 변경");
    }
}
