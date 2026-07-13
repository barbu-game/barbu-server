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
 * Sérialise l'état d'une table en JSON pour Redis. Le moteur reste pur (sans Jackson) : les deux
 * points polymorphes sont traités ici, hors du moteur — un {@link Variant} est réduit à son id
 * (reconstruit via {@link Variants#byId}), et {@link RoundState} (interface scellée) porte un
 * discriminant de type via mixin.
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
        // Les états de manche exposent des accesseurs dérivés (isComplete, playerCount…) qui n'ont pas
        // de composant de record correspondant : on les ignore à la relecture (purs, recalculés).
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // Toujours sérialiser les collections vides : les records du moteur (MatchState.playedByDealer,
        // history, hands…) font List/Set.copyOf en constructeur compact → un champ omis (inclusion
        // NON_EMPTY de l'ObjectMapper applicatif) revient null et lève une NPE à la relecture.
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
