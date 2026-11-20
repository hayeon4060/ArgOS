-- 11/19 반영: alarm_sensor_sample · equipment.fan_auto · equipment_command.source·승인
-- ArgOS DB DDL v5 (MySQL 문법 · ERD 도구 가져오기용)
-- 실제 DB는 PostgreSQL. 형식 대응: timestamptz→DATETIME(3), double precision→DOUBLE, jsonb→JSON
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `plant` (
    `plant_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '사업장 ID',
    `name` VARCHAR(50) NOT NULL COMMENT '사업장명',
    `code` VARCHAR(10) NOT NULL COMMENT '사업장 코드: 약어 코드',
    `address` VARCHAR(200) NULL COMMENT '주소',
    `lat` DOUBLE NULL COMMENT '위도',
    `lng` DOUBLE NULL COMMENT '경도',
    `map_file_id` BIGINT NULL COMMENT '지도 파일 ID: 사업장 2D 지도 그림',
    PRIMARY KEY (`plant_id`),
    UNIQUE KEY uk_plant_name (`name`),
    UNIQUE KEY uk_plant_code (`code`),
    CONSTRAINT fk_plant_map_file_id FOREIGN KEY (`map_file_id`) REFERENCES `file_asset` (`file_id`)
) COMMENT = '사업장';

CREATE TABLE `factory` (
    `factory_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '공장 ID',
    `plant_id` BIGINT NOT NULL COMMENT '사업장 ID',
    `code` VARCHAR(10) NOT NULL COMMENT '공장 코드: F1~F5',
    `name` VARCHAR(50) NOT NULL COMMENT '공장명',
    `map_shape` JSON NULL COMMENT '지도 구역: 사업장 지도 위 구역 좌표',
    `sort_order` SMALLINT NOT NULL DEFAULT 0 COMMENT '표시 순서',
    PRIMARY KEY (`factory_id`),
    UNIQUE KEY uk_factory_plant_id_code (`plant_id`, `code`),
    CONSTRAINT fk_factory_plant_id FOREIGN KEY (`plant_id`) REFERENCES `plant` (`plant_id`)
) COMMENT = '공장';

CREATE TABLE `process` (
    `process_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '공정 ID',
    `factory_id` BIGINT NOT NULL COMMENT '공장 ID',
    `code` VARCHAR(10) NOT NULL COMMENT '공정 코드: PRESS / BODY / PAINT / ASSY / INSP / COMMON',
    `name` VARCHAR(50) NOT NULL COMMENT '공정명',
    `tag_prefix` VARCHAR(5) NULL COMMENT '태그 앞자리: 센서 태그 번호 앞자리 (3 = 도장)',
    `floorplan_file_id` BIGINT NULL COMMENT '배치도 파일 ID: 2D 배치도 그림',
    PRIMARY KEY (`process_id`),
    UNIQUE KEY uk_process_factory_id_code (`factory_id`, `code`),
    CONSTRAINT fk_process_factory_id FOREIGN KEY (`factory_id`) REFERENCES `factory` (`factory_id`),
    CONSTRAINT fk_process_floorplan_file_id FOREIGN KEY (`floorplan_file_id`) REFERENCES `file_asset` (`file_id`)
) COMMENT = '공정';

CREATE TABLE `equipment` (
    `equipment_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '설비 ID',
    `process_id` BIGINT NOT NULL COMMENT '공정 ID',
    `code` VARCHAR(20) NOT NULL COMMENT '설비 코드',
    `name` VARCHAR(50) NOT NULL COMMENT '설비명: 전착 건조로 등',
    `equipment_type` VARCHAR(30) NOT NULL COMMENT '설비 종류: 배기팬 / 건조로 / 부스 / 탱크 / CAMERA 등',
    `shape` VARCHAR(20) NULL COMMENT '모양',
    `pos_x` DOUBLE NULL COMMENT 'X 위치',
    `pos_y` DOUBLE NULL COMMENT 'Y 위치',
    `pos_z` DOUBLE NULL COMMENT 'Z 위치',
    `size_w` DOUBLE NULL COMMENT '가로',
    `size_d` DOUBLE NULL COMMENT '세로',
    `size_h` DOUBLE NULL COMMENT '높이',
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '사용 여부: false면 배치도에서 숨김',
    `run_state` VARCHAR(10) NULL COMMENT '운전 상태',
    `fan_rpm` INT NULL COMMENT '팬 회전수: 배기팬 현재 회전수 (4극 모터 rpm = 30 × Hz)',
    `fan_auto` TINYINT(1) NULL COMMENT '배기팬 자동 제어: true 자동(가스 주의 45Hz·경보 60Hz로 서버가 올림) / false 수동(담당자 승인 후 제어, 승인 없으면 안내만) / 배기팬이 아니면 NULL',
    PRIMARY KEY (`equipment_id`),
    UNIQUE KEY uk_equipment_process_id_code (`process_id`, `code`),
    CONSTRAINT fk_equipment_process_id FOREIGN KEY (`process_id`) REFERENCES `process` (`process_id`)
) COMMENT = '설비';

CREATE TABLE `control_unit` (
    `unit_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '관제 단위 ID',
    `process_id` BIGINT NOT NULL COMMENT '공정 ID',
    `equipment_id` BIGINT NOT NULL COMMENT '대표 설비 ID',
    `name` VARCHAR(50) NOT NULL COMMENT '관제 단위명',
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '사용 여부: false면 판정 제외',
    PRIMARY KEY (`unit_id`),
    CONSTRAINT fk_control_unit_process_id FOREIGN KEY (`process_id`) REFERENCES `process` (`process_id`),
    CONSTRAINT fk_control_unit_equipment_id FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`equipment_id`)
) COMMENT = '관제 단위';

CREATE TABLE `control_unit_sensor` (
    `unit_id` BIGINT NOT NULL COMMENT '관제 단위 ID',
    `sensor_id` BIGINT NOT NULL COMMENT '센서 ID',
    `hazard` VARCHAR(4) NOT NULL COMMENT '위험 종류',
    PRIMARY KEY (`unit_id`, `sensor_id`, `hazard`),
    CONSTRAINT fk_control_unit_sensor_unit_id FOREIGN KEY (`unit_id`) REFERENCES `control_unit` (`unit_id`) ON DELETE CASCADE,
    CONSTRAINT fk_control_unit_sensor_sensor_id FOREIGN KEY (`sensor_id`) REFERENCES `sensor` (`sensor_id`)
) COMMENT = '관제 단위 센서';

CREATE TABLE `work_shift` (
    `shift_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '근무조 ID',
    `plant_id` BIGINT NOT NULL COMMENT '사업장 ID',
    `name` VARCHAR(20) NOT NULL COMMENT '근무조명: 주간 / 야간',
    `start_time` TIME NOT NULL COMMENT '시작 시각',
    `end_time` TIME NOT NULL COMMENT '종료 시각',
    PRIMARY KEY (`shift_id`),
    CONSTRAINT fk_work_shift_plant_id FOREIGN KEY (`plant_id`) REFERENCES `plant` (`plant_id`)
) COMMENT = '근무조';

CREATE TABLE `sensor` (
    `sensor_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '센서 ID',
    `tag` VARCHAR(20) NOT NULL COMMENT '태그: TE-303 · IT-303 · AT-301 · IR-301',
    `equipment_id` BIGINT NOT NULL COMMENT '설비 ID',
    `sensor_type` VARCHAR(2) NOT NULL COMMENT '센서 종류: TE 온도 / IT 전류 / AT 가스 / IR 열화상',
    `unit` VARCHAR(10) NULL COMMENT '단위: ℃ / A / ppm',
    `connection` VARCHAR(4) NOT NULL COMMENT '연결 방식',
    `video_url` VARCHAR(300) NULL COMMENT '영상 주소: 열화상 카메라 등 실시간 영상 주소',
    `sample_sec` SMALLINT NOT NULL DEFAULT 5 COMMENT '수집 주기(초)',
    `setpoint` DOUBLE NULL COMMENT '정상 기준값: TE-303 = 180, IT-303 = 25.5',
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '사용 여부: false면 수집·판정 제외',
    PRIMARY KEY (`sensor_id`),
    UNIQUE KEY uk_sensor_tag (`tag`),
    CONSTRAINT fk_sensor_equipment_id FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`equipment_id`)
) COMMENT = '센서';

CREATE TABLE `sensor_threshold` (
    `threshold_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '기준값 ID: 서버가 Kafka argos.config.threshold 로 엣지에 발행, 엣지가 이벤트에 붙여 보냄',
    `sensor_id` BIGINT NOT NULL COMMENT '센서 ID',
    `warn_value` DOUBLE NULL COMMENT '2단계 상한: 이 값 이상 (rule HI)',
    `alarm_value` DOUBLE NULL COMMENT '3단계 상한: 이 값 이상',
    `low_warn` DOUBLE NULL COMMENT '2단계 하한: 이 값 이하면 2단계 (rule LO · 전류 저하, IT-303 17.85A)',
    `low_alarm` DOUBLE NULL COMMENT '3단계 하한: 이 값 이하면 3단계 (전류 정지, IT-303 5.0A)',
    `rate_per_min` DOUBLE NULL COMMENT '분당 변화율 기준: 현재 미사용',
    `warn_samples` INT NOT NULL DEFAULT 6 COMMENT '2단계 연속 횟수: 5초 샘플 6회 = 30초',
    `alarm_samples` INT NOT NULL DEFAULT 3 COMMENT '3단계 연속 횟수: 5초 샘플 3회 = 15초',
    `release_samples` INT NOT NULL DEFAULT 6 COMMENT '복귀 연속 횟수: 정상 6회 연속이면 1단계 복귀',
    `release_margin` DOUBLE NOT NULL DEFAULT 0.05 COMMENT '복귀 여유: 기준값의 5% 안쪽으로 돌아와야 정상',
    `alarm_avg_sec` INT NULL COMMENT '3단계 평균 시간(초): CO 60초 평균 (현재 엣지 미구현, 예약)',
    `immediate_value` DOUBLE NULL COMMENT '즉시 3단계 값: 이 값 이상이면 바로 3단계 (rule IMMEDIATE · CO 200)',
    `basis` VARCHAR(10) NOT NULL DEFAULT 'PROPOSED' COMMENT '근거 구분: STANDARD 기준·표준 / PROPOSED 제안',
    `note` VARCHAR(200) NULL COMMENT '비고',
    `valid_from` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '적용 시작',
    `valid_to` DATETIME(3) NULL COMMENT '적용 종료: NULL = 현재 적용 중',
    `changed_by` BIGINT NULL COMMENT '변경자 ID',
    `season` VARCHAR(12) NOT NULL DEFAULT 'ALL' COMMENT '계절',
    PRIMARY KEY (`threshold_id`),
    CONSTRAINT fk_sensor_threshold_sensor_id FOREIGN KEY (`sensor_id`) REFERENCES `sensor` (`sensor_id`),
    CONSTRAINT fk_sensor_threshold_changed_by FOREIGN KEY (`changed_by`) REFERENCES `app_user` (`user_id`)
) COMMENT = '센서 기준값';

CREATE TABLE `sensor_trend` (
    `sensor_id` BIGINT NOT NULL COMMENT '센서 ID',
    `ts` DATETIME(3) NOT NULL COMMENT '시각(1분)',
    `avg_v` DOUBLE NULL COMMENT '평균',
    `min_v` DOUBLE NULL COMMENT '최소',
    `max_v` DOUBLE NULL COMMENT '최대',
    `n` SMALLINT NOT NULL DEFAULT 0 COMMENT '샘플 수: 정상이면 12 (5초 × 12)',
    `lost_n` SMALLINT NOT NULL DEFAULT 0 COMMENT '통신 끊김 수: 그 1분 동안 quality=COMM_LOST 로 온 샘플 수',
    PRIMARY KEY (`sensor_id`, `ts`),
    CONSTRAINT fk_sensor_trend_sensor_id FOREIGN KEY (`sensor_id`) REFERENCES `sensor` (`sensor_id`)
) COMMENT = '센서 추이';

CREATE TABLE `sensor_daily` (
    `sensor_id` BIGINT NOT NULL COMMENT '센서 ID',
    `day` DATE NOT NULL COMMENT '날짜',
    `avg_v` DOUBLE NULL COMMENT '평균: 1분 추이의 평균',
    `min_v` DOUBLE NULL COMMENT '최소',
    `max_v` DOUBLE NULL COMMENT '최대',
    `n` INT NOT NULL DEFAULT 0 COMMENT '샘플 수: 정상이면 17280 (5초 기준)',
    `lost_n` INT NOT NULL DEFAULT 0 COMMENT '통신 끊김 수: COMM_LOST 샘플 수',
    PRIMARY KEY (`sensor_id`, `day`),
    CONSTRAINT fk_sensor_daily_sensor_id FOREIGN KEY (`sensor_id`) REFERENCES `sensor` (`sensor_id`)
) COMMENT = '센서 일 집계';

CREATE TABLE `sensor_event` (
    `event_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '이벤트 ID',
    `edge_key` VARCHAR(40) NOT NULL COMMENT '중복 방지 키: 공장 + 태그 + 시작 시각. P5-AT-301-20261117T175525, IR은 수집 서버가 촬영 시각으로 생성',
    `sensor_id` BIGINT NOT NULL COMMENT '센서 ID',
    `threshold_id` BIGINT NULL COMMENT '기준값 ID: 엣지가 적용한 기준. IR 이벤트는 NULL',
    `stage` SMALLINT NOT NULL COMMENT '현재 단계: 2 주의 / 3 경고. UPDATE 때 갱신',
    `max_stage` SMALLINT NOT NULL COMMENT '최고 단계: 구간 중 가장 높았던 단계',
    `rule` VARCHAR(10) NOT NULL COMMENT '판정 규칙: HI 상한 / LO 하한 / AVG 평균 / IMMEDIATE 즉시 / IR 열화상',
    `started_at` DATETIME(3) NOT NULL COMMENT '시작 시각: IR 이벤트는 촬영 시각',
    `ended_at` DATETIME(3) NULL COMMENT '종료 시각: NULL = 진행 중',
    `peak_value` DOUBLE NULL COMMENT '최고값: END 때 확정. LO 는 최저값, IR은 최고 온도',
    `roi` JSON NULL COMMENT '감지 영역: IR 이벤트의 열화상 좌표 (x, y, w, h)',
    `image_file_id` BIGINT NULL COMMENT '이벤트 사진 ID: IR 이벤트 사진',
    `video_file_id` BIGINT NULL COMMENT '이벤트 영상 ID: IR 이벤트 녹화 구간',
    `plc_ok` TINYINT(1) NULL COMMENT 'PLC 기록 성공: 엣지 단계 기록 결과. IR은 NULL',
    PRIMARY KEY (`event_id`),
    UNIQUE KEY uk_sensor_event_edge_key (`edge_key`),
    CONSTRAINT fk_sensor_event_sensor_id FOREIGN KEY (`sensor_id`) REFERENCES `sensor` (`sensor_id`),
    CONSTRAINT fk_sensor_event_threshold_id FOREIGN KEY (`threshold_id`) REFERENCES `sensor_threshold` (`threshold_id`),
    CONSTRAINT fk_sensor_event_image_file_id FOREIGN KEY (`image_file_id`) REFERENCES `file_asset` (`file_id`),
    CONSTRAINT fk_sensor_event_video_file_id FOREIGN KEY (`video_file_id`) REFERENCES `file_asset` (`file_id`)
) COMMENT = '센서 이벤트';

CREATE TABLE `sensor_event_sample` (
    `event_id` BIGINT NOT NULL COMMENT '이벤트 ID',
    `ts` DATETIME(3) NOT NULL COMMENT '샘플 시각',
    `value` DOUBLE NULL COMMENT '측정값: NULL = 결측',
    PRIMARY KEY (`event_id`, `ts`),
    CONSTRAINT fk_sensor_event_sample_event_id FOREIGN KEY (`event_id`) REFERENCES `sensor_event` (`event_id`) ON DELETE CASCADE
) COMMENT = '이벤트 샘플';

CREATE TABLE `alarm` (
    `alarm_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '경보 ID',
    `unit_id` BIGINT NOT NULL COMMENT '관제 단위 ID',
    `hazard` VARCHAR(4) NOT NULL COMMENT '위험 종류',
    `stage` SMALLINT NOT NULL COMMENT '단계: 2→3 격상 시 같은 행 갱신',
    `thermal_file` BIGINT NULL COMMENT '감지 사진 ID: 감지 시점 열화상 사진. IR 이벤트가 없으면 NULL',
    `status` VARCHAR(10) NOT NULL DEFAULT 'OPEN' COMMENT '상태',
    `raised_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '발생 시각',
    `acked_by` BIGINT NULL COMMENT '확인자 ID',
    `acked_at` DATETIME(3) NULL COMMENT '확인 시각',
    `closed_by` BIGINT NULL COMMENT '종료자 ID',
    `closed_at` DATETIME(3) NULL COMMENT '종료 시각',
    `close_reason` VARCHAR(20) NULL COMMENT '종료 사유: AUTO_RECOVERED 조치 없이 복귀 / ACTION_DONE 조치 후 복귀 / MANUAL 사용자 종료',
    PRIMARY KEY (`alarm_id`),
    CONSTRAINT fk_alarm_unit_id FOREIGN KEY (`unit_id`) REFERENCES `control_unit` (`unit_id`),
    CONSTRAINT fk_alarm_thermal_file FOREIGN KEY (`thermal_file`) REFERENCES `file_asset` (`file_id`),
    CONSTRAINT fk_alarm_acked_by FOREIGN KEY (`acked_by`) REFERENCES `app_user` (`user_id`),
    CONSTRAINT fk_alarm_closed_by FOREIGN KEY (`closed_by`) REFERENCES `app_user` (`user_id`)
) COMMENT = '경보';

CREATE TABLE `alarm_event` (
    `alarm_id` BIGINT NOT NULL COMMENT '경보 ID',
    `event_id` BIGINT NOT NULL COMMENT '이벤트 ID',
    PRIMARY KEY (`alarm_id`, `event_id`),
    CONSTRAINT fk_alarm_event_alarm_id FOREIGN KEY (`alarm_id`) REFERENCES `alarm` (`alarm_id`) ON DELETE CASCADE,
    CONSTRAINT fk_alarm_event_event_id FOREIGN KEY (`event_id`) REFERENCES `sensor_event` (`event_id`)
) COMMENT = '경보 근거';

CREATE TABLE `alarm_sensor_sample` (
    `alarm_id` BIGINT NOT NULL COMMENT '경보 ID: 열려 있던 알림',
    `sensor_id` BIGINT NOT NULL COMMENT '센서 ID: 그 관제 단위 센서 (전류·온도·CO, IR 제외)',
    `ts` DATETIME(3) NOT NULL COMMENT '측정 시각: 5초 값 시각',
    `value` DOUBLE NULL COMMENT '측정값: 5초 값',
    PRIMARY KEY (`alarm_id`, `sensor_id`, `ts`),
    CONSTRAINT fk_alarm_sensor_sample_alarm_id FOREIGN KEY (`alarm_id`) REFERENCES `alarm` (`alarm_id`) ON DELETE CASCADE,
    CONSTRAINT fk_alarm_sensor_sample_sensor_id FOREIGN KEY (`sensor_id`) REFERENCES `sensor` (`sensor_id`)
) COMMENT = '알림 센서 샘플';

CREATE TABLE `alarm_action` (
    `action_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '조치 ID',
    `alarm_id` BIGINT NOT NULL COMMENT '경보 ID',
    `action` VARCHAR(20) NOT NULL COMMENT '조치 종류',
    `target` VARCHAR(50) NULL COMMENT '대상',
    `result` VARCHAR(10) NULL COMMENT '결과',
    `detail` TEXT NULL COMMENT '상세',
    `user_id` BIGINT NULL COMMENT '조치자 ID: 시스템 자동이면 NULL',
    `done_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '조치 시각',
    PRIMARY KEY (`action_id`),
    CONSTRAINT fk_alarm_action_alarm_id FOREIGN KEY (`alarm_id`) REFERENCES `alarm` (`alarm_id`),
    CONSTRAINT fk_alarm_action_user_id FOREIGN KEY (`user_id`) REFERENCES `app_user` (`user_id`)
) COMMENT = '경보 조치';

CREATE TABLE `equipment_command` (
    `command_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '명령 ID',
    `equipment_id` BIGINT NOT NULL COMMENT '설비 ID',
    `alarm_id` BIGINT NULL COMMENT '경보 ID',
    `command` VARCHAR(10) NOT NULL COMMENT '명령: FAN_HZ 주파수 변경 / FAN_STOP 정지',
    `value` DOUBLE NULL COMMENT '값: FAN_HZ면 목표 주파수 Hz. 정상 40 / 가스 주의 45(75%) / 경보 60(100%)',
    `source` VARCHAR(8) NOT NULL DEFAULT 'USER' COMMENT '누가 보냈나: USER 사람이 화면에서(수동 승인 포함) / AUTO 가스 알림 때 서버 자동 제어 / RECOVERY 경보 해제 후 40Hz 복귀',
    `result` VARCHAR(10) NOT NULL DEFAULT 'PENDING' COMMENT '결과: AWAITING 승인 대기 / PENDING 전송 / DONE 도달 / FAILED 실패 / REJECTED 엣지 거부 / EXPIRED 만료 / DUPLICATE 중복 / CANCELLED 취소 / DENIED 승인 거절',
    `requested_by` BIGINT NULL COMMENT '요청자 ID: USER 일 때 누른 사람. AUTO·RECOVERY 는 NULL',
    `requested_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '요청 시각',
    `approved_by` BIGINT NULL COMMENT '승인자 ID: 수동 모드에서 승인·거절한 담당자',
    `approved_at` DATETIME(3) NULL COMMENT '승인 시각: 승인·거절한 시각',
    `expires_at` DATETIME(3) NULL COMMENT '만료 시간: 엣지가 이 시각 지난 명령은 EXPIRED 로 거부',
    `completed_at` DATETIME(3) NULL COMMENT '완료 시간: 엣지 결과 회신 시각',
    `result_detail` JSON NULL COMMENT '결과 상세: 목표 Hz · 출력 Hz · 전류 · 메시지 (엣지 회신 detail)',
    PRIMARY KEY (`command_id`),
    CONSTRAINT fk_equipment_command_equipment_id FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`equipment_id`),
    CONSTRAINT fk_equipment_command_alarm_id FOREIGN KEY (`alarm_id`) REFERENCES `alarm` (`alarm_id`),
    CONSTRAINT fk_equipment_command_requested_by FOREIGN KEY (`requested_by`) REFERENCES `app_user` (`user_id`),
    CONSTRAINT fk_equipment_command_approved_by FOREIGN KEY (`approved_by`) REFERENCES `app_user` (`user_id`)
) COMMENT = '설비 제어 명령';

CREATE TABLE `fire_incident` (
    `fire_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '화재 ID',
    `alarm_id` BIGINT NOT NULL COMMENT '경보 ID',
    `plant_id` BIGINT NOT NULL COMMENT '사업장 ID',
    `process_id` BIGINT NULL COMMENT '공정 ID',
    `equipment_id` BIGINT NULL COMMENT '설비 ID',
    `pos_x` DOUBLE NULL COMMENT 'X 위치',
    `pos_y` DOUBLE NULL COMMENT 'Y 위치',
    `max_temp` DOUBLE NULL COMMENT '최고 온도',
    `detected_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '감지 시각',
    `broadcast_started_at` DATETIME(3) NULL COMMENT '방송 시작',
    `broadcast_ended_at` DATETIME(3) NULL COMMENT '방송 종료',
    `hq_notified_at` DATETIME(3) NULL COMMENT '본사 통보 시각',
    `unlock_fail_count` SMALLINT NOT NULL DEFAULT 0 COMMENT '비상문 해제 실패 횟수',
    `status` VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' COMMENT '상태',
    `cleared_by` BIGINT NULL COMMENT '해제자 ID',
    `cleared_at` DATETIME(3) NULL COMMENT '해제 시각',
    PRIMARY KEY (`fire_id`),
    CONSTRAINT fk_fire_incident_alarm_id FOREIGN KEY (`alarm_id`) REFERENCES `alarm` (`alarm_id`),
    CONSTRAINT fk_fire_incident_plant_id FOREIGN KEY (`plant_id`) REFERENCES `plant` (`plant_id`),
    CONSTRAINT fk_fire_incident_process_id FOREIGN KEY (`process_id`) REFERENCES `process` (`process_id`),
    CONSTRAINT fk_fire_incident_equipment_id FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`equipment_id`),
    CONSTRAINT fk_fire_incident_cleared_by FOREIGN KEY (`cleared_by`) REFERENCES `app_user` (`user_id`)
) COMMENT = '화재 대응';

CREATE TABLE `checklist_template` (
    `template_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '양식 ID',
    `equipment_type` VARCHAR(30) NULL COMMENT '설비 종류',
    `sensor_type` VARCHAR(2) NULL COMMENT '센서 종류',
    `stage` SMALLINT NULL COMMENT '단계',
    `cause` VARCHAR(100) NULL COMMENT '원인: 예: 배기팬 정지',
    `items` JSON NOT NULL COMMENT '항목 목록',
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '사용 여부',
    PRIMARY KEY (`template_id`)
) COMMENT = '체크리스트 양식';

CREATE TABLE `checklist` (
    `checklist_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '체크리스트 ID',
    `alarm_id` BIGINT NOT NULL COMMENT '경보 ID',
    `template_id` BIGINT NOT NULL COMMENT '양식 ID',
    `assignee_id` BIGINT NOT NULL COMMENT '담당자 ID',
    `status` VARCHAR(12) NOT NULL DEFAULT 'SENT' COMMENT '상태',
    `sent_by` BIGINT NULL COMMENT '발송자 ID',
    `sent_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '발송 시각',
    `remind_count` SMALLINT NOT NULL DEFAULT 0 COMMENT '재알림 횟수',
    `last_remind_at` DATETIME(3) NULL COMMENT '마지막 재알림',
    `reported_at` DATETIME(3) NULL COMMENT '완료 보고 시각',
    `confirmed_by` BIGINT NULL COMMENT '확인자 ID',
    `confirmed_at` DATETIME(3) NULL COMMENT '확인 시각',
    PRIMARY KEY (`checklist_id`),
    CONSTRAINT fk_checklist_alarm_id FOREIGN KEY (`alarm_id`) REFERENCES `alarm` (`alarm_id`),
    CONSTRAINT fk_checklist_template_id FOREIGN KEY (`template_id`) REFERENCES `checklist_template` (`template_id`),
    CONSTRAINT fk_checklist_assignee_id FOREIGN KEY (`assignee_id`) REFERENCES `app_user` (`user_id`),
    CONSTRAINT fk_checklist_sent_by FOREIGN KEY (`sent_by`) REFERENCES `app_user` (`user_id`),
    CONSTRAINT fk_checklist_confirmed_by FOREIGN KEY (`confirmed_by`) REFERENCES `app_user` (`user_id`)
) COMMENT = '체크리스트';

CREATE TABLE `checklist_item` (
    `item_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '항목 ID',
    `checklist_id` BIGINT NOT NULL COMMENT '체크리스트 ID',
    `seq` SMALLINT NOT NULL COMMENT '순서',
    `content` VARCHAR(200) NOT NULL COMMENT '내용',
    `done_at` DATETIME(3) NULL COMMENT '완료 시각: NULL = 미완료',
    `result` VARCHAR(20) NULL COMMENT '결과',
    `memo` VARCHAR(200) NULL COMMENT '메모',
    PRIMARY KEY (`item_id`),
    CONSTRAINT fk_checklist_item_checklist_id FOREIGN KEY (`checklist_id`) REFERENCES `checklist` (`checklist_id`) ON DELETE CASCADE
) COMMENT = '체크리스트 항목';

CREATE TABLE `file_asset` (
    `file_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '파일 ID',
    `kind` VARCHAR(12) NOT NULL COMMENT '파일 종류: THERMAL 열화상 사진 / VIDEO 열화상 영상 / DOC_SCAN / FLOORPLAN / REPORT',
    `path` VARCHAR(300) NOT NULL COMMENT '저장 경로',
    `mime` VARCHAR(50) NULL COMMENT '파일 형식',
    `size_bytes` BIGINT NULL COMMENT '크기(byte)',
    `sensor_id` BIGINT NULL COMMENT '센서 ID: 열화상이면 찍은 카메라(IR 센서)',
    `captured_at` DATETIME(3) NULL COMMENT '촬영 시각',
    `meta` JSON NULL COMMENT '부가 정보: 열화상 최고 온도·위치 등',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '등록 시각',
    PRIMARY KEY (`file_id`),
    CONSTRAINT fk_file_asset_sensor_id FOREIGN KEY (`sensor_id`) REFERENCES `sensor` (`sensor_id`)
) COMMENT = '파일';

CREATE TABLE `role` (
    `role_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '권한 ID',
    `code` VARCHAR(12) NOT NULL COMMENT '권한 코드',
    `name` VARCHAR(30) NOT NULL COMMENT '권한명',
    PRIMARY KEY (`role_id`),
    UNIQUE KEY uk_role_code (`code`)
) COMMENT = '권한';

CREATE TABLE `app_user` (
    `user_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '사용자 ID',
    `plant_id` BIGINT NULL COMMENT '사업장 ID',
    `role_id` BIGINT NOT NULL COMMENT '권한 ID',
    `login_id` VARCHAR(30) NOT NULL COMMENT '로그인 ID',
    `password_hash` VARCHAR(100) NOT NULL COMMENT '비밀번호 해시',
    `name` VARCHAR(30) NOT NULL COMMENT '이름',
    `phone` VARCHAR(20) NULL COMMENT '전화번호',
    `fcm_token` VARCHAR(255) NULL COMMENT '푸시 토큰',
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '사용 여부',
    `pw_reset_required` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '비밀번호 변경 필요',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '생성 시각',
    `updated_at` DATETIME(3) NULL COMMENT '수정 시각',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY uk_app_user_login_id (`login_id`),
    CONSTRAINT fk_app_user_plant_id FOREIGN KEY (`plant_id`) REFERENCES `plant` (`plant_id`),
    CONSTRAINT fk_app_user_role_id FOREIGN KEY (`role_id`) REFERENCES `role` (`role_id`)
) COMMENT = '사용자';

CREATE TABLE `user_assignment` (
    `assignment_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '배정 ID',
    `user_id` BIGINT NOT NULL COMMENT '사용자 ID',
    `plant_id` BIGINT NULL COMMENT '사업장 ID',
    `factory_id` BIGINT NULL COMMENT '공장 ID',
    `process_id` BIGINT NULL COMMENT '공정 ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '배정 시각',
    PRIMARY KEY (`assignment_id`),
    CONSTRAINT fk_user_assignment_user_id FOREIGN KEY (`user_id`) REFERENCES `app_user` (`user_id`) ON DELETE CASCADE,
    CONSTRAINT fk_user_assignment_plant_id FOREIGN KEY (`plant_id`) REFERENCES `plant` (`plant_id`),
    CONSTRAINT fk_user_assignment_factory_id FOREIGN KEY (`factory_id`) REFERENCES `factory` (`factory_id`),
    CONSTRAINT fk_user_assignment_process_id FOREIGN KEY (`process_id`) REFERENCES `process` (`process_id`)
) COMMENT = '담당 범위';

CREATE TABLE `equipment_manager` (
    `equipment_id` BIGINT NOT NULL COMMENT '설비 ID',
    `user_id` BIGINT NOT NULL COMMENT '사용자 ID',
    `is_primary` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '주 담당',
    PRIMARY KEY (`equipment_id`, `user_id`),
    CONSTRAINT fk_equipment_manager_equipment_id FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`equipment_id`) ON DELETE CASCADE,
    CONSTRAINT fk_equipment_manager_user_id FOREIGN KEY (`user_id`) REFERENCES `app_user` (`user_id`) ON DELETE CASCADE
) COMMENT = '설비 담당자';

CREATE TABLE `login_history` (
    `login_id_seq` BIGINT AUTO_INCREMENT NOT NULL COMMENT '이력 ID',
    `user_id` BIGINT NOT NULL COMMENT '사용자 ID',
    `logged_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '로그인 시각',
    `ip_addr` VARCHAR(45) NULL COMMENT '접속 IP',
    `success` BOOLEAN NOT NULL COMMENT '성공 여부',
    PRIMARY KEY (`login_id_seq`),
    CONSTRAINT fk_login_history_user_id FOREIGN KEY (`user_id`) REFERENCES `app_user` (`user_id`)
) COMMENT = '로그인 이력';

CREATE TABLE `activity_log` (
    `log_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '로그 ID',
    `user_id` BIGINT NULL COMMENT '사용자 ID',
    `plant_id` BIGINT NULL COMMENT '사업장 ID: 조회 조건용',
    `process_id` BIGINT NULL COMMENT '공정 ID: 조회 조건용',
    `type` VARCHAR(20) NOT NULL COMMENT '작업 종류: LOGOUT / THRESHOLD / SHUTDOWN / FAN / LAYOUT / USER / EXPORT / INSPECTION / FIRE_CLEAR 등',
    `target_table` VARCHAR(40) NULL COMMENT '대상 테이블',
    `target_id` BIGINT NULL COMMENT '대상 ID',
    `summary` VARCHAR(200) NULL COMMENT '요약',
    `before` JSON NULL COMMENT '변경 전',
    `after` JSON NULL COMMENT '변경 후',
    `ip_addr` VARCHAR(45) NULL COMMENT 'IP',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '기록 시각',
    PRIMARY KEY (`log_id`),
    CONSTRAINT fk_activity_log_user_id FOREIGN KEY (`user_id`) REFERENCES `app_user` (`user_id`)
) COMMENT = '작업 로그';

CREATE TABLE `inspection_schedule` (
    `schedule_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '일정 ID',
    `equipment_id` BIGINT NOT NULL COMMENT '설비 ID',
    `assignee_id` BIGINT NULL COMMENT '담당자 ID',
    `planned_at` DATE NOT NULL COMMENT '예정일',
    `status` VARCHAR(10) NOT NULL DEFAULT 'PLANNED' COMMENT '상태',
    `title` VARCHAR(100) NOT NULL COMMENT '제목',
    `done_at` DATETIME(3) NULL COMMENT '완료 시각',
    `done_by` BIGINT NULL COMMENT '완료자 ID',
    PRIMARY KEY (`schedule_id`),
    CONSTRAINT fk_inspection_schedule_equipment_id FOREIGN KEY (`equipment_id`) REFERENCES `equipment` (`equipment_id`),
    CONSTRAINT fk_inspection_schedule_assignee_id FOREIGN KEY (`assignee_id`) REFERENCES `app_user` (`user_id`),
    CONSTRAINT fk_inspection_schedule_done_by FOREIGN KEY (`done_by`) REFERENCES `app_user` (`user_id`)
) COMMENT = '점검 일정';

CREATE TABLE `doc_form` (
    `form_type` VARCHAR(4) NOT NULL COMMENT '양식 코드: F01 ~ F05',
    `name` VARCHAR(50) NOT NULL COMMENT '양식명',
    `field_schema` JSON NOT NULL COMMENT '항목 정의',
    `version` SMALLINT NOT NULL DEFAULT 1 COMMENT '버전',
    PRIMARY KEY (`form_type`)
) COMMENT = '서류 양식';

CREATE TABLE `ocr_result` (
    `ocr_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '인식 ID',
    `schedule_id` BIGINT NULL COMMENT '일정 ID',
    `file_id` BIGINT NOT NULL COMMENT '파일 ID',
    `form_type` VARCHAR(4) NOT NULL COMMENT '양식 코드',
    `fields` JSON NULL COMMENT '인식 값',
    `confidence` DOUBLE NULL COMMENT '신뢰도: 0~1',
    `status` VARCHAR(12) NOT NULL DEFAULT 'UPLOADED' COMMENT '상태',
    `reviewed_by` BIGINT NULL COMMENT '검토자 ID',
    `reviewed_at` DATETIME(3) NULL COMMENT '검토 시각',
    `uploaded_by` BIGINT NOT NULL COMMENT '업로드자 ID',
    `process_id` BIGINT NULL COMMENT '공정 ID',
    PRIMARY KEY (`ocr_id`),
    CONSTRAINT fk_ocr_result_schedule_id FOREIGN KEY (`schedule_id`) REFERENCES `inspection_schedule` (`schedule_id`),
    CONSTRAINT fk_ocr_result_file_id FOREIGN KEY (`file_id`) REFERENCES `file_asset` (`file_id`),
    CONSTRAINT fk_ocr_result_form_type FOREIGN KEY (`form_type`) REFERENCES `doc_form` (`form_type`),
    CONSTRAINT fk_ocr_result_reviewed_by FOREIGN KEY (`reviewed_by`) REFERENCES `app_user` (`user_id`),
    CONSTRAINT fk_ocr_result_uploaded_by FOREIGN KEY (`uploaded_by`) REFERENCES `app_user` (`user_id`),
    CONSTRAINT fk_ocr_result_process_id FOREIGN KEY (`process_id`) REFERENCES `process` (`process_id`)
) COMMENT = '서류 인식 결과';

CREATE TABLE `chat_message` (
    `msg_id` BIGINT AUTO_INCREMENT NOT NULL COMMENT '메시지 ID',
    `plant_id` BIGINT NOT NULL COMMENT '사업장 ID: 대화방',
    `sender_id` BIGINT NOT NULL COMMENT '보낸 사람 ID',
    `schedule_id` BIGINT NULL COMMENT '점검 일정 ID',
    `alarm_id` BIGINT NULL COMMENT '경보 ID: 첨부된 경보',
    `body` TEXT NOT NULL COMMENT '내용',
    `sent_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '보낸 시각',
    PRIMARY KEY (`msg_id`),
    CONSTRAINT fk_chat_message_plant_id FOREIGN KEY (`plant_id`) REFERENCES `plant` (`plant_id`),
    CONSTRAINT fk_chat_message_sender_id FOREIGN KEY (`sender_id`) REFERENCES `app_user` (`user_id`),
    CONSTRAINT fk_chat_message_schedule_id FOREIGN KEY (`schedule_id`) REFERENCES `inspection_schedule` (`schedule_id`),
    CONSTRAINT fk_chat_message_alarm_id FOREIGN KEY (`alarm_id`) REFERENCES `alarm` (`alarm_id`)
) COMMENT = '채팅 메시지';

CREATE TABLE `chat_read` (
    `user_id` BIGINT NOT NULL COMMENT '사용자 ID',
    `plant_id` BIGINT NOT NULL COMMENT '사업장 ID',
    `last_read_msg_id` BIGINT NULL COMMENT '마지막 읽은 메시지 ID',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '읽은 시각',
    PRIMARY KEY (`user_id`, `plant_id`),
    CONSTRAINT fk_chat_read_user_id FOREIGN KEY (`user_id`) REFERENCES `app_user` (`user_id`) ON DELETE CASCADE,
    CONSTRAINT fk_chat_read_plant_id FOREIGN KEY (`plant_id`) REFERENCES `plant` (`plant_id`),
    CONSTRAINT fk_chat_read_last_read_msg_id FOREIGN KEY (`last_read_msg_id`) REFERENCES `chat_message` (`msg_id`)
) COMMENT = '채팅 읽음';

CREATE TABLE `safety_grade` (
    `factory_id` BIGINT NOT NULL COMMENT '공장 ID',
    `month` DATE NOT NULL COMMENT '해당 월: 매월 1일',
    `grade` CHAR(1) NOT NULL COMMENT '등급',
    `score` DOUBLE NULL COMMENT '점수',
    PRIMARY KEY (`factory_id`, `month`),
    CONSTRAINT fk_safety_grade_factory_id FOREIGN KEY (`factory_id`) REFERENCES `factory` (`factory_id`)
) COMMENT = '안전 등급';

SET FOREIGN_KEY_CHECKS = 1;