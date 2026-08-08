package org.sainm.psy.counseling.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.lenient
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.appointment.domain.AppointmentDetail
import org.sainm.psy.appointment.repository.AppointmentRepository
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.counseling.api.CreateCounselingRecordRequest
import org.sainm.psy.counseling.domain.CounselingRecordDetail
import org.sainm.psy.counseling.repository.CounselingRepository
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class CounselingServiceTest {

    @Mock private lateinit var counselingRepository: CounselingRepository
    @Mock private lateinit var appointmentRepository: AppointmentRepository
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var securityAuditService: SecurityAuditService
    private lateinit var counselingService: CounselingService

    @BeforeEach
    fun setUpTenantScope() {
        val messageSource = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        counselingService = CounselingService(
            counselingRepository,
            appointmentRepository,
            currentUserFacade,
            securityAuditService,
            LocalizedMessages(messageSource)
        )
        lenient().`when`(appointmentRepository.isUserInTenant(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(1L))).thenReturn(true)
    }

    private val counselorUser = UserPrincipal(
        userId = 5L,
        username = "counselor01",
        displayName = "Counselor",
        status = UserStatus.ENABLED,
        tenantId = 1L,
        groupId = null,
        roles = setOf("COUNSELOR"),
        permissions = emptySet()
    )

    private val adminUser = UserPrincipal(
        userId = 99L,
        username = "admin01",
        displayName = "Admin",
        status = UserStatus.ENABLED,
        tenantId = 1L,
        groupId = null,
        roles = setOf("ASSESSMENT_ADMIN"),
        permissions = emptySet()
    )

    private fun makeAppointment(
        id: Long = 10L,
        counselorUserId: Long = 5L,
        status: String = "CONFIRMED"
    ) = AppointmentDetail(
        id = id,
        userId = 100L,
        counselorUserId = counselorUserId,
        warningId = null,
        scheduleId = null,
        appointmentStatus = status,
        sourceType = "USER",
        remark = null,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    private fun makeRecordDetail(id: Long = 1L, appointmentId: Long = 10L) =
        CounselingRecordDetail(
            id = id,
            appointmentId = appointmentId,
            counselorUserId = 5L,
            summaryText = "existing summary",
            suggestionText = null,
            needRetestFlag = false,
            needTransferFlag = false,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

    private val defaultRequest = CreateCounselingRecordRequest(
        appointmentId = 10L,
        summaryText = "Good session",
        suggestionText = "Follow up next week",
        needRetestFlag = false,
        needTransferFlag = false
    )

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    fun `create throws APPOINTMENT_NOT_FOUND when appointment does not exist`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(counselorUser)
        `when`(appointmentRepository.findAppointmentByIdForUpdate(10L)).thenReturn(null)

        val ex = assertThrows<BizException> {
            counselingService.create(defaultRequest)
        }
        assertEquals("APPOINTMENT_NOT_FOUND", ex.code)
        verify(counselingRepository, never()).createRecord(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyBoolean(),
            org.mockito.ArgumentMatchers.anyBoolean()
        )
    }

    @Test
    fun `create throws APPOINTMENT_FORBIDDEN when user is not the counselor`() {
        val otherCounselorAppointment = makeAppointment(counselorUserId = 999L)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(counselorUser)
        `when`(appointmentRepository.findAppointmentByIdForUpdate(10L)).thenReturn(otherCounselorAppointment)

        val ex = assertThrows<BizException> {
            counselingService.create(defaultRequest)
        }
        assertEquals("APPOINTMENT_FORBIDDEN", ex.code)
    }

    @Test
    fun `create throws APPOINTMENT_INVALID when appointment is CANCELLED`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(counselorUser)
        `when`(appointmentRepository.findAppointmentByIdForUpdate(10L)).thenReturn(
            makeAppointment(status = "CANCELLED")
        )

        val ex = assertThrows<BizException> {
            counselingService.create(defaultRequest)
        }
        assertEquals("APPOINTMENT_INVALID", ex.code)
    }

    @Test
    fun `create throws APPOINTMENT_INVALID when appointment is NO_SHOW`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(counselorUser)
        `when`(appointmentRepository.findAppointmentByIdForUpdate(10L)).thenReturn(
            makeAppointment(status = "NO_SHOW")
        )

        val ex = assertThrows<BizException> {
            counselingService.create(defaultRequest)
        }
        assertEquals("APPOINTMENT_INVALID", ex.code)
    }

    @Test
    fun `create creates new record and returns COMPLETED when no existing record`() {
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(counselorUser)
        `when`(appointmentRepository.findAppointmentByIdForUpdate(10L)).thenReturn(makeAppointment())
        `when`(counselingRepository.findByAppointmentId(10L)).thenReturn(null)
        `when`(
            counselingRepository.createRecord(
                appointmentId = 10L,
                counselorUserId = 5L,
                summaryText = "Good session",
                suggestionText = "Follow up next week",
                needRetestFlag = false,
                needTransferFlag = false
            )
        ).thenReturn(42L)

        val result = counselingService.create(defaultRequest)

        assertEquals(42L, result.recordId)
        assertEquals(10L, result.appointmentId)
        assertEquals("COMPLETED", result.appointmentStatus)
        verify(appointmentRepository).updateAppointmentStatus(10L, "COMPLETED")
        verify(appointmentRepository).createStatusLog(
            10L, "CONFIRMED", "COMPLETED", "COMPLETED", 5L, null, null
        )
        verify(securityAuditService).recordAppointmentTransition(
            10L, "CONFIRMED", "COMPLETED", "COMPLETED", null
        )
    }

    @Test
    fun `create updates existing record when one already exists`() {
        val existingRecord = makeRecordDetail(id = 7L, appointmentId = 10L)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(counselorUser)
        `when`(appointmentRepository.findAppointmentByIdForUpdate(10L)).thenReturn(makeAppointment(status = "COMPLETED"))
        `when`(counselingRepository.findByAppointmentId(10L)).thenReturn(existingRecord)

        val result = counselingService.create(defaultRequest)

        assertEquals(7L, result.recordId)
        verify(counselingRepository).updateRecord(
            recordId = 7L,
            summaryText = "Good session",
            suggestionText = "Follow up next week",
            needRetestFlag = false,
            needTransferFlag = false
        )
        verify(counselingRepository, never()).createRecord(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyBoolean(),
            org.mockito.ArgumentMatchers.anyBoolean()
        )
        verify(appointmentRepository, never()).updateAppointmentStatus(10L, "COMPLETED")
    }

    @Test
    fun `create allows ASSESSMENT_ADMIN to write record for any counselor appointment`() {
        val otherCounselorAppointment = makeAppointment(counselorUserId = 999L)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(adminUser)
        `when`(appointmentRepository.findAppointmentByIdForUpdate(10L)).thenReturn(otherCounselorAppointment)
        `when`(counselingRepository.findByAppointmentId(10L)).thenReturn(null)
        `when`(
            counselingRepository.createRecord(10L, 99L, "Good session", "Follow up next week", false, false)
        ).thenReturn(55L)

        val result = counselingService.create(defaultRequest)

        assertEquals(55L, result.recordId)
        assertEquals("COMPLETED", result.appointmentStatus)
    }
}
