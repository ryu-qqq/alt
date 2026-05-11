plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":adapter-in"))
    implementation(project(":adapter-out:persistence-mysql"))
    implementation(project(":adapter-out:cache-caffeine"))
    implementation(project(":adapter-out:client-csrng"))
    implementation(project(":adapter-out:client-llm"))

    implementation(libs.spring.boot.starter.actuator)

    // E2E 통합 테스트 — Testcontainers(MySQL) 기반 풀스택 부팅 검증
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.bundles.testcontainers)

    // bootstrap 은 다른 모듈을 implementation 으로 받으므로 transitive 가 testCompileClasspath 에 노출되지 않는다.
    // E2E 가 직접 다루는 클래스(JdbcTemplate / ObjectMapper / Caffeine Cache) 는 testImplementation 으로 명시.
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation(libs.caffeine)
}

tasks.named<Jar>("jar") {
    enabled = false
}
