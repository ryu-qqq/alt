// domain: 순수 자바. Spring/JPA/외부 라이브러리 의존성 금지 (ArchUnit으로 강제)
//
// ArchUnit은 core 모듈만 사용한다. junit5 통합(@AnalyzeClasses 등) 어노테이션을 쓰지 않고
// 일반 JUnit 5 @Test 안에서 rule.check(...) 형태로 호출하므로,
// archunit-junit5 가 가져오는 junit-platform 버전이 Spring Boot BOM과 어긋나는 문제를 회피한다.
//
// java-test-fixtures: 다른 모듈에서 도메인 테스트 픽스쳐를 재사용하기 위한 플러그인.
// testFixtures sourceSet에는 archunit/spring 등 외부 의존성을 추가하지 않는다 (도메인 순수성 유지).
plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    testImplementation(libs.archunit.core)
}
