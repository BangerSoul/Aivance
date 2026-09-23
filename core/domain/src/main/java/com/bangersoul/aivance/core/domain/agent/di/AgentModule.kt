package com.bangersoul.aivance.core.domain.agent.di

import com.bangersoul.aivance.core.domain.agent.AgentActionExecutor
import com.bangersoul.aivance.core.domain.agent.DefaultAgentActionExecutor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {

    @Binds
    @Singleton
    abstract fun bindAgentActionExecutor(
        impl: DefaultAgentActionExecutor
    ): AgentActionExecutor
}
