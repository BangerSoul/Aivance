package com.bangersoul.aivance.core.data.mapper

import com.bangersoul.aivance.core.common.enums.*
import com.bangersoul.aivance.core.common.model.*
import com.bangersoul.aivance.core.common.security.EncryptedString
import com.bangersoul.aivance.core.database.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.time.Instant

/**
 * Mappers to convert Room Entities to Domain Models and vice versa.
 */

private val jsonMapper = Json { ignoreUnknownKeys = true }

fun ResumeEntity.toDomain(versions: List<ResumeVersion> = emptyList()): Resume {
    return Resume(
        id = id,
        name = name,
        primaryVersionId = primaryVersionId,
        fileName = fileName,
        fileUri = originalFileUri,
        rawText = rawText?.value,
        dateCreated = dateCreated,
        lastModified = lastModified,
        versions = versions
    )
}

fun ResumeVersionEntity.toDomain(sections: List<ResumeSectionEntity> = emptyList()): ResumeVersion {
    return ResumeVersion(
        id = id,
        resumeId = resumeId,
        versionName = versionName,
        templateId = templateId,
        lastModified = lastModified,
        sections = sections.map { it.toDomain() }
    )
}

fun ResumeSectionEntity.toDomain(): ResumeSection {
    return ResumeSection(
        id = id,
        versionId = versionId,
        sectionType = sectionType,
        title = title,
        content = content,
        sectionOrder = sectionOrder
    )
}

fun Resume.toEntity(): ResumeEntity {
    return ResumeEntity(
        id = id,
        name = name,
        primaryVersionId = primaryVersionId,
        fileName = fileName,
        originalFileUri = fileUri,
        rawText = rawText?.let { EncryptedString(it) },
        dateCreated = dateCreated,
        lastModified = lastModified
    )
}

fun ResumeVersion.toEntity(): ResumeVersionEntity {
    return ResumeVersionEntity(
        id = id,
        resumeId = resumeId,
        versionName = versionName,
        templateId = templateId,
        lastModified = lastModified
    )
}

fun ResumeSection.toEntity(versionId: Long): ResumeSectionEntity {
    return ResumeSectionEntity(
        id = id,
        versionId = versionId,
        title = title,
        content = content,
        sectionOrder = sectionOrder,
        sectionType = sectionType
    )
}

// ATS Mappers
fun AtsReportEntity.toDomain(): AtsReport {
    return AtsReport(
        id = id,
        resumeVersionId = resumeVersionId,
        jobDescriptionId = jobDescriptionId,
        overallScore = overallScore,
        matchPercentage = matchPercentage,
        matchedKeywords = matchedKeywords.split(",").filter { it.isNotBlank() },
        missingKeywords = missingKeywords.split(",").filter { it.isNotBlank() },
        sectionScores = try { jsonMapper.decodeFromString<Map<String, Int>>(sectionScores) } catch (e: Exception) { emptyMap() },
        optimizationTips = try { jsonMapper.decodeFromString<List<OptimizationTip>>(optimizationTips) } catch (e: Exception) { emptyList() },
        dateGenerated = dateGenerated
    )
}

fun AtsReport.toEntity(): AtsReportEntity {
    return AtsReportEntity(
        id = id,
        resumeVersionId = resumeVersionId,
        jobDescriptionId = jobDescriptionId,
        overallScore = overallScore,
        matchPercentage = matchPercentage,
        matchedKeywords = matchedKeywords.joinToString(","),
        missingKeywords = missingKeywords.joinToString(","),
        sectionScores = jsonMapper.encodeToString(sectionScores),
        optimizationTips = jsonMapper.encodeToString(optimizationTips),
        dateGenerated = dateGenerated
    )
}

fun JobDescriptionEntity.toDomain(): JobDescription {
    return JobDescription(
        id = id,
        companyName = companyName,
        jobTitle = jobTitle,
        rawText = rawText,
        extractedSkills = extractedSkills?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
        dateCreated = dateCreated
    )
}

fun JobDescription.toEntity(): JobDescriptionEntity {
    return JobDescriptionEntity(
        id = id,
        companyName = companyName,
        jobTitle = jobTitle,
        rawText = rawText,
        sourceUrl = null,
        extractedSkills = extractedSkills.joinToString(","),
        dateCreated = dateCreated
    )
}

// CRM Mappers
fun CompanyEntity.toDomain(): Company {
    return Company(
        id = id.toString(),
        name = name,
        domain = domain,
        logoUrl = logoUrl,
        websiteUrl = website,
        industry = industry ?: "",
        headquarters = headquarters,
        socialLinks = socialLinks ?: emptyMap()
    )
}

fun Company.toEntity(): CompanyEntity {
    return CompanyEntity(
        id = id.toLongOrNull() ?: 0L,
        name = name,
        domain = domain,
        logoUrl = logoUrl,
        website = websiteUrl,
        industry = industry,
        headquarters = headquarters,
        socialLinks = socialLinks
    )
}

fun RecruiterEntity.toDomain(contacts: List<RecruiterContactEntity> = emptyList()): Recruiter {
    return Recruiter(
        id = id,
        name = name,
        companyId = companyId.toString(),
        title = title,
        department = department,
        linkedinUrl = linkedinUrl,
        contacts = contacts.map { it.toDomain() }
    )
}

fun Recruiter.toEntity(): RecruiterEntity {
    return RecruiterEntity(
        id = id,
        companyId = companyId.toLongOrNull() ?: 0L,
        name = name,
        title = title,
        department = department,
        linkedinUrl = linkedinUrl,
        sourceProvider = null
    )
}

fun RecruiterContactEntity.toDomain(): RecruiterContact {
    return RecruiterContact(
        id = id,
        recruiterId = recruiterId,
        email = email.value,
        confidence = confidence,
        isVerified = isVerified,
        lastUpdated = lastUpdated
    )
}

fun RecruiterContact.toEntity(): RecruiterContactEntity {
    return RecruiterContactEntity(
        id = id,
        recruiterId = recruiterId,
        email = EncryptedString(email),
        confidence = confidence,
        isVerified = isVerified,
        lastUpdated = lastUpdated
    )
}

fun OutreachDraftEntity.toDomain(): OutreachDraft {
    return OutreachDraft(
        id = id,
        recruiterId = recruiterId,
        jobId = jobId?.toString() ?: "",
        type = type,
        content = content,
        dateCreated = dateCreated
    )
}

fun OutreachDraft.toEntity(): OutreachDraftEntity {
    return OutreachDraftEntity(
        id = id,
        recruiterId = recruiterId,
        jobId = jobId.toLongOrNull(),
        type = type,
        content = content,
        dateCreated = dateCreated
    )
}

fun CommunicationHistoryEntity.toDomain(): CommunicationHistory {
    return CommunicationHistory(
        id = id,
        recruiterId = recruiterId,
        messageType = messageType,
        content = content,
        sentDate = sentDate,
        status = status,
        notes = notes
    )
}

fun CommunicationHistory.toEntity(): CommunicationHistoryEntity {
    return CommunicationHistoryEntity(
        id = id,
        recruiterId = recruiterId,
        messageType = messageType,
        content = content,
        sentDate = sentDate,
        status = status,
        notes = notes,
        nextFollowUpDate = null
    )
}

// Cover Letter Mappers
fun CoverLetterEntity.toDomain(versions: List<CoverLetterVersion> = emptyList()): CoverLetter {
    return CoverLetter(
        id = id,
        resumeVersionId = resumeVersionId,
        jobId = jobId,
        recruiterId = recruiterId,
        primaryVersionId = primaryVersionId,
        company = company,
        role = role,
        dateCreated = dateCreated,
        versions = versions
    )
}

fun CoverLetterVersionEntity.toDomain(sections: List<CoverLetterSectionEntity> = emptyList()): CoverLetterVersion {
    return CoverLetterVersion(
        id = id,
        coverLetterId = coverLetterId,
        versionName = versionName,
        templateId = templateId,
        writingStyle = writingStyle,
        state = state,
        lastModified = lastModified,
        sections = sections.map { it.toDomain() }
    )
}

fun CoverLetterSectionEntity.toDomain(): CoverLetterSection {
    return CoverLetterSection(
        id = id,
        versionId = versionId,
        sectionType = sectionType,
        title = title,
        content = content,
        sectionOrder = sectionOrder
    )
}

fun CoverLetter.toEntity(): CoverLetterEntity {
    return CoverLetterEntity(
        id = id,
        resumeVersionId = resumeVersionId,
        jobId = jobId,
        recruiterId = recruiterId,
        primaryVersionId = primaryVersionId,
        company = company,
        role = role,
        dateCreated = dateCreated
    )
}

fun CoverLetterVersion.toEntity(): CoverLetterVersionEntity {
    return CoverLetterVersionEntity(
        id = id,
        coverLetterId = coverLetterId,
        versionName = versionName,
        templateId = templateId,
        writingStyle = writingStyle,
        state = state,
        lastModified = lastModified
    )
}

fun CoverLetterSection.toEntity(versionId: Long): CoverLetterSectionEntity {
    return CoverLetterSectionEntity(
        id = id,
        versionId = versionId,
        sectionType = sectionType,
        title = title,
        content = content,
        sectionOrder = sectionOrder
    )
}

// Interview Mappers
fun InterviewSessionEntity.toDomain(
    messages: List<InterviewMessage> = emptyList(),
    questions: List<InterviewQuestion> = emptyList()
): InterviewSession {
    return InterviewSession(
        id = id.toString(),
        resumeVersionId = resumeVersionId,
        jobId = jobId,
        targetRole = targetRole,
        type = type,
        companyName = "",
        difficulty = try { InterviewDifficulty.valueOf(difficulty) } catch (e: Exception) { InterviewDifficulty.MEDIUM },
        startTime = dateStarted.toEpochMilli(),
        isCompleted = isCompleted,
        messages = messages,
        questions = questions,
        feedback = overallFeedback
    )
}

fun InterviewSession.toEntity(): InterviewSessionEntity {
    return InterviewSessionEntity(
        id = id.toLongOrNull() ?: 0L,
        resumeVersionId = resumeVersionId,
        jobId = jobId,
        targetRole = targetRole,
        type = type,
        difficulty = difficulty.name,
        dateStarted = Instant.ofEpochMilli(startTime),
        isCompleted = isCompleted,
        overallFeedback = feedback
    )
}

fun InterviewQuestionEntity.toDomain(): InterviewQuestion {
    return InterviewQuestion(
        id = id,
        text = text,
        category = category,
        difficulty = difficulty,
        expectedKeyPoints = expectedKeyPoints?.split(",") ?: emptyList(),
        idealAnswer = idealAnswer,
        isFavorite = isFavorite
    )
}

fun InterviewQuestion.toEntity(sessionId: Long?): InterviewQuestionEntity {
    return InterviewQuestionEntity(
        id = id,
        sessionId = sessionId,
        text = text,
        category = category,
        difficulty = difficulty,
        expectedKeyPoints = expectedKeyPoints.joinToString(","),
        idealAnswer = idealAnswer,
        isFavorite = isFavorite
    )
}

fun InterviewMessageEntity.toDomain(evaluation: InterviewEvaluation? = null): InterviewMessage {
    return InterviewMessage(
        id = id.toString(),
        sessionId = sessionId.toString(),
        sender = try { MessageSender.valueOf(role) } catch (e: Exception) { MessageSender.USER },
        text = text,
        timestamp = timestamp.toEpochMilli(),
        evaluation = evaluation
    )
}

fun InterviewMessage.toEntity(): InterviewMessageEntity {
    return InterviewMessageEntity(
        id = id.toLongOrNull() ?: 0L,
        sessionId = sessionId.toLongOrNull() ?: 0L,
        role = sender.name,
        text = text,
        timestamp = Instant.ofEpochMilli(timestamp)
    )
}

fun InterviewEvaluationEntity.toDomain(): InterviewEvaluation {
    return InterviewEvaluation(
        id = id,
        messageId = messageId.toString(),
        scoreClarity = scoreClarity,
        scoreAccuracy = scoreAccuracy,
        scoreTone = scoreTone,
        starMethodScore = starMethodScore,
        feedback = feedback,
        improvementTips = improvementTips?.split("\n") ?: emptyList()
    )
}

fun InterviewEvaluation.toEntity(): InterviewEvaluationEntity {
    return InterviewEvaluationEntity(
        id = id,
        messageId = messageId.toLongOrNull() ?: 0L,
        scoreClarity = scoreClarity,
        scoreAccuracy = scoreAccuracy,
        scoreTone = scoreTone,
        starMethodScore = starMethodScore,
        feedback = feedback,
        improvementTips = improvementTips.joinToString("\n")
    )
}

// Workflow Mappers
fun ApplicationEntity.toDomain(
    job: JobListing? = null,
    timeline: List<TimelineEvent> = emptyList(),
    tasks: List<ApplicationTask> = emptyList()
): Application {
    return Application(
        id = id,
        jobId = jobId,
        resumeVersionId = resumeVersionId,
        atsReportId = atsReportId,
        coverLetterVersionId = coverLetterVersionId,
        currentStageId = currentStageId,
        status = status,
        dateApplied = dateApplied,
        lastModified = lastModified,
        notes = notes,
        job = job,
        timeline = timeline,
        tasks = tasks
    )
}

fun Application.toEntity(): ApplicationEntity {
    return ApplicationEntity(
        id = id,
        jobId = jobId,
        resumeVersionId = resumeVersionId,
        atsReportId = atsReportId,
        coverLetterVersionId = coverLetterVersionId,
        currentStageId = currentStageId,
        status = status,
        dateApplied = dateApplied,
        lastModified = lastModified,
        notes = notes
    )
}

fun ApplicationStageEntity.toDomain(): ApplicationStage {
    return ApplicationStage(
        id = id,
        label = label,
        order = order,
        isSystemStage = isSystemStage
    )
}

fun ApplicationStage.toEntity(): ApplicationStageEntity {
    return ApplicationStageEntity(
        id = id,
        label = label,
        order = order,
        isSystemStage = isSystemStage
    )
}

fun ApplicationTimelineEntity.toDomain(): TimelineEvent {
    return TimelineEvent(
        id = id,
        applicationId = applicationId,
        eventType = eventType,
        title = title,
        description = description,
        timestamp = timestamp,
        metadata = try { jsonMapper.decodeFromString(metadataJson ?: "{}") } catch (e: Exception) { emptyMap() }
    )
}

fun TimelineEvent.toEntity(): ApplicationTimelineEntity {
    return ApplicationTimelineEntity(
        id = id,
        applicationId = applicationId,
        eventType = eventType,
        title = title,
        description = description,
        timestamp = timestamp,
        metadataJson = jsonMapper.encodeToString(metadata)
    )
}

fun TaskEntity.toDomain(): ApplicationTask {
    return ApplicationTask(
        id = id,
        applicationId = applicationId,
        title = title,
        description = description,
        priority = priority,
        dueDate = dueDate,
        isCompleted = isCompleted,
        completedAt = completedAt
    )
}

fun ApplicationTask.toEntity(): TaskEntity {
    return TaskEntity(
        id = id,
        applicationId = applicationId,
        title = title,
        description = description,
        priority = priority,
        dueDate = dueDate,
        isCompleted = isCompleted,
        completedAt = completedAt,
        createdAt = System.currentTimeMillis()
    )
}

// Legacy / Helper Mappers


fun JobWithDetails.toDomain(): JobListing {
    return JobListing(
        id = job.id.toString(),
        title = job.title,
        company = company.name,
        companyLogoUrl = company.logoUrl,
        location = job.location ?: "",
        salaryMin = job.salaryMin,
        salaryMax = job.salaryMax,
        currency = job.currency,
        employmentType = try { EmploymentType.valueOf(job.type ?: "FULL_TIME") } catch (_: Exception) { EmploymentType.FULL_TIME },
        remoteType = try { RemoteType.valueOf(job.remoteType ?: "ON_SITE") } catch (_: Exception) { RemoteType.ON_SITE },
        experienceLevel = try { ExperienceLevel.valueOf(job.experienceLevel ?: "NOT_SPECIFIED") } catch (_: Exception) { ExperienceLevel.NOT_SPECIFIED },
        description = job.description ?: "",
        descriptionHtml = job.descriptionHtml,
        url = job.url,
        sourceProvider = job.sourceProviderId,
        postedDate = job.postedDate
    )
}

fun JobListing.toEntity(companyId: Long): JobEntity {
    return JobEntity(
        id = id.toLongOrNull() ?: 0L,
        companyId = companyId,
        title = title,
        location = location,
        type = employmentType.name,
        remoteType = remoteType.name,
        experienceLevel = experienceLevel.name,
        salaryMin = salaryMin,
        salaryMax = salaryMax,
        currency = currency,
        description = description,
        descriptionHtml = descriptionHtml,
        url = url,
        sourceProviderId = sourceProvider,
        postedDate = postedDate
    )
}

fun UserProfileEntity.toDomain(): UserProfile {
    return UserProfile(
        id = id,
        fullName = name,
        email = email,
        phone = phone ?: "",
        targetRole = targetRole ?: "",
        currentRole = currentRole ?: "",
        company = company ?: "",
        linkedinUrl = linkedinUrl ?: "",
        githubUrl = githubUrl ?: "",
        dateOfBirth = dateOfBirth,
        profilePictureUrl = profilePictureUrl,
        bio = bio ?: "",
        location = location ?: "",
        skills = skills,
        experienceYears = experienceYears,
        preferredIndustries = preferredIndustries,
        salaryExpectation = salaryExpectation ?: "",
        workPreference = workPreference ?: "REMOTE",
        visaRequired = visaRequired,
        noticePeriod = noticePeriod ?: "",
        createdDate = createdDate
    )
}

fun UserProfile.toEntity(): UserProfileEntity {
    return UserProfileEntity(
        id = id,
        name = fullName,
        email = email,
        phone = phone,
        currentRole = currentRole,
        skills = skills,
        targetRole = targetRole,
        bio = bio,
        location = location,
        experienceYears = experienceYears,
        profilePictureUrl = profilePictureUrl,
        company = company,
        linkedinUrl = linkedinUrl,
        githubUrl = githubUrl,
        dateOfBirth = dateOfBirth,
        preferredIndustries = preferredIndustries,
        salaryExpectation = salaryExpectation,
        workPreference = workPreference,
        visaRequired = visaRequired,
        noticePeriod = noticePeriod,
        createdDate = createdDate
    )
}

fun RoadmapWithSteps.toDomain(): CareerRoadmap {
    return CareerRoadmap(
        id = roadmap.id,
        targetRole = roadmap.targetRole,
        currentLevel = roadmap.currentLevel,
        description = roadmap.description,
        steps = steps.map { it.toDomain() }
    )
}

fun RoadmapStepEntity.toDomain(): RoadmapStep {
    return RoadmapStep(
        id = id,
        roadmapId = roadmapId,
        title = title,
        description = description,
        isCompleted = isCompleted,
        stepOrder = stepOrder
    )
}

fun CareerRoadmap.toEntity(): RoadmapEntity {
    return RoadmapEntity(
        id = id,
        targetRole = targetRole,
        currentLevel = currentLevel,
        description = description,
        dateCreated = System.currentTimeMillis(),
        lastModified = System.currentTimeMillis()
    )
}

fun RoadmapStep.toEntity(roadmapId: Long): RoadmapStepEntity {
    return RoadmapStepEntity(
        id = id,
        roadmapId = roadmapId,
        title = title,
        description = description,
        stepOrder = stepOrder,
        isCompleted = isCompleted
    )
}

fun AIConversationWithMessages.toDomain(): AIConversation {
    return AIConversation(
        id = conversation.id,
        title = conversation.title,
        providerId = "default",
        modelName = "default",
        createdDate = conversation.createdAt.toEpochMilli(),
        lastUpdated = conversation.updatedAt.toEpochMilli(),
        messages = messages.map { it.toDomain() }
    )
}

fun AIMessageEntity.toDomain(): AIMessage {
    return AIMessage(
        id = id,
        conversationId = conversationId,
        role = try { MessageRole.valueOf(role) } catch (e: Exception) { MessageRole.USER },
        content = content,
        timestamp = timestamp.toEpochMilli()
    )
}

fun AIMessage.toEntity(): AIMessageEntity {
    return AIMessageEntity(
        id = id,
        conversationId = conversationId,
        role = role.name,
        content = content,
        timestamp = Instant.ofEpochMilli(timestamp)
    )
}

fun ProviderConfigurationEntity.toDomain(): AiProviderConfig {
    return AiProviderConfig(
        providerId = provider,
        apiKey = "",
        selectedModel = selectedModel ?: AIModel.GEMINI_1_5_FLASH.name,
        temperature = settings["temperature"]?.toFloatOrNull() ?: 0.7f,
        maxTokens = settings["maxTokens"]?.toIntOrNull() ?: 2048,
        customBaseUrl = baseUrl
    )
}

fun AnalyticsEventEntity.toDomain(): AnalyticsEvent {
    return AnalyticsEvent(
        id = id.toString(),
        eventName = eventName,
        timestamp = timestamp.toEpochMilli(),
        properties = params
    )
}

fun AnalyticsEvent.toEntity(): AnalyticsEventEntity {
    return AnalyticsEventEntity(
        eventName = eventName,
        params = properties,
        timestamp = Instant.ofEpochMilli(timestamp)
    )
}

fun InterviewSessionWithMessages.toDomain(): InterviewSession {
    return InterviewSession(
        id = session.id.toString(),
        targetRole = session.targetRole,
        companyName = "",
        difficulty = try { InterviewDifficulty.valueOf(session.difficulty) } catch (e: Exception) { InterviewDifficulty.MEDIUM },
        startTime = session.dateStarted.toEpochMilli(),
        isCompleted = session.isCompleted,
        messages = messages.map { it.toDomain() },
        feedback = session.overallFeedback
    )
}

fun InterviewMessage.toEntity(sessionId: Long): InterviewMessageEntity {
    return InterviewMessageEntity(
        id = id.toLongOrNull() ?: 0L,
        sessionId = sessionId,
        role = sender.name,
        text = text,
        timestamp = Instant.ofEpochMilli(timestamp)
    )
}
