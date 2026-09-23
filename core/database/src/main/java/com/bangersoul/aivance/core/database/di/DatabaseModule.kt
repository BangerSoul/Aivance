package com.bangersoul.aivance.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bangersoul.aivance.core.database.AivanceDatabase
import com.bangersoul.aivance.core.database.converter.EncryptedTypeConverters
import com.bangersoul.aivance.core.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executors
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        encryptedTypeConverters: EncryptedTypeConverters
    ): AivanceDatabase = Room.databaseBuilder(
        context,
        AivanceDatabase::class.java,
        "aivance-database"
    )
        .addTypeConverter(encryptedTypeConverters)
        .addMigrations(
            AivanceDatabase.MIGRATION_1_2,
            AivanceDatabase.MIGRATION_2_3,
            AivanceDatabase.MIGRATION_3_4,
            AivanceDatabase.MIGRATION_4_5,
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
            AivanceDatabase.MIGRATION_23_24,
            AivanceDatabase.MIGRATION_24_25,
            AivanceDatabase.MIGRATION_25_26,
            AivanceDatabase.MIGRATION_26_27
        )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .setQueryExecutor(Executors.newFixedThreadPool(4))
        .build()

    @Provides
    fun provideAivanceDao(database: AivanceDatabase): AivanceDao = database.aivanceDao()

    @Provides
    fun provideTrackerDao(database: AivanceDatabase): TrackerDao = database.trackerDao()

    @Provides
    fun provideJobDao(database: AivanceDatabase): JobDao = database.jobDao()

    @Provides
    fun provideCompanyDao(database: AivanceDatabase): CompanyDao = database.companyDao()

    @Provides
    fun provideAtsDao(database: AivanceDatabase): AtsDao = database.atsDao()

    @Provides
    fun provideCoverLetterDao(database: AivanceDatabase): CoverLetterDao = database.coverLetterDao()

    @Provides
    fun provideRoadmapDao(database: AivanceDatabase): RoadmapDao = database.roadmapDao()

    @Provides
    fun provideResumeDao(database: AivanceDatabase): ResumeDao = database.resumeDao()

    @Provides
    fun provideAiAnalyticsDao(database: AivanceDatabase): AiAnalyticsDao = database.aiAnalyticsDao()

    @Provides
    fun provideProfileDao(database: AivanceDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideInterviewDao(database: AivanceDatabase): InterviewDao = database.interviewDao()

    @Provides
    fun provideSearchDao(database: AivanceDatabase): SearchDao = database.searchDao()

    @Provides
    fun provideRecruiterDao(database: AivanceDatabase): RecruiterDao = database.recruiterDao()

    @Provides
    fun provideWorkflowDao(database: AivanceDatabase): WorkflowDao = database.workflowDao()

    @Provides
    fun provideAnalyticsDao(database: AivanceDatabase): AnalyticsDao = database.analyticsDao()

    @Provides
    fun provideAssistantDao(database: AivanceDatabase): AssistantDao = database.assistantDao()

    @Provides
    fun provideAuditDao(database: AivanceDatabase): AuditDao = database.auditDao()

    @Provides
    fun provideUserDao(database: AivanceDatabase): UserDao = database.userDao()

    @Provides
    fun provideGraphDao(database: AivanceDatabase): GraphDao = database.graphDao()

    @Provides
    fun provideCareerEventLogDao(database: AivanceDatabase): CareerEventLogDao =
        database.careerEventLogDao()

    @Provides
    fun provideCareerMemoryDao(database: AivanceDatabase): CareerMemoryDao =
        database.careerMemoryDao()
}
