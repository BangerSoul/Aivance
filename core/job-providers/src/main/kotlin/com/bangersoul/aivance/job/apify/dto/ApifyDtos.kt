package com.bangersoul.aivance.job.apify.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApifyActorRunResponse(
    val data: ApifyActorRunData
)

@Serializable
data class ApifyActorRunData(
    val id: String,
    val status: String,
    val defaultDatasetId: String? = null
)

@Serializable
data class ApifyDatasetItem(
    val id: String? = null,
    val title: String? = null,
    // Real LinkedIn-scraper actors emit `companyName`; older/generic actors emit `company`.
    // Both are read so the mapper can prefer whichever is present (QA E2E 2026-08-11).
    val company: String? = null,
    @SerialName("companyName")
    val companyName: String? = null,
    val companyLogo: String? = null,
    val location: String? = null,
    val salary: String? = null,
    val description: String? = null,
    val descriptionHtml: String? = null,
    val url: String? = null,
    // Real actors emit `postedDate`; generic actors emit `postedAt`.
    val postedAt: String? = null,
    @SerialName("postedDate")
    val postedDate: String? = null,
    // Real actors emit `contractType` (Full-time/Part-time/Contract); generic emit `type`.
    val type: String? = null,
    @SerialName("contractType")
    val contractType: String? = null,
    val experienceLevel: String? = null,
    val isRemote: Boolean? = false
)
