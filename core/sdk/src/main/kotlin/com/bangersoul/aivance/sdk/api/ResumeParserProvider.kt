package com.bangersoul.aivance.sdk.api

import com.bangersoul.aivance.core.common.model.AtsReport
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.sdk.core.BaseProvider
import com.bangersoul.aivance.sdk.core.ProviderCapability
import com.bangersoul.aivance.sdk.core.ProviderMetadata

/**
 * Interface for resume parsing providers.
 * Inherits from [BaseProvider] to manage metadata and lifecycle.
 */
abstract class ResumeParserProvider(
    metadata: ProviderMetadata,
    capabilities: Set<ProviderCapability>
) : BaseProvider(metadata, capabilities) {

    /**
     * Parses and analyzes resume text.
     * @param text The raw text of the resume.
     * @return Result containing the [AtsReport] analysis or an error.
     */
    abstract suspend fun parseResume(text: String): Result<AtsReport>
}

