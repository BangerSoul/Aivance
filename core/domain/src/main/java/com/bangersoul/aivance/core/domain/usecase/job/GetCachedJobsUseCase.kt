package com.bangersoul.aivance.core.domain.usecase.job

import com.bangersoul.aivance.core.common.model.JobListing
import com.bangersoul.aivance.core.common.result.CoreResult
import com.bangersoul.aivance.core.common.result.RepositoryError
import com.bangersoul.aivance.core.common.result.Result
import com.bangersoul.aivance.core.domain.repository.JobRepository
import com.bangersoul.aivance.core.domain.usecase.UseCase
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * Reads the locally cached job corpus (B3/B4).
 *
 * Discovery used to render exclusively from the current search result, so a
 * cold start painted "Found 0 active opportunities" while the `jobs` table
 * already held the listings harvested by `JobAlertWorker` — and the
 * notification inbox advertised matches Discovery claimed did not exist. This
 * is the read path that reconciles the two surfaces.
 */
class GetCachedJobsUseCase @Inject constructor(
    private val jobRepository: JobRepository
) : UseCase<Unit, CoreResult<List<JobListing>>>() {

    override suspend operator fun invoke(input: Unit): CoreResult<List<JobListing>> {
        return jobRepository.getJobs().firstOrNull()
            ?: Result.Failure(RepositoryError("No cached jobs available"))
    }
}
