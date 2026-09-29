package com.bangersoul.aivance.core.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration regression suite for AivanceDatabase v5 -> v28.
 *
 * Every migration is exercised individually and as part of the full chain, always with
 * [PRAGMA foreign_keys = ON] (the same constraint Room enforces in production), and rebuild
 * migrations assert that child rows survive parent-table rebuilds (no cascade loss).
 *
 * Schema equality (tables, columns, types, nullability, PKs, FKs, indices, unique
 * constraints, defaults) is validated by [MigrationTestHelper.runMigrationsAndValidate]
 * against Room's exported schema JSONs — the only source of truth.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AivanceDatabase::class.java,
        /* specs = */ emptyList(),
        /* openFactory = */ FrameworkSQLiteOpenHelperFactory()
    )

    private val ALL_FROM_5 = arrayOf(
        AivanceDatabase.MIGRATION_5_6,
        AivanceDatabase.MIGRATION_6_7,
        AivanceDatabase.MIGRATION_7_8,
        AivanceDatabase.MIGRATION_8_9,
        AivanceDatabase.MIGRATION_9_10,
        AivanceDatabase.MIGRATION_10_11,
        AivanceDatabase.MIGRATION_11_12,
        AivanceDatabase.MIGRATION_12_13,
        AivanceDatabase.MIGRATION_13_14,
        AivanceDatabase.MIGRATION_14_15,
        AivanceDatabase.MIGRATION_15_16,
        AivanceDatabase.MIGRATION_16_17,
        AivanceDatabase.MIGRATION_17_18,
        AivanceDatabase.MIGRATION_18_19,
        AivanceDatabase.MIGRATION_19_20,
        AivanceDatabase.MIGRATION_20_21,
        AivanceDatabase.MIGRATION_21_22,
        AivanceDatabase.MIGRATION_22_23,
        AivanceDatabase.MIGRATION_23_24
    )

    /** Full ordered chain 5 -> 27 (adds the legacy-drop and the v26/v27 foundation steps). */
    private val ALL_FROM_5_TO_26 = ALL_FROM_5 +
        AivanceDatabase.MIGRATION_24_25 +
        AivanceDatabase.MIGRATION_25_26

    private val ALL_FROM_5_TO_27 = ALL_FROM_5_TO_26 +
        AivanceDatabase.MIGRATION_26_27

    /** Full ordered chain 5 -> 28 (adds the R1 application-consolidation step). */
    private val ALL_FROM_5_TO_28 = ALL_FROM_5_TO_27 +
        AivanceDatabase.MIGRATION_27_28

    // ---------------------------------------------------------------- helpers

    private fun seed(version: Int, vararg statements: String) {
        helper.createDatabase(TEST_DB, version).use { db ->
            statements.forEach { db.execSQL(it) }
        }
    }

    private fun runStep(from: Int, to: Int, vararg migrations: Migration) {
        helper.runMigrationsAndValidate(TEST_DB, to, true, *migrations)
    }

    private fun raw(): SQLiteDatabase {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        return SQLiteDatabase.openDatabase(
            ctx.getDatabasePath(TEST_DB).path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
    }

    /** Runs [sql] against the migrated database and returns the first column of the first row. */
    private fun scalar(sql: String): String = raw().use { db ->
        db.rawQuery(sql, null).use { c ->
            check(c.moveToFirst()) { "no rows for: $sql" }
            c.getString(0)
        }
    }

    private fun columnExists(table: String, column: String): Boolean = raw().use { db ->
        db.rawQuery("SELECT COUNT(*) FROM pragma_table_info('$table') WHERE name = ?", arrayOf(column))
            .use { c -> c.moveToFirst(); c.getLong(0) > 0 }
    }

    private fun count(table: String): Long = raw().use { db ->
        db.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { c ->
            c.moveToFirst()
            c.getLong(0)
        }
    }

    private fun assertCount(table: String, expected: Long) {
        assertEquals("row count in $table", expected, count(table))
    }

    private fun assertTableGone(table: String) {
        val exists = raw().use { db ->
            db.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name = ?",
                arrayOf(table)
            ).use { c -> c.moveToFirst(); c.getLong(0) > 0 }
        }
        assertEquals("table $table should have been dropped", false, exists)
    }

    private fun assertTableExists(table: String) {
        val exists = raw().use { db ->
            db.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name = ?",
                arrayOf(table)
            ).use { c -> c.moveToFirst(); c.getLong(0) > 0 }
        }
        assertEquals("table $table should exist", true, exists)
    }

    private fun assertIndexExists(index: String) {
        val exists = raw().use { db ->
            db.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name = ?",
                arrayOf(index)
            ).use { c -> c.moveToFirst(); c.getLong(0) > 0 }
        }
        assertEquals("index $index should exist", true, exists)
    }

    // ------------------------------------------------------- 5 -> 6 (resume tables)

    @Test
    fun migrate5To6_createsResumesAndPreservesLegacyData() {
        seed(
            5,
            "INSERT INTO user_profiles (id, name, email, skills) VALUES ('u1', 'Alice', 'a@x.com', '[]')",
            "INSERT INTO ats_results (id, score, date, resumeName, missingKeywords, feedback) " +
                "VALUES (1, 80, 100, 'R', '[]', 'fb')"
        )
        runStep(5, 6, AivanceDatabase.MIGRATION_5_6)
        assertCount("user_profiles", 1)
        assertCount("resumes", 0)
        assertCount("resume_sections", 0)
        assertCount("resume_analyses", 0)
        // legacy ats_results was intentionally dropped
        assertTableGone("ats_results")
    }

    // ------------------------------------------------------- 6 -> 7 (interview tables)

    @Test
    fun migrate6To7_createsInterviewTables() {
        seed(
            6,
            "INSERT INTO resumes (id, name, text, dateCreated, lastModified) VALUES (1, 'R', 'body', 1, 2)"
        )
        runStep(6, 7, AivanceDatabase.MIGRATION_6_7)
        assertCount("resumes", 1)
        assertCount("interview_sessions", 0)
        assertCount("interview_messages", 0)
    }

    // ------------------------------------------------------- 7 -> 8 (jobs/companies)

    @Test
    fun migrate7To8_createsJobTablesAndDropsLegacyApplications() {
        seed(
            7,
            "INSERT INTO applications (id, company, role, status, dateApplied, lastModified) " +
                "VALUES (1, 'Acme', 'Eng', 'NEW', 100, 100)",
            "INSERT INTO interview_sessions (id, targetRole, difficulty, dateStarted, isCompleted) VALUES (1, 'Eng', 'M', 1, 0)"
        )
        runStep(7, 8, AivanceDatabase.MIGRATION_7_8)
        assertCount("interview_sessions", 1)
        assertCount("companies", 0)
        assertCount("jobs", 0)
        assertCount("job_applications", 0)
        // legacy applications table is replaced by job_applications at 7->8
        assertTableGone("applications")
    }

    // ------------------------------------------------------- 8 -> 9 (AI tables)

    @Test
    fun migrate8To9_createsAiAndProviderTables() {
        seed(
            8,
            "INSERT INTO companies (id, name) VALUES (1, 'Acme')",
            "INSERT INTO jobs (id, companyId, title, postedDate) VALUES (1, 1, 'Eng', 10)"
        )
        runStep(8, 9, AivanceDatabase.MIGRATION_8_9)
        assertCount("jobs", 1)
        assertCount("ai_conversations", 0)
        assertCount("provider_configurations", 0)
        assertCount("analytics_events", 0)
    }

    // ------------------------------------------------------- 9 -> 10 (provider rebuild)

    @Test
    fun migrate9To10_rebuildsProviderConfigurationsPreservingApiKey() {
        seed(
            9,
            "INSERT INTO provider_configurations (provider, apiKey, baseUrl, settings) " +
                "VALUES ('claude', 'sk-x', 'http://x', '{}')"
        )
        runStep(9, 10, AivanceDatabase.MIGRATION_9_10)
        assertCount("provider_configurations", 1)
        assertEquals("apiKey preserved", "sk-x", scalar(
            "SELECT apiKey FROM provider_configurations WHERE provider = 'claude'"
        ))
        assertEquals("type defaults to AI", "AI", scalar(
            "SELECT type FROM provider_configurations WHERE provider = 'claude'"
        ))
        assertEquals("isEnabled defaults to 1", "1", scalar(
            "SELECT isEnabled FROM provider_configurations WHERE provider = 'claude'"
        ))
    }

    // ----------------------------------------------- 10 -> 11 (resumes rebuild + versioning)

    @Test
    fun migrate10To11_rebuildsResumesPreservingSectionsAndAnalyses() {
        seed(
            10,
            "INSERT INTO resumes (id, name, text, dateCreated, lastModified) VALUES (1, 'My Resume', 'body text', 100, 200)",
            "INSERT INTO resume_sections (resumeId, title, content, sectionOrder) VALUES (1, 'S1', 'c1', 0)",
            "INSERT INTO resume_sections (resumeId, title, content, sectionOrder) VALUES (1, 'S2', 'c2', 1)",
            "INSERT INTO resume_analyses (resumeId, jobDescription, score, matchedKeywords, missingKeywords, feedback, date) " +
                "VALUES (1, 'jd', 85, '[]', '[]', 'fb', 300)"
        )
        runStep(10, 11, AivanceDatabase.MIGRATION_10_11)
        // resume row survived the rebuild and text -> rawText
        assertCount("resumes", 1)
        assertEquals("rawText migrated from text", "body text", scalar(
            "SELECT rawText FROM resumes WHERE id = 1"
        ))
        // one canonical version created per resume
        assertCount("resume_versions", 1)
        // child rows survived the parent rebuild (FK ON)
        assertCount("resume_sections", 2)
        assertCount("resume_analyses", 1)
        // sections were re-keyed from resumeId -> versionId (both seeded sections
        // map to the single canonical version created for the resume)
        assertEquals("section re-keyed to version", "2", scalar(
            "SELECT COUNT(*) FROM resume_sections WHERE versionId = (SELECT id FROM resume_versions LIMIT 1)"
        ))
    }

    // ------------------------------------------------------- 11 -> 12 (ATS tables)

    @Test
    fun migrate11To12_createsAtsTables() {
        seed(
            11,
            "INSERT INTO resumes (id, name, rawText, dateCreated, lastModified) VALUES (1, 'R', 'b', 1, 2)",
            "INSERT INTO resume_versions (resumeId, versionName, templateId, lastModified) VALUES (1, 'Main', 'modern', 2)"
        )
        runStep(11, 12, AivanceDatabase.MIGRATION_11_12)
        assertCount("resume_versions", 1)
        assertCount("job_descriptions", 0)
        assertCount("ats_reports", 0)
    }

    // ------------------------------------------- 12 -> 13 (jobs rebuild + saved/viewed)

    @Test
    fun migrate12To13_rebuildsJobsPreservingApplications() {
        seed(
            12,
            "INSERT INTO companies (id, name) VALUES (1, 'Acme')",
            "INSERT INTO jobs (id, companyId, title, postedDate) VALUES (1, 1, 'Eng', 100)",
            "INSERT INTO jobs (id, companyId, title, postedDate) VALUES (2, 1, 'Des', 200)",
            "INSERT INTO job_applications (id, jobId, status, dateApplied, lastModified) VALUES (1, 1, 'APPLIED', 100, 100)",
            "INSERT INTO job_applications (id, jobId, status, dateApplied, lastModified) VALUES (2, 2, 'REVIEW', 200, 200)"
        )
        runStep(12, 13, AivanceDatabase.MIGRATION_12_13)
        assertCount("jobs", 2)
        assertCount("job_applications", 2)
        assertEquals("salaryMin defaults to NULL for both rebuilt rows", "2", scalar(
            "SELECT COUNT(*) FROM jobs WHERE salaryMin IS NULL"
        ))
        assertEquals("provider defaults", "UNKNOWN", scalar(
            "SELECT sourceProviderId FROM jobs WHERE id = 1"
        ))
        assertEquals("url defaults to empty", "", scalar(
            "SELECT url FROM jobs WHERE id = 1"
        ))
        assertCount("saved_jobs", 0)
        assertCount("viewed_jobs", 0)
        assertCount("search_history", 0)
    }

    // ------------------------------------------- 13 -> 14 (user_profiles rebuild + recruiter)

    @Test
    fun migrate13To14_rebuildsUserProfilesAndCreatesRecruiters() {
        seed(
            13,
            "INSERT INTO user_profiles (id, name, email, skills) VALUES ('u1', 'Alice', 'a@x.com', '[]')",
            "INSERT INTO companies (id, name) VALUES (1, 'Acme')"
        )
        runStep(13, 14, AivanceDatabase.MIGRATION_13_14)
        assertCount("user_profiles", 1)
        assertEquals("experienceYears default 0", "0", scalar(
            "SELECT experienceYears FROM user_profiles WHERE id = 'u1'"
        ))
        assertEquals("createdDate default 0", "0", scalar(
            "SELECT createdDate FROM user_profiles WHERE id = 'u1'"
        ))
        assertEquals("company ALTER preserved row", "Acme", scalar(
            "SELECT name FROM companies WHERE id = 1"
        ))
        assertCount("recruiters", 0)
        assertCount("recruiter_contacts", 0)
        assertCount("outreach_drafts", 0)
        assertCount("communication_history", 0)
    }

    // ------------------------------------------- 14 -> 15 (cover_letters rebuild)

    @Test
    fun migrate14To15_rebuildsCoverLetters() {
        seed(
            14,
            "INSERT INTO cover_letters (id, company, role, content, dateCreated, tone) " +
                "VALUES (1, 'Acme', 'Eng', 'body', 100, 'PRO')"
        )
        runStep(14, 15, AivanceDatabase.MIGRATION_14_15)
        assertCount("cover_letters", 1)
        assertEquals("company preserved", "Acme", scalar(
            "SELECT company FROM cover_letters WHERE id = 1"
        ))
        assertCount("cover_letter_versions", 0)
        assertCount("cover_letter_sections", 0)
    }

    // ------------------------------------------- 15 -> 16 (interview rebuild)

    @Test
    fun migrate15To16_rebuildsInterviewSessionsPreservingMessages() {
        seed(
            15,
            "INSERT INTO interview_sessions (id, targetRole, difficulty, dateStarted, isCompleted) " +
                "VALUES (1, 'Eng', 'MEDIUM', 100, 0)",
            "INSERT INTO interview_messages (sessionId, role, text, timestamp) VALUES (1, 'user', 'hi', 100)",
            "INSERT INTO interview_messages (sessionId, role, text, timestamp) VALUES (1, 'ai', 'yo', 200)"
        )
        runStep(15, 16, AivanceDatabase.MIGRATION_15_16)
        assertCount("interview_sessions", 1)
        assertEquals("type backfilled", "BEHAVIORAL", scalar(
            "SELECT type FROM interview_sessions WHERE id = 1"
        ))
        assertCount("interview_messages", 2)
        assertCount("interview_questions", 0)
        assertCount("interview_evaluations", 0)
    }

    // ------------------------------------------- 16 -> 17 (pipeline model)

    @Test
    fun migrate16To17_createsPipelineFromJobApplications() {
        seed(
            16,
            "INSERT INTO companies (id, name) VALUES (1, 'Acme')",
            "INSERT INTO jobs (id, companyId, title, url, sourceProviderId, postedDate) " +
                "VALUES (1, 1, 'Eng', '', 'X', 100)",
            "INSERT INTO job_applications (id, jobId, status, dateApplied, lastModified) VALUES (1, 1, 'APPLIED', 100, 100)",
            "INSERT INTO job_applications (id, jobId, status, dateApplied, lastModified) VALUES (2, 1, 'REVIEW', 200, 200)"
        )
        runStep(16, 17, AivanceDatabase.MIGRATION_16_17)
        assertCount("job_applications", 2)
        assertCount("applications", 2)
        assertEquals("currentStageId copied from status", "APPLIED", scalar(
            "SELECT currentStageId FROM applications WHERE id = 1"
        ))
        assertEquals("status backfilled ACTIVE", "ACTIVE", scalar(
            "SELECT status FROM applications WHERE id = 1"
        ))
        assertCount("application_stages", 0)
        assertCount("application_timeline", 0)
        assertCount("application_tasks", 0)
        assertCount("automation_rules", 0)
    }

    // ------------------------------------------------------- 17 -> 18 (analytics)

    @Test
    fun migrate17To18_createsAnalyticsTables() {
        seed(
            17,
            "INSERT INTO companies (id, name) VALUES (1, 'Acme')"
        )
        runStep(17, 18, AivanceDatabase.MIGRATION_17_18)
        assertCount("companies", 1)
        assertCount("analytics_snapshots", 0)
        assertCount("recommendations", 0)
        assertCount("career_goals", 0)
    }

    // ------------------------------------------------------- 18 -> 19 (assistant)

    @Test
    fun migrate18To19_createsAssistantTables() {
        seed(
            18,
            "INSERT INTO companies (id, name) VALUES (1, 'Acme')"
        )
        runStep(18, 19, AivanceDatabase.MIGRATION_18_19)
        assertCount("companies", 1)
        assertCount("assistant_conversations", 0)
        assertCount("assistant_messages", 0)
        assertCount("workflow_executions", 0)
    }

    // ------------------------------------------- 19 -> 20 (audit_logs + apiKey removal)

    @Test
    fun migrate19To20_createsAuditLogsAndRemovesApiKey() {
        seed(
            19,
            "INSERT INTO provider_configurations (provider, type, apiKey, settings, isEnabled) " +
                "VALUES ('claude', 'AI', 'sk-x', '{}', 1)"
        )
        runStep(19, 20, AivanceDatabase.MIGRATION_19_20)
        assertCount("audit_logs", 0)
        assertCount("provider_configurations", 1)
        assertEquals("apiKey column removed", "0", scalar(
            "SELECT COUNT(*) FROM pragma_table_info('provider_configurations') WHERE name = 'apiKey'"
        ))
        assertEquals("settings preserved", "{}", scalar(
            "SELECT settings FROM provider_configurations WHERE provider = 'claude'"
        ))
    }

    // ------------------------------------------------------- 20 -> 21 (users)

    @Test
    fun migrate20To21_createsUsersTable() {
        seed(20, "INSERT INTO audit_logs (action, module, timestamp, severity) VALUES ('x', 'm', 1, 'INFO')")
        runStep(20, 21, AivanceDatabase.MIGRATION_20_21)
        assertCount("audit_logs", 1)
        assertCount("users", 0)
    }

    // ------------------------------------------- 21 -> 22 (interview_questions rebuild)

    @Test
    fun migrate21To22_rebuildsInterviewQuestions() {
        seed(
            21,
            "INSERT INTO interview_questions (text, category, difficulty) VALUES ('Q?', 'TECH', 'MEDIUM')"
        )
        runStep(21, 22, AivanceDatabase.MIGRATION_21_22)
        assertCount("interview_questions", 1)
        assertEquals("isFavorite default 0", "0", scalar(
            "SELECT isFavorite FROM interview_questions"
        ))
        assertEquals("text preserved", "Q?", scalar(
            "SELECT text FROM interview_questions"
        ))
    }

    // ------------------------------------------- 22 -> 23 (user_profiles career columns)

    @Test
    fun migrate22To23_addsCareerPreferenceColumns() {
        seed(
            22,
            "INSERT INTO user_profiles (id, name, email, skills, experienceYears, createdDate) " +
                "VALUES ('u1', 'Alice', 'a@x.com', '[]', 3, 5)"
        )
        runStep(22, 23, AivanceDatabase.MIGRATION_22_23)
        assertCount("user_profiles", 1)
        assertEquals("experienceYears preserved", "3", scalar(
            "SELECT experienceYears FROM user_profiles WHERE id = 'u1'"
        ))
        assertEquals("preferredIndustries default", "[]", scalar(
            "SELECT preferredIndustries FROM user_profiles WHERE id = 'u1'"
        ))
        assertEquals("visaRequired default 0", "0", scalar(
            "SELECT visaRequired FROM user_profiles WHERE id = 'u1'"
        ))
    }

    // ------------------------------------------- 23 -> 24 (no-op)

    @Test
    fun migrate23To24_isNoOpPreservingData() {
        seed(
            23,
            "INSERT INTO user_profiles (id, name, email, skills, experienceYears, createdDate, " +
                "preferredIndustries, visaRequired) VALUES ('u1', 'Alice', 'a@x.com', '[]', 3, 5, '[]', 0)"
        )
        runStep(23, 24, AivanceDatabase.MIGRATION_23_24)
        assertCount("user_profiles", 1)
        assertEquals("preferredIndustries preserved", "[]", scalar(
            "SELECT preferredIndustries FROM user_profiles WHERE id = 'u1'"
        ))
    }

    // ------------------------------------------- 24 -> 25 (drop legacy resume_analyses)

    @Test
    fun migrate24To25_dropsLegacyResumeAnalyses() {
        seed(
            24,
            "INSERT INTO resumes (id, name, dateCreated, lastModified) VALUES (1, 'R', 1, 2)",
            "INSERT INTO resume_versions (id, resumeId, versionName, templateId, lastModified) VALUES (1, 1, 'Main', 'modern', 2)",
            "INSERT INTO job_descriptions (id, rawText, dateCreated) VALUES (1, 'jd', 3)",
            "INSERT INTO resume_analyses (id, resumeId, jobDescription, score, matchedKeywords, missingKeywords, feedback, date) " +
                "VALUES (1, 1, 'jd', 85, '[]', '[]', 'fb', 300)",
            "INSERT INTO ats_reports (id, resumeVersionId, jobDescriptionId, overallScore, matchPercentage, matchedKeywords, missingKeywords, sectionScores, optimizationTips, dateGenerated) " +
                "VALUES (1, 1, 1, 92, 92, 'Kotlin', 'KMP', '{}', '[]', 400)"
        )
        runStep(24, 25, AivanceDatabase.MIGRATION_24_25)
        // The legacy table is gone while its modern successor keeps its data.
        assertTableGone("resume_analyses")
        assertCount("ats_reports", 1)
        assertEquals("92", scalar("SELECT overallScore FROM ats_reports WHERE id = 1"))
    }

    // ------------------------------------------- 25 -> 26 (Career Knowledge OS foundation)

    @Test
    fun migrate25To26_createsFoundationTablesAndPreservesData() {
        seed(
            25,
            // v25 user_profiles carries several NOT NULL columns without defaults
            // (experienceYears, preferredIndustries, visaRequired, createdDate); the seed must
            // satisfy them to mirror a real v25 row.
            "INSERT INTO user_profiles (id, name, email, skills, experienceYears, preferredIndustries, visaRequired, createdDate) " +
                "VALUES ('u1', 'Alice', 'a@x.com', '[]', 3, '[]', 0, 5)",
            "INSERT INTO companies (id, name) VALUES (1, 'Acme')",
            "INSERT INTO jobs (id, companyId, title, url, sourceProviderId, postedDate) VALUES (1, 1, 'Eng', '', 'X', 100)"
        )
        runStep(25, 26, AivanceDatabase.MIGRATION_25_26)

        // 1. The migration is strictly additive: all pre-existing v25 data survives untouched.
        assertCount("user_profiles", 1)
        assertCount("companies", 1)
        assertCount("jobs", 1)
        assertEquals("Alice", scalar("SELECT name FROM user_profiles WHERE id = 'u1'"))
        assertEquals("Eng", scalar("SELECT title FROM jobs WHERE id = 1"))

        // 2. The four new foundation tables exist and are empty.
        assertTableExists("graph_nodes")
        assertTableExists("graph_edges")
        assertTableExists("career_event_log")
        assertTableExists("career_memory_entries")
        assertCount("graph_nodes", 0)
        assertCount("graph_edges", 0)
        assertCount("career_event_log", 0)
        assertCount("career_memory_entries", 0)

        // 3. Their indices are present.
        assertIndexExists("idx_graph_nodes_type")
        assertIndexExists("idx_graph_edges_source")
        assertIndexExists("idx_graph_edges_target")
        assertIndexExists("idx_career_event_log_type_time")
        assertIndexExists("idx_career_memory_type")

        // 4. The new tables are writable (schema is usable, not just present).
        raw().use { db ->
            db.execSQL("INSERT INTO graph_nodes (id, type, label, propertiesJson, createdAt, updatedAt) VALUES ('skill_kotlin', 'SKILL', 'Kotlin', '{}', 1, 1)")
            db.execSQL("INSERT INTO career_memory_entries (memoryId, type, content, createdAt, updatedAt, confidence, sourceEventIdsJson, evidenceRefsJson, isUserConfirmed) VALUES ('m1', 'FACT', 'c', 1, 1, 1.0, '[]', '[]', 1)")
        }
        assertCount("graph_nodes", 1)
        assertCount("career_memory_entries", 1)
    }

    // ------------------------------------------- 26 -> 27 (event-contract schemaVersion)

    @Test
    fun migrate26To27_addsSchemaVersionColumnAndPreservesExistingEvents() {
        seed(
            26,
            "INSERT INTO career_event_log (eventId, timestamp, sourceModule, eventType, payloadJson) " +
                "VALUES ('evt_1', 100, 'feature:resume', 'ResumeCreated', '{\"name\":\"R\"}')"
        )
        runStep(26, 27, AivanceDatabase.MIGRATION_26_27)

        // 1. The pre-existing v26 audit row survives the additive column.
        assertCount("career_event_log", 1)
        assertEquals("ResumeCreated", scalar("SELECT eventType FROM career_event_log WHERE eventId = 'evt_1'"))

        // 2. The new schemaVersion column exists and backfills legacy rows to contract v1.
        assertTrue("schemaVersion column added", columnExists("career_event_log", "schemaVersion"))
        assertEquals("1", scalar("SELECT schemaVersion FROM career_event_log WHERE eventId = 'evt_1'"))

        // 3. New rows can carry an explicit version.
        raw().use { db ->
            db.execSQL(
                "INSERT INTO career_event_log (eventId, timestamp, sourceModule, eventType, schemaVersion, payloadJson) " +
                    "VALUES ('evt_2', 200, 'feature:resume', 'ResumeUpdated', 1, '{}')"
            )
        }
        assertCount("career_event_log", 2)
        assertEquals("1", scalar("SELECT schemaVersion FROM career_event_log WHERE eventId = 'evt_2'"))
    }

    // ------------------------------------------- 27 -> 28 (R1: application consolidation)

    @Test
    fun migrate27To28_importsLegacyApplicationsDedupesAndDropsTable() {
        seed(
            27,
            "INSERT INTO companies (id, name) VALUES (1, 'Acme')",
            "INSERT INTO jobs (id, companyId, title, url, sourceProviderId, postedDate) " +
                "VALUES (1, 1, 'Eng', '', 'X', 100)",
            "INSERT INTO jobs (id, companyId, title, url, sourceProviderId, postedDate) " +
                "VALUES (2, 1, 'Des', '', 'X', 200)",
            // A canonical pipeline row already exists for job 1, drifted ahead of the legacy row.
            "INSERT INTO applications (id, jobId, currentStageId, status, dateApplied, lastModified) " +
                "VALUES (1, 1, 'INTERVIEWING', 'ACTIVE', 50, 50)",
            // Legacy rows: job 1 duplicates the canonical row, job 2 has no counterpart.
            "INSERT INTO job_applications (id, jobId, status, dateApplied, salaryRange, notes, lastModified) " +
                "VALUES (1, 1, 'APPLIED', 100, '100k', 'dup', 100)",
            "INSERT INTO job_applications (id, jobId, status, dateApplied, salaryRange, notes, lastModified) " +
                "VALUES (2, 2, 'APPLIED', 200, '150k', 'new', 200)"
        )
        runStep(27, 28, AivanceDatabase.MIGRATION_27_28)

        // 1. The only column the canonical table lacked is added, additively.
        assertTrue("salaryRange column added", columnExists("applications", "salaryRange"))

        // 2. The duplicate source of truth is retired.
        assertTableGone("job_applications")

        // 3. Dedupe on jobId: only the legacy row with no canonical counterpart survives.
        assertCount("applications", 2)
        assertEquals("one canonical row per job", "1", scalar("SELECT COUNT(*) FROM applications WHERE jobId = 1"))
        assertEquals("duplicate legacy row not imported", "0", scalar("SELECT COUNT(*) FROM applications WHERE salaryRange = '100k'"))

        // 4. The pre-existing canonical row keeps its own stage untouched.
        assertEquals("INTERVIEWING", scalar("SELECT currentStageId FROM applications WHERE jobId = 1"))

        // 5. The imported row carries the legacy payload across both axes.
        assertEquals("APPLIED", scalar("SELECT currentStageId FROM applications WHERE jobId = 2"))
        assertEquals("ACTIVE", scalar("SELECT status FROM applications WHERE jobId = 2"))
        assertEquals("150k", scalar("SELECT salaryRange FROM applications WHERE jobId = 2"))
        assertEquals("new", scalar("SELECT notes FROM applications WHERE jobId = 2"))
        assertEquals("200", scalar("SELECT dateApplied FROM applications WHERE jobId = 2"))
    }

    // ------------------------------------------- full chain: 5 -> 28 (with data)

    @Test
    fun migrate5To28_fullChainConsolidatesApplications() {
        seed(
            5,
            "INSERT INTO user_profiles (id, name, email, skills) VALUES ('u1', 'Alice', 'a@x.com', '[]')"
        )
        runStep(5, 28, *ALL_FROM_5_TO_28)
        assertCount("user_profiles", 1)
        assertEquals("Alice", scalar("SELECT name FROM user_profiles WHERE id = 'u1'"))
        // The canonical table survives the whole chain; the retired one does not.
        assertTableExists("applications")
        assertTableGone("job_applications")
        assertTrue("salaryRange present at end of chain", columnExists("applications", "salaryRange"))
    }

    // ------------------------------------------- full chain: 5 -> 27 (with data)

    @Test
    fun migrate5To27_fullChainPreservesUserDataAndHardensEventLog() {
        seed(
            5,
            "INSERT INTO user_profiles (id, name, email, skills) VALUES ('u1', 'Alice', 'a@x.com', '[]')"
        )
        runStep(5, 27, *ALL_FROM_5_TO_27)
        assertCount("user_profiles", 1)
        assertEquals("Alice", scalar("SELECT name FROM user_profiles WHERE id = 'u1'"))
        assertTableExists("career_event_log")
        assertTrue("schemaVersion present at end of chain", columnExists("career_event_log", "schemaVersion"))
    }

    // ------------------------------------------- full chain: 5 -> 26 (with data)

    @Test
    fun migrate5To26_fullChainPreservesUserDataAndAddsFoundation() {
        seed(
            5,
            "INSERT INTO user_profiles (id, name, email, skills) VALUES ('u1', 'Alice', 'a@x.com', '[]')",
            "INSERT INTO cover_letters (id, company, role, content, dateCreated, tone) " +
                "VALUES (1, 'Acme', 'Eng', 'body', 100, 'PRO')"
        )
        runStep(5, 26, *ALL_FROM_5_TO_26)
        // Core user data survives the full chain including the additive v26 step.
        assertCount("user_profiles", 1)
        assertCount("cover_letters", 1)
        assertEquals("Alice", scalar("SELECT name FROM user_profiles WHERE id = 'u1'"))
        // The v26 foundation tables are present at the end of the chain.
        assertTableExists("graph_nodes")
        assertTableExists("graph_edges")
        assertTableExists("career_event_log")
        assertTableExists("career_memory_entries")
    }

    // ------------------------------------------------ full chain: 5 -> 24 (empty)

    @Test
    fun migrate5To24_emptyDatabase() {
        seed(5)
        runStep(5, 24, *ALL_FROM_5)
        assertCount("user_profiles", 0)
        assertCount("jobs", 0)
        assertCount("users", 0)
    }

    // ------------------------------------------------ full chain: 5 -> 24 (with data)

    @Test
    fun migrate5To24_fullChainPreservesUserData() {
        seed(
            5,
            "INSERT INTO user_profiles (id, name, email, skills) VALUES ('u1', 'Alice', 'a@x.com', '[]')",
            "INSERT INTO cover_letters (id, company, role, content, dateCreated, tone) " +
                "VALUES (1, 'Acme', 'Eng', 'body', 100, 'PRO')"
        )
        runStep(5, 24, *ALL_FROM_5)
        assertCount("user_profiles", 1)
        assertCount("cover_letters", 1)
        // user_profiles survived BOTH rebuilds (13->14 and 22->23)
        assertEquals("Alice", scalar("SELECT name FROM user_profiles WHERE id = 'u1'"))
        assertEquals("[]", scalar("SELECT preferredIndustries FROM user_profiles WHERE id = 'u1'"))
        assertEquals("Acme", scalar("SELECT company FROM cover_letters WHERE id = 1"))
    }

    // ------------------------------------------------ chain: 10 -> 24 (resume lineage)

    @Test
    fun migrate10To24_resumeDataSurvivesAllRebuilds() {
        seed(
            10,
            "INSERT INTO resumes (id, name, text, dateCreated, lastModified) VALUES (1, 'My Resume', 'body text', 100, 200)",
            "INSERT INTO resume_sections (resumeId, title, content, sectionOrder) VALUES (1, 'S1', 'c1', 0)",
            "INSERT INTO resume_sections (resumeId, title, content, sectionOrder) VALUES (1, 'S2', 'c2', 1)",
            "INSERT INTO resume_analyses (resumeId, jobDescription, score, matchedKeywords, missingKeywords, feedback, date) " +
                "VALUES (1, 'jd', 85, '[]', '[]', 'fb', 300)"
        )
        runStep(10, 24, *ALL_FROM_5.drop(5).toTypedArray())
        assertCount("resumes", 1)
        assertCount("resume_versions", 1)
        assertCount("resume_sections", 2)
        assertCount("resume_analyses", 1)
        assertEquals("body text", scalar("SELECT rawText FROM resumes WHERE id = 1"))
        assertEquals("jd", scalar("SELECT jobDescription FROM resume_analyses WHERE id = 1"))
    }

    // ------------------------------------------------ full chain: 10 -> 25 (legacy ATS dropped)

    @Test
    fun migrate10To25_legacyResumeAnalysesDropped() {
        seed(
            10,
            "INSERT INTO resumes (id, name, text, dateCreated, lastModified) VALUES (1, 'My Resume', 'body text', 100, 200)",
            "INSERT INTO resume_analyses (resumeId, jobDescription, score, matchedKeywords, missingKeywords, feedback, date) " +
                "VALUES (1, 'jd', 85, '[]', '[]', 'fb', 300)"
        )
        runStep(10, 25, *(ALL_FROM_5.drop(5).toTypedArray() + AivanceDatabase.MIGRATION_24_25))
        // Resume lineage survives the full chain; the legacy analyses table is
        // dropped by 24 -> 25 (T-04) and its data is intentionally discarded.
        assertCount("resumes", 1)
        assertCount("resume_versions", 1)
        assertTableGone("resume_analyses")
        assertEquals("body text", scalar("SELECT rawText FROM resumes WHERE id = 1"))
    }

    // ------------------------------------------------ chain: 16 -> 24 (job lineage)

    @Test
    fun migrate16To24_jobDataSurvivesAllRebuilds() {
        seed(
            16,
            "INSERT INTO companies (id, name) VALUES (1, 'Acme')",
            "INSERT INTO jobs (id, companyId, title, url, sourceProviderId, postedDate) " +
                "VALUES (1, 1, 'Eng', '', 'X', 100)",
            "INSERT INTO job_applications (id, jobId, status, dateApplied, lastModified) VALUES (1, 1, 'APPLIED', 100, 100)",
            "INSERT INTO job_applications (id, jobId, status, dateApplied, lastModified) VALUES (2, 1, 'REVIEW', 200, 200)"
        )
        runStep(16, 24, *ALL_FROM_5.drop(11).toTypedArray())
        assertCount("companies", 1)
        assertCount("jobs", 1)
        assertCount("job_applications", 2)
        assertCount("applications", 2)
        assertEquals("Eng", scalar("SELECT title FROM jobs WHERE id = 1"))
    }

    // ------------------------------------------------ stress dataset (16 -> 24)

    /**
     * Large-dataset upgrade (mirrors the Phase 7 stress spec): 100 companies, 1000 jobs,
     * 500 recruiters, 100 resumes + versions, 100 cover letters, 500 applications,
     * 100 interview sessions, 1000 timeline events. Seeded at v17 (the first version where
     * the full pipeline model exists) and upgraded 17->24.
     */
    @Test
    fun migrate17To24_stressDataset() {
        val companies = (1..100).joinToString(",") { "($it, 'C$it')" }
        val jobs = (1..1000).joinToString(",") { "($it, ${(it % 100) + 1}, 'J$it', '', 'PROVIDER', ${it * 10})" }
        val recruiters = (1..500).joinToString(",") { "('r$it', ${(it % 100) + 1}, 'Rec$it', 'ACTIVE')" }
        val resumes = (1..100).joinToString(",") { "($it, 'Res$it', ${it * 10}, ${it * 10 + 1})" }
        val resumeVersions = (1..100).joinToString(",") { "($it, ${(it % 100) + 1}, 'Main', 'modern', ${it * 10})" }
        val coverLetters = (1..100).joinToString(",") { "($it, ${(it % 100) + 1}, ${(it % 1000) + 1}, 'CL$it', 'Eng', ${it * 10})" }
        val applications = (1..500).joinToString(",") { "($it, ${(it % 1000) + 1}, 'ACTIVE', 'APPLIED', ${it * 10}, ${it * 10})" }
        val sessions = (1..100).joinToString(",") { "($it, 'Eng$it', 'BEHAVIORAL', 'MEDIUM', ${it * 10}, ${if (it % 2 == 0) 1 else 0})" }
        val timeline = (1..1000).joinToString(",") { "($it, ${(it % 500) + 1}, 'EVENT', 'E$it', ${it * 10})" }

        seed(
            17,
            "INSERT INTO companies (id, name) VALUES $companies",
            "INSERT INTO jobs (id, companyId, title, url, sourceProviderId, postedDate) VALUES $jobs",
            "INSERT INTO recruiters (id, companyId, name, status) VALUES $recruiters",
            "INSERT INTO resumes (id, name, dateCreated, lastModified) VALUES $resumes",
            "INSERT INTO resume_versions (id, resumeId, versionName, templateId, lastModified) VALUES $resumeVersions",
            "INSERT INTO cover_letters (id, resumeVersionId, jobId, company, role, dateCreated) VALUES $coverLetters",
            "INSERT INTO applications (id, jobId, status, currentStageId, dateApplied, lastModified) VALUES $applications",
            "INSERT INTO interview_sessions (id, targetRole, type, difficulty, dateStarted, isCompleted) VALUES $sessions",
            "INSERT INTO application_timeline (id, applicationId, eventType, title, timestamp) VALUES $timeline"
        )
        runStep(17, 24, *ALL_FROM_5.drop(12).toTypedArray())

        assertCount("companies", 100)
        assertCount("jobs", 1000)
        assertCount("recruiters", 500)
        assertCount("resumes", 100)
        assertCount("resume_versions", 100)
        assertCount("cover_letters", 100)
        assertCount("applications", 500)
        assertCount("interview_sessions", 100)
        assertCount("application_timeline", 1000)
        // foreign-key integrity is intact after the full chain
        assertEquals("no orphan applications", "0", scalar(
            "SELECT COUNT(*) FROM applications a LEFT JOIN jobs j ON a.jobId = j.id WHERE j.id IS NULL"
        ))
        assertEquals("no orphan timeline", "0", scalar(
            "SELECT COUNT(*) FROM application_timeline t LEFT JOIN applications a ON t.applicationId = a.id WHERE a.id IS NULL"
        ))
    }
}
