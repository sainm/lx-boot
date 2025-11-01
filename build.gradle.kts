plugins {
    java
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.lx"
version = "1.0.0"
description = "心理评测系统"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

val hutoolVersion = "5.8.40"
val mysqlConnectorJVersion = "9.1.0"
val druidVersion = "1.2.24"
val mybatisPlusVersion = "3.5.5"
val dynamicDatasourceVersion = "4.3.1"
val knife4jVersion = "4.5.0"
val mapstructVersion = "1.6.3"
val lombokMapstructBindingVersion = "0.2.0"
val xxlJobVersion = "3.2.0"
val fastexcelVersion = "1.3.0"
val minioVersion = "8.5.10"
val okhttp3Version = "4.8.1"
val aliyunSdkOssVersion = "3.16.3"
val redissonVersion = "3.51.0"
val mybatisPlusGeneratorVersion = "3.5.6"
val velocityVersion = "2.3"
val ip2regionVersion = "2.7.0"
val aliyunJavaSdkCoreVersion = "4.7.6"
val aliyunJavaSdkDysmsapiVersion = "2.2.1"
val weixinJavaVersion = "4.7.7.B"
val caffeineVersion = "2.9.3"

dependencies {
    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    
    // Lombok MapStruct Binding
    compileOnly("org.projectlombok:lombok-mapstruct-binding:$lombokMapstructBindingVersion")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:$lombokMapstructBindingVersion")

    // Hutool
    implementation("cn.hutool:hutool-all:$hutoolVersion")

    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // MySQL
    runtimeOnly("com.mysql:mysql-connector-j:$mysqlConnectorJVersion")

    // Druid
    implementation("com.alibaba:druid-spring-boot-starter:$druidVersion")

    // MyBatis Plus
    implementation("com.baomidou:mybatis-plus-spring-boot3-starter:$mybatisPlusVersion")

    // Knife4j (Swagger)
    implementation("com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter:$knife4jVersion") {
        exclude(group = "org.springdoc", module = "springdoc-openapi-starter-webmvc-ui")
    }
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")

    // MapStruct
    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")

    // XXL-Job
    implementation("com.xuxueli:xxl-job-core:$xxlJobVersion")

    // FastExcel
    implementation("cn.idev.excel:fastexcel:$fastexcelVersion")

    // MinIO
    implementation("io.minio:minio:$minioVersion")

    // Aliyun OSS
    implementation("com.aliyun.oss:aliyun-sdk-oss:$aliyunSdkOssVersion")

    // Redisson
    implementation("org.redisson:redisson-spring-boot-starter:$redissonVersion")

    // MyBatis Plus Generator
    implementation("com.baomidou:mybatis-plus-generator:$mybatisPlusGeneratorVersion")

    // Velocity
    implementation("org.apache.velocity:velocity-engine-core:$velocityVersion")

    // IP2Region
    implementation("org.lionsoul:ip2region:$ip2regionVersion")

    // Aliyun SDK
    implementation("com.aliyun:aliyun-java-sdk-core:$aliyunJavaSdkCoreVersion")
    implementation("com.aliyun:aliyun-java-sdk-dysmsapi:$aliyunJavaSdkDysmsapiVersion")

    // Weixin Java SDK
    implementation("com.github.binarywang:weixin-java-miniapp:$weixinJavaVersion")

    // Caffeine
    implementation("com.github.ben-manes.caffeine:caffeine:$caffeineVersion")

    // 动态多数据源 (已注释)
    // implementation("com.baomidou:dynamic-datasource-spring-boot3-starter:$dynamicDatasourceVersion")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
        "-parameters",
        "-Amapstruct.defaultComponentModel=spring",
        "-Amapstruct.suppressGeneratorTimestamp=true",
        "-Amapstruct.suppressGeneratorVersionInfoComment=true"
    ))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("lx-boot")
}

