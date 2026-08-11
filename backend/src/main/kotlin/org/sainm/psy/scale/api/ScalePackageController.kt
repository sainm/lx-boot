package org.sainm.psy.scale.api

import jakarta.validation.Valid
import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.scale.domain.ScalePackageSnapshot
import org.sainm.psy.scale.service.ScalePackageService
import org.sainm.psy.scale.service.ScalePackageExportService
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.ContentDisposition
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/api/v1/scales/{scaleId}/package")
@PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
class ScalePackageController(
    private val service: ScalePackageService,
    private val exportService: ScalePackageExportService
) {
    @GetMapping
    fun find(@PathVariable scaleId: Long): ApiResponse<ScalePackageSnapshot> = ApiResponse.ok(service.find(scaleId))

    @PutMapping
    fun replace(
        @PathVariable scaleId: Long,
        @Valid @RequestBody request: UpdateScalePackageRequest
    ): ApiResponse<ScalePackageSnapshot> = ApiResponse.ok(service.replace(scaleId, request))

    @GetMapping("/export")
    fun export(@PathVariable scaleId: Long): ResponseEntity<ByteArrayResource> {
        val artifact = exportService.export(scaleId)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(artifact.contentType))
            .contentLength(artifact.bytes.size.toLong())
            .header("Content-Disposition", ContentDisposition.attachment().filename(artifact.fileName, StandardCharsets.UTF_8).build().toString())
            .header("X-Export-Id", artifact.exportId)
            .header("X-Export-File-Name", artifact.fileName)
            .header("X-Exported-At", artifact.exportedAt.toString())
            .header("X-Scale-Content-Hash", artifact.scaleContentHash)
            .header("X-Release-Fingerprint", artifact.releaseFingerprint)
            .header("X-Scale-Package-Payload-Hash", artifact.payloadHash)
            .header("X-Scale-Package-Schema-Version", artifact.schemaVersion.toString())
            .body(ByteArrayResource(artifact.bytes))
    }
}
