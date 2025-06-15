plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
    application
}

group = "io.github.devngho"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    val ktorVersion = "3.0.3"
    val kotestVersion = "5.9.1"

    implementation("io.github.devngho:kmarkdown-jvm:0.2.3")

    implementation("io.github.reactivecircus.cache4k:cache4k:0.13.0")

    implementation("com.michael-bull.kotlin-retry:kotlin-retry:2.0.1")

    implementation("com.sksamuel.hoplite:hoplite-core:2.8.2")
    implementation("com.sksamuel.hoplite:hoplite-yaml:2.8.2")

    implementation("it.skrape:skrapeit:1.3.0-alpha.2") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "org.apache.commons", module = "commons-text")
        exclude(group = "commons-io", module = "commons-io")
        exclude(group = "commons-net", module = "commons-net")
        exclude(group = "xalan", module = "xalan")
    }
    implementation("com.github.crawler-commons:crawler-commons:1.4") {
        exclude(group = "org.apache.commons", module = "commons-text")
        exclude(group = "commons-io", module = "commons-io")
    }

    implementation(platform("software.amazon.awssdk:bom:2.27.21"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:sso")
    implementation("software.amazon.awssdk:ssooidc")

    implementation("com.google.protobuf:protobuf-java:4.28.2")
    implementation(platform("com.google.cloud:libraries-bom:26.49.0"))
    implementation("com.google.cloud:google-cloud-pubsub")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC.2")

    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:5.3.0")
    implementation("org.mongodb:bson-kotlinx:5.3.0")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")

    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.mockk:mockk:1.13.13")

    //    implementation("ch.qos.logback:logback-core:1.4.14")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("commons-net:commons-net:3.9.0")
    implementation("org.apache.commons:commons-text:1.10.0")
    implementation("commons-io:commons-io:2.14.0")
//    implementation("org.jsoup:jsoup:1.15.3")
    implementation("xalan:xalan:2.7.3")
    implementation("io.netty:netty-common:4.1.117.Final")

    implementation(project.dependencies.platform("io.insert-koin:koin-bom:4.0.2"))
    implementation("io.insert-koin:koin-core")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "io.github.devngho.geulgyeol.MainKt"
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}