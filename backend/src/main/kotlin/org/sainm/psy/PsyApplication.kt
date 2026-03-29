package org.sainm.psy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["org.sainm"])
class PsyApplication

fun main(args: Array<String>) {
    runApplication<PsyApplication>(*args)
}
