package com.barbu.app.room;

import com.barbu.app.persistence.MatchRecorder;
import com.barbu.app.rating.RatingService;
import com.barbu.engine.variant.Variant;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Value;
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
    private final com.barbu.app.metrics.GameMetrics metrics;
    private final RatingService ratingService;
    private final ReconnectIndex reconnectIndex;
    private final long botDelayMs;
    private final long turnTimeoutMs;
    private final int turnTimeoutStrikes;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Random random = new Random();

    public RoomManager(
            ObjectMapper mapper,
            MatchRecorder recorder,
            com.barbu.app.metrics.GameMetrics metrics,
            RatingService ratingService,
            ReconnectIndex reconnectIndex,
            @Value("${barbu.bot-delay-ms:650}") long botDelayMs,
            @Value("${barbu.turn-timeout-ms:60000}") long turnTimeoutMs,
            @Value("${barbu.turn-timeout-strikes:2}") int turnTimeoutStrikes) {
        this.mapper = mapper;
        this.recorder = recorder;
        this.metrics = metrics;
        this.ratingService = ratingService;
        this.reconnectIndex = reconnectIndex;
        this.botDelayMs = botDelayMs;
        this.turnTimeoutMs = turnTimeoutMs;
        this.turnTimeoutStrikes = turnTimeoutStrikes;
    }

    public GameRoom create(int requestedPlayerCount, Variant variant) {
        return create(requestedPlayerCount, variant, "private");
    }

    public GameRoom create(int requestedPlayerCount, Variant variant, String mode) {
        int playerCount = Math.clamp(requestedPlayerCount, 2, 10);
        String id = newCode();
        GameRoom room = new GameRoom(
                id,
                playerCount,
                variant,
                mapper,
                scheduler,
                botDelayMs,
                recorder,
                metrics,
                mode,
                ratingService,
                reconnectIndex,
                turnTimeoutMs,
                turnTimeoutStrikes);
        rooms.put(id, room);
        return room;
    }

    public GameRoom get(String id) {
        return id == null ? null : rooms.get(id);
    }

    public GameRoom roomForUser(long userId) {
        return get(reconnectIndex.roomForUser(userId));
    }

    public GameRoom roomForToken(String token) {
        return get(reconnectIndex.roomForToken(token));
    }

    public void remove(String id) {
        GameRoom room = rooms.remove(id);
        if (room != null) {
            room.clearReconnectEntries(reconnectIndex);
        }
    }

    public int activeRoomCount() {
        return rooms.size();
    }

    public int activeHumanCount() {
        return rooms.values().stream().mapToInt(GameRoom::connectedHumanCount).sum();
    }

    public int activeBotCount() {
        return rooms.values().stream().mapToInt(GameRoom::botCount).sum();
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
