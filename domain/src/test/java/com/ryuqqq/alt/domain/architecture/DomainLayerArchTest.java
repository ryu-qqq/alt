package com.ryuqqq.alt.domain.architecture;

import com.ryuqqq.alt.domain.error.DomainException;
import com.ryuqqq.alt.domain.error.ErrorCode;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * 도메인 레이어 컨벤션 강제. convention-guardian 소유.
 *
 * 검증 규칙:
 * 1. 외부 프레임워크 import 금지 (Spring, JPA, Hibernate, Jackson, Jakarta)
 * 2. Setter 금지 (set 으로 시작하는 public 메서드)
 * 3. *Id 는 record 타입
 * 4. *Exception 은 DomainException 상속
 * 5. *ErrorCode enum 은 ErrorCode 인터페이스 구현
 * 6. Aggregate Root(Member, Channel, SubscriptionAttempt) 는 public 생성자 금지
 */
@DisplayName("Domain Layer ArchUnit 검증")
class DomainLayerArchTest {

    private static final String DOMAIN_PACKAGE = "com.ryuqqq.alt.domain";

    private static JavaClasses domainClasses;

    @BeforeAll
    static void importDomainClasses() {
        domainClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(DOMAIN_PACKAGE);
    }

    @Test
    @DisplayName("도메인은 Spring/JPA/Hibernate/Jackson/Jakarta 에 의존하지 않는다 (DOM-CMN-002)")
    void domainShouldNotDependOnExternalFrameworks() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta..",
                "javax.persistence..",
                "org.hibernate..",
                "com.fasterxml.jackson.."
            );

        rule.check(domainClasses);
    }

    @Test
    @DisplayName("도메인 public 메서드는 set 으로 시작할 수 없다 (DOM-AGG-004)")
    void domainShouldNotHaveSetters() {
        ArchRule rule = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
            .and().arePublic()
            .should().haveNameStartingWith("set");

        rule.check(domainClasses);
    }

    @Test
    @DisplayName("*Id 는 record 타입이다 (DOM-ID-001)")
    void idClassesShouldBeRecords() {
        ArchRule rule = classes()
            .that().resideInAPackage("..domain..")
            .and().haveSimpleNameEndingWith("Id")
            .should().beRecords();

        rule.check(domainClasses);
    }

    @Test
    @DisplayName("*Exception 은 DomainException 을 상속한다 (DOM-EXC-001)")
    void exceptionsShouldExtendDomainException() {
        ArchRule rule = classes()
            .that().resideInAPackage("..domain..")
            .and().haveSimpleNameEndingWith("Exception")
            .should().beAssignableTo(DomainException.class);

        rule.check(domainClasses);
    }

    @Test
    @DisplayName("*ErrorCode enum 은 ErrorCode 인터페이스를 구현한다 (DOM-ERR-001)")
    void errorCodeShouldImplementErrorCodeInterface() {
        ArchRule rule = classes()
            .that().resideInAPackage("..domain..")
            .and().haveSimpleNameEndingWith("ErrorCode")
            .and().areEnums()
            .should().implement(ErrorCode.class);

        rule.check(domainClasses);
    }

    @Test
    @DisplayName("Aggregate Root(Member, Channel, SubscriptionAttempt)는 public 생성자를 갖지 않는다 (DOM-AGG-001)")
    void aggregatesShouldNotHavePublicConstructors() {
        ArchRule rule = classes()
            .that().resideInAPackage("..domain..")
            .and().haveSimpleName("Member")
            .or().haveSimpleName("Channel")
            .or().haveSimpleName("SubscriptionAttempt")
            .should().haveOnlyPrivateConstructors();

        rule.check(domainClasses);
    }
}
