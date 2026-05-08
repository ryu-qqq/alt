plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":adapter-in"))
    implementation(project(":adapter-out:persistence-mysql"))
    implementation(project(":adapter-out:persistence-redis"))
    implementation(project(":adapter-out:client-csrng"))
    implementation(project(":adapter-out:client-llm"))

    implementation(libs.spring.boot.starter.actuator)
}

tasks.named<Jar>("jar") {
    enabled = false
}
