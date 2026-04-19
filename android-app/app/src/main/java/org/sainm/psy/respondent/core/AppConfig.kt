package org.sainm.psy.respondent.core

import org.sainm.psy.respondent.BuildConfig

object AppConfig {
    const val deviceType: String = "ANDROID"
    val baseUrl: String = BuildConfig.API_BASE_URL
    val deviceName: String = BuildConfig.APP_DEVICE_NAME
}
