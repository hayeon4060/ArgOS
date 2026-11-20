-- 사용자 관리 (명세서 G1): 삭제해도 행은 남긴다 (체크리스트·이용 로그가 이 사용자를 가리키므로)
-- 삭제한 사용자는 is_active = false, deleted_at 이 채워지고 목록·로그인에서 빠진다
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
