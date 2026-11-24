-- 점검 관리 개편 A안 (docs/api/inspection-redesign-api.md, 2026-11-20)
-- 점검 건(inspection_schedule)에 구분(수기 PAPER / 전자 DIGITAL) · 결과(양호 OK / 불량 NG) · 전자 작성 값
ALTER TABLE inspection_schedule ADD COLUMN IF NOT EXISTS source VARCHAR(8)
    CHECK (source IN ('PAPER', 'DIGITAL'));
ALTER TABLE inspection_schedule ADD COLUMN IF NOT EXISTS result VARCHAR(2)
    CHECK (result IN ('OK', 'NG'));
ALTER TABLE inspection_schedule ADD COLUMN IF NOT EXISTS form_values JSONB;

-- 스캔본을 '연결 안 함'으로 저장하면 인식한 값으로 새 점검 건을 만든다. 설비번호를 못 찾으면 설비 없이 둔다
ALTER TABLE inspection_schedule ALTER COLUMN equipment_id DROP NOT NULL;

-- 이미 검수 완료된 스캔본이 이어진 완료 건은 수기(PAPER)
UPDATE inspection_schedule s SET source = 'PAPER'
 WHERE s.status = 'DONE' AND s.source IS NULL
   AND EXISTS (SELECT 1 FROM ocr_result o WHERE o.schedule_id = s.schedule_id AND o.status = 'DONE');
