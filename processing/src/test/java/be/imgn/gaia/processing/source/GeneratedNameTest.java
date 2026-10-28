package be.imgn.gaia.processing.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.SourceVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GeneratedNameTest {

    private static final String PACKAGE = "com.example";

    // Kinds, with underscores and digits where they could merge with the names before them.
    private static final List<String> KINDS = List.of("K", "A", "_", "_K", "K_", "K0");

    // Overload numbers: none, and numbers that could be read as the digits of an escaped dollar sign.
    private static final List<Integer> NUMBERS = List.of(0, 1, 2, 10);

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
    private static List<ClassName> types(String packageName) {
        List<String> names = simpleNames(3);
        List<String> shortNames = simpleNames(2);
        List<ClassName> types = new ArrayList<>();
        for (String first : names) {
            types.add(ClassName.of(packageName, first));
            for (String second : names) {
                types.add(ClassName.of(packageName, first, second));
            }
        }
        for (String first : shortNames) {
            for (String second : shortNames) {
                for (String third : shortNames) {
                    types.add(ClassName.of(packageName, first, second, third));
                }
            }
        }
        return types;
    }

    // Every type of one or two names of up to two characters.
    private static List<ClassName> shortTypes(String packageName) {
        List<String> names = simpleNames(2);
        List<ClassName> types = new ArrayList<>();
        for (String first : names) {
            types.add(ClassName.of(packageName, first));
            for (String second : names) {
                types.add(ClassName.of(packageName, first, second));
            }
        }
        return types;
    }

    // The unnamed package, and every package of one or two names of up to two characters.
    private static List<String> packages() {
        List<String> names = simpleNames(2);
        List<String> packages = new ArrayList<>();
        packages.add("");
        for (String first : names) {
            packages.add(first);
            for (String second : names) {
                packages.add(first + "." + second);
            }
        }
        return packages;
    }

    private static void collect(Map<ClassName, String> bySource, ClassName generated, String source) {
        String previous = bySource.putIfAbsent(generated, source);
        assertThat(previous)
                .as("%s is generated for both %s and %s", generated, previous, source)
                .isNull();
    }

    @Test
    void namesATopLevelTypeAfterItAndTheKind() {
        ClassName generated = ClassName.of(PACKAGE, "Outer").generatedName("Mapper");

        assertThat(generated).isEqualTo(ClassName.of(PACKAGE, "$Outer$$Mapper"));
        assertThat(generated.canonicalName()).isEqualTo("com.example.$Outer$$Mapper");
    }

    @Test
    void namesANestedTypeAfterEachEnclosingTypeAsATopLevelTypeOfTheSamePackage() {
        ClassName generated = ClassName.of(PACKAGE, "Outer", "Inner").generatedName("Mapper");

        assertThat(generated.canonicalName()).isEqualTo("com.example.$Outer$Inner$$Mapper");
        assertThat(generated.simpleNames()).containsExactly("$Outer$Inner$$Mapper");
        assertThat(generated.enclosing()).isEmpty();
        assertThat(ClassName.of(Map.Entry.class).generatedName("Adapter"))
                .isEqualTo(ClassName.of("java.util", "$Map$Entry$$Adapter"));
    }

    @Test
    void keepsTheUnnamedPackageAndLeavesTheAnnotationsOut() {
        ClassName annotated =
                ClassName.of("", "Outer", "Inner").annotated(Annotation.of(ClassName.of("org.example", "Nullable")));

        ClassName generated = annotated.generatedName("Mapper");

        assertThat(generated.canonicalName()).isEqualTo("$Outer$Inner$$Mapper");
        assertThat(generated.annotations()).isEmpty();
    }

    @Test
    void writesTheDollarSignsOfTheNamesSoThatNestedTypesDoNotClash() {
        assertThat(ClassName.of(PACKAGE, "A$B", "C").generatedName("M").simpleName())
                .isEqualTo("$A$0B$C$$M");
        assertThat(ClassName.of(PACKAGE, "A", "B$C").generatedName("M").simpleName())
                .isEqualTo("$A$B$0C$$M");
        assertThat(ClassName.of(PACKAGE, "A", "B", "C").generatedName("M").simpleName())
                .isEqualTo("$A$B$C$$M");
        assertThat(ClassName.of(PACKAGE, "$").generatedName("M").simpleName()).isEqualTo("$$0$$M");
    }

    @Test
    void namesATypeForAPackageAfterTheKind() {
        assertThat(ClassName.generatedName(PACKAGE, "Mappers")).isEqualTo(ClassName.of(PACKAGE, "$$Mappers"));
        assertThat(ClassName.generatedName("", "Mappers").canonicalName()).isEqualTo("$$Mappers");
    }

    @Test
    void namesATypeForAMethodAfterItsTypeItAndTheKind() {
        ClassName type = ClassName.of(PACKAGE, "Outer", "Inner");

        assertThat(type.generatedMethodName("create", 0, "Factory").canonicalName())
                .isEqualTo("com.example.$Outer$Inner$$create$$Factory");
        assertThat(type.generatedMethodName("create", 2, "Factory").simpleName())
                .isEqualTo("$Outer$Inner$$create$2$$Factory");
        assertThat(type.generatedMethodName("a$1", 1, "Factory").simpleName())
                .isEqualTo("$Outer$Inner$$a$01$1$$Factory");
    }

    @Test
    void namesATypeInAnotherPackageAfterThePackageTheTypeAndTheKind() {
        ClassName named = ClassName.of(PACKAGE, "Outer", "Named");

        assertThat(named.generatedNameIn("org.app", "Impl").canonicalName())
                .isEqualTo("org.app.$$com$example$$Outer$Named$$Impl");
        assertThat(named.generatedNameIn("", "Impl").canonicalName()).isEqualTo("$$com$example$$Outer$Named$$Impl");
        assertThat(ClassName.of("", "Named").generatedNameIn("org.app", "Impl").simpleName())
                .isEqualTo("$$$$Named$$Impl");
        assertThat(ClassName.of("a$b", "Named")
                        .generatedNameIn("org.app", "Impl")
                        .simpleName())
                .isEqualTo("$$a$0b$$Named$$Impl");
    }

    @Test
    void neverGivesTwoTypesKindsOrMethodsTheSameNameInAPackage() {
        Map<ClassName, String> bySource = new HashMap<>();
        for (String kind : KINDS) {
            collect(bySource, ClassName.generatedName(PACKAGE, kind), "package + " + kind);
            for (ClassName type : types(PACKAGE)) {
                collect(bySource, type.generatedName(kind), type.canonicalName() + " + " + kind);
            }
        }
        for (String kind : KINDS.subList(0, 2)) {
            for (ClassName type : shortTypes(PACKAGE)) {
                for (String method : simpleNames(2)) {
                    for (int number : NUMBERS) {
                        collect(
                                bySource,
                                type.generatedMethodName(method, number, kind),
                                type.canonicalName() + "#" + method + " " + number + " + " + kind);
                    }
                }
            }
            for (String packageName : packages()) {
                for (ClassName type : shortTypes(packageName)) {
                    collect(
                            bySource,
                            type.generatedNameIn(PACKAGE, kind),
                            packageName + " / " + type.canonicalName() + " + " + kind);
                }
            }
        }

        assertThat(simpleNames(3)).contains("A$", "$A", "A$$", "$0", "A$0");
        assertThat(bySource).containsEntry(ClassName.of(PACKAGE, "$$K"), "package + K");
    }

    @Test
    void acceptsAKeywordOrANonAsciiIdentifierAsKind() {
        ClassName outer = ClassName.of(PACKAGE, "Outer");

        assertThat(outer.generatedName("class").simpleName()).isEqualTo("$Outer$$class");
        assertThat(outer.generatedName("Café").simpleName()).isEqualTo("$Outer$$Café");
        assertThat(outer.generatedName("M2").simpleName()).isEqualTo("$Outer$$M2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "2Mapper", "Map.per", "Map per", "Map$per", "$"})
    void rejectsAKindThatIsNotAnIdentifierWithoutADollarSign(String kind) {
        ClassName outer = ClassName.of(PACKAGE, "Outer");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> outer.generatedName(kind))
                .withMessage("Not a kind of generated type: \"" + kind + "\"");
    }

    @Test
    void checksTheKindAndThePackageOfEveryName() {
        ClassName outer = ClassName.of(PACKAGE, "Outer");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ClassName.generatedName(PACKAGE, "Map$per"))
                .withMessage("Not a kind of generated type: \"Map$per\"");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ClassName.generatedName("com..example", "Mappers"))
                .withMessage("Not a package name: com..example");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> outer.generatedNameIn("org.app", "Map$per"))
                .withMessage("Not a kind of generated type: \"Map$per\"");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> outer.generatedNameIn("org..app", "Impl"))
                .withMessage("Not a package name: org..app");
    }
}
