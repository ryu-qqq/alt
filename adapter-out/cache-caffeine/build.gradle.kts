// adapter-out:cache-caffeine — 단일 인스턴스용 로컬 캐시 어댑터.
// 분산 환경 진화 시 Redis(SETNX) 어댑터로 교체 가능 — Application Port 만 알면 됨.

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    implementation(libs.spring.context)
    implementation(libs.spring.boot.autoconfigure)  // @ConfigurationProperties + @EnableConfigurationProperties 바인딩
    implementation(libs.caffeine)
}
