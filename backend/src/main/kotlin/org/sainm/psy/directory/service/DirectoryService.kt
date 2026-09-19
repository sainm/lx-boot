package org.sainm.psy.directory.service

import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.directory.domain.DirectoryGroup
import org.sainm.psy.directory.domain.DirectoryScale
import org.sainm.psy.directory.domain.DirectoryTask
import org.sainm.psy.directory.domain.DirectoryUser
import org.sainm.psy.directory.repository.DirectoryRepository
import org.springframework.stereotype.Service

@Service
class DirectoryService(
    private val directoryRepository: DirectoryRepository,
    private val tenantAccessPolicy: TenantAccessPolicy
) {

    fun findUserPage(
        keyword: String?,
        staffOnly: Boolean,
        activeOnly: Boolean,
        page: Int,
        size: Int
    ): PageResponse<DirectoryUser> {
        require(page > 0) { "page must be greater than 0" }
        require(size in 1..100) { "size must be between 1 and 100" }
        val tenantId = tenantAccessPolicy.currentTenantFilter("DIRECTORY", "USERS")
        val (list, total) = directoryRepository.findUserPage(keyword, staffOnly, activeOnly, page, size, tenantId)
        return PageResponse(list = list, page = page, size = size, total = total)
    }

    fun findGroups(): List<DirectoryGroup> =
        directoryRepository.findGroups(tenantAccessPolicy.currentTenantFilter("DIRECTORY", "GROUPS"))

    fun findTasks(keyword: String?): List<DirectoryTask> =
        directoryRepository.findTasks(keyword, TASK_LIMIT, tenantAccessPolicy.currentTenantFilter("DIRECTORY", "TASKS"))

    fun findScales(keyword: String?): List<DirectoryScale> =
        directoryRepository.findScales(keyword, SCALE_LIMIT, tenantAccessPolicy.currentTenantFilter("DIRECTORY", "SCALES"))

    private companion object {
        private const val TASK_LIMIT = 200
        private const val SCALE_LIMIT = 200
    }
}
