package org.example.callvideogroupspring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

class WebRtcHandler extends TextWebSocketHandler {
    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private final Object sendLock = new Object();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();

        Map<String, String> data = parseJson(payload);
        String action = data.get("type");

        if (action == null) {
            System.err.println("Received null action, ignoring message.");
            return;
        }

        switch (action) {
            case "create-room":
                String roomId = UUID.randomUUID().toString();
                rooms.put(roomId, new HashSet<>());
                rooms.get(roomId).add(session);
                sendTo(session.getId(), "room-created", roomId, "server");
                break;
            case "join":
                String joinRoomId = data.get("roomId");
                if (joinRoomId != null && rooms.containsKey(joinRoomId)) {
                    if (rooms.get(joinRoomId).contains(session)) {
                        sendTo(session.getId(), "error", "You are already in the room", "server");
                        return;
                    }
                    rooms.values().forEach(s -> s.remove(session.getId()));
                    rooms.get(joinRoomId).add(session);
                    broadcast(session.getId(), data.getOrDefault("roomId", ""), "new-user", session.getId());
                } else {
                    sendTo(session.getId(), "error", "Room not found", "server");
                }
                break;
            case "offer":
                sendTo(data.get("to"), "offer", data.get("data"), session.getId());
                break;
            case "answer":
                sendTo(data.get("to"), "answer", data.get("data"), session.getId());
                break;
            case "ice-candidate":
                sendTo(data.get("to"), "ice-candidate", data.get("data"), session.getId());
                break;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws IOException {
        sessions.remove(session.getId());
        rooms.values().forEach(room -> room.remove(session));
        broadcast(session.getId(), "", "user-disconnected", session.getId());
    }

    private void sendTo(String to, String action, Object data, String from) throws IOException {
        if (to == null || data == null) {
            System.err.println("Null 'to' or 'data', skipping send.");
            return;
        }

        WebSocketSession recipient = sessions.get(to);
        if (recipient != null && recipient.isOpen()) {
            synchronized (sendLock) {
                Map<String, Object> message = new HashMap<>();
                message.put("action", action);
                message.put("data", data);
                message.put("from", from);
                String jsonMessage = objectMapper.writeValueAsString(message);

                recipient.sendMessage(new TextMessage(jsonMessage));
            }
        }
    }
    private void broadcast(String from, String roomId, String action, String data) throws IOException {
        if (roomId == null || !rooms.containsKey(roomId)) return;

        Set<WebSocketSession> roomSessions = rooms.get(roomId); // Lấy danh sách người trong phòng
        if (roomSessions == null) return;

        for (WebSocketSession session : roomSessions) {
            if (!session.getId().equals(from) && session.isOpen()) {
                try {
                    Map<String, Object> message = new HashMap<>();
                    message.put("action", action);
                    message.put("data", data);
                    message.put("from", from);
                    String jsonMessage = objectMapper.writeValueAsString(message);

                    session.sendMessage(new TextMessage(jsonMessage));
                } catch (IOException e) {
                    System.err.println("Lỗi khi gửi tin nhắn: " + e.getMessage());
                    session.close(); // Đóng session nếu gặp lỗi
                }
            }
        }
    }


    private Map<String, String> parseJson(String json) {
        try {
            return objectMapper.readValue(json, HashMap.class);
        } catch (Exception e) {
            System.err.println("Lỗi parse JSON: " + e.getMessage());
            return new HashMap<>();
        }
    }
}
