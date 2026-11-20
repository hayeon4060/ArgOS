# 새 공정에 설비 추가 — 백엔드 전달 (2026-11-20)

프론트 브랜치 `feature/frontend-equipment-add`. **새 API · ERD 변경은 없습니다.** 이미 있는 배치 저장 API의 `equipment.create` 를 처음으로 씁니다. 아래 3번의 확인 2건만 봐 주세요.

## 1. 화면에서 바뀐 것 (재원님 요청)

공장 관리에서 새로 추가한 공정은 설비 목록이 비어 있어서 놓을 설비가 없었습니다.

| 곳 | 바뀐 것 |
|---|---|
| 설비 목록 카드 | 맨 아래 '+ 설비 추가' (도장 공정 그림이 없는 공정만) |
| 설비 추가 창 | 지금 있는 설비 15종에서 여러 개 고르기: 도장 공정 설비 8종(전처리 · 전착 탱크 · 전착 건조로 · 실링 · 중도 부스 · 중도 건조로 · 상도 부스 · 상도 건조로), 보조 설비 7종(배기팬 · 버너실 · 펌프 · 열화상 카메라 · 공조기 · 도료 혼합실 · 소각설비) |
| 설비 배치도 | 도장 공정 그림 대신 빈 배치판(격자). 설비를 이름표 상자로 그리고 끌어서 옮김 |
| 저장 | 새 설비 · 옮기기 · 이름 바꾸기를 '저장' 한 번에 `PUT /processes/{processId}/layout` |

- 목록 위치: `gateway/frontend/src/features/factory/equipmentCatalog.js`
- 울산 5공장 도장 공정은 지금과 같습니다 (그림 위에서 옮기기만, '설비 추가' 없음).

## 2. 보내는 값 (`PUT /processes/{processId}/layout`)

```json
{
  "version": 1,
  "equipment": {
    "create": [
      { "tempId": "n1", "name": "전처리", "type": "TANK", "pos": { "x": 120, "y": 80, "z": null } },
      { "tempId": "n2", "name": "중도 부스", "type": null, "pos": { "x": 280, "y": 80, "z": null } }
    ],
    "update": [],
    "delete": []
  },
  "sensors": { "create": [], "update": [], "delete": [] }
}
```

- `code` 는 보내지 않습니다 → 서버가 `EQ-{processId}-{n}` 로 만듦 (`MasterAdminService.code`).
- `pos` 는 빈 배치판 좌표입니다 (가로 1120 × 세로 640, 격자 40, 설비 가운데 점). 도장 공정 그림 좌표와는 다른 판이라, 공정마다 한 가지 좌표만 씁니다.
- 응답의 `created.equipment` (`{ "n1": 52 }`)로 고르고 있던 새 설비를 실제 id 로 바꿉니다.
- `shape` · `size` · 담당자 · 센서는 아직 보내지 않습니다.

## 3. 확인 요청

1. **`equipment_type` 이 비어도(null) 되는지**: 부스 · 실링 · 공조기 · 도료 혼합실 · 소각설비는 종류 코드가 정해지지 않아 `type: null` 로 보냅니다. 중앙 서버 스키마(`V1__central_schema.sql`)는 `VARCHAR(20)` 비어도 됨이라 지금은 저장되지만, 저장소 `db/argos_ddl.sql` 은 `equipment_type varchar(30) NOT NULL` 입니다. 최종 DB 가 NOT NULL 이면 이 설비들의 코드(예: `BOOTH`, `SEALING`, `AHU`, `MIX`, `RTO`)를 정해 주세요 (backend-notes 15장 질문과 같음).
2. **대시보드 3D 배치도**: 공정 배치도 화면(`ProcessPage`)은 아직 도장 공정 그림만 그립니다. 새 공정의 대시보드 배치도는 다음 작업으로 남깁니다 (API 변경 없음).

## 4. ERD

변경 없음. `equipment` 테이블의 `process_id, code, name, equipment_type, pos_x, pos_y, pos_z, is_active` 에 쓰입니다.
