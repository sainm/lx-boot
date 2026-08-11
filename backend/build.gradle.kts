plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "2.1.0"
    id("org.springframework.boot") version "3.4.0"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "org.sainm"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

springBoot {
    // The guarded databaseMigration JavaExec task has its own main class.
    // Keep the executable application entry point deterministic for bootRun
    // and bootJar now that both mains live in the backend source set.
    mainClass.set("org.sainm.psy.PsyApplicationKt")
}

configurations.configureEach {
    exclude(group = "commons-logging", module = "commons-logging")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    implementation("org.sainm:auth-spring-boot-starter:0.1.0-SNAPSHOT")
    // Needed at compile time to build the default JdbcSocialAccountService that
    // our SSO-aware SocialAccountService wraps (see AuthMappingConfiguration).
    implementation("org.sainm:auth-persistence:0.1.0-SNAPSHOT")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    implementation("org.apache.poi:poi-ooxml:5.4.1")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("io.micrometer:micrometer-registry-prometheus")
    testImplementation("com.h2database:h2")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.register<JavaExec>("databaseMigration") {
    group = "database"
    description = "Runs the guarded Flyway info, validate, baseline, or migrate operation from environment variables"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.sainm.psy.migration.PsyDatabaseMigrationCli")
}
