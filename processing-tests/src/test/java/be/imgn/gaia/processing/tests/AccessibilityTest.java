package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import be.imgn.gaia.processing.MoreElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.testing.Compilation;
import be.imgn.gaia.processing.testing.CompilationExtension;
import be.imgn.gaia.processing.testing.TestCompiler;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/** Whether generated code can access an element, by the language rules and by the modules: {@code isAccessible}. */
@ExtendWith(CompilationExtension.class)
class AccessibilityTest {

    private static final JavaFileObject MARK = source("example.Mark", """
            package example;

            public @interface Mark {}
            """);

    private static final JavaFileObject BASE = source("lib.Base", """
            package lib;

            import example.Mark;

            @Mark
            public class Base {
                public int publicField;
                protected int protectedField;
                int packageField;
                private int privateField;

                public Base() {}

                protected Base(int value) {}

                Base(long value) {}

                private Base(String value) {}

                public void publicMethod() {}

                protected void protectedMethod() {}

                protected static void protectedStaticMethod() {}

                void packageMethod() {}

                private void privateMethod() {}

                public static class PublicNested {
                    public void open() {}

                    protected void guarded() {}
                }

                protected static class ProtectedNested {
                    public void open() {}

                    protected void guarded() {}
                }

                static class PackageNested {
                    public void open() {}
                }

                private static class PrivateNested {
                    public void open() {}
                }
            }
            """);

    private static final JavaFileObject MIDDLE = source("lib.Middle", """
            package lib;

            public class Middle extends Base {}

            class Unrelated {}

            class NestedChild extends Base.PublicNested {}
            """);

    // What everyone can access in Base, from any package.
    private static final List<String> PUBLIC = List.of(
            "Base",
            "Base.publicField",
            "Base.Base()",
            "Base.publicMethod()",
            "Base.PublicNested",
            "Base.PublicNested.PublicNested()",
            "Base.PublicNested.open()");

    private static Compilation process(
            TestCompiler compiler, BiConsumer<ProcessingContext, Element> step, JavaFileObject... sources) {
        List<JavaFileObject> all = new ArrayList<>(List.of(MARK));
        all.addAll(List.of(sources));
        return compiler.withProcessors(new Running(step)).compile(all);
    }

    // The elements of the types annotated with @Mark, and their members, that the check accepts, by path.
    private static List<String> accepted(BiPredicate<ProcessingContext, Element> check, JavaFileObject... sources) {
        List<String> results = new ArrayList<>();
        Compilation compilation = process(
                javac(),
                (context, marked) -> walk(marked, element -> {
                    if (check.test(context, element)) {
                        results.add(path(element));
                    }
                }),
                sources);
        assertThat(compilation).succeeded();
        return results;
    }

    // Whether the module of the element annotated with @Mark can access each type or package, by name.
    private static Map<String, Boolean> exported(TestCompiler compiler, List<String> names, JavaFileObject... sources) {
        Map<String, Boolean> results = new LinkedHashMap<>();
        Compilation compilation = process(
                compiler,
                (context, marked) -> {
                    ModuleElement module = context.elements().getModuleOf(marked);
                    for (String name : names) {
                        results.put(name, MoreElements.isExportedTo(element(context.elements(), name), module));
                    }
                },
                sources);
        assertThat(compilation).succeeded();
        return results;
    }

    private static Element element(Elements elements, String name) {
        Element type = elements.getTypeElement(name);
        return requireNonNull(type == null ? elements.getPackageElement(name) : type, name);
    }

    private static void walk(Element element, Consumer<Element> action) {
        action.accept(element);
        if (element instanceof TypeElement) {
            element.getEnclosedElements().forEach(enclosed -> walk(enclosed, action));
        }
    }

    // As Outer.Inner.method(int), from the top-level type.
    private static String path(Element element) {
        Element enclosing = requireNonNull(element.getEnclosingElement());
        String name = element.getKind() == ElementKind.CONSTRUCTOR
                ? enclosing.getSimpleName().toString()
                : element.getSimpleName().toString();
        String label = element instanceof ExecutableElement executable
                ? executable.getParameters().stream()
                        .map(parameter -> parameter.asType().toString())
                        .collect(Collectors.joining(",", name + "(", ")"))
                : name;
        return enclosing.getKind() == ElementKind.PACKAGE ? label : path(enclosing) + "." + label;
    }

    private static List<String> plus(List<String> first, String... more) {
        List<String> all = new ArrayList<>(first);
        all.addAll(List.of(more));
        return all;
    }

    private static ExecutableElement method(TypeElement type, String name) {
        return type.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD
                        && element.getSimpleName().contentEquals(name))
                .map(ExecutableElement.class::cast)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void accessesOnlyPublicElementsOfPublicTypesFromAnotherPackage() {
        assertThat(accepted((context, element) -> MoreElements.isAccessible(element, "example"), BASE))
                .containsExactlyInAnyOrderElementsOf(PUBLIC);
    }

    @Test
    void accessesAllButPrivateElementsFromTheirOwnPackage() {
        assertThat(accepted((context, element) -> MoreElements.isAccessible(element, "lib"), BASE))
                .containsExactlyInAnyOrder(
                        "Base",
                        "Base.publicField",
                        "Base.protectedField",
                        "Base.packageField",
                        "Base.Base()",
                        "Base.Base(int)",
                        "Base.Base(long)",
                        "Base.publicMethod()",
                        "Base.protectedMethod()",
                        "Base.protectedStaticMethod()",
                        "Base.packageMethod()",
                        "Base.PublicNested",
                        "Base.PublicNested.PublicNested()",
                        "Base.PublicNested.open()",
                        "Base.PublicNested.guarded()",
                        "Base.ProtectedNested",
                        "Base.ProtectedNested.ProtectedNested()",
                        "Base.ProtectedNested.open()",
                        "Base.ProtectedNested.guarded()",
                        "Base.PackageNested",
                        "Base.PackageNested.PackageNested()",
                        "Base.PackageNested.open()");
    }

    @Test
    void accessesTheProtectedMembersOfItsSuperclassesFromASubclassInAnotherPackage() {
        assertThat(accepted(
                        (context, element) -> MoreElements.isAccessible(
                                element, "example", context.elements().getTypeElement("lib.Middle")),
                        BASE,
                        MIDDLE))
                .containsExactlyInAnyOrderElementsOf(plus(
                        PUBLIC,
                        "Base.protectedField",
                        "Base.Base(int)",
                        "Base.protectedMethod()",
                        "Base.protectedStaticMethod()",
                        "Base.ProtectedNested",
                        "Base.ProtectedNested.open()"));
    }

    @Test
    void accessesTheProtectedMembersOfANestedClassOnlyFromItsOwnSubclasses() {
        assertThat(accepted(
                        (context, element) -> MoreElements.isAccessible(
                                element, "example", context.elements().getTypeElement("lib.Base.PublicNested")),
                        BASE))
                .containsExactlyInAnyOrderElementsOf(plus(PUBLIC, "Base.PublicNested.guarded()"));
        assertThat(accepted(
                        (context, element) -> MoreElements.isAccessible(
                                element, "example", context.elements().getTypeElement("lib.NestedChild")),
                        BASE,
                        MIDDLE))
                .containsExactlyInAnyOrderElementsOf(plus(PUBLIC, "Base.PublicNested.guarded()"));
    }

    @Test
    void accessesNoProtectedMemberFromASubclassOfAnotherClass() {
        assertThat(accepted(
                        (context, element) -> MoreElements.isAccessible(
                                element, "example", context.elements().getTypeElement("lib.Unrelated")),
                        BASE,
                        MIDDLE))
                .containsExactlyInAnyOrderElementsOf(PUBLIC);
        assertThat(accepted(
                        (context, element) -> MoreElements.isAccessible(
                                element, "example", context.elements().getTypeElement("java.lang.Object")),
                        BASE))
                .containsExactlyInAnyOrderElementsOf(PUBLIC);
    }

    @Test
    void neverAccessesPrivateElementsNorPackagePrivateOnesOfAnotherPackageEvenFromASubclass() {
        assertThat(accepted(
                        (context, element) -> MoreElements.isAccessible(
                                element, "lib", context.elements().getTypeElement("lib.Middle")),
                        BASE,
                        MIDDLE))
                .doesNotContain(
                        "Base.privateField",
                        "Base.Base(java.lang.String)",
                        "Base.privateMethod()",
                        "Base.PrivateNested",
                        "Base.PrivateNested.open()")
                .contains("Base.packageMethod()", "Base.PackageNested.open()");
        assertThat(accepted(
                        (context, element) -> MoreElements.isAccessible(
                                element, "example", context.elements().getTypeElement("lib.Middle")),
                        BASE,
                        MIDDLE))
                .doesNotContain(
                        "Base.packageField",
                        "Base.Base(long)",
                        "Base.packageMethod()",
                        "Base.PackageNested",
                        "Base.PackageNested.open()",
                        "Base.PrivateNested.open()");
    }

    @Test
    void accessesTheProtectedMethodsOfObjectFromAnySubclass(Elements elements) {
        TypeElement object = elements.getTypeElement("java.lang.Object");
        ExecutableElement clone = method(object, "clone");

        assertThat(MoreElements.isAccessible(clone, "example")).isFalse();
        assertThat(MoreElements.isAccessible(clone, "java.lang")).isTrue();
        assertThat(MoreElements.isAccessible(clone, "example", object)).isTrue();
        assertThat(MoreElements.isAccessible(clone, "example", elements.getTypeElement("java.util.ArrayList")))
                .isTrue();
        assertThat(MoreElements.isAccessible(method(object, "toString"), "example"))
                .isTrue();
    }

    @Test
    void judgesAnInheritedMemberWhereItIsDeclared(Elements elements) {
        TypeElement builder = elements.getTypeElement("java.lang.AbstractStringBuilder");
        ExecutableElement length = method(builder, "length");

        assertThat(MoreElements.isAccessible(length, "example")).isFalse();
        assertThat(MoreElements.isAccessible(length, "java.lang")).isTrue();
        assertThat(MoreElements.isAccessible(builder, "example", elements.getTypeElement("java.lang.StringBuilder")))
                .isFalse();
    }

    @Test
    void treatsTheUnnamedPackageAsAnyOther() {
        JavaFileObject plain = source("Plain", """
                @example.Mark
                class Plain {
                    void packageMethod() {}

                    private void privateMethod() {}
                }
                """);

        assertThat(accepted((context, element) -> MoreElements.isAccessible(element, ""), plain))
                .containsExactlyInAnyOrder("Plain", "Plain.Plain()", "Plain.packageMethod()");
        assertThat(accepted((context, element) -> MoreElements.isAccessible(element, "example"), plain))
                .isEmpty();
    }

    @Test
    void accessesPackagesAndModules(Elements elements) {
        TypeElement string = elements.getTypeElement("java.lang.String");
        ModuleElement base = elements.getModuleOf(string);

        assertThat(MoreElements.isAccessible(MoreElements.packageOf(string), "example"))
                .isTrue();
        assertThat(MoreElements.isAccessible(base, "example")).isTrue();
        assertThat(MoreElements.isAccessible(base, "example", elements.getTypeElement("java.lang.Object")))
                .isTrue();
    }

    @Test
    void rejectsAnInterfaceAsTheSuperclass(Elements elements) {
        TypeElement runnable = elements.getTypeElement("java.lang.Runnable");

        assertThatThrownBy(() -> MoreElements.isAccessible(method(runnable, "run"), "example", runnable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("java.lang.Runnable");
        assertThatThrownBy(() -> MoreElements.isAccessible(
                        runnable, "example", elements.getTypeElement("java.lang.annotation.Retention")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accessesWhatTheModulesANamedModuleReadsExportToIt() {
        Map<String, Boolean> exported = exported(
                javac(),
                List.of(
                        "example.Marked",
                        "java.lang.String",
                        "java.sql.Connection",
                        "java.util.logging.Logger",
                        "javax.xml.parsers.DocumentBuilder",
                        "java.awt.Frame",
                        "be.imgn.gaia.processing.MoreElements",
                        "javax.lang.model.element.Element",
                        "org.jspecify.annotations.Nullable",
                        "be.imgn.gaia.processing.internal",
                        "be.imgn.gaia.processing.internal.Inheritance",
                        "be.imgn.gaia.core.text.Splitter",
                        "jdk.internal.misc.Unsafe"),
                source("module-info", """
                        module example {
                            requires be.imgn.gaia.processing;
                            requires java.sql;
                            requires static java.desktop;
                        }
                        """),
                source("example.Marked", """
                        package example;

                        @Mark
                        class Marked {}
                        """));

        assertThat(exported)
                .containsExactly(
                        entry("example.Marked", true),
                        entry("java.lang.String", true),
                        entry("java.sql.Connection", true),
                        entry("java.util.logging.Logger", true),
                        entry("javax.xml.parsers.DocumentBuilder", true),
                        entry("java.awt.Frame", true),
                        entry("be.imgn.gaia.processing.MoreElements", true),
                        entry("javax.lang.model.element.Element", true),
                        entry("org.jspecify.annotations.Nullable", true),
                        entry("be.imgn.gaia.processing.internal", false),
                        entry("be.imgn.gaia.processing.internal.Inheritance", false),
                        entry("be.imgn.gaia.core.text.Splitter", false),
                        entry("jdk.internal.misc.Unsafe", false));
    }

    @Test
    void accessesWhatNamedModulesExportFromTheUnnamedModule() {
        Map<String, Boolean> exported = exported(
                javac(),
                List.of(
                        "example.Plain",
                        "java.lang.String",
                        "java.util.logging.Logger",
                        "be.imgn.gaia.processing.MoreElements",
                        "be.imgn.gaia.core.text.Splitter",
                        "be.imgn.gaia.processing.internal.Inheritance",
                        "jdk.internal.misc.Unsafe"),
                source("example.Plain", """
                        package example;

                        @Mark
                        class Plain {}
                        """));

        assertThat(exported)
                .containsExactly(
                        entry("example.Plain", true),
                        entry("java.lang.String", true),
                        entry("java.util.logging.Logger", true),
                        entry("be.imgn.gaia.processing.MoreElements", true),
                        entry("be.imgn.gaia.core.text.Splitter", true),
                        entry("be.imgn.gaia.processing.internal.Inheritance", false),
                        entry("jdk.internal.misc.Unsafe", false));
    }

    @Test
    void keepsTheUnnamedModuleFromNamedModulesAndQualifiedExportsToTheirTargets() {
        Map<String, Boolean> results = new LinkedHashMap<>();
        Compilation compilation = process(
                javac(),
                (context, plain) -> {
                    Elements elements = context.elements();
                    TypeElement unsafe = elements.getTypeElement("jdk.internal.misc.Unsafe");
                    ModuleElement unsupported = elements.getModuleElement("jdk.unsupported");
                    ModuleElement sql = elements.getModuleElement("java.sql");
                    results.put("Plain to java.sql", MoreElements.isExportedTo(plain, sql));
                    results.put("Plain to unnamed", MoreElements.isExportedTo(plain, elements.getModuleOf(plain)));
                    results.put("Unsafe to jdk.unsupported", MoreElements.isExportedTo(unsafe, unsupported));
                    results.put("Unsafe to java.sql", MoreElements.isExportedTo(unsafe, sql));
                    results.put(
                            "Unsafe to java.base",
                            MoreElements.isExportedTo(unsafe, elements.getModuleElement("java.base")));
                },
                source("example.Plain", """
                        package example;

                        @Mark
                        class Plain {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(results)
                .containsExactly(
                        entry("Plain to java.sql", false),
                        entry("Plain to unnamed", true),
                        entry("Unsafe to jdk.unsupported", true),
                        entry("Unsafe to java.sql", false),
                        entry("Unsafe to java.base", true));
    }

    @Test
    void readsAndExportsEverythingInAnAutomaticModule(@TempDir Path directory) throws IOException {
        Compilation library = javac().compile(source("auto.Library", """
                package auto;

                public class Library {}
                """));
        assertThat(library).succeeded();
        Path jar = directory.resolve("automatic.jar");
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream out = new JarOutputStream(file);
                InputStream in = library.generatedFile(StandardLocation.CLASS_OUTPUT, "auto/Library.class")
                        .orElseThrow()
                        .openInputStream()) {
            out.putNextEntry(new JarEntry("auto/Library.class"));
            in.transferTo(out);
            out.closeEntry();
        }
        String modulePath = directory
                + File.pathSeparator
                + requireNonNull(System.getProperty("jdk.module.path"), "tests run on the module path");
        Map<String, Boolean> results = new LinkedHashMap<>();

        Compilation compilation = process(
                javac().withOptions("--module-path", modulePath),
                (context, marked) -> {
                    Elements elements = context.elements();
                    TypeElement automatic = elements.getTypeElement("auto.Library");
                    ModuleElement automaticModule = elements.getModuleOf(automatic);
                    results.put(
                            "Library to example", MoreElements.isExportedTo(automatic, elements.getModuleOf(marked)));
                    results.put("Marked to automatic", MoreElements.isExportedTo(marked, automaticModule));
                    results.put(
                            "Hidden to automatic",
                            MoreElements.isExportedTo(
                                    elements.getTypeElement("example.internal.Hidden"), automaticModule));
                    results.put(
                            "Library to java.sql",
                            MoreElements.isExportedTo(automatic, elements.getModuleElement("java.sql")));
                },
                source("module-info", """
                        module example {
                            requires automatic;
                            requires java.sql;

                            exports example;
                        }
                        """),
                source("example.internal.Hidden", """
                        package example.internal;

                        public class Hidden {}
                        """),
                source("example.Marked", """
                        package example;

                        @Mark
                        public class Marked {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(results)
                .containsExactly(
                        entry("Library to example", true),
                        entry("Marked to automatic", true),
                        entry("Hidden to automatic", false),
                        entry("Library to java.sql", false));
    }

    @Test
    void rejectsAModuleAsTheElementToExport(Elements elements) {
        ModuleElement base = elements.getModuleElement("java.base");

        assertThatThrownBy(() -> MoreElements.isExportedTo(base, base)).isInstanceOf(IllegalArgumentException.class);
    }

    /** Runs a step on each element annotated with {@code @Mark}, in the first round that has them. */
    @SupportedAnnotationTypes("example.Mark")
    private static final class Running extends AbstractProcessor {

        private final BiConsumer<ProcessingContext, Element> step;

        Running(BiConsumer<ProcessingContext, Element> step) {
            this.step = step;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            ProcessingContext context = ProcessingContext.of(processingEnv, Running.class);
            for (TypeElement annotation : annotations) {
                round.getElementsAnnotatedWith(annotation).forEach(element -> step.accept(context, element));
            }
            return false;
        }
    }
}
