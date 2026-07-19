package com.changhong.onlinecode.dao;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static migration coverage checks for the progress-ledger acceptance gate.
 *
 * <p>This does not replace {@link ProgressLedgerDaoTest}: ACC-001 still requires a real
 * PostgreSQL/Testcontainers run with Hibernate {@code ddl-auto=validate}. It only prevents the
 * previously observed code-side blocker where the Flyway chain did not create core entity tables
 * before V7/V8 ledger migrations.</p>
 */
class ProgressLedgerMigrationStaticTest {

    private static final Path ENTITY_DIR = firstExisting(
            Path.of("src/main/java/com/changhong/onlinecode/entity"),
            Path.of("sei-online-code-service/src/main/java/com/changhong/onlinecode/entity"));
    private static final Path MIGRATION_DIR = firstExisting(
            Path.of("src/main/resources/db/migration"),
            Path.of("sei-online-code-service/src/main/resources/db/migration"));

    private static final Pattern TABLE_ANNOTATION = Pattern.compile(
            "@Table\\s*\\(\\s*name\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern ENTITY_MARKER = Pattern.compile("@Entity\\b");
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?i)\\bCREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-zA-Z0-9_]+)");
    private static final Pattern COLUMN = Pattern.compile(
            "@Column\\s*\\(\\s*name\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__.*\\.sql$");

    @Test
    void allCurrentEntitiesHaveCreateTableMigration() throws IOException {
        Set<String> entityTables = entityTables();
        Set<String> createdTables = createdTablesByMigration().keySet();

        Set<String> missing = new HashSet<>(entityTables);
        missing.removeAll(createdTables);

        assertTrue(missing.isEmpty(), "Missing CREATE TABLE migration(s): " + missing);
    }

    @Test
    void v8RunAlterHasPriorRunTable() throws IOException {
        Map<String, Integer> created = createdTablesByMigration();

        Integer runVersion = created.get("oc_run");

        assertTrue(runVersion != null && runVersion < 8,
                "oc_run must be created before V8 alters progress fields; actual version=" + runVersion);
    }

    @Test
    void declaredEntityColumnsAppearInMigrationChain() throws IOException {
        String allMigrations = allMigrationSql().toLowerCase(Locale.ROOT);
        Map<String, Set<String>> missing = new HashMap<>();

        for (Path entity : entityFiles()) {
            String source = Files.readString(entity, StandardCharsets.UTF_8);
            Matcher tableMatcher = TABLE_ANNOTATION.matcher(source);
            if (!ENTITY_MARKER.matcher(source).find() || !tableMatcher.find()) {
                continue;
            }
            Set<String> missingColumns = new HashSet<>();
            Matcher columnMatcher = COLUMN.matcher(source);
            while (columnMatcher.find()) {
                String column = columnMatcher.group(1).toLowerCase(Locale.ROOT);
                if (!allMigrations.contains(column)) {
                    missingColumns.add(column);
                }
            }
            if (!missingColumns.isEmpty()) {
                missing.put(tableMatcher.group(1), missingColumns);
            }
        }

        assertTrue(missing.isEmpty(), "Entity columns missing from migrations: " + missing);
    }

    private static Set<String> entityTables() throws IOException {
        Set<String> tables = new HashSet<>();
        for (Path entity : entityFiles()) {
            String source = Files.readString(entity, StandardCharsets.UTF_8);
            Matcher tableMatcher = TABLE_ANNOTATION.matcher(source);
            if (ENTITY_MARKER.matcher(source).find() && tableMatcher.find()) {
                tables.add(tableMatcher.group(1));
            }
        }
        return tables;
    }

    private static Map<String, Integer> createdTablesByMigration() throws IOException {
        Map<String, Integer> tables = new HashMap<>();
        for (Path migration : migrationFiles()) {
            int version = versionOf(migration);
            Matcher matcher = CREATE_TABLE.matcher(Files.readString(migration, StandardCharsets.UTF_8));
            while (matcher.find()) {
                tables.merge(matcher.group(1).toLowerCase(Locale.ROOT), version, Math::min);
            }
        }
        return tables;
    }

    private static String allMigrationSql() throws IOException {
        StringBuilder sql = new StringBuilder();
        for (Path migration : migrationFiles()) {
            sql.append(Files.readString(migration, StandardCharsets.UTF_8)).append('\n');
        }
        return sql.toString();
    }

    private static int versionOf(Path migration) {
        Matcher matcher = VERSION.matcher(migration.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unexpected migration name: " + migration);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static Iterable<Path> entityFiles() throws IOException {
        try (Stream<Path> files = Files.list(ENTITY_DIR)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static Iterable<Path> migrationFiles() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
            return files.filter(path -> path.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted()
                    .toList();
        }
    }

    private static Path firstExisting(Path first, Path second) {
        if (Files.isDirectory(first)) {
            return first;
        }
        if (Files.isDirectory(second)) {
            return second;
        }
        throw new IllegalStateException("Cannot find path: " + first + " or " + second);
    }
}
