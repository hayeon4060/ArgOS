# 배기팬 자동 제어 — DB 변경 (2026-11-19)

> **2026-11-19 최종 ERD 확정 뒤 달라진 값이 있습니다** (명령 `FAN_HZ` · Hz 단위, `source` 에 `RECOVERY`, 수동 승인 등). 최종 값은 `docs/api/erd-final-frontend-check.md` 6장을 보세요.

API 변경은 같은 폴더 `fan-auto-api.md`. 새 테이블은 없고, 컬럼 2개 추가 + CHECK 1개 수정입니다.
기준값 관리를 공장 관리 창으로 옮긴 것은 DB 변경이 없습니다.

## 1. `equipment.fan_auto` (새 컬럼)

배기팬 자동/수동 스위치 값. 공장 관리 › 설비 정보 › '배기팬 자동 제어'.

```sql
ALTER TABLE equipment ADD COLUMN fan_auto boolean;   -- 배기팬만 값, 그 밖의 설비는 NULL

-- 지금 있는 배기팬은 모두 자동으로 시작
UPDATE equipment SET fan_auto = true WHERE equipment_type = '배기팬';

COMMENT ON COLUMN equipment.fan_auto IS '배기팬 자동 제어 — true 자동(가스 주의 75%·경보 100%로 서버가 회전수를 올림) / false 수동(안내만) / 배기팬이 아니면 NULL';
```

- 읽는 곳: `GET /processes/{id}/layout` (`equipment[].fanAuto`), 가스 알림이 생길 때 서버의 자동 제어
- 쓰는 곳: `PUT /processes/{id}/layout` (`equipment.update[].fanAuto`)
- 배기팬이 별도 행(`ED-FAN`·`MID-FAN`·`TOP-FAN`)이라 값은 그 행에 둡니다. 화면 설비(건조로)와 잇는 방법은 API 문서 5장 1번 질문.

## 2. `equipment_command` — 자동 명령 구분

자동 명령은 누른 사람이 없어서 `requested_by NOT NULL` 에 넣을 값이 없습니다.

```sql
ALTER TABLE equipment_command
  ADD COLUMN source varchar(6) NOT NULL DEFAULT 'USER' CHECK (source IN ('USER', 'AUTO'));

ALTER TABLE equipment_command ALTER COLUMN requested_by DROP NOT NULL;   -- AUTO 는 NULL

-- (전부터 있던 차이) 백엔드 EquipmentActionService.fan() 이 FAN_OFF 를 보냄 → CHECK 에 추가
ALTER TABLE equipment_command DROP CONSTRAINT IF EXISTS equipment_command_command_check;
ALTER TABLE equipment_command
  ADD CONSTRAINT equipment_command_command_check CHECK (command IN ('FAN_ON', 'FAN_OFF', 'FAN_RPM'));

COMMENT ON COLUMN equipment_command.source IS '누가 보냈나 — USER(사람이 화면에서) / AUTO(가스 알림 때 서버 자동 제어)';
```

- 자동 명령 한 줄 예: `equipment_id = TOP-FAN, alarm_id = 1042, command = 'FAN_RPM', value = 1350, source = 'AUTO', requested_by = NULL, result = 'PENDING' → 'OK'`
- 알림 응답의 `fanAuto` 는 이 표에서 `alarm_id` + `source = 'AUTO'` 인 가장 최근 줄로 만듭니다
  (`result` PENDING → `SENT`, OK → `APPLIED`, FAIL → `FAILED`. 수동이라 명령이 없으면 `SKIPPED`, 이때 `mode = 'MANUAL'`)
- 수동일 때 '안 보냈다'는 기록이 필요하면 줄을 남기지 말고, 알림이 났을 때의 `fan_auto` 값만 알면 됩니다 (알림 응답 만들 때 `equipment.fan_auto` 를 읽으면 나중에 스위치를 바꾼 값이 보이는 점은 감안)

## 3. 바뀌지 않는 것

- `alarm`(`hazard` = GAS 로 판단), `threshold`·`threshold_history`(기준값 창은 같은 테이블), `activity_log`(`type = 'FAN'`, `'LAYOUT'` 그대로)
