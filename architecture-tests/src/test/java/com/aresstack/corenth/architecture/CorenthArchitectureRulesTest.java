package com.aresstack.corenth.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

public class CorenthArchitectureRulesTest {

    private static final String ARCHITECTURE_CLASSPATH_PROPERTY = "corenth.architecture.classpath";

    private static final String ADYTON = "com.aresstack.corenth.adyton..";
    private static final String ASTU = "com.aresstack.corenth.astu..";
    private static final String PROASTEION = "com.aresstack.corenth.proasteion..";
    private static final String EXEDRA = "com.aresstack.corenth.proasteion.exedra..";
    private static final String KATAGOGION = "com.aresstack.corenth.proasteion.katagogion..";
    private static final String HOLKAS = "com.aresstack.corenth.proasteion.emporion.holkas..";
    private static final String DEIGMA = "com.aresstack.corenth.proasteion.emporion.deigma..";
    private static final String CHALCOTHECA_ROOT = "com.aresstack.corenth.astu.acropolis.chalcotheca";
    private static final String TAMIAS = "com.aresstack.corenth.astu.acropolis.chalcotheca.tamias..";
    private static final String ANAGRAPHAI = "com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai..";
    private static final String PINAKES = "com.aresstack.corenth.astu.acropolis.chalcotheca.pinakes..";
    private static final String PROPYLAEA = "com.aresstack.corenth.astu.propylaea..";
    private static final String PLATFORM_NETWORK = "com.aresstack.corenth.proasteion.platform.network..";
    private static final String PLATFORM_SECURITY_KEEPASSRPC = "com.aresstack.corenth.proasteion.platform.security.keepassrpc..";

    private static JavaClasses corenthClasses;

    private static final ArchRule INNER_CITY_MUST_NOT_DEPEND_ON_OUTER_RING = noClasses()
            .that().resideInAnyPackage(ADYTON, ASTU)
            .should().dependOnClassesThat().resideInAnyPackage(PROASTEION)
            .because("the inner city must not depend on the outer adapter ring");

    private static final ArchRule CORE_MUST_NOT_DEPEND_ON_UI_TECHNOLOGY = noClasses()
            .that().resideInAnyPackage(ADYTON, ASTU)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "javax.swing..",
                    "java.awt..",
                    "javafx..",
                    EXEDRA)
            .because("core modules must stay independent from local UI technology");

    private static final ArchRule CLIENT_ADAPTERS_MUST_NOT_BYPASS_MEDIATED_RESOURCE_ACCESS = noClasses()
            .that().resideInAnyPackage(EXEDRA, KATAGOGION)
            .should().dependOnClassesThat().resideInAnyPackage(HOLKAS)
            .because("client-facing adapters must go through Chalcotheca mediated access instead of Holkas directly");

    private static final ArchRule CLIENT_ADAPTERS_MUST_NOT_BYPASS_RESOURCE_POLICY = noClasses()
            .that().resideInAnyPackage(EXEDRA, KATAGOGION)
            .should().dependOnClassesThat().resideInAnyPackage(TAMIAS)
            .because("client-facing adapters must not make access-policy decisions directly");

    private static final ArchRule HOLKAS_MUST_STAY_RAW_AND_UI_FREE = noClasses()
            .that().resideInAnyPackage(HOLKAS)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "javax.swing..",
                    "java.awt..",
                    "javafx..",
                    EXEDRA,
                    DEIGMA,
                    TAMIAS,
                    ANAGRAPHAI,
                    PINAKES,
                    PROPYLAEA)
            .because("Holkas fetches raw resources and must not parse, index, enforce policy, or know the UI adapter");

    private static final ArchRule DEIGMA_MUST_STAY_SHALLOW_EXTRACTION = noClasses()
            .that().resideInAnyPackage(DEIGMA)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "javax.swing..",
                    "java.awt..",
                    "javafx..",
                    ADYTON,
                    HOLKAS,
                    CHALCOTHECA_ROOT,
                    TAMIAS,
                    ANAGRAPHAI,
                    PINAKES,
                    PROPYLAEA,
                    EXEDRA)
            .because("Deigma detects and extracts shallow content without policy, lifecycle, indexing, source acquisition, or UI coupling");

    private static final ArchRule TAMIAS_MUST_STAY_POLICY_STEWARD = noClasses()
            .that().resideInAnyPackage(TAMIAS)
            .should().dependOnClassesThat().resideInAnyPackage(
                    PROASTEION,
                    CHALCOTHECA_ROOT,
                    ANAGRAPHAI,
                    PINAKES,
                    PROPYLAEA,
                    ADYTON)
            .because("Tamias owns access and cache policy without knowing adapters, archive lifecycle, indexing, or secrets");

    private static final ArchRule ANAGRAPHAI_MUST_STAY_LEXICAL_ONLY = noClasses()
            .that().resideInAnyPackage(ANAGRAPHAI)
            .should().dependOnClassesThat().resideInAnyPackage(
                    PROASTEION,
                    CHALCOTHECA_ROOT,
                    TAMIAS,
                    PINAKES,
                    PROPYLAEA,
                    ADYTON)
            .because("Anagraphai is the lexical register and must not know semantic indexes, policy, lifecycle, adapters, or secrets");

    private static final ArchRule EXEDRA_MUST_STAY_THIN_UI_SHELL = noClasses()
            .that().resideInAnyPackage(EXEDRA)
            .should().dependOnClassesThat().resideInAnyPackage(
                    HOLKAS,
                    DEIGMA,
                    TAMIAS,
                    ANAGRAPHAI,
                    PINAKES,
                    ADYTON)
            .because("Exedra is a concrete Swing shell and must not own acquisition, extraction, indexing, policy, or credential flow");

    private static final ArchRule RAW_SECRET_MATERIAL_MUST_STAY_INSIDE_VAULT_OR_TRUSTED_SECRET_ADAPTER = noClasses()
            .that().resideOutsideOfPackages(ADYTON, PLATFORM_SECURITY_KEEPASSRPC)
            .should().dependOnClassesThat(secretMaterialTypes())
            .because("raw secret material is Adyton vault state and may only be used by trusted secret adapters");

    private static final ArchRule SECRET_REFERENCES_MUST_STAY_INSIDE_VAULT_OR_TRUSTED_PLATFORM_ADAPTERS = noClasses()
            .that().resideOutsideOfPackages(ADYTON, PLATFORM_NETWORK, PLATFORM_SECURITY_KEEPASSRPC)
            .should().dependOnClassesThat(secretReferenceTypes())
            .because("normal modules must receive grants, leases, handles, or mediated requests instead of secret references");

    private static final ArchRule SECRET_MATERIAL_PROVIDERS_MUST_LIVE_IN_VAULT_OR_TRUSTED_SECRET_ADAPTER = classes()
            .that().implement("com.aresstack.corenth.adyton.SecretMaterialProvider")
            .should().resideInAnyPackage(ADYTON, PLATFORM_SECURITY_KEEPASSRPC)
            .because("only the vault and explicitly trusted secret adapters may resolve SecretRef values into SecretMaterial");

    private static final ArchRule PRODUCTION_CODE_MUST_NOT_DECLARE_PASSWORD_GETTERS = noMethods()
            .that().haveName("getPassword")
            .should().beDeclaredInClassesThat().resideInAnyPackage("com.aresstack.corenth..")
            .because("production code must not expose raw passwords through JavaBean-style getters")
            .allowEmptyShould(true);

    private static final ArchRule TOP_LEVEL_CITY_DISTRICTS_MUST_BE_FREE_OF_CYCLES = slices()
            .matching("com.aresstack.corenth.(*)..")
            .should().beFreeOfCycles()
            .because("top-level city districts must not form bidirectional dependencies");

    private static final ArchRule EMPORION_DISTRICTS_MUST_BE_FREE_OF_CYCLES = slices()
            .matching("com.aresstack.corenth.proasteion.emporion.(*)..")
            .should().beFreeOfCycles()
            .because("Emporion subdistricts must not grow reciprocal acquisition/extraction dependencies");

    private static final ArchRule CHALCOTHECA_REGISTERS_MUST_BE_FREE_OF_CYCLES = slices()
            .matching("com.aresstack.corenth.astu.acropolis.chalcotheca.(*)..")
            .should().beFreeOfCycles()
            .because("Chalcotheca policy, lexical, semantic, and gateway registers must stay independently replaceable");

    @BeforeAll
    public static void importCorenthProductionClasses() {
        corenthClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPaths(productionClassPaths());
    }

    @Test
    public void innerCityMustNotDependOnOuterRing() {
        INNER_CITY_MUST_NOT_DEPEND_ON_OUTER_RING.check(corenthClasses);
    }

    @Test
    public void coreMustNotDependOnUiTechnology() {
        CORE_MUST_NOT_DEPEND_ON_UI_TECHNOLOGY.check(corenthClasses);
    }

    @Test
    public void clientAdaptersMustNotBypassMediatedResourceAccess() {
        CLIENT_ADAPTERS_MUST_NOT_BYPASS_MEDIATED_RESOURCE_ACCESS.check(corenthClasses);
    }

    @Test
    public void clientAdaptersMustNotBypassResourcePolicy() {
        CLIENT_ADAPTERS_MUST_NOT_BYPASS_RESOURCE_POLICY.check(corenthClasses);
    }

    @Test
    public void holkasMustStayRawAndUiFree() {
        HOLKAS_MUST_STAY_RAW_AND_UI_FREE.check(corenthClasses);
    }

    @Test
    public void deigmaMustStayShallowExtraction() {
        DEIGMA_MUST_STAY_SHALLOW_EXTRACTION.check(corenthClasses);
    }

    @Test
    public void tamiasMustStayPolicySteward() {
        TAMIAS_MUST_STAY_POLICY_STEWARD.check(corenthClasses);
    }

    @Test
    public void anagraphaiMustStayLexicalOnly() {
        ANAGRAPHAI_MUST_STAY_LEXICAL_ONLY.check(corenthClasses);
    }

    @Test
    public void exedraMustStayThinUiShell() {
        EXEDRA_MUST_STAY_THIN_UI_SHELL.check(corenthClasses);
    }

    @Test
    public void rawSecretMaterialMustStayInsideVaultOrTrustedSecretAdapter() {
        RAW_SECRET_MATERIAL_MUST_STAY_INSIDE_VAULT_OR_TRUSTED_SECRET_ADAPTER.check(corenthClasses);
    }

    @Test
    public void secretReferencesMustStayInsideVaultOrTrustedPlatformAdapters() {
        SECRET_REFERENCES_MUST_STAY_INSIDE_VAULT_OR_TRUSTED_PLATFORM_ADAPTERS.check(corenthClasses);
    }

    @Test
    public void secretMaterialProvidersMustLiveInVaultOrTrustedSecretAdapter() {
        SECRET_MATERIAL_PROVIDERS_MUST_LIVE_IN_VAULT_OR_TRUSTED_SECRET_ADAPTER.check(corenthClasses);
    }

    @Test
    public void productionCodeMustNotDeclarePasswordGetters() {
        PRODUCTION_CODE_MUST_NOT_DECLARE_PASSWORD_GETTERS.check(corenthClasses);
    }

    @Test
    public void topLevelCityDistrictsMustBeFreeOfCycles() {
        TOP_LEVEL_CITY_DISTRICTS_MUST_BE_FREE_OF_CYCLES.check(corenthClasses);
    }

    @Test
    public void emporionDistrictsMustBeFreeOfCycles() {
        EMPORION_DISTRICTS_MUST_BE_FREE_OF_CYCLES.check(corenthClasses);
    }

    @Test
    public void chalcothecaRegistersMustBeFreeOfCycles() {
        CHALCOTHECA_REGISTERS_MUST_BE_FREE_OF_CYCLES.check(corenthClasses);
    }

    private static List<Path> productionClassPaths() {
        String architectureClasspath = System.getProperty(ARCHITECTURE_CLASSPATH_PROPERTY, "");
        String[] classPathEntries = architectureClasspath.split(Pattern.quote(File.pathSeparator));
        List<Path> classPaths = new ArrayList<Path>();

        for (String classPathEntry : classPathEntries) {
            if (!classPathEntry.trim().isEmpty()) {
                classPaths.add(Paths.get(classPathEntry));
            }
        }

        if (classPaths.isEmpty()) {
            throw new IllegalStateException("Missing production class directories in " + ARCHITECTURE_CLASSPATH_PROPERTY);
        }

        return classPaths;
    }

    private static DescribedPredicate<JavaClass> secretMaterialTypes() {
        return haveFullyQualifiedNames(
                "com.aresstack.corenth.adyton.SecretMaterial",
                "com.aresstack.corenth.adyton.SecretMaterialFactory",
                "com.aresstack.corenth.adyton.SecretMaterialProvider");
    }

    private static DescribedPredicate<JavaClass> secretReferenceTypes() {
        return haveFullyQualifiedNames(
                "com.aresstack.corenth.adyton.SecretRef",
                "com.aresstack.corenth.adyton.CredentialRef");
    }

    private static DescribedPredicate<JavaClass> haveFullyQualifiedNames(String... fullyQualifiedNames) {
        final Set<String> acceptedNames = new HashSet<String>(Arrays.asList(fullyQualifiedNames));
        return DescribedPredicate.describe("have fully qualified name in " + acceptedNames,
                javaClass -> acceptedNames.contains(javaClass.getName()));
    }
}
