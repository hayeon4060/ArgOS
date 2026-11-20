package com.gte.argos.gateway.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.gte.argos.gateway.central.CentralDto;
import com.gte.argos.gateway.central.MasterCache;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserScopeTest {

    // 울산(1) → 5공장(5) → 도장(12), 차체(11) / 울산 4공장(4) → 공정(9) / 아산(2) → 1공장(21) → 공정(30)
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

    @Test
    void 본사_관리자는_전부() {
        UserScope hq = new UserScope(1, UserScope.HQ_ADMIN, null, Set.of(), Set.of(), Set.of());
        assertThat(hq.canSite(2, m)).isEqualTo(true);
        assertThat(hq.canProcess(30, m)).isEqualTo(true);
    }

    @Test
    void 공장_담당자는_담당_공장과_그_공정만() {
        UserScope admin = new UserScope(7, UserScope.PLANT_ADMIN, 1L, Set.of(), Set.of(5L), Set.of());
        assertThat(admin.canSite(1, m)).isEqualTo(true);
        assertThat(admin.canSite(2, m)).isEqualTo(false);
        assertThat(admin.canFactory(5, m)).isEqualTo(true);
        assertThat(admin.canFactory(4, m)).isEqualTo(false);
        assertThat(admin.canProcess(12, m)).isEqualTo(true);
        assertThat(admin.canProcess(9, m)).isEqualTo(false);
    }

    @Test
    void 사업장_전체를_맡은_공장_담당자() {
        UserScope admin = new UserScope(8, UserScope.PLANT_ADMIN, 1L, Set.of(1L), Set.of(), Set.of());
        assertThat(admin.canFactory(4, m)).isEqualTo(true);
        assertThat(admin.canProcess(9, m)).isEqualTo(true);
        assertThat(admin.canProcess(30, m)).isEqualTo(false);
    }

    @Test
    void 공정_담당자는_배정_공정만() {
        UserScope worker = new UserScope(21, UserScope.WORKER, 1L, Set.of(), Set.of(), Set.of(12L));
        assertThat(worker.canProcess(12, m)).isEqualTo(true);
        assertThat(worker.canProcess(11, m)).isEqualTo(false);
        assertThat(worker.canFactory(5, m)).isEqualTo(true);
    }
}
