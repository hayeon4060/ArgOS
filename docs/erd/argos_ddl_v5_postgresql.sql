-- ArgOS DB DDL v5 (PostgreSQL)
-- 11/19 반영: 통계·이력 개편(alarm_sensor_sample, sensor_trend 1년 보관, alarm (hazard, raised_at) 인덱스) · 배기팬 자동/수동(equipment.fan_auto, equipment_command.source·승인)
-- v5 기준: 시스템 아키텍처 (DAS ↔ Kafka ↔ 데이터 수집 서버 · VMS → REST → 수집 서버) +
--          Node-RED "ArgOS 엣지 (운영)" Kafka 메시지 (argos.edge.raw / stage / cmd / cmd.result, argos.config.threshold)
-- 35개 테이블 · 자료형 가정: bigint / timestamptz / double precision (DB 담당 확정 전)
-- 순서: 테이블 생성 → 외래키 → 코멘트. 외래키는 순환 참조(file_asset ↔ sensor 등) 때문에 마지막에 추가

-- 사업장
CREATE TABLE plant (
    plant_id               bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    name                   varchar(50) NOT NULL,
    code                   varchar(10) NOT NULL,
    address                varchar(200),
    lat                    double precision,
    lng                    double precision,
    map_file_id            bigint,
    CONSTRAINT pk_plant PRIMARY KEY (plant_id),
    CONSTRAINT uk_plant_name UNIQUE (name),
    CONSTRAINT uk_plant_code UNIQUE (code)
);

-- 공장
CREATE TABLE factory (
    factory_id             bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    plant_id               bigint NOT NULL,
    code                   varchar(10) NOT NULL,
    name                   varchar(50) NOT NULL,
    map_shape              jsonb,
    sort_order             smallint NOT NULL DEFAULT 0,
    CONSTRAINT pk_factory PRIMARY KEY (factory_id),
    CONSTRAINT uk_factory_plant_id_code UNIQUE (plant_id, code)
);

-- 공정
CREATE TABLE process (
    process_id             bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    factory_id             bigint NOT NULL,
    code                   varchar(10) NOT NULL,
    name                   varchar(50) NOT NULL,
    tag_prefix             varchar(5),
    floorplan_file_id      bigint,
    CONSTRAINT pk_process PRIMARY KEY (process_id),
    CONSTRAINT uk_process_factory_id_code UNIQUE (factory_id, code)
);

-- 설비
CREATE TABLE equipment (
    equipment_id           bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    process_id             bigint NOT NULL,
    code                   varchar(20) NOT NULL,
    name                   varchar(50) NOT NULL,
    equipment_type         varchar(30) NOT NULL,
    shape                  varchar(20),
    pos_x                  double precision,
    pos_y                  double precision,
    pos_z                  double precision,
    size_w                 double precision,
    size_d                 double precision,
    size_h                 double precision,
    is_active              boolean NOT NULL DEFAULT true,
    run_state              varchar(10) CHECK (run_state IN ('RUN', 'STOPPING', 'STOPPED')),
    fan_rpm                int,
    fan_auto               boolean,
    CONSTRAINT pk_equipment PRIMARY KEY (equipment_id),
    CONSTRAINT uk_equipment_process_id_code UNIQUE (process_id, code)
);

-- 관제 단위
CREATE TABLE control_unit (
    unit_id                bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    process_id             bigint NOT NULL,
    equipment_id           bigint NOT NULL,
    name                   varchar(50) NOT NULL,
    is_active              boolean NOT NULL DEFAULT true,
    CONSTRAINT pk_control_unit PRIMARY KEY (unit_id)
);

-- 관제 단위 센서
CREATE TABLE control_unit_sensor (
    unit_id                bigint NOT NULL,
    sensor_id              bigint NOT NULL,
    hazard                 varchar(4) NOT NULL CHECK (hazard IN ('FIRE', 'GAS')),
    CONSTRAINT pk_control_unit_sensor PRIMARY KEY (unit_id, sensor_id, hazard)
);

-- 근무조
CREATE TABLE work_shift (
    shift_id               bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    plant_id               bigint NOT NULL,
    name                   varchar(20) NOT NULL,
    start_time             time NOT NULL,
    end_time               time NOT NULL,
    CONSTRAINT pk_work_shift PRIMARY KEY (shift_id)
);

-- 센서
CREATE TABLE sensor (
    sensor_id              bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    tag                    varchar(20) NOT NULL,
    equipment_id           bigint NOT NULL,
    sensor_type            varchar(2) NOT NULL CHECK (sensor_type IN ('TE', 'IT', 'AT', 'IR')),
    unit                   varchar(10),
    connection             varchar(4) NOT NULL CHECK (connection IN ('PLC', 'GW', 'VMS')),
    video_url              varchar(300),
    sample_sec             smallint NOT NULL DEFAULT 5,
    setpoint               double precision,
    is_active              boolean NOT NULL DEFAULT true,
    CONSTRAINT pk_sensor PRIMARY KEY (sensor_id),
    CONSTRAINT uk_sensor_tag UNIQUE (tag)
);

-- 센서 기준값
CREATE TABLE sensor_threshold (
    threshold_id           bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    sensor_id              bigint NOT NULL,
    warn_value             double precision,
    alarm_value            double precision,
    low_warn               double precision,
    low_alarm              double precision,
    rate_per_min           double precision,
    warn_samples           int NOT NULL DEFAULT 6,
    alarm_samples          int NOT NULL DEFAULT 3,
    release_samples        int NOT NULL DEFAULT 6,
    release_margin         double precision NOT NULL DEFAULT 0.05,
    alarm_avg_sec          int,
    immediate_value        double precision,
    basis                  varchar(10) NOT NULL DEFAULT 'PROPOSED' CHECK (basis IN ('STANDARD', 'PROPOSED')),
    note                   varchar(200),
    valid_from             timestamptz NOT NULL DEFAULT now(),
    valid_to               timestamptz,
    changed_by             bigint,
    season                 varchar(12) NOT NULL DEFAULT 'ALL' CHECK (season IN ('ALL', 'SPRING_FALL', 'SUMMER', 'WINTER')),
    CONSTRAINT pk_sensor_threshold PRIMARY KEY (threshold_id)
);

-- 센서 추이
CREATE TABLE sensor_trend (
    sensor_id              bigint NOT NULL,
    ts                     timestamptz NOT NULL,
    avg_v                  double precision,
    min_v                  double precision,
    max_v                  double precision,
    n                      smallint NOT NULL DEFAULT 0,
    lost_n                 smallint NOT NULL DEFAULT 0,
    CONSTRAINT pk_sensor_trend PRIMARY KEY (sensor_id, ts)
);

-- 센서 일 집계
CREATE TABLE sensor_daily (
    sensor_id              bigint NOT NULL,
    day                    date NOT NULL,
    avg_v                  double precision,
    min_v                  double precision,
    max_v                  double precision,
    n                      int NOT NULL DEFAULT 0,
    lost_n                 int NOT NULL DEFAULT 0,
    CONSTRAINT pk_sensor_daily PRIMARY KEY (sensor_id, day)
);

-- 센서 이벤트
CREATE TABLE sensor_event (
    event_id               bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    edge_key               varchar(40) NOT NULL,
    sensor_id              bigint NOT NULL,
    threshold_id           bigint,
    stage                  smallint NOT NULL CHECK (stage IN (2, 3)),
    max_stage              smallint NOT NULL CHECK (max_stage IN (2, 3)),
    rule                   varchar(10) NOT NULL CHECK (rule IN ('HI', 'LO', 'AVG', 'IMMEDIATE', 'IR')),
    started_at             timestamptz NOT NULL,
    ended_at               timestamptz,
    peak_value             double precision,
    roi                    jsonb,
    image_file_id          bigint,
    video_file_id          bigint,
    plc_ok                 boolean,
    CONSTRAINT pk_sensor_event PRIMARY KEY (event_id),
    CONSTRAINT uk_sensor_event_edge_key UNIQUE (edge_key)
);

-- 이벤트 샘플
CREATE TABLE sensor_event_sample (
    event_id               bigint NOT NULL,
    ts                     timestamptz NOT NULL,
    value                  double precision,
    CONSTRAINT pk_sensor_event_sample PRIMARY KEY (event_id, ts)
);

-- 경보
CREATE TABLE alarm (
    alarm_id               bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    unit_id                bigint NOT NULL,
    hazard                 varchar(4) NOT NULL CHECK (hazard IN ('FIRE', 'GAS')),
    stage                  smallint NOT NULL CHECK (stage IN (2, 3)),
    thermal_file           bigint,
    status                 varchar(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ACKED', 'ACTING', 'CLOSED')),
    raised_at              timestamptz NOT NULL DEFAULT now(),
    acked_by               bigint,
    acked_at               timestamptz,
    closed_by              bigint,
    closed_at              timestamptz,
    close_reason           varchar(20) CHECK (close_reason IN ('AUTO_RECOVERED', 'ACTION_DONE', 'MANUAL')),
    CONSTRAINT pk_alarm PRIMARY KEY (alarm_id)
);

-- 경보 근거
CREATE TABLE alarm_event (
    alarm_id               bigint NOT NULL,
    event_id               bigint NOT NULL,
    CONSTRAINT pk_alarm_event PRIMARY KEY (alarm_id, event_id)
);

-- 알림 센서 샘플
CREATE TABLE alarm_sensor_sample (
    alarm_id               bigint NOT NULL,
    sensor_id              bigint NOT NULL,
    ts                     timestamptz NOT NULL,
    value                  double precision,
    CONSTRAINT pk_alarm_sensor_sample PRIMARY KEY (alarm_id, sensor_id, ts)
);

-- 경보 조치
CREATE TABLE alarm_action (
    action_id              bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    alarm_id               bigint NOT NULL,
    action                 varchar(20) NOT NULL CHECK (action IN ('PUSH', 'BROADCAST', 'SHUTDOWN', 'CHECKLIST', 'MANUAL_NOTE', 'ACK', 'REASSIGN', 'EVACUATED')),
    target                 varchar(50),
    result                 varchar(10) CHECK (result IN ('OK', 'FAIL', 'PENDING')),
    detail                 text,
    user_id                bigint,
    done_at                timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_alarm_action PRIMARY KEY (action_id)
);

-- 설비 제어 명령
CREATE TABLE equipment_command (
    command_id             bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    equipment_id           bigint NOT NULL,
    alarm_id               bigint,
    command                varchar(10) NOT NULL CHECK (command IN ('FAN_HZ', 'FAN_STOP')),
    value                  double precision,
    source                 varchar(8) NOT NULL DEFAULT 'USER' CHECK (source IN ('USER', 'AUTO', 'RECOVERY')),
    result                 varchar(10) NOT NULL DEFAULT 'PENDING' CHECK (result IN ('AWAITING', 'PENDING', 'DONE', 'FAILED', 'REJECTED', 'EXPIRED', 'DUPLICATE', 'CANCELLED', 'DENIED')),
    requested_by           bigint,
    requested_at           timestamptz NOT NULL DEFAULT now(),
    approved_by            bigint,
    approved_at            timestamptz,
    expires_at             timestamptz,
    completed_at           timestamptz,
    result_detail          jsonb,
    CONSTRAINT pk_equipment_command PRIMARY KEY (command_id)
);

-- 화재 대응
CREATE TABLE fire_incident (
    fire_id                bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    alarm_id               bigint NOT NULL,
    plant_id               bigint NOT NULL,
    process_id             bigint,
    equipment_id           bigint,
    pos_x                  double precision,
    pos_y                  double precision,
    max_temp               double precision,
    detected_at            timestamptz NOT NULL DEFAULT now(),
    broadcast_started_at   timestamptz,
    broadcast_ended_at     timestamptz,
    hq_notified_at         timestamptz,
    unlock_fail_count      smallint NOT NULL DEFAULT 0,
    status                 varchar(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CLEARED')),
    cleared_by             bigint,
    cleared_at             timestamptz,
    CONSTRAINT pk_fire_incident PRIMARY KEY (fire_id)
);

-- 체크리스트 양식
CREATE TABLE checklist_template (
    template_id            bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    equipment_type         varchar(30),
    sensor_type            varchar(2),
    stage                  smallint,
    cause                  varchar(100),
    items                  jsonb NOT NULL,
    is_active              boolean NOT NULL DEFAULT true,
    CONSTRAINT pk_checklist_template PRIMARY KEY (template_id)
);

-- 체크리스트
CREATE TABLE checklist (
    checklist_id           bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    alarm_id               bigint NOT NULL,
    template_id            bigint NOT NULL,
    assignee_id            bigint NOT NULL,
    status                 varchar(12) NOT NULL DEFAULT 'SENT' CHECK (status IN ('SENT', 'IN_PROGRESS', 'REPORTED', 'CONFIRMED', 'CANCELLED')),
    sent_by                bigint,
    sent_at                timestamptz NOT NULL DEFAULT now(),
    remind_count           smallint NOT NULL DEFAULT 0,
    last_remind_at         timestamptz,
    reported_at            timestamptz,
    confirmed_by           bigint,
    confirmed_at           timestamptz,
    CONSTRAINT pk_checklist PRIMARY KEY (checklist_id)
);

-- 체크리스트 항목
CREATE TABLE checklist_item (
    item_id                bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    checklist_id           bigint NOT NULL,
    seq                    smallint NOT NULL,
    content                varchar(200) NOT NULL,
    done_at                timestamptz,
    result                 varchar(20),
    memo                   varchar(200),
    CONSTRAINT pk_checklist_item PRIMARY KEY (item_id)
);

-- 파일
CREATE TABLE file_asset (
    file_id                bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    kind                   varchar(12) NOT NULL CHECK (kind IN ('THERMAL', 'VIDEO', 'DOC_SCAN', 'FLOORPLAN', 'REPORT')),
    path                   varchar(300) NOT NULL,
    mime                   varchar(50),
    size_bytes             bigint,
    sensor_id              bigint,
    captured_at            timestamptz,
    meta                   jsonb,
    created_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_file_asset PRIMARY KEY (file_id)
);

-- 권한
CREATE TABLE role (
    role_id                bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    code                   varchar(12) NOT NULL CHECK (code IN ('WORKER', 'PLANT_ADMIN', 'HQ_ADMIN')),
    name                   varchar(30) NOT NULL,
    CONSTRAINT pk_role PRIMARY KEY (role_id),
    CONSTRAINT uk_role_code UNIQUE (code)
);

-- 사용자
CREATE TABLE app_user (
    user_id                bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    plant_id               bigint,
    role_id                bigint NOT NULL,
    login_id               varchar(30) NOT NULL,
    password_hash          varchar(100) NOT NULL,
    name                   varchar(30) NOT NULL,
    phone                  varchar(20),
    fcm_token              varchar(255),
    is_active              boolean NOT NULL DEFAULT true,
    pw_reset_required      boolean NOT NULL DEFAULT false,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz,
    CONSTRAINT pk_app_user PRIMARY KEY (user_id),
    CONSTRAINT uk_app_user_login_id UNIQUE (login_id)
);

-- 담당 범위
CREATE TABLE user_assignment (
    assignment_id          bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id                bigint NOT NULL,
    plant_id               bigint,
    factory_id             bigint,
    process_id             bigint,
    created_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_assignment PRIMARY KEY (assignment_id)
);

-- 설비 담당자
CREATE TABLE equipment_manager (
    equipment_id           bigint NOT NULL,
    user_id                bigint NOT NULL,
    is_primary             boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_equipment_manager PRIMARY KEY (equipment_id, user_id)
);

-- 로그인 이력
CREATE TABLE login_history (
    login_id_seq           bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id                bigint NOT NULL,
    logged_at              timestamptz NOT NULL DEFAULT now(),
    ip_addr                varchar(45),
    success                boolean NOT NULL,
    CONSTRAINT pk_login_history PRIMARY KEY (login_id_seq)
);

-- 작업 로그
CREATE TABLE activity_log (
    log_id                 bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    user_id                bigint,
    plant_id               bigint,
    process_id             bigint,
    type                   varchar(20) NOT NULL,
    target_table           varchar(40),
    target_id              bigint,
    summary                varchar(200),
    before                 jsonb,
    after                  jsonb,
    ip_addr                varchar(45),
    created_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_activity_log PRIMARY KEY (log_id)
);

-- 점검 일정
CREATE TABLE inspection_schedule (
    schedule_id            bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    equipment_id           bigint NOT NULL,
    assignee_id            bigint,
    planned_at             date NOT NULL,
    status                 varchar(10) NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED', 'DONE', 'OVERDUE')),
    title                  varchar(100) NOT NULL,
    done_at                timestamptz,
    done_by                bigint,
    CONSTRAINT pk_inspection_schedule PRIMARY KEY (schedule_id)
);

-- 서류 양식
CREATE TABLE doc_form (
    form_type              varchar(4) NOT NULL,
    name                   varchar(50) NOT NULL,
    field_schema           jsonb NOT NULL,
    version                smallint NOT NULL DEFAULT 1,
    CONSTRAINT pk_doc_form PRIMARY KEY (form_type)
);

-- 서류 인식 결과
CREATE TABLE ocr_result (
    ocr_id                 bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    schedule_id            bigint,
    file_id                bigint NOT NULL,
    form_type              varchar(4) NOT NULL,
    fields                 jsonb,
    confidence             double precision,
    status                 varchar(12) NOT NULL DEFAULT 'UPLOADED' CHECK (status IN ('UPLOADED', 'RECOGNIZING', 'REVIEW', 'DONE')),
    reviewed_by            bigint,
    reviewed_at            timestamptz,
    uploaded_by            bigint NOT NULL,
    process_id             bigint,
    CONSTRAINT pk_ocr_result PRIMARY KEY (ocr_id)
);

-- 채팅 메시지
CREATE TABLE chat_message (
    msg_id                 bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    plant_id               bigint NOT NULL,
    sender_id              bigint NOT NULL,
    schedule_id            bigint,
    alarm_id               bigint,
    body                   text NOT NULL,
    sent_at                timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_chat_message PRIMARY KEY (msg_id)
);

-- 채팅 읽음
CREATE TABLE chat_read (
    user_id                bigint NOT NULL,
    plant_id               bigint NOT NULL,
    last_read_msg_id       bigint,
    updated_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_chat_read PRIMARY KEY (user_id, plant_id)
);

-- 안전 등급
CREATE TABLE safety_grade (
    factory_id             bigint NOT NULL,
    month                  date NOT NULL,
    grade                  char(1) NOT NULL CHECK (grade IN ('A', 'B', 'C')),
    score                  double precision,
    CONSTRAINT pk_safety_grade PRIMARY KEY (factory_id, month)
);

-- ───────── 외래키 ─────────
ALTER TABLE plant ADD CONSTRAINT fk_plant_map_file_id FOREIGN KEY (map_file_id) REFERENCES file_asset (file_id);
ALTER TABLE factory ADD CONSTRAINT fk_factory_plant_id FOREIGN KEY (plant_id) REFERENCES plant (plant_id);
ALTER TABLE process ADD CONSTRAINT fk_process_factory_id FOREIGN KEY (factory_id) REFERENCES factory (factory_id);
ALTER TABLE process ADD CONSTRAINT fk_process_floorplan_file_id FOREIGN KEY (floorplan_file_id) REFERENCES file_asset (file_id);
ALTER TABLE equipment ADD CONSTRAINT fk_equipment_process_id FOREIGN KEY (process_id) REFERENCES process (process_id);
ALTER TABLE control_unit ADD CONSTRAINT fk_control_unit_process_id FOREIGN KEY (process_id) REFERENCES process (process_id);
ALTER TABLE control_unit ADD CONSTRAINT fk_control_unit_equipment_id FOREIGN KEY (equipment_id) REFERENCES equipment (equipment_id);
ALTER TABLE control_unit_sensor ADD CONSTRAINT fk_control_unit_sensor_unit_id FOREIGN KEY (unit_id) REFERENCES control_unit (unit_id) ON DELETE CASCADE;
ALTER TABLE control_unit_sensor ADD CONSTRAINT fk_control_unit_sensor_sensor_id FOREIGN KEY (sensor_id) REFERENCES sensor (sensor_id);
ALTER TABLE work_shift ADD CONSTRAINT fk_work_shift_plant_id FOREIGN KEY (plant_id) REFERENCES plant (plant_id);
ALTER TABLE sensor ADD CONSTRAINT fk_sensor_equipment_id FOREIGN KEY (equipment_id) REFERENCES equipment (equipment_id);
ALTER TABLE sensor_threshold ADD CONSTRAINT fk_sensor_threshold_sensor_id FOREIGN KEY (sensor_id) REFERENCES sensor (sensor_id);
ALTER TABLE sensor_threshold ADD CONSTRAINT fk_sensor_threshold_changed_by FOREIGN KEY (changed_by) REFERENCES app_user (user_id);
ALTER TABLE sensor_trend ADD CONSTRAINT fk_sensor_trend_sensor_id FOREIGN KEY (sensor_id) REFERENCES sensor (sensor_id);
ALTER TABLE sensor_daily ADD CONSTRAINT fk_sensor_daily_sensor_id FOREIGN KEY (sensor_id) REFERENCES sensor (sensor_id);
ALTER TABLE sensor_event ADD CONSTRAINT fk_sensor_event_sensor_id FOREIGN KEY (sensor_id) REFERENCES sensor (sensor_id);
ALTER TABLE sensor_event ADD CONSTRAINT fk_sensor_event_threshold_id FOREIGN KEY (threshold_id) REFERENCES sensor_threshold (threshold_id);
ALTER TABLE sensor_event ADD CONSTRAINT fk_sensor_event_image_file_id FOREIGN KEY (image_file_id) REFERENCES file_asset (file_id);
ALTER TABLE sensor_event ADD CONSTRAINT fk_sensor_event_video_file_id FOREIGN KEY (video_file_id) REFERENCES file_asset (file_id);
ALTER TABLE sensor_event_sample ADD CONSTRAINT fk_sensor_event_sample_event_id FOREIGN KEY (event_id) REFERENCES sensor_event (event_id) ON DELETE CASCADE;
ALTER TABLE alarm ADD CONSTRAINT fk_alarm_unit_id FOREIGN KEY (unit_id) REFERENCES control_unit (unit_id);
ALTER TABLE alarm ADD CONSTRAINT fk_alarm_thermal_file FOREIGN KEY (thermal_file) REFERENCES file_asset (file_id);
ALTER TABLE alarm ADD CONSTRAINT fk_alarm_acked_by FOREIGN KEY (acked_by) REFERENCES app_user (user_id);
ALTER TABLE alarm ADD CONSTRAINT fk_alarm_closed_by FOREIGN KEY (closed_by) REFERENCES app_user (user_id);
ALTER TABLE alarm_event ADD CONSTRAINT fk_alarm_event_alarm_id FOREIGN KEY (alarm_id) REFERENCES alarm (alarm_id) ON DELETE CASCADE;
ALTER TABLE alarm_event ADD CONSTRAINT fk_alarm_event_event_id FOREIGN KEY (event_id) REFERENCES sensor_event (event_id);
ALTER TABLE alarm_sensor_sample ADD CONSTRAINT fk_alarm_sensor_sample_alarm_id FOREIGN KEY (alarm_id) REFERENCES alarm (alarm_id) ON DELETE CASCADE;
ALTER TABLE alarm_sensor_sample ADD CONSTRAINT fk_alarm_sensor_sample_sensor_id FOREIGN KEY (sensor_id) REFERENCES sensor (sensor_id);
ALTER TABLE equipment_command ADD CONSTRAINT fk_equipment_command_approved_by FOREIGN KEY (approved_by) REFERENCES app_user (user_id);
ALTER TABLE alarm_action ADD CONSTRAINT fk_alarm_action_alarm_id FOREIGN KEY (alarm_id) REFERENCES alarm (alarm_id);
ALTER TABLE alarm_action ADD CONSTRAINT fk_alarm_action_user_id FOREIGN KEY (user_id) REFERENCES app_user (user_id);
ALTER TABLE equipment_command ADD CONSTRAINT fk_equipment_command_equipment_id FOREIGN KEY (equipment_id) REFERENCES equipment (equipment_id);
ALTER TABLE equipment_command ADD CONSTRAINT fk_equipment_command_alarm_id FOREIGN KEY (alarm_id) REFERENCES alarm (alarm_id);
ALTER TABLE equipment_command ADD CONSTRAINT fk_equipment_command_requested_by FOREIGN KEY (requested_by) REFERENCES app_user (user_id);
ALTER TABLE fire_incident ADD CONSTRAINT fk_fire_incident_alarm_id FOREIGN KEY (alarm_id) REFERENCES alarm (alarm_id);
ALTER TABLE fire_incident ADD CONSTRAINT fk_fire_incident_plant_id FOREIGN KEY (plant_id) REFERENCES plant (plant_id);
ALTER TABLE fire_incident ADD CONSTRAINT fk_fire_incident_process_id FOREIGN KEY (process_id) REFERENCES process (process_id);
ALTER TABLE fire_incident ADD CONSTRAINT fk_fire_incident_equipment_id FOREIGN KEY (equipment_id) REFERENCES equipment (equipment_id);
ALTER TABLE fire_incident ADD CONSTRAINT fk_fire_incident_cleared_by FOREIGN KEY (cleared_by) REFERENCES app_user (user_id);
ALTER TABLE checklist ADD CONSTRAINT fk_checklist_alarm_id FOREIGN KEY (alarm_id) REFERENCES alarm (alarm_id);
ALTER TABLE checklist ADD CONSTRAINT fk_checklist_template_id FOREIGN KEY (template_id) REFERENCES checklist_template (template_id);
ALTER TABLE checklist ADD CONSTRAINT fk_checklist_assignee_id FOREIGN KEY (assignee_id) REFERENCES app_user (user_id);
ALTER TABLE checklist ADD CONSTRAINT fk_checklist_sent_by FOREIGN KEY (sent_by) REFERENCES app_user (user_id);
ALTER TABLE checklist ADD CONSTRAINT fk_checklist_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES app_user (user_id);
ALTER TABLE checklist_item ADD CONSTRAINT fk_checklist_item_checklist_id FOREIGN KEY (checklist_id) REFERENCES checklist (checklist_id) ON DELETE CASCADE;
ALTER TABLE file_asset ADD CONSTRAINT fk_file_asset_sensor_id FOREIGN KEY (sensor_id) REFERENCES sensor (sensor_id);
ALTER TABLE app_user ADD CONSTRAINT fk_app_user_plant_id FOREIGN KEY (plant_id) REFERENCES plant (plant_id);
ALTER TABLE app_user ADD CONSTRAINT fk_app_user_role_id FOREIGN KEY (role_id) REFERENCES role (role_id);
ALTER TABLE user_assignment ADD CONSTRAINT fk_user_assignment_user_id FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE;
ALTER TABLE user_assignment ADD CONSTRAINT fk_user_assignment_plant_id FOREIGN KEY (plant_id) REFERENCES plant (plant_id);
ALTER TABLE user_assignment ADD CONSTRAINT fk_user_assignment_factory_id FOREIGN KEY (factory_id) REFERENCES factory (factory_id);
ALTER TABLE user_assignment ADD CONSTRAINT fk_user_assignment_process_id FOREIGN KEY (process_id) REFERENCES process (process_id);
ALTER TABLE equipment_manager ADD CONSTRAINT fk_equipment_manager_equipment_id FOREIGN KEY (equipment_id) REFERENCES equipment (equipment_id) ON DELETE CASCADE;
ALTER TABLE equipment_manager ADD CONSTRAINT fk_equipment_manager_user_id FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE;
ALTER TABLE login_history ADD CONSTRAINT fk_login_history_user_id FOREIGN KEY (user_id) REFERENCES app_user (user_id);
ALTER TABLE activity_log ADD CONSTRAINT fk_activity_log_user_id FOREIGN KEY (user_id) REFERENCES app_user (user_id);
ALTER TABLE inspection_schedule ADD CONSTRAINT fk_inspection_schedule_equipment_id FOREIGN KEY (equipment_id) REFERENCES equipment (equipment_id);
ALTER TABLE inspection_schedule ADD CONSTRAINT fk_inspection_schedule_assignee_id FOREIGN KEY (assignee_id) REFERENCES app_user (user_id);
ALTER TABLE inspection_schedule ADD CONSTRAINT fk_inspection_schedule_done_by FOREIGN KEY (done_by) REFERENCES app_user (user_id);
ALTER TABLE ocr_result ADD CONSTRAINT fk_ocr_result_schedule_id FOREIGN KEY (schedule_id) REFERENCES inspection_schedule (schedule_id);
ALTER TABLE ocr_result ADD CONSTRAINT fk_ocr_result_file_id FOREIGN KEY (file_id) REFERENCES file_asset (file_id);
ALTER TABLE ocr_result ADD CONSTRAINT fk_ocr_result_form_type FOREIGN KEY (form_type) REFERENCES doc_form (form_type);
ALTER TABLE ocr_result ADD CONSTRAINT fk_ocr_result_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES app_user (user_id);
ALTER TABLE ocr_result ADD CONSTRAINT fk_ocr_result_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES app_user (user_id);
ALTER TABLE ocr_result ADD CONSTRAINT fk_ocr_result_process_id FOREIGN KEY (process_id) REFERENCES process (process_id);
ALTER TABLE chat_message ADD CONSTRAINT fk_chat_message_plant_id FOREIGN KEY (plant_id) REFERENCES plant (plant_id);
ALTER TABLE chat_message ADD CONSTRAINT fk_chat_message_sender_id FOREIGN KEY (sender_id) REFERENCES app_user (user_id);
ALTER TABLE chat_message ADD CONSTRAINT fk_chat_message_schedule_id FOREIGN KEY (schedule_id) REFERENCES inspection_schedule (schedule_id);
ALTER TABLE chat_message ADD CONSTRAINT fk_chat_message_alarm_id FOREIGN KEY (alarm_id) REFERENCES alarm (alarm_id);
ALTER TABLE chat_read ADD CONSTRAINT fk_chat_read_user_id FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE;
ALTER TABLE chat_read ADD CONSTRAINT fk_chat_read_plant_id FOREIGN KEY (plant_id) REFERENCES plant (plant_id);
ALTER TABLE chat_read ADD CONSTRAINT fk_chat_read_last_read_msg_id FOREIGN KEY (last_read_msg_id) REFERENCES chat_message (msg_id);
ALTER TABLE safety_grade ADD CONSTRAINT fk_safety_grade_factory_id FOREIGN KEY (factory_id) REFERENCES factory (factory_id);

-- 조회용 인덱스
CREATE INDEX ix_sensor_trend_ts ON sensor_trend (ts);   -- 7일 지난 행 삭제용
CREATE INDEX ix_sensor_event_sensor_started ON sensor_event (sensor_id, started_at);
CREATE INDEX ix_alarm_unit_status ON alarm (unit_id, status);
CREATE INDEX ix_alarm_hazard_raised ON alarm (hazard, raised_at DESC);   -- 통계·이력: 기간 + 유형
CREATE INDEX ix_equipment_command_awaiting ON equipment_command (equipment_id) WHERE result = 'AWAITING';   -- 승인 대기
CREATE INDEX ix_equipment_command_alarm ON equipment_command (alarm_id);
CREATE INDEX ix_sensor_threshold_sensor_valid ON sensor_threshold (sensor_id, valid_from);

-- ───────── 코멘트 (논리 이름: 설명) ─────────
COMMENT ON TABLE plant IS '사업장';
COMMENT ON COLUMN plant.plant_id IS '사업장 ID';
COMMENT ON COLUMN plant.name IS '사업장명';
COMMENT ON COLUMN plant.code IS '사업장 코드: 약어 코드';
COMMENT ON COLUMN plant.address IS '주소';
COMMENT ON COLUMN plant.lat IS '위도';
COMMENT ON COLUMN plant.lng IS '경도';
COMMENT ON COLUMN plant.map_file_id IS '지도 파일 ID: 사업장 2D 지도 그림';

COMMENT ON TABLE factory IS '공장: 공장 (1~5공장)';
COMMENT ON COLUMN factory.factory_id IS '공장 ID';
COMMENT ON COLUMN factory.plant_id IS '사업장 ID';
COMMENT ON COLUMN factory.code IS '공장 코드: F1~F5';
COMMENT ON COLUMN factory.name IS '공장명';
COMMENT ON COLUMN factory.map_shape IS '지도 구역: 사업장 지도 위 구역 좌표';
COMMENT ON COLUMN factory.sort_order IS '표시 순서';

COMMENT ON TABLE process IS '공정';
COMMENT ON COLUMN process.process_id IS '공정 ID';
COMMENT ON COLUMN process.factory_id IS '공장 ID';
COMMENT ON COLUMN process.code IS '공정 코드: PRESS / BODY / PAINT / ASSY / INSP / COMMON';
COMMENT ON COLUMN process.name IS '공정명';
COMMENT ON COLUMN process.tag_prefix IS '태그 앞자리: 센서 태그 번호 앞자리 (3 = 도장)';
COMMENT ON COLUMN process.floorplan_file_id IS '배치도 파일 ID: 2D 배치도 그림';

COMMENT ON TABLE equipment IS '설비';
COMMENT ON COLUMN equipment.equipment_id IS '설비 ID';
COMMENT ON COLUMN equipment.process_id IS '공정 ID';
COMMENT ON COLUMN equipment.code IS '설비 코드';
COMMENT ON COLUMN equipment.name IS '설비명: 전착 건조로 등';
COMMENT ON COLUMN equipment.equipment_type IS '설비 종류: 배기팬 / 건조로 / 부스 / 탱크 / CAMERA 등';
COMMENT ON COLUMN equipment.shape IS '모양';
COMMENT ON COLUMN equipment.pos_x IS 'X 위치';
COMMENT ON COLUMN equipment.pos_y IS 'Y 위치';
COMMENT ON COLUMN equipment.pos_z IS 'Z 위치';
COMMENT ON COLUMN equipment.size_w IS '가로';
COMMENT ON COLUMN equipment.size_d IS '세로';
COMMENT ON COLUMN equipment.size_h IS '높이';
COMMENT ON COLUMN equipment.is_active IS '사용 여부: false면 배치도에서 숨김';
COMMENT ON COLUMN equipment.run_state IS '운전 상태';
COMMENT ON COLUMN equipment.fan_rpm IS '팬 회전수: 배기팬 현재 회전수 (4극 모터 rpm = 30 × Hz)';
COMMENT ON COLUMN equipment.fan_auto IS '배기팬 자동 제어: true 자동(가스 주의 45Hz·경보 60Hz로 서버가 올림) / false 수동(담당자 승인 후 제어, 승인 없으면 안내만) / 배기팬이 아니면 NULL';

COMMENT ON TABLE control_unit IS '관제 단위: 판정 단위 (예: 전착 건조로). 서버가 단위별로 최종 판정';
COMMENT ON COLUMN control_unit.unit_id IS '관제 단위 ID';
COMMENT ON COLUMN control_unit.process_id IS '공정 ID';
COMMENT ON COLUMN control_unit.equipment_id IS '대표 설비 ID';
COMMENT ON COLUMN control_unit.name IS '관제 단위명';
COMMENT ON COLUMN control_unit.is_active IS '사용 여부: false면 판정 제외';

COMMENT ON TABLE control_unit_sensor IS '관제 단위 센서: 관제 단위에 묶인 센서와 위험 종류';
COMMENT ON COLUMN control_unit_sensor.unit_id IS '관제 단위 ID';
COMMENT ON COLUMN control_unit_sensor.sensor_id IS '센서 ID';
COMMENT ON COLUMN control_unit_sensor.hazard IS '위험 종류';

COMMENT ON TABLE work_shift IS '근무조';
COMMENT ON COLUMN work_shift.shift_id IS '근무조 ID';
COMMENT ON COLUMN work_shift.plant_id IS '사업장 ID';
COMMENT ON COLUMN work_shift.name IS '근무조명: 주간 / 야간';
COMMENT ON COLUMN work_shift.start_time IS '시작 시각';
COMMENT ON COLUMN work_shift.end_time IS '종료 시각';

COMMENT ON TABLE sensor IS '센서: 센서 (태그)';
COMMENT ON COLUMN sensor.sensor_id IS '센서 ID';
COMMENT ON COLUMN sensor.tag IS '태그: TE-303 · IT-303 · AT-301 · IR-301';
COMMENT ON COLUMN sensor.equipment_id IS '설비 ID';
COMMENT ON COLUMN sensor.sensor_type IS '센서 종류: TE 온도 / IT 전류 / AT 가스 / IR 열화상';
COMMENT ON COLUMN sensor.unit IS '단위: ℃ / A / ppm';
COMMENT ON COLUMN sensor.connection IS '연결 방식';
COMMENT ON COLUMN sensor.video_url IS '영상 주소: 열화상 카메라 등 실시간 영상 주소';
COMMENT ON COLUMN sensor.sample_sec IS '수집 주기(초)';
COMMENT ON COLUMN sensor.setpoint IS '정상 기준값: TE-303 = 180, IT-303 = 25.5';
COMMENT ON COLUMN sensor.is_active IS '사용 여부: false면 수집·판정 제외';

COMMENT ON TABLE sensor_threshold IS '센서 기준값: 센서별 단계 기준 (행 단위 이력). 엣지가 센서별 단계 판정에 사용';
COMMENT ON COLUMN sensor_threshold.threshold_id IS '기준값 ID: 서버가 Kafka argos.config.threshold 로 엣지에 발행, 엣지가 이벤트에 붙여 보냄';
COMMENT ON COLUMN sensor_threshold.sensor_id IS '센서 ID';
COMMENT ON COLUMN sensor_threshold.warn_value IS '2단계 상한: 이 값 이상 (rule HI)';
COMMENT ON COLUMN sensor_threshold.alarm_value IS '3단계 상한: 이 값 이상';
COMMENT ON COLUMN sensor_threshold.low_warn IS '2단계 하한: 이 값 이하면 2단계 (rule LO · 전류 저하, IT-303 17.85A)';
COMMENT ON COLUMN sensor_threshold.low_alarm IS '3단계 하한: 이 값 이하면 3단계 (전류 정지, IT-303 5.0A)';
COMMENT ON COLUMN sensor_threshold.rate_per_min IS '분당 변화율 기준: 현재 미사용';
COMMENT ON COLUMN sensor_threshold.warn_samples IS '2단계 연속 횟수: 5초 샘플 6회 = 30초';
COMMENT ON COLUMN sensor_threshold.alarm_samples IS '3단계 연속 횟수: 5초 샘플 3회 = 15초';
COMMENT ON COLUMN sensor_threshold.release_samples IS '복귀 연속 횟수: 정상 6회 연속이면 1단계 복귀';
COMMENT ON COLUMN sensor_threshold.release_margin IS '복귀 여유: 기준값의 5% 안쪽으로 돌아와야 정상';
COMMENT ON COLUMN sensor_threshold.alarm_avg_sec IS '3단계 평균 시간(초): CO 60초 평균 (현재 엣지 미구현, 예약)';
COMMENT ON COLUMN sensor_threshold.immediate_value IS '즉시 3단계 값: 이 값 이상이면 바로 3단계 (rule IMMEDIATE · CO 200)';
COMMENT ON COLUMN sensor_threshold.basis IS '근거 구분: STANDARD 기준·표준 / PROPOSED 제안';
COMMENT ON COLUMN sensor_threshold.note IS '비고';
COMMENT ON COLUMN sensor_threshold.valid_from IS '적용 시작';
COMMENT ON COLUMN sensor_threshold.valid_to IS '적용 종료: NULL = 현재 적용 중';
COMMENT ON COLUMN sensor_threshold.changed_by IS '변경자 ID';
COMMENT ON COLUMN sensor_threshold.season IS '계절';


COMMENT ON TABLE sensor_trend IS '센서 추이: 1분 집계 · 최소 1년 보관 (통계·이력 1년 조회, 30분 넘는 알림 그래프). 수집 서버가 Kafka argos.edge.raw 5초 값을 1분으로 묶어 저장';
COMMENT ON COLUMN sensor_trend.sensor_id IS '센서 ID';
COMMENT ON COLUMN sensor_trend.ts IS '시각(1분)';
COMMENT ON COLUMN sensor_trend.avg_v IS '평균';
COMMENT ON COLUMN sensor_trend.min_v IS '최소';
COMMENT ON COLUMN sensor_trend.max_v IS '최대';
COMMENT ON COLUMN sensor_trend.n IS '샘플 수: 정상이면 12 (5초 × 12)';
COMMENT ON COLUMN sensor_trend.lost_n IS '통신 끊김 수: 그 1분 동안 quality=COMM_LOST 로 온 샘플 수';

COMMENT ON TABLE sensor_daily IS '센서 일 집계: 중앙 서버가 하루 1번 sensor_trend 를 묶어 저장 · 장기 보관';
COMMENT ON COLUMN sensor_daily.sensor_id IS '센서 ID';
COMMENT ON COLUMN sensor_daily.day IS '날짜';
COMMENT ON COLUMN sensor_daily.avg_v IS '평균: 1분 추이의 평균';
COMMENT ON COLUMN sensor_daily.min_v IS '최소';
COMMENT ON COLUMN sensor_daily.max_v IS '최대';
COMMENT ON COLUMN sensor_daily.n IS '샘플 수: 정상이면 17280 (5초 기준)';
COMMENT ON COLUMN sensor_daily.lost_n IS '통신 끊김 수: COMM_LOST 샘플 수';
COMMENT ON TABLE sensor_event IS '센서 이벤트: 센서 1개의 이상 구간 1건. 엣지가 Kafka argos.edge.stage 로 START/UPDATE/END 전송, IR 이벤트는 수집 서버가 VMS 수신 후 생성';
COMMENT ON COLUMN sensor_event.event_id IS '이벤트 ID';
COMMENT ON COLUMN sensor_event.edge_key IS '중복 방지 키: 공장 + 태그 + 시작 시각. P5-AT-301-20261117T175525, IR은 수집 서버가 촬영 시각으로 생성';
COMMENT ON COLUMN sensor_event.sensor_id IS '센서 ID';
COMMENT ON COLUMN sensor_event.threshold_id IS '기준값 ID: 엣지가 적용한 기준. IR 이벤트는 NULL';
COMMENT ON COLUMN sensor_event.stage IS '현재 단계: 2 주의 / 3 경고. UPDATE 때 갱신';
COMMENT ON COLUMN sensor_event.max_stage IS '최고 단계: 구간 중 가장 높았던 단계';
COMMENT ON COLUMN sensor_event.rule IS '판정 규칙: HI 상한 / LO 하한 / AVG 평균 / IMMEDIATE 즉시 / IR 열화상';
COMMENT ON COLUMN sensor_event.started_at IS '시작 시각: IR 이벤트는 촬영 시각';
COMMENT ON COLUMN sensor_event.ended_at IS '종료 시각: NULL = 진행 중';
COMMENT ON COLUMN sensor_event.peak_value IS '최고값: END 때 확정. LO 는 최저값, IR은 최고 온도';
COMMENT ON COLUMN sensor_event.roi IS '감지 영역: IR 이벤트의 열화상 좌표 (x, y, w, h)';
COMMENT ON COLUMN sensor_event.image_file_id IS '이벤트 사진 ID: IR 이벤트 사진';
COMMENT ON COLUMN sensor_event.video_file_id IS '이벤트 영상 ID: IR 이벤트 녹화 구간';
COMMENT ON COLUMN sensor_event.plc_ok IS 'PLC 기록 성공: 엣지 단계 기록 결과. IR은 NULL';

COMMENT ON TABLE sensor_event_sample IS '이벤트 샘플: 이벤트 구간 5초 값 · 장기 보관. 수집 서버가 이벤트 진행 중 argos.edge.raw 값을 저장';
COMMENT ON COLUMN sensor_event_sample.event_id IS '이벤트 ID';
COMMENT ON COLUMN sensor_event_sample.ts IS '샘플 시각';
COMMENT ON COLUMN sensor_event_sample.value IS '측정값: NULL = 결측';

COMMENT ON TABLE alarm IS '경보: 관제 단위 경보. 서버가 센서 이벤트 + IR 이벤트를 합쳐 최종 판정';
COMMENT ON COLUMN alarm.alarm_id IS '경보 ID';
COMMENT ON COLUMN alarm.unit_id IS '관제 단위 ID';
COMMENT ON COLUMN alarm.hazard IS '위험 종류';
COMMENT ON COLUMN alarm.stage IS '단계: 2→3 격상 시 같은 행 갱신';
COMMENT ON COLUMN alarm.thermal_file IS '감지 사진 ID: 감지 시점 열화상 사진. IR 이벤트가 없으면 NULL';
COMMENT ON COLUMN alarm.status IS '상태';
COMMENT ON COLUMN alarm.raised_at IS '발생 시각';
COMMENT ON COLUMN alarm.acked_by IS '확인자 ID';
COMMENT ON COLUMN alarm.acked_at IS '확인 시각';
COMMENT ON COLUMN alarm.closed_by IS '종료자 ID';
COMMENT ON COLUMN alarm.closed_at IS '종료 시각';
COMMENT ON COLUMN alarm.close_reason IS '종료 사유: AUTO_RECOVERED 조치 없이 복귀 / ACTION_DONE 조치 후 복귀 / MANUAL 사용자 종료';

COMMENT ON TABLE alarm_event IS '경보 근거: 경보의 근거가 된 센서 이벤트';
COMMENT ON COLUMN alarm_event.alarm_id IS '경보 ID';
COMMENT ON COLUMN alarm_event.event_id IS '이벤트 ID';

COMMENT ON TABLE alarm_sensor_sample IS '알림 센서 샘플: 알림이 열려 있는 동안(처음 기준 초과 ~ 해제) 그 관제 단위 센서 전부의 5초 값. 30분 이하 알림 상세 그래프·센서값 CSV';
COMMENT ON COLUMN alarm_sensor_sample.alarm_id IS '경보 ID: 열려 있던 알림';
COMMENT ON COLUMN alarm_sensor_sample.sensor_id IS '센서 ID: 그 관제 단위 센서 (전류·온도·CO, IR 제외)';
COMMENT ON COLUMN alarm_sensor_sample.ts IS '측정 시각: 5초 값 시각';
COMMENT ON COLUMN alarm_sensor_sample.value IS '측정값: 5초 값';
COMMENT ON TABLE alarm_action IS '경보 조치: 경보 조치 기록';
COMMENT ON COLUMN alarm_action.action_id IS '조치 ID';
COMMENT ON COLUMN alarm_action.alarm_id IS '경보 ID';
COMMENT ON COLUMN alarm_action.action IS '조치 종류';
COMMENT ON COLUMN alarm_action.target IS '대상';
COMMENT ON COLUMN alarm_action.result IS '결과';
COMMENT ON COLUMN alarm_action.detail IS '상세';
COMMENT ON COLUMN alarm_action.user_id IS '조치자 ID: 시스템 자동이면 NULL';
COMMENT ON COLUMN alarm_action.done_at IS '조치 시각';

COMMENT ON TABLE equipment_command IS '설비 제어 명령: 배기팬 제어 명령. 중앙 서버 → Redis → 수집 서버 → Kafka argos.edge.cmd 로 엣지 전달, 결과는 argos.edge.cmd.result 로 회신. 자동(fan_auto=true)이면 가스 알림 때 source=AUTO 로 바로 PENDING, 수동이면 승인 요청 시 AWAITING → 담당자 승인 → PENDING';
COMMENT ON COLUMN equipment_command.command_id IS '명령 ID';
COMMENT ON COLUMN equipment_command.equipment_id IS '설비 ID';
COMMENT ON COLUMN equipment_command.alarm_id IS '경보 ID';
COMMENT ON COLUMN equipment_command.command IS '명령: FAN_HZ 주파수 변경 / FAN_STOP 정지';
COMMENT ON COLUMN equipment_command.value IS '값: FAN_HZ면 목표 주파수 Hz. 정상 40 / 가스 주의 45(75%) / 경보 60(100%)';
COMMENT ON COLUMN equipment_command.source IS '누가 보냈나: USER 사람이 화면에서(수동 승인 포함) / AUTO 가스 알림 때 서버 자동 제어 / RECOVERY 경보 해제 후 40Hz 복귀';
COMMENT ON COLUMN equipment_command.result IS '결과: AWAITING 승인 대기 / PENDING 전송 / DONE 도달 / FAILED 실패 / REJECTED 엣지 거부 / EXPIRED 만료 / DUPLICATE 중복 / CANCELLED 취소 / DENIED 승인 거절';
COMMENT ON COLUMN equipment_command.requested_by IS '요청자 ID: USER 일 때 누른 사람. AUTO·RECOVERY 는 NULL';
COMMENT ON COLUMN equipment_command.approved_by IS '승인자 ID: 수동 모드에서 승인·거절한 담당자';
COMMENT ON COLUMN equipment_command.approved_at IS '승인 시각: 승인·거절한 시각';
COMMENT ON COLUMN equipment_command.requested_at IS '요청 시각';
COMMENT ON COLUMN equipment_command.expires_at IS '만료 시간: 엣지가 이 시각 지난 명령은 EXPIRED 로 거부';
COMMENT ON COLUMN equipment_command.completed_at IS '완료 시간: 엣지 결과 회신 시각';
COMMENT ON COLUMN equipment_command.result_detail IS '결과 상세: 목표 Hz · 출력 Hz · 전류 · 메시지 (엣지 회신 detail)';

COMMENT ON TABLE fire_incident IS '화재 대응';
COMMENT ON COLUMN fire_incident.fire_id IS '화재 ID';
COMMENT ON COLUMN fire_incident.alarm_id IS '경보 ID';
COMMENT ON COLUMN fire_incident.plant_id IS '사업장 ID';
COMMENT ON COLUMN fire_incident.process_id IS '공정 ID';
COMMENT ON COLUMN fire_incident.equipment_id IS '설비 ID';
COMMENT ON COLUMN fire_incident.pos_x IS 'X 위치';
COMMENT ON COLUMN fire_incident.pos_y IS 'Y 위치';
COMMENT ON COLUMN fire_incident.max_temp IS '최고 온도';
COMMENT ON COLUMN fire_incident.detected_at IS '감지 시각';
COMMENT ON COLUMN fire_incident.broadcast_started_at IS '방송 시작';
COMMENT ON COLUMN fire_incident.broadcast_ended_at IS '방송 종료';
COMMENT ON COLUMN fire_incident.hq_notified_at IS '본사 통보 시각';
COMMENT ON COLUMN fire_incident.unlock_fail_count IS '비상문 해제 실패 횟수';
COMMENT ON COLUMN fire_incident.status IS '상태';
COMMENT ON COLUMN fire_incident.cleared_by IS '해제자 ID';
COMMENT ON COLUMN fire_incident.cleared_at IS '해제 시각';

COMMENT ON TABLE checklist_template IS '체크리스트 양식';
COMMENT ON COLUMN checklist_template.template_id IS '양식 ID';
COMMENT ON COLUMN checklist_template.equipment_type IS '설비 종류';
COMMENT ON COLUMN checklist_template.sensor_type IS '센서 종류';
COMMENT ON COLUMN checklist_template.stage IS '단계';
COMMENT ON COLUMN checklist_template.cause IS '원인: 예: 배기팬 정지';
COMMENT ON COLUMN checklist_template.items IS '항목 목록';
COMMENT ON COLUMN checklist_template.is_active IS '사용 여부';

COMMENT ON TABLE checklist IS '체크리스트: 현장 점검 지시';
COMMENT ON COLUMN checklist.checklist_id IS '체크리스트 ID';
COMMENT ON COLUMN checklist.alarm_id IS '경보 ID';
COMMENT ON COLUMN checklist.template_id IS '양식 ID';
COMMENT ON COLUMN checklist.assignee_id IS '담당자 ID';
COMMENT ON COLUMN checklist.status IS '상태';
COMMENT ON COLUMN checklist.sent_by IS '발송자 ID';
COMMENT ON COLUMN checklist.sent_at IS '발송 시각';
COMMENT ON COLUMN checklist.remind_count IS '재알림 횟수';
COMMENT ON COLUMN checklist.last_remind_at IS '마지막 재알림';
COMMENT ON COLUMN checklist.reported_at IS '완료 보고 시각';
COMMENT ON COLUMN checklist.confirmed_by IS '확인자 ID';
COMMENT ON COLUMN checklist.confirmed_at IS '확인 시각';

COMMENT ON TABLE checklist_item IS '체크리스트 항목';
COMMENT ON COLUMN checklist_item.item_id IS '항목 ID';
COMMENT ON COLUMN checklist_item.checklist_id IS '체크리스트 ID';
COMMENT ON COLUMN checklist_item.seq IS '순서';
COMMENT ON COLUMN checklist_item.content IS '내용';
COMMENT ON COLUMN checklist_item.done_at IS '완료 시각: NULL = 미완료';
COMMENT ON COLUMN checklist_item.result IS '결과';
COMMENT ON COLUMN checklist_item.memo IS '메모';

COMMENT ON TABLE file_asset IS '파일: 파일 (열화상 사진, 서류 스캔, 배치도, 보고서)';
COMMENT ON COLUMN file_asset.file_id IS '파일 ID';
COMMENT ON COLUMN file_asset.kind IS '파일 종류: THERMAL 열화상 사진 / VIDEO 열화상 영상 / DOC_SCAN / FLOORPLAN / REPORT';
COMMENT ON COLUMN file_asset.path IS '저장 경로';
COMMENT ON COLUMN file_asset.mime IS '파일 형식';
COMMENT ON COLUMN file_asset.size_bytes IS '크기(byte)';
COMMENT ON COLUMN file_asset.sensor_id IS '센서 ID: 열화상이면 찍은 카메라(IR 센서)';
COMMENT ON COLUMN file_asset.captured_at IS '촬영 시각';
COMMENT ON COLUMN file_asset.meta IS '부가 정보: 열화상 최고 온도·위치 등';
COMMENT ON COLUMN file_asset.created_at IS '등록 시각';

COMMENT ON TABLE role IS '권한';
COMMENT ON COLUMN role.role_id IS '권한 ID';
COMMENT ON COLUMN role.code IS '권한 코드';
COMMENT ON COLUMN role.name IS '권한명';

COMMENT ON TABLE app_user IS '사용자';
COMMENT ON COLUMN app_user.user_id IS '사용자 ID';
COMMENT ON COLUMN app_user.plant_id IS '사업장 ID';
COMMENT ON COLUMN app_user.role_id IS '권한 ID';
COMMENT ON COLUMN app_user.login_id IS '로그인 ID';
COMMENT ON COLUMN app_user.password_hash IS '비밀번호 해시';
COMMENT ON COLUMN app_user.name IS '이름';
COMMENT ON COLUMN app_user.phone IS '전화번호';
COMMENT ON COLUMN app_user.fcm_token IS '푸시 토큰';
COMMENT ON COLUMN app_user.is_active IS '사용 여부';
COMMENT ON COLUMN app_user.pw_reset_required IS '비밀번호 변경 필요';
COMMENT ON COLUMN app_user.created_at IS '생성 시각';
COMMENT ON COLUMN app_user.updated_at IS '수정 시각';

COMMENT ON TABLE user_assignment IS '담당 범위: 사용자 담당 범위';
COMMENT ON COLUMN user_assignment.assignment_id IS '배정 ID';
COMMENT ON COLUMN user_assignment.user_id IS '사용자 ID';
COMMENT ON COLUMN user_assignment.plant_id IS '사업장 ID';
COMMENT ON COLUMN user_assignment.factory_id IS '공장 ID';
COMMENT ON COLUMN user_assignment.process_id IS '공정 ID';
COMMENT ON COLUMN user_assignment.created_at IS '배정 시각';

COMMENT ON TABLE equipment_manager IS '설비 담당자';
COMMENT ON COLUMN equipment_manager.equipment_id IS '설비 ID';
COMMENT ON COLUMN equipment_manager.user_id IS '사용자 ID';
COMMENT ON COLUMN equipment_manager.is_primary IS '주 담당';

COMMENT ON TABLE login_history IS '로그인 이력';
COMMENT ON COLUMN login_history.login_id_seq IS '이력 ID';
COMMENT ON COLUMN login_history.user_id IS '사용자 ID';
COMMENT ON COLUMN login_history.logged_at IS '로그인 시각';
COMMENT ON COLUMN login_history.ip_addr IS '접속 IP';
COMMENT ON COLUMN login_history.success IS '성공 여부';

COMMENT ON TABLE activity_log IS '작업 로그: 작업 로그 (감사)';
COMMENT ON COLUMN activity_log.log_id IS '로그 ID';
COMMENT ON COLUMN activity_log.user_id IS '사용자 ID';
COMMENT ON COLUMN activity_log.plant_id IS '사업장 ID: 조회 조건용';
COMMENT ON COLUMN activity_log.process_id IS '공정 ID: 조회 조건용';
COMMENT ON COLUMN activity_log.type IS '작업 종류: LOGOUT / THRESHOLD / SHUTDOWN / FAN / LAYOUT / USER / EXPORT / INSPECTION / FIRE_CLEAR 등';
COMMENT ON COLUMN activity_log.target_table IS '대상 테이블';
COMMENT ON COLUMN activity_log.target_id IS '대상 ID';
COMMENT ON COLUMN activity_log.summary IS '요약';
COMMENT ON COLUMN activity_log.before IS '변경 전';
COMMENT ON COLUMN activity_log.after IS '변경 후';
COMMENT ON COLUMN activity_log.ip_addr IS 'IP';
COMMENT ON COLUMN activity_log.created_at IS '기록 시각';

COMMENT ON TABLE inspection_schedule IS '점검 일정';
COMMENT ON COLUMN inspection_schedule.schedule_id IS '일정 ID';
COMMENT ON COLUMN inspection_schedule.equipment_id IS '설비 ID';
COMMENT ON COLUMN inspection_schedule.assignee_id IS '담당자 ID';
COMMENT ON COLUMN inspection_schedule.planned_at IS '예정일';
COMMENT ON COLUMN inspection_schedule.status IS '상태';
COMMENT ON COLUMN inspection_schedule.title IS '제목';
COMMENT ON COLUMN inspection_schedule.done_at IS '완료 시각';
COMMENT ON COLUMN inspection_schedule.done_by IS '완료자 ID';

COMMENT ON TABLE doc_form IS '서류 양식';
COMMENT ON COLUMN doc_form.form_type IS '양식 코드: F01 ~ F05';
COMMENT ON COLUMN doc_form.name IS '양식명';
COMMENT ON COLUMN doc_form.field_schema IS '항목 정의';
COMMENT ON COLUMN doc_form.version IS '버전';

COMMENT ON TABLE ocr_result IS '서류 인식 결과: 서류 인식(OCR) 결과';
COMMENT ON COLUMN ocr_result.ocr_id IS '인식 ID';
COMMENT ON COLUMN ocr_result.schedule_id IS '일정 ID';
COMMENT ON COLUMN ocr_result.file_id IS '파일 ID';
COMMENT ON COLUMN ocr_result.form_type IS '양식 코드';
COMMENT ON COLUMN ocr_result.fields IS '인식 값';
COMMENT ON COLUMN ocr_result.confidence IS '신뢰도: 0~1';
COMMENT ON COLUMN ocr_result.status IS '상태';
COMMENT ON COLUMN ocr_result.reviewed_by IS '검토자 ID';
COMMENT ON COLUMN ocr_result.reviewed_at IS '검토 시각';
COMMENT ON COLUMN ocr_result.uploaded_by IS '업로드자 ID';
COMMENT ON COLUMN ocr_result.process_id IS '공정 ID';

COMMENT ON TABLE chat_message IS '채팅 메시지: 채팅';
COMMENT ON COLUMN chat_message.msg_id IS '메시지 ID';
COMMENT ON COLUMN chat_message.plant_id IS '사업장 ID: 대화방';
COMMENT ON COLUMN chat_message.sender_id IS '보낸 사람 ID';
COMMENT ON COLUMN chat_message.schedule_id IS '점검 일정 ID';
COMMENT ON COLUMN chat_message.alarm_id IS '경보 ID: 첨부된 경보';
COMMENT ON COLUMN chat_message.body IS '내용';
COMMENT ON COLUMN chat_message.sent_at IS '보낸 시각';

COMMENT ON TABLE chat_read IS '채팅 읽음: 채팅 읽음 위치';
COMMENT ON COLUMN chat_read.user_id IS '사용자 ID';
COMMENT ON COLUMN chat_read.plant_id IS '사업장 ID';
COMMENT ON COLUMN chat_read.last_read_msg_id IS '마지막 읽은 메시지 ID';
COMMENT ON COLUMN chat_read.updated_at IS '읽은 시각';

COMMENT ON TABLE safety_grade IS '안전 등급: 공장별 월 안전 등급';
COMMENT ON COLUMN safety_grade.factory_id IS '공장 ID';
COMMENT ON COLUMN safety_grade.month IS '해당 월: 매월 1일';
COMMENT ON COLUMN safety_grade.grade IS '등급';
COMMENT ON COLUMN safety_grade.score IS '점수';
