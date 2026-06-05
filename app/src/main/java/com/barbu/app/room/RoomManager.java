package com.barbu.app.room;

import com.barbu.app.persistence.MatchRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Singleton
public class RoomManager {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final MatchRecorder recorder;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Random random = new Random();

    public RoomManager(ObjectMapper mapper, MatchRecorder recorder) {
        this.mapper = mapper;
        this.recorder = recorder;
    }

    private static final long BOT_DELAY_MS = 650;

    public GameRoom create(int requestedPlayerCount) {
        int playerCount = Math.max(2, Math.min(10, requestedPlayerCount));
        String id = newCode();
        GameRoom room = new GameRoom(id, playerCount, mapper, scheduler, BOT_DELAY_MS, recorder);
        rooms.put(id, room);
        return room;
    }

    public GameRoom get(String id) {
        return id == null ? null : rooms.get(id);
    }

    public void remove(String id) {
        rooms.remove(id);
    }

    public long newSeed() {
        return random.nextLong();
    }

    private String newCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(5);
            for (int i = 0; i < 5; i++) {
                sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (rooms.containsKey(code));
        return code;
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
