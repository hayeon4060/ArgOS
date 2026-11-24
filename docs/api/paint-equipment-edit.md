# 공장 관리 설비 추가 · 삭제 (설비 그림 그대로) — 백엔드 전달 (2026-11-20)

프론트 브랜치 `feature/frontend-paint-equipment-edit`. **새 API 는 없습니다. ERD 칸도 그대로입니다.** 이미 있는 배치 저장 API 에서 `equipment.delete` 와 `create[].shape` 를 새로 쓰고, DB `equipment.shape` 에 넣는 값의 뜻을 정했습니다. 아래 4번의 확인 4건을 봐 주세요.

## 1. 화면에서 바뀐 것 (재원님 요청)

| 곳 | 바뀐 것 |
|---|---|
| 공장 관리 › 설비 목록 | 맨 아래 '+ 설비 추가' — 도장 공정(울산 5공장)에서도 보임 (전에는 새로 만든 공정만) |
| 공장 관리 › 설비 배치도 | 추가한 설비를 **도장 공정 그림의 그 설비 그림 그대로**(건물 · 배기팬 · 버너실 · 그림자) 복사해서 놓음. 끌어서 옮기기 · 상태 테두리 · 마우스 올림도 원래 설비와 같음 |
| 공장 관리 › 설비 정보 | '설비 삭제' 단추 → 저장 전까지 '삭제 예정'(삭제 취소 가능), 저장하면 `equipment.delete` |
| 새로 만든 공정 | 격자 판(이름표 상자) 대신 **같은 배치도 바닥**에 설비 그림으로 놓음. 등록된 설비가 없으면 빈 바닥 |
| 대시보드 공정 배치도 · 설비 상세 | 추가한 설비가 같은 그림으로 보이고, 지운 설비 그림은 사라짐 |

## 2. 보내는 값 (`PUT /processes/{processId}/layout`)

```json
{
  "version": 14,
  "equipment": {
    "create": [
      { "tempId": "n1", "name": "상도 건조로 2", "type": "OVEN", "shape": "top-oven", "pos": { "x": 366.2, "y": 297.4, "z": null } },
      { "tempId": "n2", "name": "배기팬", "type": "FAN", "shape": "fan", "pos": { "x": -241, "y": 409, "z": null } }
    ],
    "update": [],
    "delete": [27, 30]
  },
  "sensors": { "create": [], "update": [], "delete": [] }
}
```

- `create[].shape`: 어떤 그림으로 그릴지. 다시 불러왔을 때(`GET /processes/{id}/layout` 의 `shape`) 같은 그림으로 그리려고 DB 에 둡니다.
- `create[].pos`: 배치도 그림 좌표(설비 가운데 점). 지금 `update[].pos` 와 같은 좌표계입니다.
  (PR #82 문서 `equipment-add.md` 의 '빈 배치판 좌표 1120 × 640' 은 이번에 없앴습니다.)
- `code` 는 보내지 않습니다 → 서버가 `EQ-{processId}-{n}`.
- `delete`: 지울 설비 id. 서버 규칙(`MasterAdminService.removeEquipment`)에 맞춰 화면에 안내합니다.
  센서가 붙은 설비는 지워지지 않고 `is_active = false` 가 되므로, 저장 뒤 다시 불러와 남아 있으면 "센서가 있는 설비 N대는 지우지 않고 '사용 안 함'으로 바꿨습니다" 를 띄웁니다.

## 3. ERD — `equipment.shape` 에 넣는 값 (칸은 그대로, `varchar(20)`)

| shape | 그림 | shape | 그림 |
|---|---|---|---|
| `pretreat` | 전처리 (탱크 + 펌프) | `top-oven` | 상도 건조로 (+ 배기팬 · 버너실 · 열화상) |
| `ed-tank` | 전착 탱크 (+ 펌프) | `ahu` | 공조기 |
| `ed-oven` | 전착 건조로 (+ 배기팬 · 버너실) | `paint-mix` | 도료 혼합실 |
| `sealing` | 실링 | `incinerator` | 소각설비 |
| `primer-booth` | 중도 부스 (+ 배기팬) | `fan` | 배기팬만 |
| `primer-oven` | 중도 건조로 (+ 배기팬 · 버너실) | `burner` | 버너실만 |
| `top-booth` | 상도 부스 (+ 배기팬) | `pump` · `ircam` | 펌프만 · 열화상 카메라만 |

- 목록 위치: `gateway/frontend/src/features/process/paintLayout.js` (`PAINT_EQUIPMENT` · `PART_SHAPES`).
- 시드 설비(`PT-TANK` … `IR-CAM-01`)는 지금처럼 **code 로** 그림과 짝을 맞춥니다. 시드의 `shape` 값(`TANK` · `BOX` · `FAN`)은 화면이 쓰지 않으니 바꿀 필요 없습니다.
- 위 표에 없는 `shape` 이고 code 도 짝이 없으면 배치도에는 그리지 않고 설비 목록에만 나옵니다.

## 4. 확인 요청

1. **`GET /processes/{id}/layout` 의 `shape`** 가 저장한 값 그대로 오는지 (코드로는 `DashboardService.processLayout` 이 `e.shape()` 를 넣고 있어 될 것으로 봅니다. 마스터 캐시가 `shape` 를 싣는지만 확인 부탁드립니다).
2. **삭제 결과**: 응답에 '지우지 않고 사용 안 함으로 바꾼 설비 id' (`kept: [30]` 같은 값)를 주면 다시 불러와 비교하지 않아도 됩니다 (선택).
3. **실제 서버 도장 공정의 그림만 있는 설비**: 실링 · 중도/상도 부스 · 공조기 · 도료 혼합실 · 소각설비는 시드에 없어서, 실제 서버에서는 지금처럼 그림만 그대로 둡니다 (`paintLayout.js hiddenDrawings` 의 예외). 시드에 넣어 주시거나 공장 관리에서 추가하면 이 예외를 지우겠습니다. 예시 데이터에서는 이 설비들도 등록돼 있어 지우면 사라집니다.
4. **`equipment_type` null**: 부스 · 실링 · 공조기 · 도료 혼합실 · 소각설비는 종류 코드가 없어 `type: null` 로 보냅니다 (`equipment-add.md` 3번과 같은 질문).

## 5. 프론트 메모

- 설비 바깥에 그려진 그림자 · 덕트 · 급기선은 그림 파일(`paint-map.svg`)에 `data-for="설비 이름"` 을 달아, 설비와 같이 옮기고 숨기고 복사합니다. (그림 모양은 바뀌지 않았습니다. 설비를 옮기면 그림자가 같이 따라오게 됐습니다.)
- 예시 데이터: 추가한 설비의 상세 화면은 센서 없는 빈 설비로 나옵니다 (`mock/equipmentData.js`).
