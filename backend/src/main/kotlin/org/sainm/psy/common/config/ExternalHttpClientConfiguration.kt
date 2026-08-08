package org.sainm.psy.common.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.web.client.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import java.time.Clock

@ConfigurationProperties(prefix = "psy.http-client")
data class ExternalHttpClientProperties(
    var connectTimeoutMillis: Int = 5_000,
    var readTimeoutMillis: Int = 15_000
)

@Configuration
class ExternalHttpClientConfiguration {

    @Bean
    fun systemClock(): Clock = Clock.systemDefaultZone()

    @Bean
    fun externalHttpClientProperties() = ExternalHttpClientProperties()

    @Bean
    fun externalRestClientTimeoutCustomizer(
        properties: ExternalHttpClientProperties
    ): RestClientCustomizer = RestClientCustomizer { builder ->
        builder.requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(properties.connectTimeoutMillis.coerceAtLeast(100))
                setReadTimeout(properties.readTimeoutMillis.coerceAtLeast(100))
            }
        )
    }
}
