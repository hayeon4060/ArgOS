package com.gte.argos.gateway.chat;

import com.gte.argos.gateway.access.CurrentUser;
import com.gte.argos.gateway.access.UserScope;
import com.gte.argos.gateway.central.CentralClient;
import com.gte.argos.gateway.central.CentralDto;
import com.gte.argos.gateway.central.Codes;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.central.SimTime;
import com.gte.argos.gateway.common.api.ApiException;
import com.gte.argos.gateway.stream.StreamHub;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅 (명세서 I2). 사업장 하나가 대화방 하나라 roomId = 사업장(plant) id.
 * 본사 관리자는 모든 사업장 방, 공장 담당자는 자기 사업장 방 하나. 공정 담당자는 쓰지 않는다.
 */
@Service
public class ChatService {

    static final int MAX_BODY = 1000;

    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final MasterCache masterCache;
    private final CentralClient central;
    private final StreamHub hub;
    private final SimTime time;

    public ChatService(NamedParameterJdbcTemplate jdbc, CurrentUser currentUser, MasterCache masterCache,
                       CentralClient central, StreamHub hub, SimTime time) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.masterCache = masterCache;
        this.central = central;
        this.hub = hub;
        this.time = time;
    }

    /** GET /chat/rooms */
    public List<Map<String, Object>> rooms() {
        UserScope scope = scope();
        List<Map<String, Object>> out = new ArrayList<>();
        for (CentralDto.Plant p : masterCache.get().plants()) {
            if (!scope.canSite(p.id(), masterCache.get())) {
                continue;
            }
            Map<String, Object> room = new LinkedHashMap<>();
            room.put("roomId", p.id());
            room.put("siteName", p.name());
            room.put("peer", peer(scope, p.id()));
            List<Map<String, Object>> last = jdbc.queryForList("""
                    SELECT body, sent_at FROM chat_message WHERE plant_id = :room ORDER BY msg_id DESC LIMIT 1
                    """, new MapSqlParameterSource("room", p.id()));
            room.put("lastMessage", last.isEmpty() ? null
                    : Map.of("body", last.get(0).get("body"), "sentAt", at(last.get(0).get("sent_at"))));
            room.put("unreadCount", unread(scope.userId(), p.id()));
            out.add(room);
        }
        return out;
    }

    /** GET /chat/rooms/{roomId}/messages?before=&size= */
    public Map<String, Object> messages(long roomId, Long before, int size) {
        UserScope scope = scope();
        checkRoom(scope, roomId);
        MapSqlParameterSource p = new MapSqlParameterSource("room", roomId)
                .addValue("before", before == null ? Long.MAX_VALUE : before).addValue("size", size + 1);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT m.msg_id, m.sender_id, u.name AS sender_name, m.body, m.alarm_id, m.sent_at
                FROM chat_message m JOIN app_user u ON u.user_id = m.sender_id
                WHERE m.plant_id = :room AND m.msg_id < :before
                ORDER BY m.msg_id DESC LIMIT :size
                """, p);
        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = rows.subList(0, size);
        }
        long myRead = lastRead(scope.userId(), roomId);
        long peerRead = peerLastRead(scope, roomId);
        Map<Long, Map<String, Object>> alarmCache = new HashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = rows.size() - 1; i >= 0; i--) { // 오래된 것이 위
            Map<String, Object> r = rows.get(i);
            long id = ((Number) r.get("msg_id")).longValue();
            long sender = ((Number) r.get("sender_id")).longValue();
            boolean mine = sender == scope.userId();
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", id);
            v.put("senderId", sender);
            v.put("senderName", r.get("sender_name"));
            v.put("mine", mine);
            v.put("body", r.get("body"));
            Object alarmId = r.get("alarm_id");
            v.put("alarm", alarmId == null ? null
                    : alarmCache.computeIfAbsent(((Number) alarmId).longValue(), this::alarmCard));
            v.put("sentAt", at(r.get("sent_at")));
            v.put("read", mine ? peerRead >= id : myRead >= id);
            items.add(v);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("hasMore", hasMore);
        return out;
    }

    /** POST /chat/rooms/{roomId}/messages */
    @Transactional
    public Map<String, Object> send(long roomId, String body, Long alarmId) {
        UserScope scope = scope();
        checkRoom(scope, roomId);
        String text = body == null ? "" : body.strip();
        if (text.isEmpty() || text.length() > MAX_BODY) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "메시지는 1~1,000자로 입력하세요.");
        }
        OffsetDateTime now = time.now();
        Long id = jdbc.queryForObject("""
                INSERT INTO chat_message (plant_id, sender_id, body, alarm_id, sent_at)
                VALUES (:room, :sender, :body, :alarm, :at) RETURNING msg_id
                """, new MapSqlParameterSource("room", roomId).addValue("sender", scope.userId())
                .addValue("body", text).addValue("alarm", alarmId).addValue("at", now), Long.class);
        markRead(scope.userId(), roomId, id, now); // 내가 보낸 글은 읽은 것
        String senderName = jdbc.queryForObject("SELECT name FROM app_user WHERE user_id = :id",
                new MapSqlParameterSource("id", scope.userId()), String.class);
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("roomId", roomId);
        ev.put("id", id);
        ev.put("senderId", scope.userId());
        ev.put("senderName", senderName);
        ev.put("body", text);
        ev.put("alarmId", alarmId);
        ev.put("sentAt", now);
        hub.publish("chat.message", ev, s -> !s.scope().isWorker() && s.scope().canSite(roomId, masterCache.get()));
        return Map.of("id", id, "sentAt", now);
    }

    /** POST /chat/rooms/{roomId}/read */
    public Map<String, Object> read(long roomId, long lastReadMessageId) {
        UserScope scope = scope();
        checkRoom(scope, roomId);
        markRead(scope.userId(), roomId, lastReadMessageId, time.now());
        return Map.of("unreadCount", unread(scope.userId(), roomId));
    }

    // ------------------------------------------------------------------

    private UserScope scope() {
        UserScope scope = currentUser.scope();
        if (scope.isWorker()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CHAT_NOT_ALLOWED", "공정 담당자는 채팅을 쓰지 않습니다.");
        }
        return scope;
    }

    private void checkRoom(UserScope scope, long roomId) {
        if (masterCache.get().plant(roomId) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "대화방이 없습니다.");
        }
        currentUser.checkSite(scope, roomId);
    }

    /** 본사 관리자에게는 그 사업장 공장 담당자, 공장 담당자에게는 본사 관리자 */
    private Map<String, Object> peer(UserScope scope, long roomId) {
        String role = scope.isHq() ? UserScope.PLANT_ADMIN : UserScope.HQ_ADMIN;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.user_id, u.name FROM app_user u JOIN role r ON r.role_id = u.role_id
                WHERE r.code = :role AND u.is_active AND (:role = 'HQ_ADMIN' OR u.plant_id = :room
                   OR EXISTS (SELECT 1 FROM user_assignment a WHERE a.user_id = u.user_id AND a.plant_id = :room))
                ORDER BY u.user_id LIMIT 1
                """, new MapSqlParameterSource("role", role).addValue("room", roomId));
        if (rows.isEmpty()) {
            return null;
        }
        long id = ((Number) rows.get(0).get("user_id")).longValue();
        return Map.of("id", id, "name", rows.get(0).get("name"), "role", role, "online", hub.online(id));
    }

    private long peerLastRead(UserScope scope, long roomId) {
        Long v = jdbc.queryForObject("""
                SELECT COALESCE(MAX(cr.last_read_msg_id), 0) FROM chat_read cr
                JOIN app_user u ON u.user_id = cr.user_id JOIN role r ON r.role_id = u.role_id
                WHERE cr.plant_id = :room AND cr.user_id <> :me AND r.code <> 'WORKER'
                """, new MapSqlParameterSource("room", roomId).addValue("me", scope.userId()), Long.class);
        return v == null ? 0 : v;
    }

    private long lastRead(long userId, long roomId) {
        List<Long> v = jdbc.queryForList("SELECT last_read_msg_id FROM chat_read WHERE user_id = :u AND plant_id = :room",
                new MapSqlParameterSource("u", userId).addValue("room", roomId), Long.class);
        return v.isEmpty() ? 0 : v.get(0);
    }

    private int unread(long userId, long roomId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM chat_message WHERE plant_id = :room AND sender_id <> :u
                  AND msg_id > COALESCE((SELECT last_read_msg_id FROM chat_read WHERE user_id = :u AND plant_id = :room), 0)
                """, new MapSqlParameterSource("u", userId).addValue("room", roomId), Integer.class);
        return n == null ? 0 : n;
    }

    private void markRead(long userId, long roomId, long msgId, OffsetDateTime at) {
        jdbc.update("""
                INSERT INTO chat_read (user_id, plant_id, last_read_msg_id, updated_at) VALUES (:u, :room, :id, :at)
                ON CONFLICT (user_id, plant_id)
                DO UPDATE SET last_read_msg_id = GREATEST(chat_read.last_read_msg_id, EXCLUDED.last_read_msg_id), updated_at = EXCLUDED.updated_at
                """, new MapSqlParameterSource("u", userId).addValue("room", roomId).addValue("id", msgId)
                .addValue("at", at));
    }

    /** 붙인 알림 카드: 제목(센서 종류 + 단계)과 설비 이름 */
    private Map<String, Object> alarmCard(long alarmId) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", alarmId);
        try {
            CentralDto.Alarm a = central.alarm(alarmId);
            CentralDto.Event e = a.mainEvent();
            String sensor = e == null ? "" : Codes.sensorName(e.type()) + " ";
            card.put("title", sensor + Codes.stageName(a.stage()) + " 기준 초과");
            CentralDto.Equipment eq = a.equipmentId() == null ? null : masterCache.get().equipment(a.equipmentId());
            card.put("equipmentName", eq == null ? a.unitName() : eq.name());
            card.put("level", Codes.level(a.stage()));
            card.put("hazard", a.hazard());
            card.put("sensorTag", e == null ? null : e.tag());
            card.put("value", e == null ? null : e.peak());
            card.put("unit", e == null ? null : e.unit());
        } catch (RuntimeException ex) {
            card.put("title", "알림 #" + alarmId);
            card.put("equipmentName", null);
            card.put("level", null);
            card.put("hazard", null);
            card.put("sensorTag", null);
            card.put("value", null);
            card.put("unit", null);
        }
        return card;
    }

    private static Object at(Object v) {
        if (v instanceof OffsetDateTime t) {
            return t.atZoneSameInstant(SimTime.ZONE).toOffsetDateTime();
        }
        if (v instanceof java.sql.Timestamp t) {
            return t.toInstant().atZone(SimTime.ZONE).toOffsetDateTime();
        }
        return v;
    }
}
