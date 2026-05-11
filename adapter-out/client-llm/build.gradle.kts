dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    implementation(libs.spring.boot.starter.web)         // RestClient
    implementation(libs.resilience4j.spring.boot3)       // @CircuitBreaker / @Retry / @TimeLimiter

    testImplementation(testFixtures(project(":application")))
    testImplementation(testFixtures(project(":domain")))
}
