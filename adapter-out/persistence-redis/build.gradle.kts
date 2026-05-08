dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.redisson.spring.boot.starter)
}
