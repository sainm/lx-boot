package org.sainm.psy.assessment.service

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object AnonymousAssessmentIdentity {
    fun token(secret: String, taskId: Long, userId: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal("$taskId:$userId".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
