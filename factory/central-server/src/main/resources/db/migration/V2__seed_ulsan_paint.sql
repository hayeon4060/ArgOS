-- 시연 기준 데이터: 울산공장 5공장 도장 공정
-- 센서 태그는 factory/raw-data/dummy-data 의 sensor_serial_no 와 같아야 7일치 가져오기가 연결된다.
-- 기준값은 수행계획서 '단계별 경보 조건' + 7일치 평균(전류 정상 평균, 온도 설정값)으로 계산했다.
-- ID 를 직접 넣는 것은 프론트 예시 데이터(5공장 = factoryId 5)와 맞추기 위해서다.

-- 사업장
INSERT INTO plant (plant_id, name, code, address, lat, lng) VALUES
    (1, '울산공장', 'USN', '울산광역시 북구 염포로 700', 35.5438, 129.3697),
    (2, '아산공장', 'ASN', '충청남도 아산시 인주면 현대로 1077', 36.9237, 126.9148),
    (3, '전주공장', 'JJU', '전라북도 완주군 봉동읍 완주산단로 1', 35.9198, 127.1398);

-- 공장 (울산)
INSERT INTO factory (factory_id, plant_id, code, name, sort_order) VALUES
    (1, 1, 'P1', '1공장', 1),
    (2, 1, 'P2', '2공장', 2),
    (3, 1, 'P3', '3공장', 3),
    (4, 1, 'P4', '4공장', 4),
    (5, 1, 'P5', '5공장', 5),
    (6, 1, 'EV', '엔진·변속기 공장', 6);

-- 공정 (5공장). tag_prefix: 1=프레스, 2=차체, 3=도장, 4=의장
INSERT INTO process (process_id, factory_id, code, name, tag_prefix) VALUES
    (10, 5, 'PRS', '프레스 공정', '1'),
    (11, 5, 'BDY', '차체 공정',   '2'),
    (12, 5, 'PNT', '도장 공정',   '3'),
    (13, 5, 'ASM', '의장 공정',   '4');

-- 설비 (도장 공정)
INSERT INTO equipment (equipment_id, process_id, code, name, equipment_type, shape, fan_rpm) VALUES
    (31, 12, 'PT-TANK',   '전처리 탱크',         'TANK',    'TANK',  NULL),
    (32, 12, 'PT-PUMP',   '전처리 순환펌프',     'PUMP',    'BOX',   NULL),
    (33, 12, 'ED-TANK',   '전착 탱크',           'TANK',    'TANK',  NULL),
    (34, 12, 'ED-PUMP',   '전착 순환펌프',       'PUMP',    'BOX',   NULL),
    (35, 12, 'ED-OVEN',   '전착 건조로',         'OVEN',    'BOX',   NULL),
    (36, 12, 'ED-FAN',    '전착 건조로 배기팬',  'FAN',     'FAN',   1200),
    (37, 12, 'ED-BURNER', '전착 건조로 버너실',  'BURNER',  'BOX',   NULL),
    (38, 12, 'PR-OVEN',   '중도 건조로',         'OVEN',    'BOX',   NULL),
    (39, 12, 'PR-FAN',    '중도 건조로 배기팬',  'FAN',     'FAN',   1200),
    (40, 12, 'PR-BURNER', '중도 건조로 버너실',  'BURNER',  'BOX',   NULL),
    (41, 12, 'TC-OVEN',   '상도 건조로',         'OVEN',    'BOX',   NULL),
    (42, 12, 'TC-FAN',    '상도 건조로 배기팬',  'FAN',     'FAN',   1200),
    (43, 12, 'TC-BURNER', '상도 건조로 버너실',  'BURNER',  'BOX',   NULL),
    (44, 12, 'IR-CAM-01', '상도 열화상 카메라',  'CAMERA',  'BOX',   NULL);

-- 센서 (7일치 더미데이터의 태그 그대로). setpoint = 온도 정상 운전값 (7일 평균)
INSERT INTO sensor (sensor_id, tag, equipment_id, sensor_type, unit, connection, sample_sec, setpoint) VALUES
    (301, 'TE-301', 31, 'TE', '℃',   'GW',  5, 50),
    (302, 'TE-302', 33, 'TE', '℃',   'GW',  5, 28),
    (303, 'TE-303', 35, 'TE', '℃',   'GW',  5, 180),
    (304, 'TE-304', 38, 'TE', '℃',   'GW',  5, 150),
    (305, 'TE-305', 41, 'TE', '℃',   'GW',  5, 140),
    (311, 'IT-301', 32, 'IT', 'A',   'PLC', 5, NULL),
    (312, 'IT-302', 34, 'IT', 'A',   'PLC', 5, NULL),
    (313, 'IT-303', 36, 'IT', 'A',   'PLC', 5, NULL),
    (315, 'IT-305', 39, 'IT', 'A',   'PLC', 5, NULL),
    (317, 'IT-307', 42, 'IT', 'A',   'PLC', 5, NULL),
    (321, 'AT-301', 37, 'AT', 'ppm', 'GW',  5, NULL),
    (322, 'AT-302', 40, 'AT', 'ppm', 'GW',  5, NULL),
    (323, 'AT-303', 43, 'AT', 'ppm', 'GW',  5, NULL),
    (331, 'IR-301', 44, 'IR', '℃',   'VMS', 5, NULL);

-- 기준값 (계절 ALL, 7일치 시작 전부터 적용)
--   온도 TE : 주의 = 설정값 +10, 경보 = 설정값 +20
--   전류 IT : 정상 평균 기준 주의 110% / 90%, 경보 125% / 70%  (정지 = 0 A 도 경보 하한에 걸림)
--   CO   AT : 주의 15 ppm, 경보 30 ppm (경보는 60초 = 12샘플 지속)
--   열화상 IR: 주의 50 ℃, 경보 60 ℃
--   지속 조건: 주의 24샘플(2분), 경보 6샘플(30초)  ← ERD 기본값
INSERT INTO sensor_threshold
    (sensor_id, warn_value, alarm_value, low_warn, low_alarm, warn_samples, alarm_samples, basis, note, valid_from) VALUES
    (301, 60,    70,    NULL,  NULL,  24, 6,  'STANDARD', '설정값 50℃ +10/+20',              '2026-11-01T00:00:00+09:00'),
    (302, 38,    48,    NULL,  NULL,  24, 6,  'STANDARD', '설정값 28℃ +10/+20',              '2026-11-01T00:00:00+09:00'),
    (303, 190,   200,   NULL,  NULL,  24, 6,  'STANDARD', '설정값 180℃ +10/+20',             '2026-11-01T00:00:00+09:00'),
    (304, 160,   170,   NULL,  NULL,  24, 6,  'STANDARD', '설정값 150℃ +10/+20',             '2026-11-01T00:00:00+09:00'),
    (305, 150,   160,   NULL,  NULL,  24, 6,  'STANDARD', '설정값 140℃ +10/+20',             '2026-11-01T00:00:00+09:00'),
    (311, 26.4,  30.0,  21.6,  16.8,  24, 6,  'STANDARD', '정상 평균 24.0A 의 110·125% / 90·70%', '2026-11-01T00:00:00+09:00'),
    (312, 13.2,  15.0,  10.8,  8.4,   24, 6,  'STANDARD', '정상 평균 12.0A 의 110·125% / 90·70%', '2026-11-01T00:00:00+09:00'),
    (313, 28.05, 31.88, 22.95, 17.85, 24, 6,  'STANDARD', '정상 평균 25.5A 의 110·125% / 90·70%', '2026-11-01T00:00:00+09:00'),
    (315, 24.2,  27.5,  19.8,  15.4,  24, 6,  'STANDARD', '정상 평균 22.0A 의 110·125% / 90·70%', '2026-11-01T00:00:00+09:00'),
    (317, 19.8,  22.5,  16.2,  12.6,  24, 6,  'STANDARD', '정상 평균 18.0A 의 110·125% / 90·70%', '2026-11-01T00:00:00+09:00'),
    (321, 15,    30,    NULL,  NULL,  24, 12, 'STANDARD', 'CO 15ppm / 30ppm(60초)',          '2026-11-01T00:00:00+09:00'),
    (322, 15,    30,    NULL,  NULL,  24, 12, 'STANDARD', 'CO 15ppm / 30ppm(60초)',          '2026-11-01T00:00:00+09:00'),
    (323, 15,    30,    NULL,  NULL,  24, 12, 'STANDARD', 'CO 15ppm / 30ppm(60초)',          '2026-11-01T00:00:00+09:00'),
    (331, 50,    60,    NULL,  NULL,  24, 6,  'STANDARD', '열화상 50℃ / 60℃',              '2026-11-01T00:00:00+09:00');

-- 관제 단위
INSERT INTO control_unit (unit_id, process_id, equipment_id, name) VALUES
    (1, 12, 31, '전처리'),
    (2, 12, 33, '전착 탱크'),
    (3, 12, 35, '전착 건조로'),
    (4, 12, 38, '중도 건조로'),
    (5, 12, 41, '상도 건조로');

-- 관제 단위 구성 센서. 전류(IT)는 화재·가스 판정에 모두 쓰인다.
INSERT INTO control_unit_sensor (unit_id, sensor_id, hazard) VALUES
    (1, 301, 'FIRE'), (1, 311, 'FIRE'),
    (2, 302, 'FIRE'), (2, 312, 'FIRE'),
    (3, 303, 'FIRE'), (3, 313, 'FIRE'), (3, 313, 'GAS'), (3, 321, 'GAS'),
    (4, 304, 'FIRE'), (4, 315, 'FIRE'), (4, 315, 'GAS'), (4, 322, 'GAS'),
    (5, 305, 'FIRE'), (5, 317, 'FIRE'), (5, 317, 'GAS'), (5, 323, 'GAS'), (5, 331, 'FIRE');

-- 직접 넣은 ID 다음부터 자동 번호가 이어지도록
SELECT setval(pg_get_serial_sequence('plant', 'plant_id'),               (SELECT MAX(plant_id) FROM plant));
SELECT setval(pg_get_serial_sequence('factory', 'factory_id'),           (SELECT MAX(factory_id) FROM factory));
SELECT setval(pg_get_serial_sequence('process', 'process_id'),           (SELECT MAX(process_id) FROM process));
SELECT setval(pg_get_serial_sequence('equipment', 'equipment_id'),       (SELECT MAX(equipment_id) FROM equipment));
SELECT setval(pg_get_serial_sequence('sensor', 'sensor_id'),             (SELECT MAX(sensor_id) FROM sensor));
SELECT setval(pg_get_serial_sequence('control_unit', 'unit_id'),         (SELECT MAX(unit_id) FROM control_unit));
