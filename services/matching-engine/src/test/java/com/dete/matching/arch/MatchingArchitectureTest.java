package com.dete.matching.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.dete.matching",
    importOptions = {ImportOption.DoNotIncludeTests.class})
public class MatchingArchitectureTest {

  @ArchTest
  public static final ArchRule engineCoreMustNotDependOnDatabase =
      noClasses()
          .that()
          .resideInAPackage("..engine..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "java.sql..",
              "javax.sql..",
              "org.springframework.jdbc..",
              "org.springframework.data..")
          .because(
              "The matching engine hot path must remain 100% in-memory with zero database dependencies");

  @ArchTest
  public static final ArchRule engineModelsShouldBeIndependentOfSpring =
      classes()
          .that()
          .resideInAPackage("..engine.model..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("com.dete..", "java..", "org.slf4j..")
          .because(
              "Engine models must be pure high-performance data structures independent of framework annotations");
}
