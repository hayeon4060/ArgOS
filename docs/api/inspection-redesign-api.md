# 점검 관리 개편 (A안) — 백엔드 요청 (2026-11-20)

점검 관리 화면을 개편안 A안으로 다시 만들었습니다.

> 2026-11-20 2차 수정: **변전소(F03 수변전설비) 점검은 점검 관리에서 뺐습니다** — 점검 관리는 F02 환기·배기설비 점검일지 한 가지만 씁니다. **스캔본 ↔ 점검 건 연결**(3-7)을 추가했습니다.

프론트는 예시 데이터(mock)로 먼저 동작하고, 아래 API 가 생기면 `VITE_USE_MOCK=false` 에서 그대로 붙습니다.
프론트 코드에서는 `TODO(백엔드` 로 검색하면 이 문서의 항목이 나옵니다 (`gateway/frontend/src/api/inspections.js`).

## 화면 요약

| 자리 | 내용 | 부르는 API |
| --- | --- | --- |
| 맨 위 | 점검 서류 양식 바 — F02 빈 양식 PDF 내려받기 | 3-6 |
| 왼쪽 위 | 점검 스케줄 달력 — 월 합계(완료 · 예정 · 누락), 날짜 칸 '완료 2' '예정 1' '누락 1', 그날 목록, 스케줄 추가 | 3-1, 3-2, 3-3 |
| 왼쪽 아래 | 서류 등록 — 파일을 놓으면 '수기 서류 등록' 창(예전 4단계 + 점검 건 연결) | 기존 E1 + 3-7 |
| 오른쪽 | 점검 이력 — 스케줄의 점검 건, 최신순(오늘 건이 맨 위), 구분 · 결과 · 기간(1/7/30일) · 찾기 | 3-1 |
| 창 | 서류 양식 보기 · 작성 (F02 양식 그대로), 스캔 원본 미리보기, 월별 보고서 | 3-4, 3-5, 기존 `/inspections/{id}/file` |

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

- **추가 칸**: `equipmentNo`(양식의 설비번호 EF-07), `assigneeId`, `source`, `result`, `inspectionId`, `scanName`
- `source` · `inspectionId`: 그 스케줄에 검수 완료(DONE)된 `ocr_result` 가 있으면 `PAPER` + 그 id, 전자 작성이면 `DIGITAL` + `null`
- `result`: 저장된 양식 값(3-4)에서 계산하거나 저장할 때 같이 둠

### 3-2. 스케줄 추가 창 고르는 칸 — `GET /inspections/schedule/options?factoryId=` (새로)

```json
{
  "equipments": [{ "id": 101, "name": "전처리 탈지 탱크", "code": "EF-01", "formType": "F02" }],
  "inspectors": [{ "id": 21, "name": "김도현" }]
}
```

- `formType`: 그 설비를 점검하는 양식. 점검 관리는 F02 설비만 보여 줍니다 (변전소 등 F02 가 아닌 설비는 빼도 됨)
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
- 고르는 칸 값: `inspType` 일상 · 주간 · 월간, `zone` 시험 챔버 · 도장 · 차체 용접 · 변전소 · 기타(F02 양식 원본의 구역 칸), `itemNN` 양호 · 불량 · 해당 없음, `verdict` 정상 운전 · 조건부 운전 · 운전 중지
- 날짜 칸(`issueNDue` · `issueNDoneAt`)은 `YYYY-MM-DD`, `inspectedAt` 은 `YYYY-MM-DDTHH:mm`

### 3-5. 전자 작성 저장

| 경우 | API | 몸통 |
| --- | --- | --- |
| 예정 · 누락 건을 양식으로 작성 | `PUT /inspections/schedule/{scheduleId}/form` (새로) | `{ "values": {...}, "result": "OK" }` |
| 스케줄 없이 '점검일지 작성' | `POST /inspections/records` (새로) | `{ "formType": "F02", "equipmentId": 103, "date": "2026-11-20", "time": "17:32", "values": {...}, "result": "OK" }` |

- 저장하면 그 건: `status DONE`, `source DIGITAL`, `done_at` 지금, `done_by` 로그인 사용자. 응답은 3-1 한 줄 모양
- `POST /inspections/records` 는 완료 상태의 스케줄 한 줄 + 양식 값을 같이 만들면 됩니다.
- 값을 둘 곳: 지금 테이블에 전자 작성 값을 둘 칸이 없습니다. 예) `inspection_schedule` 에 `form_values JSONB`, `result VARCHAR(2)` 추가, 또는 `inspection_record(schedule_id, form_type, values JSONB, result, written_by, written_at)` 새 테이블
- 이미 완료된 건에 PUT 하면 409 (프론트 예시는 `ALREADY_DONE`)
- 권한: 공장 담당자. 본사 관리자는 열람만 (화면에서도 작성 · 추가 버튼을 숨김)

### 3-6. 빈 서류 양식 — `GET /doc-forms/{formType}/template` (새로)

- 응답: PDF 파일 (팀 양식 `ArgOS_서류양식.pdf` 의 그 쪽만). 프론트는 Blob 으로 받아 `ARG-F02_환기·배기설비 점검일지.pdf` 로 저장
- 점검 관리는 F02 만 부릅니다 ('전체 받기'는 양식이 하나라 없앰)

### 3-7. 스캔본 ↔ 점검 건 연결 (2차 추가)

수기 서류 등록 창에서 문자 인식이 끝나면 '점검 건 연결' 줄이 나옵니다. 인식한 **양식 · 설비 · 날짜**로 맞는 예정 · 누락 건을 미리 골라 두고(추천), 사용자가 확인하거나 다른 건 / '연결 안 함'으로 바꾼 뒤 '검수 완료 · 저장'을 누릅니다.

| 무엇 | API | 내용 |
| --- | --- | --- |
| 후보 건 | `GET /inspections/schedule?from=&to=` (3-1) | 오늘 포함 최근 7일 중 예정 · 누락 건. 새 API 필요 없음 |
| 추천 (선택) | `GET /inspections/{id}/ocr` 응답에 `scheduleId` 추가 | 백엔드가 인식 값으로 맞춘 건. 없으면 프론트가 맞춤(아래 규칙) |
| 잇기 | `PUT /inspections/{id}` 몸통에 `scheduleId` 추가 | `{ "status": "DONE", "fields": [...], "scheduleId": 812 }` |

- `scheduleId` 가 숫자면: 그 건을 `status DONE`, `source PAPER`, `ocr_result.schedule_id` = 그 건, `result` = 인식 값에 '불량'이 있으면 `NG`
- `scheduleId` 가 `null`('연결 안 함')이면: 인식 값(양식 · 설비번호 · 점검 일자 · 점검자)으로 **새 점검 건(완료 · 수기)** 을 만들어 이력에 보이게
- 이미 완료된 건이면 409
- 프론트 추천 규칙(`features/inspection/scheduleMatch.js`): 양식이 같은 후보 중 '설비번호가 같음 2점 + 날짜가 같음 1점'이 가장 높은 건, 같으면 늦은 날짜. 둘 다 다르면 추천 없음
  - 날짜 칸: `inspectedAt` → `checkDate` → `applyDate` → `workDate` 순으로 찾음, 설비번호: `equipmentNo` 의 숫자 부분
- 백엔드가 맞춰 줄 수 있으면 같은 규칙이면 좋습니다 (EF-03 처럼 '설비번호'로 설비를 찾는 표가 필요)
- 예시 모드 한계: 예시 스캔 원본이 F01(밀폐공간 작업허가서)뿐이라, 화면에서는 추천이 뜨지 않고 사용자가 고르는 흐름만 보입니다

## 4. 정해 주실 것

1. ~~'지연' / '누락' 이름~~ → 2026-11-20 '누락'으로 확정
2. ~~스캔본을 어느 건에 이을지~~ → 자동 추천 + 사용자가 바꿈으로 확정 (3-7)
3. 스케줄 반복의 끝 — 지금은 '횟수' (답 대기, 끝 날짜로 바꿀 수 있음)
4. 변전소(F03) 점검 — 점검 관리에서는 뺐습니다. 다른 곳(수기 서류 등록의 서류 종류, `doc_form` F03)은 그대로 둡니다
