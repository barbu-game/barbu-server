package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.*;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.http.MediaType;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketSession;
import jakarta.inject.Inject;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

@MicronautTest
class RankedMatchmakerEnqueueTest {

    @Inject
    RankedMatchmaker matchmaker;

    @Test
    void enqueuing_the_same_session_twice_takes_a_single_queue_slot() {
        WebSocketSession session = sessionForUser(4242L);
        int before = matchmaker.queuedCount();
        matchmaker.enqueue(session, "alice", 0);
        matchmaker.enqueue(session, "alice", 0);
        assertEquals(before + 1, matchmaker.queuedCount(), "a repeated click must not enqueue the account twice");
        matchmaker.cancel(session);
    }

    @Test
    void two_tabs_of_the_same_account_cannot_both_queue() {
        WebSocketSession tabA = sessionForUser(99L);
        WebSocketSession tabB = sessionForUser(99L);
        int before = matchmaker.queuedCount();
        matchmaker.enqueue(tabA, "bob", 0);
        matchmaker.enqueue(tabB, "bob", 0);
        assertEquals(before + 1, matchmaker.queuedCount(), "one account holds at most one ranked queue slot");
        matchmaker.cancel(tabA);
        matchmaker.cancel(tabB);
    }

    private static WebSocketSession sessionForUser(long userId) {
        FakeSession s = new FakeSession();
        s.put("userId", userId);
        return s;
    }

    /** Session WebSocket minimale : ne porte que ses attributs (userId…), le transport est inutilisé. */
    private static final class FakeSession implements WebSocketSession {
        private final MutableConvertibleValues<Object> attrs = MutableConvertibleValues.of(new HashMap<>());

        @Override
        public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> context) {
            return attrs.get(name, context);
        }

        @Override
        public Set<String> names() {
            return attrs.names();
        }

        @Override
        public Collection<Object> values() {
            return attrs.values();
        }

        @Override
        public MutableConvertibleValues<Object> put(CharSequence key, Object value) {
            attrs.put(key, value);
            return this;
        }

        @Override
        public MutableConvertibleValues<Object> remove(CharSequence key) {
            attrs.remove(key);
            return this;
        }

        @Override
        public MutableConvertibleValues<Object> clear() {
            attrs.clear();
            return this;
        }

        @Override
        public String getId() {
            return "fake";
        }

        @Override
        public MutableConvertibleValues<Object> getAttributes() {
            return attrs;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public boolean isWritable() {
            return true;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public Set<? extends WebSocketSession> getOpenSessions() {
            return Set.of();
        }

        @Override
        public URI getRequestURI() {
            return URI.create("/ws/game");
        }

        @Override
        public String getProtocolVersion() {
            return "13";
        }

        @Override
        public <T> Publisher<T> send(T message, MediaType mediaType) {
            return null;
        }

        @Override
        public <T> CompletableFuture<T> sendAsync(T message, MediaType mediaType) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {}

        @Override
        public void close(CloseReason closeReason) {}
    }
}
