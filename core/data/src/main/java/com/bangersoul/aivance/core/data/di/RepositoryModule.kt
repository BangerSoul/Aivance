package com.bangersoul.aivance.core.data.di

import com.bangersoul.aivance.core.data.repository.*
import com.bangersoul.aivance.core.data.repository.crm.*
import com.bangersoul.aivance.core.domain.repository.*
import com.bangersoul.aivance.core.domain.repository.crm.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAiRepository(impl: AiRepositoryImpl): AiRepository

    @Binds
    @Singleton
    abstract fun bindAnalyticsRepository(impl: AnalyticsRepositoryImpl): AnalyticsRepository

    @Binds
    @Singleton
    abstract fun bindCoverLetterRepository(impl: CoverLetterRepositoryImpl): CoverLetterRepository

    @Binds
    @Singleton
    abstract fun bindInterviewRepository(impl: InterviewRepositoryImpl): InterviewRepository

    @Binds
    @Singleton
    abstract fun bindInterviewKnowledgeRepository(impl: InterviewKnowledgeRepositoryImpl): InterviewKnowledgeRepository

    @Binds
    @Singleton
    abstract fun bindJobRepository(impl: JobRepositoryImpl): JobRepository

    @Binds
    @Singleton
    abstract fun bindResumeRepository(impl: ResumeRepositoryImpl): ResumeRepository

    @Binds
    @Singleton
    abstract fun bindProviderRepository(impl: ProviderRepositoryImpl): ProviderRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindAtsRepository(impl: AtsRepositoryImpl): AtsRepository

    @Binds
    @Singleton
    abstract fun bindAssistantRepository(impl: AssistantRepositoryImpl): AssistantRepository

    @Binds
    @Singleton
    abstract fun bindApplicationWorkflowRepository(impl: ApplicationWorkflowRepositoryImpl): ApplicationWorkflowRepository

    // V2 Career Knowledge OS foundation (Room v26)
    @Binds
    @Singleton
    abstract fun bindCareerGraphRepository(impl: CareerGraphRepositoryImpl): CareerGraphRepository

    @Binds
    @Singleton
    abstract fun bindCareerMemoryRepository(impl: CareerMemoryRepositoryImpl): CareerMemoryRepository

    @Binds
    @Singleton
    abstract fun bindCareerEventLogRepository(impl: CareerEventLogRepositoryImpl): CareerEventLogRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindSkillGapProgressRepository(impl: SkillGapProgressRepositoryImpl): SkillGapProgressRepository

    // CRM / Networking Repositories
    @Binds
    @Singleton
    abstract fun bindCompanyIntelligenceRepository(impl: CompanyIntelligenceRepositoryImpl): CompanyIntelligenceRepository

    @Binds
    @Singleton
    abstract fun bindCompanyCatalogRepository(impl: CompanyCatalogRepositoryImpl): CompanyCatalogRepository

    @Binds
    @Singleton
    abstract fun bindApplicationPreferencesRepository(impl: ApplicationPreferencesRepositoryImpl): ApplicationPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindRecruiterIntelligenceRepository(impl: RecruiterIntelligenceRepositoryImpl): RecruiterIntelligenceRepository

    @Binds
    @Singleton
    abstract fun bindOutreachRepository(impl: OutreachRepositoryImpl): OutreachRepository

    @Binds
    @Singleton
    abstract fun bindCRMRepository(impl: CRMRepositoryImpl): CRMRepository
}
