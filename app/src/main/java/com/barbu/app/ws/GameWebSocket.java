package com.barbu.app.ws;

import com.barbu.app.protocol.Codec;
import com.barbu.app.room.GameRoom;
import com.barbu.app.room.RoomManager;
import com.barbu.engine.model.Contract;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;

import java.util.Map;

@ServerWebSocket("/ws/game")
public class GameWebSocket {

    private final RoomManager rooms;
    private final ObjectMapper mapper;

    public GameWebSocket(RoomManager rooms, ObjectMapper mapper) {
        this.rooms = rooms;
        this.mapper = mapper;
    }

    @OnOpen
    public void onOpen(WebSocketSession session) {
        // The client introduces itself with a createRoom or join command.
    }

    @OnMessage
    @SuppressWarnings("unchecked")
    public void onMessage(String message, WebSocketSession session) {
        Map<String, Object> command;
        try {
            command = mapper.readValue(message, Map.class);
        } catch (Exception e) {
            sendError(session, "malformed message");
            return;
        }

        String type = String.valueOf(command.get("type"));
        switch (type) {
            case "createRoom" -> {
                GameRoom room = rooms.create(asInt(command.get("playerCount"), 4));
                int seat = room.addHuman(session, asString(command.get("name")));
                bind(session, room.id(), seat);
                sendJoined(session, room.id(), seat);
                room.broadcast();
            }
            case "join" -> {
                GameRoom room = rooms.get(asString(command.get("roomId")));
                if (room == null) {
                    sendError(session, "room not found");
                    return;
                }
                int seat = room.addHuman(session, asString(command.get("name")));
                if (seat < 0) {
                    sendError(session, "room is full");
                    return;
                }
                bind(session, room.id(), seat);
                sendJoined(session, room.id(), seat);
                room.broadcast();
            }
            case "addBot" -> withRoom(session, room -> {
                room.addBot();
                room.broadcast();
            });
            case "start" -> withRoom(session, room -> {
                if (!room.start(rooms.newSeed())) {
                    sendError(session, "cannot start (seats must all be filled)");
                }
            });
            case "chooseContract" -> withRoomSeat(session, (room, seat) ->
                    room.chooseContract(seat, Contract.valueOf(asString(command.get("contract")))));
            case "play" -> withRoomSeat(session, (room, seat) ->
                    room.play(seat, Codec.parseMove((Map<String, Object>) command.get("move"))));
            default -> sendError(session, "unknown command: " + type);
        }
    }

    @OnClose
    public void onClose(WebSocketSession session) {
        String roomId = session.get("roomId", String.class).orElse(null);
        Integer seat = session.get("seat", Integer.class).orElse(null);
        if (roomId == null || seat == null) {
            return;
        }
        GameRoom room = rooms.get(roomId);
        if (room != null) {
            room.handleDisconnect(seat);
            if (room.isEmptyOfHumans()) {
                rooms.remove(roomId);
            }
        }
    }

    private void bind(WebSocketSession session, String roomId, int seat) {
        session.put("roomId", roomId);
        session.put("seat", seat);
    }

    private void withRoom(WebSocketSession session, java.util.function.Consumer<GameRoom> action) {
        String roomId = session.get("roomId", String.class).orElse(null);
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            sendError(session, "not in a room");
            return;
        }
        action.accept(room);
    }

    private void withRoomSeat(WebSocketSession session, java.util.function.BiConsumer<GameRoom, Integer> action) {
        String roomId = session.get("roomId", String.class).orElse(null);
        Integer seat = session.get("seat", Integer.class).orElse(null);
        GameRoom room = rooms.get(roomId);
        if (room == null || seat == null) {
            sendError(session, "not in a room");
            return;
        }
        action.accept(room, seat);
    }

    private void sendJoined(WebSocketSession session, String roomId, int seat) {
        send(session, Map.of("type", "joined", "roomId", roomId, "seat", seat));
    }

    private void sendError(WebSocketSession session, String reason) {
        send(session, Map.of("type", "error", "message", reason));
    }

    private void send(WebSocketSession session, Map<String, Object> payload) {
        try {
            session.sendSync(mapper.writeValueAsString(payload));
        } catch (Exception ignored) {
            // client gone; nothing to do
        }
    }

    private static int asInt(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
