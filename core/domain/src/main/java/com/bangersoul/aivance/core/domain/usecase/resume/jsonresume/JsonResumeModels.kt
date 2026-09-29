package com.bangersoul.aivance.core.domain.usecase.resume.jsonresume

import kotlinx.serialization.Serializable

@Serializable
data class JsonResumeSchema(
    val selectedTemplate: String? = "modern",
    val basics: JsonResumeBasics? = null,
    val work: List<JsonResumeWork>? = emptyList(),
    val education: List<JsonResumeEducation>? = emptyList(),
    val skills: List<JsonResumeSkill>? = emptyList(),
    val projects: List<JsonResumeProject>? = emptyList()
)

@Serializable
data class JsonResumeBasics(
    val name: String? = null,
    val label: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val url: String? = null,
    val summary: String? = null,
    val location: JsonResumeLocation? = null
)

@Serializable
data class JsonResumeLocation(
    val address: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val countryCode: String? = null,
    val region: String? = null
)

@Serializable
data class JsonResumeWork(
    val name: String? = null,
    val position: String? = null,
    val url: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val summary: String? = null,
    val highlights: List<String>? = emptyList()
)

@Serializable
data class JsonResumeEducation(
    val institution: String? = null,
    val url: String? = null,
    val area: String? = null,
    val studyType: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val score: String? = null
)

@Serializable
data class JsonResumeSkill(
    val name: String? = null,
    val level: String? = null,
    val keywords: List<String>? = emptyList()
)

@Serializable
data class JsonResumeProject(
    val name: String? = null,
    val description: String? = null,
    val highlights: List<String>? = emptyList(),
    val keywords: List<String>? = emptyList(),
    val startDate: String? = null,
    val endDate: String? = null,
    val url: String? = null
)
