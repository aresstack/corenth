package com.aresstack.corenth.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.aresstack.corenth",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public class CorenthArchitectureRulesTest {

    @ArchTest
    static final ArchRule inner_city_must_not_depend_on_outer_ring = noClasses()
            .that().resideInAnyPackage(
                    "com.aresstack.corenth.adyton..",
                    "com.aresstack.corenth.astu..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.aresstack.corenth.proasteion..")
            .because("the inner city must not depend on the outer adapter ring");

    @ArchTest
    static final ArchRule core_must_not_depend_on_swing_or_exedra = noClasses()
            .that().resideInAnyPackage(
                    "com.aresstack.corenth.adyton..",
                    "com.aresstack.corenth.astu..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "javax.swing..",
                    "java.awt..",
                    "com.aresstack.corenth.proasteion.exedra..")
            .because("core modules must stay independent from local UI technology");

    @ArchTest
    static final ArchRule holkas_must_not_depend_on_ui = noClasses()
            .that().resideInAnyPackage(
                    "com.aresstack.corenth.proasteion.emporion.holkas..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "javax.swing..",
                    "java.awt..",
                    "com.aresstack.corenth.proasteion.exedra..")
            .because("Holkas fetches raw resources and must not know the UI adapter");

    @ArchTest
    static final ArchRule client_adapters_must_not_bypass_mediated_access = noClasses()
            .that().resideInAnyPackage(
                    "com.aresstack.corenth.proasteion.exedra..",
                    "com.aresstack.corenth.proasteion.katagogion..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.aresstack.corenth.proasteion.emporion.holkas..")
            .because("client-facing adapters must go through mediated resource access instead of Holkas directly");
}
