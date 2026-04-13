package org.sainm.psy.common.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.sainm.psy.assessment.api.AnswerSheetController
import org.sainm.psy.assessment.api.SaveAnswerSheetRequest
import org.sainm.psy.assessment.api.SubmitAnswerSheetRequest
import org.sainm.psy.export.api.ExportController
import org.sainm.psy.export.api.ExportReportRequest
import org.sainm.psy.report.api.ReportController
import org.springframework.security.access.prepost.PreAuthorize

class ControllerAuthorizationContractTest {

    @Test
    fun `report detail endpoints require authentication`() {
        assertPreAuthorize(
            ReportController::class.java,
            "findDetail",
            "isAuthenticated()",
            Long::class.javaPrimitiveType!!
        )
        assertPreAuthorize(
            ReportController::class.java,
            "findDetailByResultId",
            "isAuthenticated()",
            Long::class.javaPrimitiveType!!
        )
        assertPreAuthorize(
            ReportController::class.java,
            "findMyReports",
            "isAuthenticated()"
        )
    }

    @Test
    fun `answer sheet endpoints require USER role`() {
        assertPreAuthorize(
            AnswerSheetController::class.java,
            "getTaskQuestions",
            "hasRole('USER')",
            Long::class.javaPrimitiveType!!
        )
        assertPreAuthorize(
            AnswerSheetController::class.java,
            "save",
            "hasRole('USER')",
            SaveAnswerSheetRequest::class.java
        )
        assertPreAuthorize(
            AnswerSheetController::class.java,
            "submit",
            "hasRole('USER')",
            SubmitAnswerSheetRequest::class.java,
            String::class.java
        )
    }

    @Test
    fun `export endpoints require staff roles`() {
        val staffRoles = "hasAnyRole('COUNSELOR', 'ASSESSMENT_ADMIN', 'ORG_MANAGER', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')"
        assertPreAuthorize(
            ExportController::class.java,
            "exportReport",
            staffRoles,
            ExportReportRequest::class.java
        )
        assertPreAuthorize(
            ExportController::class.java,
            "downloadReport",
            staffRoles,
            java.lang.Long::class.java,
            java.lang.Long::class.java,
            String::class.java,
            java.lang.Boolean.TYPE
        )
        assertPreAuthorize(
            ExportController::class.java,
            "submitExportJob",
            staffRoles,
            ExportReportRequest::class.java
        )
        assertPreAuthorize(
            ExportController::class.java,
            "getExportJobStatus",
            staffRoles,
            String::class.java
        )
        assertPreAuthorize(
            ExportController::class.java,
            "downloadExportJob",
            staffRoles,
            String::class.java
        )
    }

    private fun assertPreAuthorize(
        controllerClass: Class<*>,
        methodName: String,
        expectedExpression: String,
        vararg parameterTypes: Class<*>
    ) {
        val annotation = controllerClass
            .getDeclaredMethod(methodName, *parameterTypes)
            .getAnnotation(PreAuthorize::class.java)

        assertNotNull(annotation, "${controllerClass.simpleName}.$methodName must declare @PreAuthorize")
        assertEquals(expectedExpression, annotation.value)
    }
}
