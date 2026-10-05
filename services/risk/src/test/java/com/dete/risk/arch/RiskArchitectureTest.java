package com.dete.risk.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.dete.risk", importOptions = ImportOption.DoNotIncludeTests.class)
public class RiskArchitectureTest {

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
  static final ArchRule grpc_services_must_reside_in_grpc_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("GrpcService")
          .should()
          .resideInAPackage("..grpc..");
}
