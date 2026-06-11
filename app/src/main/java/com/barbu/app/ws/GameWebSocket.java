package com.barbu.app.ws;

import com.barbu.app.auth.JwtVerifier;
import com.barbu.app.persistence.Repositories.UserRepository;
import com.barbu.app.protocol.ChatSend;
import com.barbu.app.protocol.Codec;
import com.barbu.app.room.GameRoom;
import com.barbu.app.room.InMemoryMatchmaker;
import com.barbu.app.room.RoomManager;
import com.barbu.engine.variant.Variant;
import com.barbu.engine.variant.Variants;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import java.util.Map;

@ServerWebSocket("/ws/game")
@Secured(SecurityRule.IS_ANONYMOUS)
public class GameWebSocket {

    private final RoomManager rooms;
    private final InMemoryMatchmaker matchmaker;
    private final JwtVerifier jwtVerifier;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public GameWebSocket(
            RoomManager rooms,
            InMemoryMatchmaker matchmaker,
            JwtVerifier jwtVerifier,
            UserRepository users,
            ObjectMapper mapper) {
        this.rooms = rooms;
        this.matchmaker = matchmaker;
        this.jwtVerifier = jwtVerifier;
        this.users = users;
        this.mapper = mapper;
    }

    @OnOpen
    public void onOpen(WebSocketSession session) {
        // The client may authenticate with an "auth" command, then create or join.
    }

    private void authenticate(WebSocketSession session, String token) {
        jwtVerifier
                .usernameOf(token)
                .ifPresent(username -> users.findByUsername(username).ifPresent(user -> {
                    session.put("username", username);
                    session.put("userId", user.id());
                }));
    }

    private String accountNameOr(WebSocketSession session, Object fallback) {
        return session.get("username", String.class).orElse(asString(fallback));
    }

    private Long accountId(WebSocketSession session) {
        return session.get("userId", Long.class).orElse(null);
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
            case "auth" -> authenticate(session, asString(command.get("token")));
            case "createRoom" -> {
                authenticate(session, asString(command.get("token")));
                String variantId = asString(command.get("variant"));
                Variant variant = variantId == null ? Variants.DEVELOPER : Variants.byId(variantId);
                if (variant == null) {
                    sendError(session, "unknown variant: " + variantId);
                    return;
                }
                GameRoom room = rooms.create(asInt(command.get("playerCount"), 4), variant);
                int seat = room.addHuman(session, accountNameOr(session, command.get("name")), accountId(session));
                bind(session, room.id(), seat);
                sendJoined(session, room.id(), seat);
                room.broadcast();
            }
            case "join" -> {
                authenticate(session, asString(command.get("token")));
                GameRoom room = rooms.get(asString(command.get("roomId")));
                if (room == null) {
                    sendError(session, "room not found");
                    return;
                }
                int seat = room.addHuman(session, accountNameOr(session, command.get("name")), accountId(session));
                if (seat < 0) {
                    sendError(session, "room is full");
                    return;
                }
                bind(session, room.id(), seat);
                sendJoined(session, room.id(), seat);
                room.broadcast();
            }
            case "enqueueMatchmaking" -> {
                authenticate(session, asString(command.get("token")));
                matchmaker.enqueue(session, accountNameOr(session, command.get("name")), asInt(command.get("size"), 4));
            }
            case "cancelMatchmaking" -> matchmaker.cancel(session);
            case "addBot" ->
                withRoom(session, room -> {
                    room.addBot();
                    room.broadcast();
                });
            case "start" ->
                withRoom(session, room -> {
                    if (!room.start(rooms.newSeed())) {
                        sendError(session, "cannot start (seats must all be filled)");
                    }
                });
            case "play" ->
                withRoomSeat(
                        session,
                        (room, seat) -> room.play(seat, Codec.parseMove((Map<String, Object>) command.get("move"))));
            case "castStopVote" ->
                withRoomSeat(
                        session, (room, seat) -> room.castStopVote(seat, Boolean.TRUE.equals(command.get("stop"))));
            case "chat" -> {
                ChatSend chat = mapper.convertValue(command, ChatSend.class);
                withRoomSeat(session, (room, seat) -> room.chat(seat, chat.text()));
            }
            default -> sendError(session, "unknown command: " + type);
        }
    }

    @OnClose
    public void onClose(WebSocketSession session) {
        matchmaker.cancel(session);
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
