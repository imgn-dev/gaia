package be.imgn.gaia.processing.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.SourceVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GeneratedNameTest {

    private static final String PACKAGE = "com.example";

    // Prefixes and suffixes, with underscores where they could merge with the separators.
    private static final List<String> AFFIXES = List.of("M", "M_", "_M", "A_M", "_", "$", "A");

    // Every simple name over a few characters, up to a length, the underscore keyword excepted.
    private static List<String> simpleNames(int maxLength) {
        List<String> names = new ArrayList<>();
        List<String> shorter = List.of("");
        for (int length = 1; length <= maxLength; length++) {
            List<String> current = new ArrayList<>();
            for (String prefix : shorter) {
                for (char character : new char[] {'A', '_', '$', '0'}) {
                    current.add(prefix + character);
                }
            }
            current.stream()
                    .filter(name -> SourceVersion.isIdentifier(name) && !SourceVersion.isKeyword(name))
                    .forEach(names::add);
            shorter = current;
        }
        return names;
    }

    // Every type of up to two names of up to three characters, and of three names of up to two characters.
    private static List<ClassName> types() {
        List<String> names = simpleNames(3);
        List<String> shortNames = simpleNames(2);
        List<ClassName> types = new ArrayList<>();
        for (String first : names) {
            types.add(ClassName.of(PACKAGE, first));
            for (String second : names) {
                types.add(ClassName.of(PACKAGE, first, second));
            }
        }
        for (String first : shortNames) {
            for (String second : shortNames) {
                for (String third : shortNames) {
                    types.add(ClassName.of(PACKAGE, first, second, third));
                }
            }
        }
        return types;
    }

    private static void collect(Map<ClassName, String> byGenerated, ClassName generated, String source) {
        String previous = byGenerated.putIfAbsent(generated, source);
        assertThat(previous)
                .as("%s is generated for both %s and %s", generated, previous, source)
                .isNull();
    }

    // The names a generated name is made of, in order: the prefix or the suffix, and the simple names of the type.
    private static void collectParts(
            Map<ClassName, List<String>> byGenerated, ClassName generated, List<String> parts) {
        List<String> previous = byGenerated.putIfAbsent(generated, parts);
        if (previous != null) {
            assertThat(previous)
                    .as("%s is generated for both %s and %s", generated, previous, parts)
                    .isEqualTo(parts);
        }
    }

    @Test
    void namesATopLevelTypeAfterItAndTheSuffix() {
        ClassName generated = ClassName.of(PACKAGE, "Outer").generatedNameWithSuffix("Mapper");

        assertThat(generated).isEqualTo(ClassName.of(PACKAGE, "Outer_Mapper"));
        assertThat(generated.canonicalName()).isEqualTo("com.example.Outer_Mapper");
    }

    @Test
    void namesANestedTypeAfterEachEnclosingTypeAsATopLevelTypeOfTheSamePackage() {
        ClassName generated = ClassName.of(PACKAGE, "Outer", "Inner").generatedNameWithSuffix("Mapper");

        assertThat(generated.canonicalName()).isEqualTo("com.example.Outer_Inner_Mapper");
        assertThat(generated.packageName()).isEqualTo(PACKAGE);
        assertThat(generated.simpleNames()).containsExactly("Outer_Inner_Mapper");
        assertThat(generated.enclosing()).isEmpty();
    }

    @Test
    void namesADeeplyNestedTypeAfterAllItsEnclosingTypes() {
        ClassName generated = ClassName.of(PACKAGE, "A", "B", "C", "D", "E").generatedNameWithSuffix("Builder");

        assertThat(generated).isEqualTo(ClassName.of(PACKAGE, "A_B_C_D_E_Builder"));
    }

    @Test
    void keepsTheUnnamedPackage() {
        ClassName generated = ClassName.of("", "Outer", "Inner").generatedNameWithSuffix("Mapper");

        assertThat(generated.packageName()).isEmpty();
        assertThat(generated.canonicalName()).isEqualTo("Outer_Inner_Mapper");
    }

    @Test
    void namesAJavaClassAfterItsName() {
        assertThat(ClassName.of(Map.Entry.class).generatedNameWithSuffix("Adapter"))
                .isEqualTo(ClassName.of("java.util", "Map_Entry_Adapter"));
    }

    @Test
    void leavesTheAnnotationsOut() {
        ClassName annotated = ClassName.of(PACKAGE, "Outer", "Inner")
                .annotated(Annotation.of(ClassName.of("org.example", "Nullable")));

        assertThat(annotated.generatedNameWithSuffix("Mapper").annotations()).isEmpty();
    }

    @Test
    void writesTheUnderscoresOfTheNamesSoThatNestedTypesDoNotClash() {
        ClassName nestedInUnderscored = ClassName.of(PACKAGE, "A_B", "C");
        ClassName underscoredNested = ClassName.of(PACKAGE, "A", "B_C");
        ClassName underscoredTopLevel = ClassName.of(PACKAGE, "A_B_C");

        assertThat(nestedInUnderscored.generatedNameWithSuffix("Mapper").simpleName())
                .isEqualTo("A_0B_C_Mapper");
        assertThat(underscoredNested.generatedNameWithSuffix("Mapper").simpleName())
                .isEqualTo("A_B_0C_Mapper");
        assertThat(underscoredTopLevel.generatedNameWithSuffix("Mapper").simpleName())
                .isEqualTo("A_0B_0C_Mapper");
    }

    @Test
    void writesLeadingAndTrailingUnderscoresSoThatTheyDoNotMergeWithTheSeparators() {
        assertThat(ClassName.of(PACKAGE, "A_", "B").generatedNameWithSuffix("M").simpleName())
                .isEqualTo("A_0_B_M");
        assertThat(ClassName.of(PACKAGE, "A", "_B").generatedNameWithSuffix("M").simpleName())
                .isEqualTo("A__0B_M");
        assertThat(ClassName.of(PACKAGE, "A__B").generatedNameWithSuffix("M").simpleName())
                .isEqualTo("A_0_0B_M");
        assertThat(ClassName.of(PACKAGE, "__").generatedNameWithSuffix("M").simpleName())
                .isEqualTo("_0_0_M");
    }

    @Test
    void writesTheUnderscoresOfTheSuffixSoThatSuffixesDoNotClash() {
        ClassName outer = ClassName.of(PACKAGE, "A");

        assertThat(outer.generatedNameWithSuffix("B_Mapper").simpleName()).isEqualTo("A_B_0Mapper");
        assertThat(outer.nested("B").generatedNameWithSuffix("Mapper").simpleName())
                .isEqualTo("A_B_Mapper");
        assertThat(outer.generatedNameWithSuffix("_").simpleName()).isEqualTo("A__0");
    }

    @Test
    void keepsDollarSigns() {
        assertThat(ClassName.of(PACKAGE, "Outer$Inner")
                        .generatedNameWithSuffix("Mapper")
                        .simpleName())
                .isEqualTo("Outer$Inner_Mapper");
        assertThat(ClassName.of(PACKAGE, "Outer", "Inner")
                        .generatedNameWithSuffix("$Proxy")
                        .simpleName())
                .isEqualTo("Outer_Inner_$Proxy");
    }

    @Test
    void neverGivesTwoTypesOrSuffixesTheSameName() {
        Map<ClassName, String> byGenerated = new HashMap<>();
        List<ClassName> types = types();
        for (String suffix : AFFIXES) {
            for (ClassName type : types) {
                collect(byGenerated, type.generatedNameWithSuffix(suffix), type.canonicalName() + " + " + suffix);
            }
        }

        assertThat(simpleNames(3)).contains("A_", "_A", "A__", "$_0", "_$");
        assertThat(byGenerated).hasSize(AFFIXES.size() * types.size());
    }

    @Test
    void neverGivesTwoTypesOrPrefixesTheSameName() {
        Map<ClassName, String> byGenerated = new HashMap<>();
        List<ClassName> types = types();
        for (String prefix : AFFIXES) {
            for (ClassName type : types) {
                collect(byGenerated, type.generatedNameWithPrefix(prefix), prefix + " + " + type.canonicalName());
            }
        }

        assertThat(byGenerated).hasSize(AFFIXES.size() * types.size());
    }

    @Test
    void givesAPrefixedAndASuffixedTypeTheSameNameOnlyWhenTheirNamesAreTheSameInOrder() {
        Map<ClassName, List<String>> byGenerated = new HashMap<>();
        Set<List<String>> allParts = new HashSet<>();
        List<ClassName> types = types();
        for (String affix : AFFIXES) {
            for (ClassName type : types) {
                List<String> suffixed = new ArrayList<>(type.simpleNames());
                suffixed.add(affix);
                collectParts(byGenerated, type.generatedNameWithSuffix(affix), suffixed);
                List<String> prefixed = new ArrayList<>();
                prefixed.add(affix);
                prefixed.addAll(type.simpleNames());
                collectParts(byGenerated, type.generatedNameWithPrefix(affix), prefixed);
                allParts.add(suffixed);
                allParts.add(prefixed);
            }
        }

        // One name per sequence of names; some are shared, such as A with the prefix $ and $ with the suffix A.
        assertThat(byGenerated).hasSize(allParts.size());
        assertThat(allParts).hasSizeLessThan(2 * AFFIXES.size() * types.size());
        assertThat(byGenerated).containsEntry(ClassName.of(PACKAGE, "$_A"), List.of("$", "A"));
    }

    @Test
    void namesATopLevelTypeAfterThePrefixAndIt() {
        ClassName generated = ClassName.of(PACKAGE, "Outer").generatedNameWithPrefix("Generated");

        assertThat(generated).isEqualTo(ClassName.of(PACKAGE, "Generated_Outer"));
        assertThat(generated.canonicalName()).isEqualTo("com.example.Generated_Outer");
    }

    @Test
    void namesANestedTypeAfterThePrefixAndEachEnclosingTypeAsATopLevelTypeOfTheSamePackage() {
        ClassName generated = ClassName.of(PACKAGE, "Outer", "Inner").generatedNameWithPrefix("Generated");

        assertThat(generated.canonicalName()).isEqualTo("com.example.Generated_Outer_Inner");
        assertThat(generated.simpleNames()).containsExactly("Generated_Outer_Inner");
        assertThat(generated.enclosing()).isEmpty();
        assertThat(ClassName.of(Map.Entry.class).generatedNameWithPrefix("AutoValue"))
                .isEqualTo(ClassName.of("java.util", "AutoValue_Map_Entry"));
    }

    @Test
    void keepsTheUnnamedPackageAndLeavesTheAnnotationsOutWithAPrefix() {
        ClassName annotated =
                ClassName.of("", "Outer", "Inner").annotated(Annotation.of(ClassName.of("org.example", "Nullable")));

        ClassName generated = annotated.generatedNameWithPrefix("Generated");

        assertThat(generated.packageName()).isEmpty();
        assertThat(generated.canonicalName()).isEqualTo("Generated_Outer_Inner");
        assertThat(generated.annotations()).isEmpty();
    }

    @Test
    void writesTheUnderscoresOfThePrefixAndTheNamesAsWithASuffix() {
        assertThat(ClassName.of(PACKAGE, "A_B", "C")
                        .generatedNameWithPrefix("P")
                        .simpleName())
                .isEqualTo("P_A_0B_C");
        assertThat(ClassName.of(PACKAGE, "A", "B_C")
                        .generatedNameWithPrefix("P")
                        .simpleName())
                .isEqualTo("P_A_B_0C");
        assertThat(ClassName.of(PACKAGE, "A").generatedNameWithPrefix("P_B").simpleName())
                .isEqualTo("P_0B_A");
        assertThat(ClassName.of(PACKAGE, "B", "A").generatedNameWithPrefix("P").simpleName())
                .isEqualTo("P_B_A");
        assertThat(ClassName.of(PACKAGE, "_A").generatedNameWithPrefix("_").simpleName())
                .isEqualTo("_0__0A");
        assertThat(ClassName.of(PACKAGE, "Inner")
                        .generatedNameWithPrefix("$Proxy")
                        .simpleName())
                .isEqualTo("$Proxy_Inner");
    }

    @Test
    void givesTheSameNameToAPrefixedAndASuffixedTypeWhoseNamesAreTheSameInOrder() {
        assertThat(ClassName.of(PACKAGE, "Outer", "Inner").generatedNameWithPrefix("Generated"))
                .isEqualTo(ClassName.of(PACKAGE, "Generated", "Outer").generatedNameWithSuffix("Inner"));
        assertThat(ClassName.of(PACKAGE, "Inner").generatedNameWithPrefix("Outer"))
                .isEqualTo(ClassName.of(PACKAGE, "Outer").generatedNameWithSuffix("Inner"));
        assertThat(ClassName.of(PACKAGE, "Outer").generatedNameWithPrefix("Inner"))
                .isNotEqualTo(ClassName.of(PACKAGE, "Outer").generatedNameWithSuffix("Inner"));
    }

    @Test
    void acceptsAKeywordOrANonAsciiIdentifierAsPrefix() {
        ClassName outer = ClassName.of(PACKAGE, "Outer");

        assertThat(outer.generatedNameWithPrefix("class").simpleName()).isEqualTo("class_Outer");
        assertThat(outer.generatedNameWithPrefix("Café").simpleName()).isEqualTo("Café_Outer");
        assertThat(outer.generatedNameWithPrefix("M2").simpleName()).isEqualTo("M2_Outer");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "2Mapper", "0", "Map.per", "Map-per", " Mapper", "Mapper ", "Map per", "Map<T>"})
    void rejectsAPrefixThatIsNotAnIdentifier(String prefix) {
        ClassName outer = ClassName.of(PACKAGE, "Outer");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> outer.generatedNameWithPrefix(prefix))
                .withMessage("Not a prefix for a generated type name: \"" + prefix + "\"");
    }

    @Test
    void acceptsAKeywordOrANonAsciiIdentifierAsSuffix() {
        ClassName outer = ClassName.of(PACKAGE, "Outer");

        assertThat(outer.generatedNameWithSuffix("class").simpleName()).isEqualTo("Outer_class");
        assertThat(outer.generatedNameWithSuffix("Café").simpleName()).isEqualTo("Outer_Café");
        assertThat(outer.generatedNameWithSuffix("M2").simpleName()).isEqualTo("Outer_M2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "2Mapper", "0", "Map.per", "Map-per", " Mapper", "Mapper ", "Map per", "Map<T>"})
    void rejectsASuffixThatIsNotAnIdentifier(String suffix) {
        ClassName outer = ClassName.of(PACKAGE, "Outer");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> outer.generatedNameWithSuffix(suffix))
                .withMessage("Not a suffix for a generated type name: \"" + suffix + "\"");
    }
}
