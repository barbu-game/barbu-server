package com.barbu.app.room;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.http.MediaType;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketSession;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.reactivestreams.Publisher;

/** Minimal WebSocket session for tests: carries only its attributes, the transport is inert. */
class FakeSession implements WebSocketSession {
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
