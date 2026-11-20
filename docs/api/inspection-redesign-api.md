# 점검 관리 개편 (A안) — 백엔드 요청 (2026-11-20)

점검 관리 화면을 개편안 A안으로 다시 만들었습니다. 프론트는 예시 데이터(mock)로 먼저 동작하고, 아래 API 가 생기면 `VITE_USE_MOCK=false` 에서 그대로 붙습니다.
프론트 코드에서는 `TODO(백엔드` 로 검색하면 이 문서의 항목이 나옵니다 (`gateway/frontend/src/api/inspections.js`).

## 화면 요약

| 자리 | 내용 | 부르는 API |
| --- | --- | --- |
| 맨 위 | 점검 서류 양식 바 — F02 · F03 빈 양식 PDF 내려받기, 전체 받기 | 3-6 |
| 왼쪽 위 | 점검 스케줄 달력 — 월 합계(완료 · 예정 · 누락), 날짜 칸 '완료 2' '예정 1' '누락 1', 그날 목록, 스케줄 추가 | 3-1, 3-2, 3-3 |
| 왼쪽 아래 | 서류 등록 — 파일을 놓으면 '수기 서류 등록' 창(예전 4단계 그대로) | 기존 E1 (바뀐 것 없음) |
| 오른쪽 | 점검 이력 — 스케줄의 점검 건, 최신순(오늘 건이 맨 위), 구분 · 결과 · 기간(1/7/30일) · 찾기 | 3-1 |
| 창 | 서류 양식 보기 · 작성 (F02 · F03 양식 그대로), 스캔 원본 미리보기, 월별 보고서 | 3-4, 3-5, 기존 `/inspections/{id}/file` |

## 1. 용어 · 코드

| 화면 | 코드 | 비고 |
| --- | --- | --- |
| 완료 / 예정 / **누락** | `DONE` / `PLANNED` / `DELAYED` | 지금 `InspectionService.scheduleStatus` 그대로. 화면 글자만 '지연' → '누락' (프론트 `inspectionMeta.js SCHEDULE_STATE` 한 곳) |
| 구분 수기 / 전자 | `PAPER` / `DIGITAL` | 완료 건만. 수기 = 스캔본을 올려 OCR 검수 완료, 전자 = 화면 양식에 바로 작성 |
| 결과 양호 / 불량 | `OK` / `NG` | 완료 건만. 양식의 점검 항목에 '불량'이 하나라도 있으면 `NG` |

- 예정 · 누락 건은 `source` · `result` 가 `null` 입니다.

## 2. 기존 API 와 겹치지 않게 정한 것

- `POST /inspections`(파일 올리기), `GET /inspections`(OCR 등록 목록), `PUT /inspections/{id}`(OCR 저장)는 **수기 서류 등록 창이 그대로 씁니다.** 그래서 새 기능은 스케줄 아래로 넣었습니다.
- 스캔 원본은 새 주소 없이 기존 `GET /inspections/{inspectionId}/file` 을 씁니다. 이력 줄에 그 건의 `inspectionId`(ocr_result id)만 넣어 주세요.
- 월별 보고서는 **프론트에서 계산**합니다 (`GET /inspections/schedule?month=` 목록으로). 보고서 API 는 필요 없습니다.

## 3. 요청 API

### 3-1. 점검 건 목록 — `GET /inspections/schedule` (지금 있는 API 확장)

지금은 `date`(하루)만 받습니다. 아래 두 방식도 받아 주세요. 달력 · 이력 · 월별 보고서가 모두 이 목록 하나를 씁니다.

| 쿼리 | 쓰는 곳 |
| --- | --- |
| `month=2026-11` | 달력, 월별 보고서 |
| `from=2026-11-14&to=2026-11-20` | 점검 이력 (오늘 기준 최근 1 · 7 · 30일) |
| `factoryId` | 공장 담당자는 자기 공장 (지금과 같음) |
| `source`, `result`, `q` (선택) | 프론트는 받은 목록 안에서 거르므로 없어도 됨 |

응답 한 줄 (지금 응답에 굵은 칸을 더해 주세요):

```json
{
  "scheduleId": 812,
  "date": "2026-11-19",
  "time": "09:10",
  "formType": "F02",
  "equipmentId": 107,
  "equipmentName": "상도 건조로",
  "equipmentNo": "EF-07",
  "assigneeId": 21,
  "assignee": "김도현",
  "status": "DONE",
  "source": "PAPER",
  "result": "NG",
  "inspectionId": 5812,
  "scanName": "F02_상도건조로_1119.pdf",
  "doneAt": "2026-11-19T09:10:00+09:00",
  "doneBy": "김도현"
}
```

- **추가 칸**: `equipmentNo`(양식의 설비번호 EF-07 · TR-01), `assigneeId`, `source`, `result`, `inspectionId`, `scanName`
- `source` · `inspectionId`: 그 스케줄에 검수 완료(DONE)된 `ocr_result` 가 있으면 `PAPER` + 그 id, 전자 작성이면 `DIGITAL` + `null`
- `result`: 저장된 양식 값(3-4)에서 계산하거나 저장할 때 같이 둠

### 3-2. 스케줄 추가 창 고르는 칸 — `GET /inspections/schedule/options?factoryId=` (새로)

```json
{
  "equipments": [{ "id": 101, "name": "전처리 탈지 탱크", "code": "EF-01", "formType": "F02" }, { "id": 109, "name": "5공장 변전소", "code": "TR-01", "formType": "F03" }],
  "inspectors": [{ "id": 21, "name": "김도현" }]
}
```

- `formType`: 그 설비를 점검하는 양식 (창에서 양식을 고르면 맞는 설비만 보임). 없으면 모든 양식에 보임
- 다른 API(설비 목록 · 사용자 목록)로 대신할 수 있으면 알려 주세요.

### 3-3. 스케줄 추가 — `POST /inspections/schedule` (새로)

```json
{ "date": "2026-11-24", "time": "14:00", "formType": "F02", "equipmentId": 103, "assigneeId": 22, "repeat": "WEEKLY", "count": 4 }
```

- `repeat`: `ONCE` · `DAILY` · `WEEKLY` · `MONTHLY`, `count`: 몇 건 만들지 (`ONCE` 면 1). 시안에는 반복 끝이 없어 프론트가 '횟수' 칸을 더했습니다 (기본 매일 5 · 매주 4 · 매월 4)
- 응답: 만든 건 목록 (3-1 한 줄 모양)
- `inspection_schedule.title` 은 NOT NULL 이라 `'{양식 이름}'` 등으로 채워 주세요.

### 3-4. 양식 값 — `GET /inspections/schedule/{scheduleId}/form` (새로)

```json
{ "scheduleId": 812, "formType": "F02", "source": "PAPER",
  "values": { "inspectedAt": "2026-11-19T09:10", "inspType": "일상", "factory": "울산", "zone": "도장", "equipmentNo": "07",
              "inspector": "김도현", "checker": "정해준", "item01": "양호", "item02Value": "18.2", "item04": "불량", "item04Note": "조치 필요",
              "issue1No": "4", "issue1Problem": "벨트 균열", "issue1Action": "벨트 교체", "issue1Due": "2026-11-26",
              "verdict": "조건부 운전", "opinion": "벨트 균열, 조치 후 재점검" } }
```

- `values` 의 키는 **`GET /doc-forms` 의 `fields.key` 와 같습니다** → 수기 건은 `ocr_result.fields`(검수 완료 값), 전자 건은 저장한 값을 그대로 주면 됩니다.
- 예정 · 누락 건은 `values: null` (프론트가 날짜 · 설비 · 점검자를 미리 채운 빈 양식을 띄움)
- 고르는 칸 값: `inspType` 일상 · 주간 · 월간(F03 일상 · 월간 · 분기), `itemNN` 양호 · 불량 · 해당 없음, `verdict` 정상 운전 · 조건부 운전 · 운전 중지(F03 정상 · 주의 · 운전 정지 필요), `voltage` 22.9kV · 154kV · 기타
- 날짜 칸(`issueNDue` · `issueNDoneAt`)은 `YYYY-MM-DD`, `inspectedAt` 은 `YYYY-MM-DDTHH:mm`

### 3-5. 전자 작성 저장

| 경우 | API | 몸통 |
| --- | --- | --- |
| 예정 · 누락 건을 양식으로 작성 | `PUT /inspections/schedule/{scheduleId}/form` (새로) | `{ "values": {...}, "result": "OK" }` |
| 스케줄 없이 '점검일지 작성' | `POST /inspections/records` (새로) | `{ "formType": "F03", "equipmentId": 109, "date": "2026-11-20", "time": "17:32", "values": {...}, "result": "OK" }` |

- 저장하면 그 건: `status DONE`, `source DIGITAL`, `done_at` 지금, `done_by` 로그인 사용자. 응답은 3-1 한 줄 모양
- `POST /inspections/records` 는 완료 상태의 스케줄 한 줄 + 양식 값을 같이 만들면 됩니다.
- 값을 둘 곳: 지금 테이블에 전자 작성 값을 둘 칸이 없습니다. 예) `inspection_schedule` 에 `form_values JSONB`, `result VARCHAR(2)` 추가, 또는 `inspection_record(schedule_id, form_type, values JSONB, result, written_by, written_at)` 새 테이블
- 이미 완료된 건에 PUT 하면 409 (프론트 예시는 `ALREADY_DONE`)
- 권한: 공장 담당자. 본사 관리자는 열람만 (화면에서도 작성 · 추가 버튼을 숨김)

### 3-6. 빈 서류 양식 — `GET /doc-forms/{formType}/template` (새로)

- 응답: PDF 파일 (팀 양식 `ArgOS_서류양식.pdf` 의 그 쪽만). 프론트는 Blob 으로 받아 `ARG-F02_환기·배기설비 점검일지.pdf` 로 저장
- '전체 받기'는 양식마다 차례로 부릅니다 (한 번에 묶은 zip 이 있으면 그걸로 바꿀 수 있음)

## 4. 정해 주실 것

1. '지연' / '누락' 화면 이름 통일 — 지금 점검 관리 화면은 '누락'. 바꾸려면 `inspectionMeta.js` 의 `SCHEDULE_STATE.DELAYED.label` 한 줄
2. 올린 스캔본(수기 서류 등록)을 어느 스케줄 건에 이을지 — 지금 창은 `scheduleId` 없이 올립니다. 백엔드가 양식 · 설비 · 날짜로 맞춰 줄지, 창에서 건을 고르게 할지
3. 스케줄 반복의 끝 — '횟수'로 받을지, '끝 날짜'로 받을지
