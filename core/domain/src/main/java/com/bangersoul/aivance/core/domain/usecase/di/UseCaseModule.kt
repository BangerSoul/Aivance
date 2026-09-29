package com.bangersoul.aivance.core.domain.usecase.di

import com.bangersoul.aivance.core.domain.repository.*
import com.bangersoul.aivance.core.domain.repository.crm.*
import com.bangersoul.aivance.core.domain.usecase.ai.*
import com.bangersoul.aivance.core.domain.usecase.analytics.*
import com.bangersoul.aivance.core.domain.usecase.ats.*
import com.bangersoul.aivance.core.domain.usecase.career.*
import com.bangersoul.aivance.core.domain.usecase.coverletter.*
import com.bangersoul.aivance.core.domain.usecase.crm.*
import com.bangersoul.aivance.core.domain.usecase.interview.*
import com.bangersoul.aivance.core.domain.usecase.job.*
import com.bangersoul.aivance.core.domain.usecase.provider.*
import com.bangersoul.aivance.core.domain.usecase.resume.*
import com.bangersoul.aivance.core.domain.usecase.settings.*
import com.bangersoul.aivance.core.domain.usecase.user.*
import com.bangersoul.aivance.core.domain.usecase.workflow.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides @Singleton
    fun provideImportResumeUseCase(repo: ResumeRepository): ImportResumeUseCase = ImportResumeUseCase(repo)

    @Provides @Singleton
    fun provideParseResumeUseCase(repo: ResumeRepository): ParseResumeUseCase = ParseResumeUseCase(repo)

    @Provides @Singleton
    fun provideAnalyseResumeUseCase(repo: ResumeRepository): AnalyseResumeUseCase = AnalyseResumeUseCase(repo)

    @Provides @Singleton
    fun provideCalculateATSScoreUseCase(repo: ResumeRepository): CalculateATSScoreUseCase = CalculateATSScoreUseCase(repo)

    @Provides @Singleton
    fun provideImproveResumeUseCase(repo: ResumeRepository, aiRepo: AiRepository): ImproveResumeUseCase = ImproveResumeUseCase(repo, aiRepo)

    @Provides @Singleton
    fun provideStreamImproveSectionUseCase(repo: ResumeRepository, aiRepo: AiRepository): StreamImproveSectionUseCase = StreamImproveSectionUseCase(repo, aiRepo)

    @Provides @Singleton
    fun provideGenerateResumeSummaryUseCase(repo: ResumeRepository): GenerateResumeSummaryUseCase = GenerateResumeSummaryUseCase(repo)

    @Provides @Singleton
    fun provideExportResumeUseCase(repo: ResumeRepository): ExportResumeUseCase = ExportResumeUseCase(repo)

    @Provides @Singleton
    fun provideStreamAtsAnalysisUseCase(repo: AtsRepository): StreamAtsAnalysisUseCase = StreamAtsAnalysisUseCase(repo)

    @Provides @Singleton
    fun provideSearchJobsUseCase(repo: JobRepository): SearchJobsUseCase = SearchJobsUseCase(repo)

    @Provides @Singleton
    fun provideGetJobDetailsUseCase(repo: JobRepository): GetJobDetailsUseCase = GetJobDetailsUseCase(repo)

    @Provides @Singleton
    fun provideToggleJobBookmarkUseCase(repo: JobRepository): ToggleJobBookmarkUseCase = ToggleJobBookmarkUseCase(repo)

    @Provides @Singleton
    fun provideGetCachedJobsUseCase(repo: JobRepository): GetCachedJobsUseCase = GetCachedJobsUseCase(repo)

    @Provides @Singleton
    fun provideGetAiProviderAvailabilityUseCase(
        providerManager: com.bangersoul.aivance.sdk.infrastructure.ProviderManager,
        providerRegistry: com.bangersoul.aivance.sdk.infrastructure.ProviderRegistry
    ): GetAiProviderAvailabilityUseCase = GetAiProviderAvailabilityUseCase(providerManager, providerRegistry)

    @Provides @Singleton
    fun provideSendMessageUseCase(repo: AiRepository): SendMessageUseCase = SendMessageUseCase(repo)

    @Provides @Singleton
    fun provideStartInterviewSessionUseCase(repo: InterviewRepository): StartInterviewSessionUseCase = StartInterviewSessionUseCase(repo)

    @Provides @Singleton
    fun provideLoadProfileUseCase(repo: UserRepository): LoadProfileUseCase = LoadProfileUseCase(repo)

    @Provides @Singleton
    fun provideUpdateProfileUseCase(repo: UserRepository): UpdateProfileUseCase = UpdateProfileUseCase(repo)

    @Provides @Singleton
    fun provideTrackEventUseCase(engine: com.bangersoul.aivance.core.domain.analytics.AnalyticsEngine): TrackEventUseCase = TrackEventUseCase(engine)

    @Provides @Singleton
    fun provideGenerateUsageReportUseCase(repo: AnalyticsRepository): GenerateUsageReportUseCase = GenerateUsageReportUseCase(repo)

    @Provides @Singleton
    fun provideExportAnalyticsUseCase(repo: AnalyticsRepository): ExportAnalyticsUseCase = ExportAnalyticsUseCase(repo)

    // CRM Use Cases
    @Provides @Singleton
    fun provideFindRecruitersUseCase(repo: RecruiterIntelligenceRepository): FindRecruitersUseCase = FindRecruitersUseCase(repo)

    @Provides @Singleton
    fun provideGenerateOutreachDraftUseCase(repo: OutreachRepository): GenerateOutreachDraftUseCase = GenerateOutreachDraftUseCase(repo)

    // Cover Letter Use Cases
    @Provides @Singleton
    fun provideGenerateCoverLetterUseCase(repo: CoverLetterRepository): GenerateCoverLetterUseCase = GenerateCoverLetterUseCase(repo)

    @Provides @Singleton
    fun provideStreamGenerateCoverLetterUseCase(repo: CoverLetterRepository): StreamGenerateCoverLetterUseCase = StreamGenerateCoverLetterUseCase(repo)

    @Provides @Singleton
    fun provideRegenerateCoverLetterSectionUseCase(repo: CoverLetterRepository): RegenerateCoverLetterSectionUseCase = RegenerateCoverLetterSectionUseCase(repo)

    // Workflow Use Cases
    @Provides @Singleton
    fun provideTaskGeneratorUseCase(repo: ApplicationWorkflowRepository): TaskGeneratorUseCase = TaskGeneratorUseCase(repo)
}
