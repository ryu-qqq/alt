dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
}
