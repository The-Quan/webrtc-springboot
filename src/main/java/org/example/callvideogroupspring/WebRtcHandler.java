package org.example.callvideogroupspring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class WebRtcHandler extends TextWebSocketHandler {
    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
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
            case "join":
                broadcast(session.getId(), data.getOrDefault("roomId", ""), "new-user", session.getId());
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
        for (WebSocketSession session : sessions.values()) {
            if (!session.getId().equals(from) && session.isOpen()) {
                Map<String, Object> message = new HashMap<>();
                message.put("action", action);
                message.put("data", data);
                message.put("from", from);
                String jsonMessage = objectMapper.writeValueAsString(message);

                session.sendMessage(new TextMessage(jsonMessage));
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
