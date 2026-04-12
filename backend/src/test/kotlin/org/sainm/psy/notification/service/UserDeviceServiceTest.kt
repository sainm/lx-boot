package org.sainm.psy.notification.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.auth.CurrentUser
import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.notification.api.RegisterDeviceRequest
import org.sainm.psy.notification.domain.UserDeviceSummary
import org.sainm.psy.notification.repository.UserDeviceRepository
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class UserDeviceServiceTest {

    @Mock private lateinit var userDeviceRepository: UserDeviceRepository
    @Mock private lateinit var currentUserFacade: CurrentUserFacade

    private val messages = LocalizedMessages(
        ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
    )

    @Test
    fun `registerMyDevice normalizes request and upserts current user device`() {
        val service = UserDeviceService(userDeviceRepository, currentUserFacade, messages)
        val user = currentUser()
        val summary = deviceSummary(deviceType = "ANDROID", deviceId = "phone-1")
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(user)
        `when`(
            userDeviceRepository.upsertDevice(
                userId = 5L,
                deviceType = "ANDROID",
                deviceId = "phone-1",
                pushToken = "token-123",
                appVersion = "1.0.0"
            )
        ).thenReturn(summary)

        val result = service.registerMyDevice(
            RegisterDeviceRequest(
                deviceType = " android ",
                deviceId = " phone-1 ",
                pushToken = " token-123 ",
                appVersion = " 1.0.0 "
            )
        )

        assertEquals(summary, result)
    }

    @Test
    fun `deactivateMyDevice throws when active device is not found`() {
        val service = UserDeviceService(userDeviceRepository, currentUserFacade, messages)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(currentUser())
        `when`(userDeviceRepository.deactivate(5L, "phone-404")).thenReturn(false)

        val ex = assertThrows<BizException> {
            service.deactivateMyDevice(" phone-404 ")
        }

        assertEquals("NOTIFICATION_DEVICE_NOT_FOUND", ex.code)
    }

    @Test
    fun `deactivateMyDevice returns deactivated device`() {
        val service = UserDeviceService(userDeviceRepository, currentUserFacade, messages)
        val deactivated = deviceSummary(activeFlag = false)
        `when`(currentUserFacade.requireCurrentUser()).thenReturn(currentUser())
        `when`(userDeviceRepository.deactivate(5L, "phone-1")).thenReturn(true)
        `when`(userDeviceRepository.findByUser(5L)).thenReturn(listOf(deactivated))

        val result = service.deactivateMyDevice("phone-1")

        assertEquals(false, result.activeFlag)
        verify(userDeviceRepository).deactivate(5L, "phone-1")
    }

    private fun currentUser() = CurrentUser(
        userId = 5L,
        username = "tester",
        displayName = "Tester",
        tenantId = null,
        groupId = null,
        roles = emptySet(),
        permissions = emptySet()
    )

    private fun deviceSummary(
        deviceType: String = "ANDROID",
        deviceId: String = "phone-1",
        activeFlag: Boolean = true
    ) = UserDeviceSummary(
        id = 1L,
        deviceType = deviceType,
        deviceId = deviceId,
        pushTokenMasked = "toke****-123",
        appVersion = "1.0.0",
        activeFlag = activeFlag,
        lastActiveAt = LocalDateTime.now(),
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )
}
