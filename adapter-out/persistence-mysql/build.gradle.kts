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
}

val querydslDir = layout.buildDirectory.dir("generated/querydsl")

sourceSets["main"].java.srcDir(querydslDir)

tasks.withType<JavaCompile>().configureEach {
    options.generatedSourceOutputDirectory.set(querydslDir.get().asFile)
}

tasks.named("clean") {
    doLast { querydslDir.get().asFile.deleteRecursively() }
}
