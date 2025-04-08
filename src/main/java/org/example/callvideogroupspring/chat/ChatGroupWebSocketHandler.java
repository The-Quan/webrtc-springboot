package org.example.callvideogroupspring.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatGroupWebSocketHandler extends TextWebSocketHandler {

    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private static final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private static final Map<String, List<Map<String, String>>> roomMessages = new ConcurrentHashMap<>();
    private static final Map<WebSocketSession, String> userNames = new ConcurrentHashMap<>();
    private static final Map<String, String> roomTypes = new ConcurrentHashMap<>();


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
        String type = data.get("type");

        if (type == null) {
            sendError(session, "Invalid message type");
            return;
        }

        switch (type) {
            case "create-room":
                String creatorName = data.get("name");
                String roomTypeCreate = data.get("roomType");
                String roomType = data.getOrDefault("roomType", roomTypeCreate);
                System.out.println(roomType);

                if (!roomType.equals("group") && !roomType.equals("1v1")) {
                    sendError(session, "Invalid room type");
                    return;
                }
                userNames.put(session, creatorName);
                String newRoomId = UUID.randomUUID().toString();
                rooms.putIfAbsent(newRoomId, ConcurrentHashMap.newKeySet());
                roomTypes.put(newRoomId, roomType);
                rooms.get(newRoomId).add(session);

                sendTo(session, "room-created", newRoomId, "server");
                break;

            case "join":
                String joinRoomId = data.get("roomId");
                String joinerName = data.get("name");

                if (joinRoomId != null && rooms.containsKey(joinRoomId)) {
                    Set<WebSocketSession> participants = rooms.get(joinRoomId);
                    String typeOfRoom = roomTypes.get(joinRoomId);

                    if ("1v1".equals(typeOfRoom) && participants.size() >= 2) {
                        sendError(session, "Room is full (1:1 room)");
                        return;
                    }

                    rooms.get(joinRoomId).add(session);
                    userNames.put(session, joinerName);

                    List<Map<String, String>> messages = roomMessages.get(joinRoomId);
                    if (messages != null) {
                        for (Map<String, String> oldMsg : messages) {
                            sendTo(session, "chat", oldMsg.get("message"), oldMsg.get("from"));
                        }
                    }

                    broadcast(joinRoomId, "new-user", joinerName, "server");
                } else {
                    sendError(session, "Room not found");
                }
                break;

            case "chat":
                String roomId = data.get("roomId");
                String chatMessage = data.get("message");

                if (roomId != null && chatMessage != null) {
                    String senderName = userNames.getOrDefault(session, session.getId());

                    roomMessages.putIfAbsent(roomId, Collections.synchronizedList(new ArrayList<>()));
                    Map<String, String> messageObj = new HashMap<>();
                    messageObj.put("from", senderName);
                    messageObj.put("message", chatMessage);
                    roomMessages.get(roomId).add(messageObj);

                    broadcast(roomId, "chat", chatMessage, senderName);
                } else {
                    sendError(session, "Invalid chat message");
                }
                break;

            case "offer":
            case "answer":
            case "ice-candidate":
                String to = data.get("to");
                WebSocketSession targetSession = sessions.get(to);
                if (targetSession != null && targetSession.isOpen()) {
                    sendTo(targetSession, type, data.get("data"), userNames.getOrDefault(session, session.getId()));
                }
                break;

            default:
                sendError(session, "Unknown message type");
                break;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        String name = userNames.remove(session);

        rooms.values().forEach(room -> room.remove(session));

        rooms.forEach((roomId, users) -> {
            if (users.contains(session)) {
                try {
                    broadcast(roomId, "user-disconnected", name != null ? name : session.getId(), "server");
                } catch (IOException e) {
                    System.err.println("Error broadcasting disconnect: " + e.getMessage());
                }
            }
        });
    }

    private void sendTo(WebSocketSession session, String action, Object data, String from) throws IOException {
        if (session == null || !session.isOpen()) return;

        synchronized (sendLock) {
            Map<String, Object> message = new HashMap<>();
            message.put("action", action);
            message.put("data", data);
            message.put("from", from);

            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        }
    }

    private void sendError(WebSocketSession session, String errorMessage) throws IOException {
        sendTo(session, "error", errorMessage, "server");
    }

    private void broadcast(String roomId, String action, String message, String from) throws IOException {
        Set<WebSocketSession> roomUsers = rooms.get(roomId);
        if (roomUsers == null) return;

        for (WebSocketSession session : roomUsers) {
            if (session.isOpen()) {
                sendTo(session, action, message, from);
            }
        }
    }

    private Map<String, String> parseJson(String json) {
        try {
            return objectMapper.readValue(json, HashMap.class);
        } catch (Exception e) {
            System.err.println("JSON parse error: " + e.getMessage());
            return new HashMap<>();
        }
    }
}
