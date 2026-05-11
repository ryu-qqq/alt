dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    implementation(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.mysql.connector)

    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.mysql)

    // QueryDSL (Spring Boot 3 / Hibernate 6 → jakarta classifier 필요)
    implementation("com.querydsl:querydsl-jpa:${libs.versions.querydsl.get()}:jakarta")
    annotationProcessor("com.querydsl:querydsl-apt:${libs.versions.querydsl.get()}:jakarta")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")

    // 도메인 모듈의 testFixtures 재사용 (테스트 코드용)
    testImplementation(testFixtures(project(":domain")))

    // Testcontainers (MySQL 통합 테스트)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.bundles.testcontainers)

    // application 모듈의 testFixtures 재사용 (Command 픽스쳐 등 필요시)
    testImplementation(testFixtures(project(":application")))
}

val querydslDir = layout.buildDirectory.dir("generated/querydsl")

sourceSets["main"].java.srcDir(querydslDir)

tasks.withType<JavaCompile>().configureEach {
    options.generatedSourceOutputDirectory.set(querydslDir.get().asFile)
}

tasks.named("clean") {
    doLast { querydslDir.get().asFile.deleteRecursively() }
}
