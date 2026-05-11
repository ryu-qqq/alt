plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.spring.context)
    implementation(libs.spring.tx)

    // 도메인 모듈의 testFixtures 재사용 (테스트 코드용)
    testImplementation(testFixtures(project(":domain")))

    // application 자체 testFixtures 에서도 도메인 모델 + 도메인 testFixtures 사용
    testFixturesImplementation(project(":domain"))
    testFixturesImplementation(testFixtures(project(":domain")))
}
