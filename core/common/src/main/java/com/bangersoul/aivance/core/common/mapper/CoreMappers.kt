package com.bangersoul.aivance.core.common.mapper

import com.bangersoul.aivance.core.common.dto.*
import com.bangersoul.aivance.core.common.enums.*
import com.bangersoul.aivance.core.common.model.*

// CoverLetter
fun CoverLetterDto.toDomain(id: Long = 0): CoverLetter = CoverLetter(
    id = id,
    company = company,
    role = role,
    resumeVersionId = null,
    jobId = null,
    recruiterId = null,
    dateCreated = System.currentTimeMillis()
)

fun CoverLetter.toDto(): CoverLetterDto = CoverLetterDto(
    company = company,
    role = role,
    content = "",
    tone = "PROFESSIONAL"
)

// InterviewFeedback
fun InterviewFeedbackDto.toDomain(): InterviewFeedback = InterviewFeedback(
    overallScore = overallScore,
    strengths = strengths,
    improvements = improvements,
    detailedSummary = detailedSummary
)

fun InterviewFeedback.toDto(): InterviewFeedbackDto = InterviewFeedbackDto(
    overallScore = overallScore,
    strengths = strengths,
    improvements = improvements,
    detailedSummary = detailedSummary
)

// ApifyJobScraperDto
fun ApifyJobScraperDto.toDomain(): JobListing = JobListing(
    id = id,
    title = positionName,
    company = companyName,
    location = location,
    salaryMin = null,
    salaryMax = null,
    employmentType = EmploymentType.FULL_TIME,
    isRemote = location.lowercase().contains("remote"),
    description = description,
    url = url,
    sourceProvider = "APIFY",
    postedDate = System.currentTimeMillis()
)

// AIMessage
fun AiMessageDto.toDomain(): AIMessage = AIMessage(
    id = id,
    conversationId = conversationId,
    role = try { MessageRole.valueOf(role.uppercase()) } catch (e: Exception) { MessageRole.USER },
    content = content,
    timestamp = timestamp,
    tokenCount = tokenCount
)

fun AIMessage.toDto(): AiMessageDto = AiMessageDto(
    id = id,
    conversationId = conversationId,
    role = role.name,
    content = content,
    timestamp = timestamp,
    tokenCount = tokenCount
)

// UserProfile
fun UserProfileDto.toDomain(): UserProfile = UserProfile(
    id = id,
    fullName = fullName,
    email = email,
    phone = phone,
    targetRole = targetRole,
    bio = bio,
    location = location,
    skills = skills,
    experienceYears = experienceYears,
    createdDate = createdDate
)

fun UserProfile.toDto(): UserProfileDto = UserProfileDto(
    id = id,
    fullName = fullName,
    email = email,
    phone = phone,
    targetRole = targetRole,
    bio = bio,
    location = location,
    skills = skills,
    experienceYears = experienceYears,
    createdDate = createdDate
)

// ProviderInfo
fun ProviderInfoDto.toDomain(): ProviderInfo = ProviderInfo(
    id = id,
    name = name,
    type = try { ProviderType.valueOf(type.uppercase()) } catch (e: Exception) { ProviderType.AI },
    state = try { ProviderState.valueOf(state.uppercase()) } catch (e: Exception) { ProviderState.UNCONFIGURED },
    supportedModels = supportedModels.mapNotNull {
        try { AIModel.valueOf(it.uppercase()) } catch (e: Exception) { null }
    }
)

fun ProviderInfo.toDto(): ProviderInfoDto = ProviderInfoDto(
    id = id,
    name = name,
    type = type.name,
    state = state.name,
    supportedModels = supportedModels.map { it.name }
)

// AnalyticsEvent
fun AnalyticsEventDto.toDomain(): AnalyticsEvent = AnalyticsEvent(
    id = id,
    eventName = eventName,
    timestamp = timestamp,
    properties = properties,
    category = category
)

fun AnalyticsEvent.toDto(): AnalyticsEventDto = AnalyticsEventDto(
    id = id,
    eventName = eventName,
    timestamp = timestamp,
    properties = properties,
    category = category
)

// JobListing
fun JobListingDto.toDomain(): JobListing = JobListing(
    id = id,
    title = title,
    company = company,
    location = location,
    salaryMin = salaryMin,
    salaryMax = salaryMax,
    employmentType = try { EmploymentType.valueOf(employmentType.uppercase()) } catch (e: Exception) { EmploymentType.FULL_TIME },
    isRemote = isRemote,
    description = description,
    url = url,
    sourceProvider = sourceProvider,
    postedDate = postedDate,
    matchScore = matchScore
)

fun JobListing.toDto(): JobListingDto = JobListingDto(
    id = id,
    title = title,
    company = company,
    location = location,
    salaryMin = salaryMin,
    salaryMax = salaryMax,
    employmentType = employmentType.name,
    isRemote = isRemote,
    description = description,
    url = url,
    sourceProvider = sourceProvider,
    postedDate = postedDate,
    matchScore = matchScore
)

// Settings
fun SettingsDto.toDomain(): AiProviderConfig = AiProviderConfig(
    providerId = providerId,
    apiKey = apiKey,
    selectedModel = selectedModel,
    temperature = temperature,
    maxTokens = maxTokens,
    customBaseUrl = customBaseUrl,
    isEnabled = isEnabled
)

fun AiProviderConfig.toDto(): SettingsDto = SettingsDto(
    providerId = providerId,
    apiKey = apiKey,
    selectedModel = selectedModel,
    temperature = temperature,
    maxTokens = maxTokens,
    customBaseUrl = customBaseUrl,
    isEnabled = isEnabled
)
