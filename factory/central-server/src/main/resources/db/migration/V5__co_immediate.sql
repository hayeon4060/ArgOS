-- CO 즉시 3단계: 200 ppm 이상이면 지속 조건 없이 바로 경보 (2026-11-20, DAS 요청)
-- 엣지가 서버 기준값(argos.config.threshold)을 받으면 immediate_value 가 null 일 때 내장 200ppm 판정이 꺼지므로 DB 에 넣어 둔다
UPDATE sensor_threshold t SET immediate_value = 200
  FROM sensor s
 WHERE s.sensor_id = t.sensor_id AND s.sensor_type = 'AT'
   AND t.valid_to IS NULL AND t.immediate_value IS NULL;
