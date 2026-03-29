package org.sainm.psy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["org.sainm"])
@EnableAsync
@EnableScheduling
class PsyApplication

fun main(args: Array<String>) {
    runApplication<PsyApplication>(*args)
}
