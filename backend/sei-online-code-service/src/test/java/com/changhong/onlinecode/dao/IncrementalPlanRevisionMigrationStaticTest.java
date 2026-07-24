package com.changhong.onlinecode.dao;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V13 增量计划修订修复迁移的兼容性约束。
 */
class IncrementalPlanRevisionMigrationStaticTest {

    private static final Path MIGRATION = firstExisting(
            Path.of("src/main/resources/db/migration/V13__restore_incremental_plan_revision.sql"),
            Path.of("sei-online-code-service/src/main/resources/db/migration/V13__restore_incremental_plan_revision.sql"));

    @Test
    void existingRecordsReceiveBackwardCompatibleRevisionDefaults() throws IOException {
        String sql = migrationSql();

        assertTrue(sql.contains("revision_seq bigint not null default 0"));
        assertTrue(sql.contains("applied_revision_seq bigint not null default 0"));
        assertTrue(sql.contains("revision_state varchar(32) not null default 'none'"));
        assertTrue(sql.contains("alter column revision_seq set default 0"));
        assertTrue(sql.contains("alter column applied_revision_seq set default 0"));
        assertTrue(sql.contains("alter column revision_state set default 'none'"));
        assertFalse(sql.contains("update oc_requirement"), "migration must not create or replace an existing loop");
    }

    @Test
    void revisionAndHandoffLookupsHaveIndexes() throws IOException {
        String sql = migrationSql();

        assertTrue(sql.contains("idx_execution_plan_requirement_revision"));
        assertTrue(sql.contains("idx_coding_task_requirement_revision"));
        assertTrue(sql.contains("idx_task_handoff_requirement_revision"));
        assertTrue(sql.contains("uk_task_handoff_task_revision"));
    }

    @Test
    void historicalRevisionZeroPlansAreExcludedFromNewUniquenessRule() throws IOException {
        String sql = migrationSql();

        assertTrue(sql.contains("create unique index if not exists uk_execution_plan_revision"));
        assertTrue(sql.contains("where revision_seq > 0"));
    }

    private static String migrationSql() throws IOException {
        return Files.readString(MIGRATION, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
    }

    private static Path firstExisting(Path first, Path second) {
        if (Files.isRegularFile(first)) {
            return first;
        }
        if (Files.isRegularFile(second)) {
            return second;
        }
        throw new IllegalStateException("Cannot find V13 migration");
    }
}
