package com.bangersoul.aivance.core.data.repository

import com.bangersoul.aivance.core.common.model.CompanyCatalogEntry
import com.bangersoul.aivance.core.data.company.CompanyCatalog
import com.bangersoul.aivance.core.domain.repository.CompanyCatalogRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompanyCatalogRepositoryImpl @Inject constructor(
    private val companyCatalog: CompanyCatalog
) : CompanyCatalogRepository {

    override val size: Int get() = companyCatalog.size

    override fun findCompany(name: String): CompanyCatalogEntry? =
        companyCatalog.findByName(name)

    override fun findCompanyByDomain(domain: String): CompanyCatalogEntry? =
        companyCatalog.findByDomain(domain)
}
