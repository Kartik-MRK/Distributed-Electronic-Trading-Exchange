package com.dete.auth.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.dete.auth", importOptions = ImportOption.DoNotIncludeTests.class)
public class AuthArchitectureTest {

  @ArchTest
  static final ArchRule controllers_must_reside_in_controller_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .resideInAPackage("..controller..")
          .andShould()
          .beAnnotatedWith(RestController.class);

  @ArchTest
  static final ArchRule controllers_should_not_depend_on_repositories_directly =
      noClasses()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..repository..");

  @ArchTest
  static final ArchRule repositories_must_reside_in_repository_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Repository")
          .should()
          .resideInAPackage("..repository..");

  @ArchTest
  static final ArchRule noJavaUtilLogging =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAPackage("java.util.logging..")
          .because("Use SLF4J for logging across all components");

  @ArchTest
  static final ArchRule authMustNotImportOtherServiceInternals =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.dete.order..",
              "com.dete.account..",
              "com.dete.matching..",
              "com.dete.risk..",
              "com.dete.marketdata..",
              "com.dete.audit..",
              "com.dete.gateway..")
          .because("Services must only communicate via com.dete.common-* modules");
}
