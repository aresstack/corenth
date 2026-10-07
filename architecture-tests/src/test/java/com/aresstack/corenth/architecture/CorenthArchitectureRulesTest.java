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

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Executable boundary rules for the Corenth city model.
 *
 * <p>Maintenance notes:
 * <ul>
 *   <li>The production classes under test come from the explicit {@code architectureProjects}
 *       list in {@code architecture-tests/build.gradle}. A Gradle project missing from that list
 *       fails the build configuration, so new modules must be added there deliberately (as
 *       {@code :proasteion:application} was in #10 Slice 2).</li>
 *   <li>The secret-containment rules whitelist the vault and the trusted secret adapters by
 *       package ({@link #PLATFORM_SECURITY_KEEPASSRPC}, {@link #PLATFORM_SECURITY_PROMPT},
 *       {@link #PLATFORM_NETWORK}). Every further secret-source adapter from #43 (encrypted store,
 *       DPAPI) must be added to these whitelists explicitly; otherwise its use of
 *       {@code SecretMaterial} fails these rules. All secret adapters stay UI-free.</li>
 *   <li>The outer composition root {@code proasteion.application} (ADR-0001) may depend on every
 *       adapter and on the inner city. {@link #APPLICATION_MUST_STAY_HEADLESS},
 *       {@link #ONLY_HOSTS_MAY_DEPEND_ON_APPLICATION}, {@link #APPLICATION_MUST_NOT_DECIDE_POLICIES}
 *       and {@link #APPLICATION_MUST_NOT_HOLD_STATIC_STATE} keep it headless, consumed only by
 *       hosts, policy-free and free of global state; {@link #ACQUISITION_BRIDGE_IS_WIRED_ONLY_AT_COMPOSITION_ROOT}
 *       makes it the only place that plugs Holkas into the archive counter. A new host module is
 *       added to the consumers by leaving it out of {@link #ONLY_HOSTS_MAY_DEPEND_ON_APPLICATION}.</li>
 *   <li>{@link #ACROPOLIS_LIFECYCLE_MUST_ACQUIRE_THROUGH_MEDIATED_ACCESS} covers every package of
 *       the {@code astu:acropolis} module except the nested {@code chalcotheca} registers, so new
 *       acropolis sub-packages (e.g. a run model) are covered automatically.
 *       {@link #ACROPOLIS_ROOT_PORTS_ARE_AN_EXPLICIT_WHITELIST} lists the inward ports the acropolis
 *       root package may declare; a new port there is a deliberate whitelist change, never an
 *       accidental second acquisition path.</li>
 * </ul>
 */
public class CorenthArchitectureRulesTest {

    private static final String ARCHITECTURE_CLASSPATH_PROPERTY = "corenth.architecture.classpath";

    private static final String ADYTON = "com.aresstack.corenth.adyton..";
    private static final String ASTU = "com.aresstack.corenth.astu..";
    private static final String ACROPOLIS_ROOT = "com.aresstack.corenth.astu.acropolis";
    private static final String PROASTEION = "com.aresstack.corenth.proasteion..";
    private static final String EXEDRA = "com.aresstack.corenth.proasteion.exedra..";
    private static final String APPLICATION = "com.aresstack.corenth.proasteion.application..";
    private static final String EMPORION = "com.aresstack.corenth.proasteion.emporion..";
    private static final String PLATFORM = "com.aresstack.corenth.proasteion.platform..";
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
    private static final String PLATFORM_SECURITY_PROMPT = "com.aresstack.corenth.proasteion.platform.security.prompt..";
    private static final String PLATFORM_SECURITY = "com.aresstack.corenth.proasteion.platform.security..";
    private static final String MEDIATED_RESOURCE_SERVICE = "com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResourceService";
    private static final String ACQUISITION_PORT = "com.aresstack.corenth.astu.acropolis.chalcotheca.AcquisitionPort";
    private static final String CONTENT_INSPECTOR = "com.aresstack.corenth.astu.acropolis.ContentInspector";
    private static final String HOLKAS_ACQUISITION_PORT = "com.aresstack.corenth.proasteion.emporion.holkas.HolkasAcquisitionPort";
    private static final String RESOURCE_ACCESS_POLICY = "com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessPolicy";
    private static final String RESOURCE_POLICY = "com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourcePolicy";

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

    private static final ArchRule ACROPOLIS_LIFECYCLE_MUST_ACQUIRE_THROUGH_MEDIATED_ACCESS = noClasses()
            .that(resideInAPackage(ACROPOLIS_ROOT + "..").and(not(resideInAPackage(CHALCOTHECA_ROOT + ".."))))
            .should().dependOnClassesThat(
                    haveFullyQualifiedNames(MEDIATED_RESOURCE_SERVICE, ACQUISITION_PORT)
                            .or(resideInAnyPackage(HOLKAS)))
            .because("the Acropolis lifecycle obtains resources only through the MediatedResourceAccess contract; "
                    + "the concrete archive counter, the acquisition port and Holkas are wired at the composition point (ADR-0001)");

    private static final ArchRule ACROPOLIS_ROOT_PORTS_ARE_AN_EXPLICIT_WHITELIST = classes()
            .that().resideInAPackage(ACROPOLIS_ROOT).and().areInterfaces()
            .should().haveFullyQualifiedName(CONTENT_INSPECTOR)
            .because("the lifecycle's only inward port besides MediatedResourceAccess (chalcotheca) is ContentInspector; "
                    + "a new acquisition-shaped port in acropolis is a deliberate whitelist change, not a second fetch path");

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

    private static final ArchRule TAMIAS_MUST_DECIDE_FROM_HANDED_IN_FACTS = noClasses()
            .that().resideInAnyPackage(TAMIAS)
            .should().dependOnClassesThat().resideInAPackage(ACROPOLIS_ROOT)
            .because("Tamias decides from fact projections that the Acropolis orchestration (#10) hands in; "
                    + "it must not know the orchestration that executes its decisions (#5)");

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

    private static final ArchRule PROPYLAEA_MUST_STAY_A_PURE_SOURCE_GATE = noClasses()
            .that().resideInAnyPackage(PROPYLAEA)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.aresstack.corenth.astu.acropolis..",
                    PROASTEION,
                    ADYTON,
                    "java.nio.file..",
                    "java.net..")
            .because("Propylaea parses source text handed in by the caller into a language-neutral structure; "
                    + "it must not read resources, know lifecycle, policy, indexing, adapters, or secrets (#3)");

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

    private static final ArchRule APPLICATION_MUST_STAY_HEADLESS = noClasses()
            .that().resideInAnyPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "javax.swing..",
                    "java.awt..",
                    "javafx..",
                    EXEDRA)
            .because("the composition root must run in CI, tests and future CLI/server hosts without a display (ADR-0001)");

    private static final ArchRule ONLY_HOSTS_MAY_DEPEND_ON_APPLICATION = noClasses()
            .that().resideInAnyPackage(ADYTON, ASTU, EMPORION, PLATFORM, KATAGOGION)
            .should().dependOnClassesThat().resideInAnyPackage(APPLICATION)
            .because("the composition root depends inward on contracts and adapters; only hosts such as Exedra may consume it (ADR-0001)");

    private static final ArchRule ACQUISITION_BRIDGE_IS_WIRED_ONLY_AT_COMPOSITION_ROOT = noClasses()
            .that().resideOutsideOfPackages(HOLKAS, APPLICATION)
            .should().dependOnClassesThat(haveFullyQualifiedNames(HOLKAS_ACQUISITION_PORT))
            .because("the concrete Holkas AcquisitionPort bridge is plugged into the archive counter only by the outer composition root");

    private static final ArchRule APPLICATION_MUST_NOT_DECIDE_POLICIES = noClasses()
            .that().resideInAnyPackage(APPLICATION)
            .should().implement(RESOURCE_ACCESS_POLICY)
            .orShould().implement(RESOURCE_POLICY)
            .because("the composition root selects and parameterises Tamias policies but never decides access or indexing itself");

    private static final ArchRule APPLICATION_MUST_NOT_HOLD_STATIC_STATE = fields()
            .that().areDeclaredInClassesThat().resideInAnyPackage(APPLICATION)
            .and().areStatic()
            .should().beFinal()
            .andShould().haveRawType(constantTypes())
            .because("the composition root is instantiated explicitly; no global singletons or static registries (ADR-0001)")
            .allowEmptyShould(true);

    private static final ArchRule ACROPOLIS_AND_CHALCOTHECA_MUST_NOT_SEE_THE_VAULT = noClasses()
            .that().resideInAPackage(ACROPOLIS_ROOT + "..")
            .should().dependOnClassesThat().resideInAnyPackage(ADYTON)
            .because("the archive counter and the lifecycle prepare authenticated acquisitions only through the opaque "
                    + "AcquisitionAccessPort capability; secret material, references and handles stay in Adyton and the "
                    + "outer adapters (#10 Slice 3)");

    private static final ArchRule RAW_SECRET_MATERIAL_MUST_STAY_INSIDE_VAULT_OR_TRUSTED_SECRET_ADAPTER = noClasses()
            .that().resideOutsideOfPackages(ADYTON, PLATFORM_SECURITY_KEEPASSRPC, PLATFORM_SECURITY_PROMPT)
            .should().dependOnClassesThat(secretMaterialTypes())
            .because("raw secret material is Adyton vault state and may only be used by trusted secret adapters");

    private static final ArchRule SECRET_REFERENCES_MUST_STAY_INSIDE_VAULT_OR_TRUSTED_PLATFORM_ADAPTERS = noClasses()
            .that().resideOutsideOfPackages(ADYTON, PLATFORM_NETWORK, PLATFORM_SECURITY_KEEPASSRPC, PLATFORM_SECURITY_PROMPT)
            .should().dependOnClassesThat(secretReferenceTypes())
            .because("normal modules must receive grants, leases, handles, or mediated requests instead of secret references");

    private static final ArchRule SECRET_MATERIAL_PROVIDERS_MUST_LIVE_IN_VAULT_OR_TRUSTED_SECRET_ADAPTER = classes()
            .that().implement("com.aresstack.corenth.adyton.SecretMaterialProvider")
            .should().resideInAnyPackage(ADYTON, PLATFORM_SECURITY_KEEPASSRPC, PLATFORM_SECURITY_PROMPT)
            .because("only the vault and explicitly trusted secret adapters may resolve SecretRef values into SecretMaterial");

    private static final ArchRule SECRET_ADAPTERS_MUST_STAY_UI_FREE = noClasses()
            .that().resideInAnyPackage(PLATFORM_SECURITY)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "javax.swing..",
                    "java.awt..",
                    "javafx..",
                    EXEDRA)
            .because("trusted secret adapters reach people only through UI-free ports such as SecretPromptPort");

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
    public void acropolisLifecycleMustAcquireThroughMediatedAccess() {
        ACROPOLIS_LIFECYCLE_MUST_ACQUIRE_THROUGH_MEDIATED_ACCESS.check(corenthClasses);
    }

    @Test
    public void acropolisRootPortsAreAnExplicitWhitelist() {
        ACROPOLIS_ROOT_PORTS_ARE_AN_EXPLICIT_WHITELIST.check(corenthClasses);
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
    public void tamiasMustDecideFromHandedInFacts() {
        TAMIAS_MUST_DECIDE_FROM_HANDED_IN_FACTS.check(corenthClasses);
    }

    @Test
    public void anagraphaiMustStayLexicalOnly() {
        ANAGRAPHAI_MUST_STAY_LEXICAL_ONLY.check(corenthClasses);
    }

    @Test
    public void propylaeaMustStayAPureSourceGate() {
        PROPYLAEA_MUST_STAY_A_PURE_SOURCE_GATE.check(corenthClasses);
    }

    @Test
    public void exedraMustStayThinUiShell() {
        EXEDRA_MUST_STAY_THIN_UI_SHELL.check(corenthClasses);
    }

    @Test
    public void applicationMustStayHeadless() {
        APPLICATION_MUST_STAY_HEADLESS.check(corenthClasses);
    }

    @Test
    public void onlyHostsMayDependOnApplication() {
        ONLY_HOSTS_MAY_DEPEND_ON_APPLICATION.check(corenthClasses);
    }

    @Test
    public void acquisitionBridgeIsWiredOnlyAtCompositionRoot() {
        ACQUISITION_BRIDGE_IS_WIRED_ONLY_AT_COMPOSITION_ROOT.check(corenthClasses);
    }

    @Test
    public void applicationMustNotDecidePolicies() {
        APPLICATION_MUST_NOT_DECIDE_POLICIES.check(corenthClasses);
    }

    @Test
    public void applicationMustNotHoldStaticState() {
        APPLICATION_MUST_NOT_HOLD_STATIC_STATE.check(corenthClasses);
    }

    @Test
    public void acropolisAndChalcothecaMustNotSeeTheVault() {
        ACROPOLIS_AND_CHALCOTHECA_MUST_NOT_SEE_THE_VAULT.check(corenthClasses);
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
    public void secretAdaptersMustStayUiFree() {
        SECRET_ADAPTERS_MUST_STAY_UI_FREE.check(corenthClasses);
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

    private static DescribedPredicate<JavaClass> constantTypes() {
        return DescribedPredicate.describe("a primitive or String constant type",
                javaClass -> javaClass.isPrimitive() || javaClass.getName().equals(String.class.getName()));
    }

    private static DescribedPredicate<JavaClass> haveFullyQualifiedNames(String... fullyQualifiedNames) {
        final Set<String> acceptedNames = new HashSet<String>(Arrays.asList(fullyQualifiedNames));
        return DescribedPredicate.describe("have fully qualified name in " + acceptedNames,
                javaClass -> acceptedNames.contains(javaClass.getName()));
    }
}
