plugins {
    java
    idea
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

allprojects {
    group = "com.ryuqqq"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    extensions.configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:${rootProject.libs.versions.springBoot.get()}")
        }
        // Spring Boot 3.5.6 BOM 이 강제하는 testcontainers / docker-java 버전을 override.
        //
        // 배경:
        //   - Spring Boot BOM 은 testcontainers 1.21.3 + docker-java 3.4.2 를 가져옴.
        //   - docker-java 3.4.x 의 기본 Docker API version 이 1.32 라 Docker Desktop 29.x (최소 1.44 요구)
        //     데몬과 통신 실패 → "client version 1.32 is too old" 로 통합 테스트 전수 실패.
        //
        // 해결:
        //   - testcontainers 1.21.4 (recent Docker Engine 호환성 개선)
        //   - docker-java 3.7.1 (기본 API version 1.44, 3.7.0 부터 적용)
        dependencies {
            dependency("org.testcontainers:testcontainers:${rootProject.libs.versions.testcontainers.get()}")
            dependency("org.testcontainers:junit-jupiter:${rootProject.libs.versions.testcontainers.get()}")
            dependency("org.testcontainers:mysql:${rootProject.libs.versions.testcontainers.get()}")
            dependency("org.testcontainers:database-commons:${rootProject.libs.versions.testcontainers.get()}")
            dependency("org.testcontainers:jdbc:${rootProject.libs.versions.testcontainers.get()}")
            dependency("com.github.docker-java:docker-java-core:${rootProject.libs.versions.dockerJava.get()}")
            dependency("com.github.docker-java:docker-java-api:${rootProject.libs.versions.dockerJava.get()}")
            dependency("com.github.docker-java:docker-java-transport:${rootProject.libs.versions.dockerJava.get()}")
            dependency("com.github.docker-java:docker-java-transport-zerodep:${rootProject.libs.versions.dockerJava.get()}")
        }
    }

    dependencies {
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testImplementation"("org.assertj:assertj-core")
        // Gradle 8.14 내장 junit-platform-launcher 와 Spring Boot 3.5 의 junit-platform-engine
        // 버전이 어긋나면 OutputDirectoryProvider 에러가 발생한다. Spring Boot BOM 이 관리하는
        // junit-platform-launcher 를 명시적으로 추가하여 정합성을 맞춘다.
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            html.required.set(true)
            xml.required.set(true)
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }
}
