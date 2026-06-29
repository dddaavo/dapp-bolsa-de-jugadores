package com.unq.dapp.bolsa;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import org.springframework.stereotype.Service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(
    packages = "com.unq.dapp.bolsa",
    importOptions = ImportOption.DoNotIncludeTests.class
)
public class ArchitectureTest {

    // --- Reglas de capas ---

    @ArchTest
    static ArchRule controllers_no_acceden_a_repositories =
        noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .because("los controllers deben pasar por la capa de aplicación");

    @ArchTest
    static ArchRule domain_no_accede_a_application =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..application..")
            .because("el dominio no debe depender de servicios de aplicación");

    @ArchTest
    static ArchRule domain_no_accede_a_infrastructure =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .because("el dominio no debe depender de infraestructura");

    @ArchTest
    static ArchRule domain_no_usa_spring_web =
        noClasses()
            .that().resideInAPackage("..domain..")
            .and().areNotAssignableTo(Exception.class)
            .should().dependOnClassesThat().resideInAPackage("org.springframework.web..")
            .because("el dominio no debe acoplarse a Spring Web");

    @ArchTest
    static ArchRule domain_no_usa_spring_data =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.data..")
            .because("el dominio no debe acoplarse a Spring Data");

    @ArchTest
    static ArchRule services_no_acceden_a_adapters_concretos =
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..integration.whoscored..")
            .because("los services solo deben conocer el puerto, no el adaptador concreto");

    // --- Convenciones de nombres ---

    @ArchTest
    static ArchRule controllers_deben_vivir_en_api =
        classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("..api..")
            .because("los controllers pertenecen a la capa api");

    @ArchTest
    static ArchRule services_deben_vivir_en_application =
        classes()
            .that().areAnnotatedWith(Service.class)
            .should().resideInAPackage("..application..")
            .because("los services (@Service) pertenecen a la capa application");

    @ArchTest
    static ArchRule repositories_deben_vivir_en_infrastructure =
        classes()
            .that().haveSimpleNameEndingWith("Repository")
            .should().resideInAPackage("..infrastructure..")
            .because("los repositories pertenecen a la capa infrastructure");
}
