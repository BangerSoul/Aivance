package com.bangersoul.aivance.core.domain.engine.di

import com.bangersoul.aivance.core.domain.analytics.RecommendationEngine
import com.bangersoul.aivance.core.domain.engine.*
import com.bangersoul.aivance.core.domain.repository.*
import com.bangersoul.aivance.core.domain.workflow.WorkflowEngine
import com.bangersoul.aivance.sdk.infrastructure.ProviderManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideCareerStateEngine(
        userRepository: UserRepository,
        resumeRepository: ResumeRepository,
        workflowRepository: ApplicationWorkflowRepository,
        analyticsRepository: AnalyticsRepository,
        jobRepository: JobRepository,
        interviewRepository: InterviewRepository,
        providerManager: ProviderManager,
        careerEventBus: com.bangersoul.aivance.core.common.events.CareerEventBus,
        careerGraphEngine: com.bangersoul.aivance.core.domain.careergraph.CareerGraphEngine
    ): CareerStateEngine = CareerStateEngine(
        userRepository,
        resumeRepository,
        workflowRepository,
        analyticsRepository,
        jobRepository,
        interviewRepository,
        providerManager,
        careerEventBus,
        careerGraphEngine
    )

    @Provides
    @Singleton
    fun provideContextEngine(
        resumeRepository: ResumeRepository,
        jobRepository: JobRepository
    ): ContextEngine = ContextEngine(resumeRepository, jobRepository)

    @Provides
    @Singleton
    fun provideIntentEngine(): IntentEngine = IntentEngine()

    @Provides
    @Singleton
    fun providePromptOrchestrator(
        contextEngine: ContextEngine
    ): PromptOrchestrator = PromptOrchestrator(contextEngine)

    @Provides
    @Singleton
    fun provideNavigationWorkflowEngine(): NavigationWorkflowEngine = NavigationWorkflowEngine()

    @Provides
    @Singleton
    fun provideWorkflowEngine(
        repository: ApplicationWorkflowRepository,
        analyticsRepository: AnalyticsRepository,
        taskGenerator: com.bangersoul.aivance.core.domain.usecase.workflow.TaskGeneratorUseCase,
        careerEventDispatcher: com.bangersoul.aivance.core.domain.events.CareerEventDispatcher,
        notificationRepository: NotificationRepository
    ): WorkflowEngine = WorkflowEngine(
        repository, analyticsRepository, taskGenerator, careerEventDispatcher, notificationRepository
    )

    @Provides
    @Singleton
    fun provideRecommendationEngine(
        providerManager: ProviderManager
    ): RecommendationEngine = RecommendationEngine(providerManager)
}
