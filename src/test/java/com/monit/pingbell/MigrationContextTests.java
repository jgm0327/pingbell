package com.monit.pingbell;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.startupcheck.IsRunningStartupCheckStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
@ActiveProfiles("migration-test")
@SpringBootTest
@Testcontainers
class MigrationContextTests {

    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg16")
                    .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
            .withDatabaseName("pingbell_migration")
            .withUsername("pingbell")
            .withPassword("synthetic-migration-password")
            .withStartupCheckStrategy(new IsRunningStartupCheckStrategy()
                    .withTimeout(Duration.ofSeconds(30)))
            .withStartupTimeout(Duration.ofMinutes(2));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void emptyDatabaseMigratesThroughV14WithPgvectorAndHibernateValidation() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            assertThat(singleString(statement,
                    "select extversion from pg_extension where extname = 'vector'"))
                    .isEqualTo("0.8.2");
            assertThat(singleString(statement,
                    "select version from flyway_schema_history where success = true order by installed_rank desc limit 1"))
                    .isEqualTo("14");
            assertThat(singleString(statement,
                    "select data_type from information_schema.columns " +
                            "where table_schema = 'public' and table_name = 'runbook_chunk_embeddings' " +
                            "and column_name = 'embedding'"))
                    .isEqualTo("USER-DEFINED");
        }
    }

    @Test
    void existingV13DatabaseMigratesIncrementallyToV14() throws SQLException {
        String databaseName = "pingbell_v13_upgrade";
        createDatabase(databaseName);

        Flyway v13 = flyway(databaseName, MigrationVersion.fromVersion("13"));
        MigrateResult v13Result = v13.migrate();
        assertThat(v13Result.targetSchemaVersion).isEqualTo("13");

        MigrateResult v14Result = flyway(databaseName, null).migrate();
        assertThat(v14Result.targetSchemaVersion).isEqualTo("14");

        try (Connection connection = connection(databaseName);
             Statement statement = connection.createStatement()) {
            assertThat(singleString(statement,
                    "select extversion from pg_extension where extname = 'vector'"))
                    .isEqualTo("0.8.2");
            assertThat(singleInt(statement,
                    "select count(*) from information_schema.tables where table_schema = 'public' " +
                            "and table_name in ('runbook_chunks', 'runbook_chunk_embeddings')"))
                    .isEqualTo(2);
        }
    }

    @Test
    void vectorDimensionAndDuplicateIdentifiersFailWithDatabaseConstraints() throws SQLException {
        String databaseName = "pingbell_vector_constraints";
        createDatabase(databaseName);
        flyway(databaseName, null).migrate();

        try (Connection connection = connection(databaseName);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into runbook_chunks
                        (tenant_id, document_id, version, chunk_id, chunk_order, section_title, content, content_hash, created_at)
                    values
                        (101, 'synthetic-runbook', 1, 'chunk-001', 0, 'Symptoms', 'Synthetic content',
                         'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', now())
                    """);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    insert into runbook_chunks
                        (tenant_id, document_id, version, chunk_id, chunk_order, section_title, content, content_hash, created_at)
                    values
                        (101, 'synthetic-runbook', 1, 'chunk-001', 1, 'Checks', 'Other synthetic content',
                         'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', now())
                    """))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertSqlFailure((SQLException) error,
                            "23505", "uk_runbook_chunk_boundary_id"));

            assertThatThrownBy(() -> statement.executeUpdate("""
                    insert into runbook_chunk_embeddings
                        (tenant_id, document_id, version, chunk_id, embedding, model_name,
                         embedding_dimension, generated_at, active)
                    values
                        (101, 'synthetic-runbook', 1, 'chunk-001', '[0.1,0.2,0.3]'::vector,
                         'synthetic-embedding-model', 2, now(), true)
                    """))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertSqlFailure((SQLException) error,
                            "23514", "ck_runbook_chunk_embedding_dimension"));

            statement.executeUpdate("""
                    insert into runbook_chunk_embeddings
                        (tenant_id, document_id, version, chunk_id, embedding, model_name,
                         embedding_dimension, generated_at, active)
                    values
                        (101, 'synthetic-runbook', 1, 'chunk-001', '[0.1,0.2,0.3]'::vector,
                         'synthetic-embedding-model', 3, now(), true)
                    """);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    insert into runbook_chunk_embeddings
                        (tenant_id, document_id, version, chunk_id, embedding, model_name,
                         embedding_dimension, generated_at, active)
                    values
                        (101, 'synthetic-runbook', 1, 'chunk-001', '[0.3,0.2,0.1]'::vector,
                         'synthetic-embedding-model-v2', 3, now(), true)
                    """))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertSqlFailure((SQLException) error,
                            "23505", "uk_runbook_embedding_single_active_chunk"));
        }
    }

    @Test
    void plainPostgresqlFailsWithExplicitPgvectorUnavailableMessage() {
        try (PostgreSQLContainer<?> plainPostgres = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("pingbell_without_pgvector")
                .withUsername("pingbell")
                .withPassword("synthetic-migration-password")
                .withStartupCheckStrategy(new IsRunningStartupCheckStrategy()
                        .withTimeout(Duration.ofSeconds(30)))
                .withStartupTimeout(Duration.ofMinutes(2))) {
            plainPostgres.start();

            Flyway flyway = Flyway.configure()
                    .dataSource(plainPostgres.getJdbcUrl(), plainPostgres.getUsername(), plainPostgres.getPassword())
                    .locations("classpath:db/migration")
                    .load();

            assertThatThrownBy(flyway::migrate)
                    .hasStackTraceContaining("PGVECTOR_EXTENSION_NOT_AVAILABLE");
        }
    }

    private static Flyway flyway(String databaseName, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(databaseUrl(databaseName), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static void createDatabase(String databaseName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("create database " + databaseName);
        }
    }

    private static Connection connection(String databaseName) throws SQLException {
        return DriverManager.getConnection(databaseUrl(databaseName), postgres.getUsername(), postgres.getPassword());
    }

    private static String databaseUrl(String databaseName) {
        return postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + databaseName);
    }

    private static String singleString(Statement statement, String sql) throws SQLException {
        try (var resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private static int singleInt(Statement statement, String sql) throws SQLException {
        try (var resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private static void assertSqlFailure(SQLException error, String sqlState, String constraintName) {
        assertThat(error.getSQLState()).isEqualTo(sqlState);
        assertThat(error.getMessage()).contains(constraintName);
    }
}
