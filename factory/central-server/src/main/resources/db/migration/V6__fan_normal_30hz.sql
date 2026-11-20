-- 배기팬 평상시 주파수 40Hz → 30Hz (2026-11-20 팀 기준: 정상 30 · 주의 45 · 경보 60 Hz). 회전수 = Hz × 30
-- 평상시 회전수(1200rpm = 40Hz)로 돌던 배기팬을 900rpm 으로
UPDATE equipment SET fan_rpm = 900 WHERE equipment_type = 'FAN' AND fan_rpm = 1200;
