package com.barbu.app.persistence;

import com.barbu.app.persistence.Entities.GameEntity;
import com.barbu.app.persistence.Entities.GamePlayerEntity;
import com.barbu.app.persistence.Entities.RoundEntity;
import com.barbu.app.persistence.Entities.RoundScoreEntity;
import com.barbu.app.persistence.Entities.UserEntity;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.Optional;

public final class Repositories {
    private Repositories() {}

    @JdbcRepository(dialect = Dialect.POSTGRES)
    public interface UserRepository extends CrudRepository<UserEntity, Long> {
        Optional<UserEntity> findByUsername(String username);

        boolean existsByUsername(String username);
    }

    @JdbcRepository(dialect = Dialect.POSTGRES)
    public interface GameRepository extends CrudRepository<GameEntity, Long> {}

    @JdbcRepository(dialect = Dialect.POSTGRES)
    public interface GamePlayerRepository extends CrudRepository<GamePlayerEntity, Long> {
        Iterable<GamePlayerEntity> findByGameId(long gameId);
    }

    @JdbcRepository(dialect = Dialect.POSTGRES)
    public interface RoundRepository extends CrudRepository<RoundEntity, Long> {
        Iterable<RoundEntity> findByGameId(long gameId);
    }

    @JdbcRepository(dialect = Dialect.POSTGRES)
    public interface RoundScoreRepository extends CrudRepository<RoundScoreEntity, Long> {}
}
