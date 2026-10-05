package com.dete.marketdata.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(
    packages = "com.dete.marketdata",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class MarketDataArchitectureTest {

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
  static final ArchRule consumers_must_reside_in_consumer_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Consumer")
          .should()
          .resideInAPackage("..consumer..");

  @ArchTest
  static final ArchRule repositories_must_reside_in_repository_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Repository")
          .should()
          .resideInAPackage("..repository..")
          .andShould()
          .beAnnotatedWith(Repository.class);

  @ArchTest
  static final ArchRule services_must_reside_in_service_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Service")
          .should()
          .resideInAPackage("..service..")
          .andShould()
          .beAnnotatedWith(Service.class);
}
