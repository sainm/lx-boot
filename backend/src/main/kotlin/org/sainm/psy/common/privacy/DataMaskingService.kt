package org.sainm.psy.common.privacy

import org.springframework.stereotype.Service

@Service
class DataMaskingService {

    private val mainlandMobilePattern = Regex("""(?<!\d)(1[3-9]\d)\d{4}(\d{4})(?!\d)""")
    private val citizenIdPattern = Regex("""(?<![0-9A-Za-z])(\d{6})\d{8}(\d{3}[0-9Xx])(?![0-9A-Za-z])""")
    private val emailPattern = Regex("""([A-Za-z0-9._%+-])([A-Za-z0-9._%+-]*)(@[A-Za-z0-9.-]+\.[A-Za-z]{2,})""")

    fun maskText(text: String): String =
        text
            .replace(mainlandMobilePattern, "$1****$2")
            .replace(citizenIdPattern, "$1********$2")
            .replace(emailPattern) { match ->
                val first = match.groupValues[1]
                val domain = match.groupValues[3]
                "$first***$domain"
            }
}
