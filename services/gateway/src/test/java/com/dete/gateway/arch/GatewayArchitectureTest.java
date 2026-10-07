package com.dete.gateway.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.dete.gateway", importOptions = ImportOption.DoNotIncludeTests.class)
public class GatewayArchitectureTest {

  @ArchTest
  static final ArchRule controllers_must_reside_in_fallback_or_controller_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .resideInAnyPackage("..controller..", "..fallback..")
          .andShould()
          .beAnnotatedWith(RestController.class);

  @ArchTest
  static final ArchRule filters_must_reside_in_filter_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Filter")
          .and()
          .areNotAnonymousClasses()
          .should()
          .resideInAPackage("..filter..")
          .andShould()
          .implement(GlobalFilter.class);

  @ArchTest
  static final ArchRule noJavaUtilLogging =
      com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAPackage("java.util.logging..")
          .because("Use SLF4J for logging across all components");

  @ArchTest
  static final ArchRule gatewayMustNotImportOtherServiceInternals =
      com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.dete.order..",
              "com.dete.account..",
              "com.dete.matching..",
              "com.dete.risk..",
              "com.dete.marketdata..",
              "com.dete.audit..",
              "com.dete.auth..")
          .because("Services must only communicate via com.dete.common-* modules");
}
