-- ERD V6 반영 (db/ArgOS ERD V6.sql, 기본값·허용 값은 v5 DDL) (연계 DB)
-- 로그인 쪽 테이블(app_user, login_history, activity_log 등 V1)은 건드리지 않는다.

-- chat_read: V6 컬럼 이름
ALTER TABLE chat_read RENAME COLUMN last_msg_id TO last_read_msg_id;
ALTER TABLE chat_read RENAME COLUMN read_at TO updated_at;
ALTER TABLE chat_read ALTER COLUMN last_read_msg_id DROP NOT NULL;

-- chat_message: 내용은 text (길이 검사는 API 에서 1000자)
ALTER TABLE chat_message ALTER COLUMN body TYPE TEXT;

-- file_asset: 종류에 THERMAL(열화상 사진)과 촬영 정보 컬럼. 영상은 저장하지 않음 (V6)
ALTER TABLE file_asset DROP CONSTRAINT IF EXISTS file_asset_kind_check;
ALTER TABLE file_asset ADD CONSTRAINT file_asset_kind_check
    CHECK (kind IN ('THERMAL', 'DOC_SCAN', 'FLOORPLAN', 'REPORT'));
ALTER TABLE file_asset ADD COLUMN IF NOT EXISTS sensor_id   BIGINT;      -- 중앙 sensor.sensor_id (열화상이면 찍은 카메라)
ALTER TABLE file_asset ADD COLUMN IF NOT EXISTS captured_at TIMESTAMPTZ;
