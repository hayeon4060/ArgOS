-- 테이블 명세서 v5 (docs/erd/ArgOS_테이블명세서_v5.xlsx) 반영 (연계 DB)
-- 로그인 쪽 테이블(app_user, login_history, activity_log 등 V1)은 건드리지 않는다.

-- chat_read: v5 컬럼 이름
ALTER TABLE chat_read RENAME COLUMN last_msg_id TO last_read_msg_id;
ALTER TABLE chat_read RENAME COLUMN read_at TO updated_at;
ALTER TABLE chat_read ALTER COLUMN last_read_msg_id DROP NOT NULL;

-- chat_message: 내용은 text (길이 검사는 API 에서 1000자)
ALTER TABLE chat_message ALTER COLUMN body TYPE TEXT;

-- file_asset: v5 종류(THERMAL · VIDEO 포함)와 촬영 정보 컬럼
ALTER TABLE file_asset DROP CONSTRAINT IF EXISTS file_asset_kind_check;
ALTER TABLE file_asset ADD CONSTRAINT file_asset_kind_check
    CHECK (kind IN ('THERMAL', 'VIDEO', 'DOC_SCAN', 'FLOORPLAN', 'REPORT'));
ALTER TABLE file_asset ADD COLUMN IF NOT EXISTS sensor_id   BIGINT;      -- 중앙 sensor.sensor_id (열화상이면 찍은 카메라)
ALTER TABLE file_asset ADD COLUMN IF NOT EXISTS captured_at TIMESTAMPTZ;
