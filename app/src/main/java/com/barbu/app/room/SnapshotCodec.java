package com.barbu.app.room;

import com.barbu.engine.round.MontanteState;
import com.barbu.engine.round.RoundState;
import com.barbu.engine.round.TrickTakingState;
import com.barbu.engine.variant.Variant;
import com.barbu.engine.variant.Variants;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.inject.Singleton;
import java.io.IOException;

/**
 * Serializes a table's state to JSON for Redis. The engine stays pure (without Jackson): the two
 * polymorphic points are handled here, outside the engine — a {@link Variant} is reduced to its id
 * (rebuilt via {@link Variants#byId}), and {@link RoundState} (sealed interface) carries a type
 * discriminant via mixin.
 */
@Singleton
public class SnapshotCodec {

    private final ObjectMapper mapper;

    public SnapshotCodec(ObjectMapper base) {
        this.mapper = base.copy();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Variant.class, new VariantSerializer());
        module.addDeserializer(Variant.class, new VariantDeserializer());
        this.mapper.registerModule(module);
        this.mapper.addMixIn(RoundState.class, RoundStateMixin.class);
        // Round states expose derived accessors (isComplete, playerCount…) that have no corresponding
        // record component: we ignore them on read-back (pure, recomputed).
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // Always serialize empty collections: the engine records (MatchState.playedByDealer,
        // history, hands…) do List/Set.copyOf in their compact constructor → an omitted field (NON_EMPTY
        // inclusion of the application ObjectMapper) comes back null and throws an NPE on read-back.
        this.mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    public String encode(GameSnapshot snapshot) {
        try {
            return mapper.writeValueAsString(snapshot);
        } catch (IOException e) {
            throw new IllegalStateException("cannot encode snapshot for room " + snapshot.roomId(), e);
        }
    }

    public GameSnapshot decode(String json) {
        try {
            return mapper.readValue(json, GameSnapshot.class);
        } catch (IOException e) {
            throw new IllegalStateException("cannot decode snapshot", e);
        }
    }

    private static final class VariantSerializer extends JsonSerializer<Variant> {
        @Override
        public void serialize(Variant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.id());
        }
    }

    private static final class VariantDeserializer extends JsonDeserializer<Variant> {
        @Override
        public Variant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return Variants.byId(p.getValueAsString());
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@t")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = TrickTakingState.class, name = "trick"),
        @JsonSubTypes.Type(value = MontanteState.class, name = "montante")
    })
    private abstract static class RoundStateMixin {}
}
