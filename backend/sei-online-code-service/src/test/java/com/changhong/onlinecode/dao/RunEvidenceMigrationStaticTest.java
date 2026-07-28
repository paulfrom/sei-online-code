package com.changhong.onlinecode.dao;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RunEvidenceMigrationStaticTest {

    private static final Path MIGRATION = firstExisting(
            Path.of("src/main/resources/db/migration/V14__run_evidence_and_behavior_memory.sql"),
            Path.of("sei-online-code-service/src/main/resources/db/migration/"
                    + "V14__run_evidence_and_behavior_memory.sql"));

    @Test
    void createsReplayEvidenceFeedbackAndBehaviorMemorySchema() throws IOException {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8)
                .toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("create table if not exists oc_run_artifact"));
        assertTrue(sql.contains("create table if not exists oc_run_artifact_chunk"));
        assertTrue(sql.contains("create table if not exists oc_run_evidence"));
        assertTrue(sql.contains("create table if not exists oc_agent_behavior_memory"));
        assertTrue(sql.contains("create table if not exists oc_run_behavior_memory"));
        assertTrue(sql.contains("add column if not exists acceptance_criteria"));
        assertTrue(sql.contains("add column if not exists remediation_brief_json"));
        assertTrue(sql.contains("add column if not exists feedback_applied_run_id"));
    }

    private static Path firstExisting(Path first, Path second) {
        if (Files.isRegularFile(first)) {
            return first;
        }
        if (Files.isRegularFile(second)) {
            return second;
        }
        throw new IllegalStateException("Cannot find V14 run evidence migration");
    }
}
