package org.sainm.psy.directory.api

import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.directory.domain.DirectoryGroup
import org.sainm.psy.directory.domain.DirectoryScale
import org.sainm.psy.directory.domain.DirectoryTask
import org.sainm.psy.directory.domain.DirectoryUser
import org.sainm.psy.directory.service.DirectoryService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Tenant-scoped lookup endpoints backing the operator pickers.  Read-only and
 * available to every role that manages appointments, warnings, reports or
 * scale publication.
 */
@RestController
@RequestMapping("/api/v1/directory")
@PreAuthorize("hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
class DirectoryController(
    private val directoryService: DirectoryService
) {

    @GetMapping("/users")
    fun users(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "false") staffOnly: Boolean,
        @RequestParam(defaultValue = "false") activeOnly: Boolean,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<PageResponse<DirectoryUser>> =
        ApiResponse.ok(directoryService.findUserPage(keyword, staffOnly, activeOnly, page, size))

    @GetMapping("/groups")
    fun groups(): ApiResponse<List<DirectoryGroup>> =
        ApiResponse.ok(directoryService.findGroups())

    @GetMapping("/tasks")
    fun tasks(@RequestParam(required = false) keyword: String?): ApiResponse<List<DirectoryTask>> =
        ApiResponse.ok(directoryService.findTasks(keyword))

    @GetMapping("/scales")
    fun scales(@RequestParam(required = false) keyword: String?): ApiResponse<List<DirectoryScale>> =
        ApiResponse.ok(directoryService.findScales(keyword))
}
