package org.sainm.psy.common.security

object SensitiveTextSanitizer {
    private val quotedSecret = Regex(
        """(?i)([\"'](?:authorization|token|password|secret|credential|api[_-]?key)[\"']\s*:\s*[\"'])([^\"']*)([\"'])"""
    )
    private val plainSecret = Regex(
        """(?i)((?:token|password|secret|credential|api[_-]?key)\s*[:=]\s*)([^\s,;}]+)"""
    )
    private val plainAuthorization = Regex("""(?i)(authorization\s*[:=]\s*)(?:bearer\s+)?([^\s,;}]+)""")
    private val bearerCredential = Regex("""(?i)(bearer\s+)[A-Za-z0-9._~+/-]+=*""")

    fun redact(value: String?, maxLength: Int): String? = value
        ?.replace(quotedSecret, "$1[REDACTED]$3")
        ?.replace(plainAuthorization, "$1[REDACTED]")
        ?.replace(plainSecret, "$1[REDACTED]")
        ?.replace(bearerCredential, "$1[REDACTED]")
        ?.take(maxLength.coerceAtLeast(0))
}
