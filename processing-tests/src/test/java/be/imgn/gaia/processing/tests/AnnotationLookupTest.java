package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.AnnotationReader;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.StepProcessor;
import be.imgn.gaia.processing.testing.Compilation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Annotation lookups: {@code annotation} for one annotation, {@code annotations} for repeatable annotations,
 * {@code annotationsAnnotatedWith} and {@code annotationsMetaAnnotatedWith} for meta-annotations, and
 * {@code inheritedAnnotation} for {@code @Inherited}.
 */
class AnnotationLookupTest {

    private static final JavaFileObject MARK = source("example.Mark", """
            package example;

            public @interface Mark {}
            """);

    private static final JavaFileObject TAG = source("example.Tag", """
            package example;

            import java.lang.annotation.Repeatable;

            @Repeatable(Tags.class)
            public @interface Tag {
                String value();
            }
            """);

    private static final JavaFileObject TAGS = source("example.Tags", """
            package example;

            public @interface Tags {
                Tag[] value();
            }
            """);

    private static final JavaFileObject QUALIFIER = source("example.Qualifier", """
            package example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;

            @Target(ElementType.ANNOTATION_TYPE)
            public @interface Qualifier {}
            """);

    private static final JavaFileObject MARKER = source("example.Marker", """
            package example;

            import java.lang.annotation.Inherited;

            @Inherited
            public @interface Marker {
                String value();
            }
            """);

    private static Run run(
            BiFunction<ProcessingContext, Element, List<AnnotationReader>> lookup,
            List<String> options,
            JavaFileObject... sources) {
        Map<String, List<String>> results = new LinkedHashMap<>();
        List<JavaFileObject> all = new ArrayList<>(List.of(MARK, TAG, TAGS, QUALIFIER, MARKER));
        all.addAll(List.of(sources));
        Compilation compilation = javac().withProcessors(new Looking((context, element) -> results.put(
                        element.getSimpleName().toString(),
                        lookup.apply(context, element).stream()
                                .map(AnnotationReader::toString)
                                .toList())))
                .withOptions(options.toArray(String[]::new))
                .compile(all);
        return new Run(compilation, results);
    }

    // The annotations the lookup gives for each element annotated with @Mark, by simple name, as javac prints them.
    private static Run lookUp(
            BiFunction<ProcessingContext, Element, List<AnnotationReader>> lookup, JavaFileObject... sources) {
        Run run = run(lookup, List.of(), sources);
        assertThat(run.compilation()).succeeded();
        return run;
    }

    private static Run annotations(String qualifiedName, JavaFileObject... sources) {
        return lookUp((context, element) -> context.annotations(element, qualifiedName), sources);
    }

    private static Run annotatedWith(String metaAnnotation, JavaFileObject... sources) {
        return lookUp((context, element) -> context.annotationsAnnotatedWith(element, metaAnnotation), sources);
    }

    private static Run metaAnnotatedWith(String metaAnnotation, JavaFileObject... sources) {
        return lookUp((context, element) -> context.annotationsMetaAnnotatedWith(element, metaAnnotation), sources);
    }

    private static Run inherited(String qualifiedName, JavaFileObject... sources) {
        return lookUp(
                (context, element) -> asList(context.inheritedAnnotation((TypeElement) element, qualifiedName)),
                sources);
    }

    private static List<AnnotationReader> asList(Optional<AnnotationReader> annotation) {
        return annotation.stream().toList();
    }

    // Compiles a dependency into a directory, to read its annotations from class files.
    private static void compileDependency(Path classOutput, JavaFileObject... sources) {
        List<JavaFileObject> all = new ArrayList<>(List.of(TAG, TAGS, MARKER));
        all.addAll(List.of(sources));
        assertThat(javac().withOptions("-d", classOutput.toString()).compile(all))
                .succeeded();
    }

    private static List<String> withoutValues(List<String> annotations) {
        return annotations.stream()
                .map(annotation -> annotation.replaceFirst("\\(.*", ""))
                .toList();
    }

    private static TypeElement type(ProcessingContext context, String qualifiedName) {
        return Objects.requireNonNull(context.elements().getTypeElement(qualifiedName), qualifiedName);
    }

    @Test
    void findsAnAnnotationWrittenOnce() {
        Run run = annotations("example.Tag", source("example.Once", """
                package example;

                @Mark
                @Tag("a")
                class Once {}
                """));

        assertThat(run.get("Once")).containsExactly("@example.Tag(\"a\")");
    }

    @Test
    void findsEachRepeatedAnnotationInTheOrderWrittenWhereJavacOnlyKeepsTheContainer() {
        Run run = lookUp(
                (context, element) -> {
                    assertThat(context.annotation(element, "example.Tag")).isEmpty();
                    assertThat(context.annotation(element, "example.Tags")).isPresent();
                    return context.annotations(element, "example.Tag");
                },
                source("example.Repeated", """
                        package example;

                        @Mark
                        @Tag("b")
                        @Deprecated
                        @Tag("a")
                        @Tag("c")
                        class Repeated {}
                        """));

        assertThat(run.get("Repeated"))
                .containsExactly("@example.Tag(\"b\")", "@example.Tag(\"a\")", "@example.Tag(\"c\")");
    }

    @Test
    void findsTheAnnotationsOfAContainerWrittenExplicitly() {
        Run run = annotations("example.Tag", source("example.Explicit", """
                package example;

                @Mark
                @Tags({@Tag("a"), @Tag("b")})
                class Explicit {}
                """));

        assertThat(run.get("Explicit")).containsExactly("@example.Tag(\"a\")", "@example.Tag(\"b\")");
    }

    @Test
    void findsAnAnnotationWrittenOnceNextToItsContainerInTheOrderWritten() {
        Run run = annotations("example.Tag", source("example.Mixed", """
                package example;

                @Mark
                @Tag("a")
                @Tags({@Tag("b"), @Tag("c")})
                class DirectFirst {}

                @Mark
                @Tags({@Tag("b"), @Tag("c")})
                @Tag("a")
                class ContainerFirst {}
                """));

        assertThat(run.get("DirectFirst"))
                .containsExactly("@example.Tag(\"a\")", "@example.Tag(\"b\")", "@example.Tag(\"c\")");
        assertThat(run.get("ContainerFirst"))
                .containsExactly("@example.Tag(\"b\")", "@example.Tag(\"c\")", "@example.Tag(\"a\")");
    }

    @Test
    void rejectsAnAnnotationWrittenSeveralTimesNextToItsContainer() {
        Run run = run(
                (context, element) -> context.annotations(element, "example.Tag"),
                List.of(),
                source("example.Illegal", """
                        package example;

                        @Mark
                        @Tag("a")
                        @Tag("b")
                        @Tags({@Tag("c")})
                        class Illegal {}
                        """));

        assertThat(run.compilation()).failed();
        assertThat(run.compilation()).hadErrorContaining("container example.Tags must not be present");
    }

    @Test
    void readsTheDefaultValueOfAContainer() {
        Run run = annotations("example.Label", source("example.Label", """
                        package example;

                        import java.lang.annotation.Repeatable;

                        @Repeatable(Labels.class)
                        @interface Label {
                            String value();
                        }

                        @interface Labels {
                            Label[] value() default {@Label("default")};
                        }
                        """), source("example.Defaults", """
                        package example;

                        @Mark
                        @Labels
                        class Defaulted {}

                        @Mark
                        @Labels({})
                        class Empty {}
                        """));

        assertThat(run.get("Defaulted")).containsExactly("@example.Label(\"default\")");
        assertThat(run.get("Empty")).isEmpty();
    }

    @Test
    void doesNotLookIntoAnnotationsThatAreNotTheContainer() {
        Run run = annotations("example.Tag", source("example.Group", """
                        package example;

                        @interface Group {
                            Tag[] value();
                        }

                        @interface Pair {
                            Tag value();
                        }
                        """), source("example.Grouped", """
                        package example;

                        @Mark
                        @Group({@Tag("a"), @Tag("b")})
                        @Pair(@Tag("c"))
                        class Grouped {}
                        """));

        assertThat(run.get("Grouped")).isEmpty();
    }

    @Test
    void findsNothingForAnAbsentOrUnknownAnnotationType() {
        Run run = lookUp(
                (context, element) -> {
                    assertThat(context.annotations(element, "example.Missing")).isEmpty();
                    assertThat(context.annotations(element, "Tag")).isEmpty();
                    return context.annotations(element, "example.Marker");
                },
                source("example.Plain", """
                        package example;

                        @Mark
                        @Tag("a")
                        class Plain {}
                        """));

        assertThat(run.get("Plain")).isEmpty();
    }

    @Test
    void findsTheContainerItselfByItsName() {
        Run run = annotations("example.Tags", source("example.Repeated", """
                package example;

                @Mark
                @Tag("a")
                @Tag("b")
                class Repeated {}
                """));

        assertThat(run.get("Repeated")).containsExactly("@example.Tags({@example.Tag(\"a\"), @example.Tag(\"b\")})");
    }

    @Test
    void findsRepeatedAnnotationsOnMethodsFieldsAndParameters() {
        Run run = lookUp(
                (context, type) -> {
                    ExecutableElement method =
                            ElementFilter.methodsIn(type.getEnclosedElements()).getFirst();
                    VariableElement field =
                            ElementFilter.fieldsIn(type.getEnclosedElements()).getFirst();
                    VariableElement parameter = method.getParameters().getFirst();
                    List<AnnotationReader> all = new ArrayList<>();
                    all.addAll(context.annotations(method, "example.Tag"));
                    all.addAll(context.annotations(field, "example.Tag"));
                    all.addAll(context.annotations(parameter, "example.Tag"));
                    return all;
                },
                source("example.Members", """
                        package example;

                        @Mark
                        class Members {
                            @Tag("field")
                            @Tag("field again")
                            int field;

                            @Tag("method")
                            @Tag("method again")
                            void run(@Tag("parameter") @Tag("parameter again") int parameter) {}
                        }
                        """));

        assertThat(run.get("Members"))
                .containsExactly(
                        "@example.Tag(\"method\")",
                        "@example.Tag(\"method again\")",
                        "@example.Tag(\"field\")",
                        "@example.Tag(\"field again\")",
                        "@example.Tag(\"parameter\")",
                        "@example.Tag(\"parameter again\")");
    }

    @Test
    void findsRepeatedAnnotationsInClassFiles(@TempDir Path classOutput) {
        compileDependency(classOutput, source("dependency.Notes", """
                        package dependency;

                        import java.lang.annotation.Repeatable;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;

                        @Retention(RetentionPolicy.CLASS)
                        @Repeatable(Notes.class)
                        @interface Note {
                            String value();
                        }

                        @Retention(RetentionPolicy.RUNTIME)
                        @interface Notes {
                            Note[] value();
                        }
                        """), source("dependency.Compiled", """
                        package dependency;

                        import example.Tag;
                        import example.Tags;

                        @Tag("a")
                        @Tag("b")
                        public class Compiled {
                            @Tag("c")
                            @Tags({@Tag("d"), @Tag("e")})
                            public void mixed() {}

                            @Note("first")
                            @Notes({@Note("second")})
                            public void retentions() {}
                        }
                        """));

        Run run = run(
                (context, ignored) -> {
                    TypeElement compiled = type(context, "dependency.Compiled");
                    List<AnnotationReader> all = new ArrayList<>(context.annotations(compiled, "example.Tag"));
                    for (ExecutableElement method : ElementFilter.methodsIn(compiled.getEnclosedElements())) {
                        all.addAll(context.annotations(method, "example.Tag"));
                        all.addAll(context.annotations(method, "dependency.Note"));
                    }
                    return all;
                },
                List.of("-classpath", classOutput.toString()),
                source("example.Seed", """
                        package example;

                        @Mark
                        class Seed {}
                        """));

        assertThat(run.compilation()).succeeded();
        // From a class file, javac reads the annotations kept at run time before the others.
        assertThat(run.get("Seed"))
                .containsExactly(
                        "@example.Tag(\"a\")",
                        "@example.Tag(\"b\")",
                        "@example.Tag(\"c\")",
                        "@example.Tag(\"d\")",
                        "@example.Tag(\"e\")",
                        "@dependency.Note(\"second\")",
                        "@dependency.Note(\"first\")");
    }

    @Test
    void findsAnnotationsWhoseTypeIsAnnotatedWithTheMetaAnnotation() {
        Run run =
                annotatedWith("example.Qualifier", source("example.Qualifiers", """
                        package example;

                        @Qualifier
                        @interface Named {
                            String value();
                        }

                        @Qualifier
                        @interface Primary {}

                        @interface Plain {}
                        """), source("example.Injected", """
                        package example;

                        @Mark
                        @Named("main")
                        @Plain
                        @Primary
                        class Injected {
                            @Mark
                            void inject(@Primary String parameter) {}
                        }

                        @Mark
                        @Plain
                        class Unqualified {}
                        """));

        assertThat(run.get("Injected")).containsExactly("@example.Named(\"main\")", "@example.Primary");
        assertThat(run.get("inject")).isEmpty();
        assertThat(run.get("Unqualified")).isEmpty();
    }

    @Test
    void followsMetaAnnotationsOfMetaAnnotationsOnlyWhenAskedTo() {
        JavaFileObject stereotypes = source("example.Stereotypes", """
                package example;

                @interface Component {}

                @Component
                @interface Controller {}

                @Controller
                @interface RestController {}

                @RestController
                @interface ApiController {}
                """);
        JavaFileObject annotated = source("example.Annotated", """
                package example;

                @Mark
                @ApiController
                @RestController
                @Controller
                @Component
                @Deprecated
                class Annotated {}
                """);

        assertThat(annotatedWith("example.Component", stereotypes, annotated).get("Annotated"))
                .containsExactly("@example.Controller");
        assertThat(metaAnnotatedWith("example.Component", stereotypes, annotated)
                        .get("Annotated"))
                .containsExactly("@example.ApiController", "@example.RestController", "@example.Controller");
    }

    @Test
    void stopsOnCyclicMetaAnnotations() {
        JavaFileObject cycles = source("example.Cycles", """
                package example;

                @Second
                @interface First {}

                @First
                @interface Second {}

                @First
                @interface Third {}

                @Itself
                @interface Itself {}
                """);
        JavaFileObject annotated = source("example.Annotated", """
                package example;

                @Mark
                @First
                @Second
                @Third
                @Itself
                @Deprecated
                @SuppressWarnings("all")
                class Annotated {}
                """);

        assertThat(annotatedWith("example.First", cycles, annotated).get("Annotated"))
                .containsExactly("@example.Second", "@example.Third");
        assertThat(metaAnnotatedWith("example.First", cycles, annotated).get("Annotated"))
                .containsExactly("@example.First", "@example.Second", "@example.Third");
        assertThat(metaAnnotatedWith("example.Itself", cycles, annotated).get("Annotated"))
                .containsExactly("@example.Itself");
        assertThat(metaAnnotatedWith("example.Absent", cycles, annotated).get("Annotated"))
                .isEmpty();
    }

    @Test
    void followsTheCyclicMetaAnnotationsOfTheJdk() {
        JavaFileObject annotated = source("example.Annotated", """
                package example;

                import java.lang.annotation.Documented;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @Mark
                @Deprecated
                @SuppressWarnings("all")
                @Documented
                @Retention(RetentionPolicy.RUNTIME)
                @interface Annotated {}
                """);

        // Without values, which javac prints differently from one version to the next.
        assertThat(withoutValues(annotatedWith("java.lang.annotation.Documented", annotated)
                        .get("Annotated")))
                .containsExactly(
                        "@java.lang.Deprecated", "@java.lang.annotation.Documented", "@java.lang.annotation.Retention");
        assertThat(withoutValues(metaAnnotatedWith("java.lang.annotation.Retention", annotated)
                        .get("Annotated")))
                .containsExactly(
                        "@java.lang.Deprecated",
                        "@java.lang.SuppressWarnings",
                        "@java.lang.annotation.Documented",
                        "@java.lang.annotation.Retention");
        assertThat(metaAnnotatedWith("java.lang.annotation.Repeatable", annotated)
                        .get("Annotated"))
                .isEmpty();
    }

    @Test
    void findsMetaAnnotatedAnnotationsRepeatedInTheirContainer() {
        Run run = annotatedWith("example.Qualifier", source("example.Label", """
                        package example;

                        import java.lang.annotation.Repeatable;

                        @Qualifier
                        @Repeatable(Labels.class)
                        @interface Label {
                            String value();
                        }

                        @interface Labels {
                            Label[] value();
                        }
                        """), source("example.Labelled", """
                        package example;

                        @Mark
                        @Label("a")
                        @Label("b")
                        class Labelled {}
                        """));

        assertThat(run.get("Labelled")).containsExactly("@example.Label(\"a\")", "@example.Label(\"b\")");
    }

    @Test
    void findsAMetaAnnotationRepeatedOnTheAnnotationType() {
        JavaFileObject roles = source("example.Role", """
                package example;

                import java.lang.annotation.Repeatable;

                @Repeatable(Roles.class)
                @interface Role {
                    String value();
                }

                @interface Roles {
                    Role[] value();
                }

                @Role("user")
                @Role("admin")
                @interface Admin {}

                @Admin
                @interface SuperAdmin {}
                """);
        JavaFileObject annotated = source("example.Secured", """
                package example;

                @Mark
                @SuperAdmin
                @Admin
                class Secured {}
                """);

        assertThat(annotatedWith("example.Role", roles, annotated).get("Secured"))
                .containsExactly("@example.Admin");
        assertThat(metaAnnotatedWith("example.Role", roles, annotated).get("Secured"))
                .containsExactly("@example.SuperAdmin", "@example.Admin");
        assertThat(annotatedWith("example.Roles", roles, annotated).get("Secured"))
                .containsExactly("@example.Admin");
    }

    @Test
    void findsMetaAnnotatedAnnotationsInClassFiles(@TempDir Path classOutput) {
        compileDependency(classOutput, source("dependency.Stereotypes", """
                package dependency;

                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @Retention(RetentionPolicy.CLASS)
                @interface Component {}

                @Component
                @interface Service {}

                @Service
                @interface Repository {}

                @Repository
                @Service
                class Compiled {}
                """));

        Run run = run(
                (context, ignored) -> context.annotationsMetaAnnotatedWith(
                        type(context, "dependency.Compiled"), "dependency.Component"),
                List.of("-classpath", classOutput.toString()),
                source("example.Seed", """
                        package example;

                        @Mark
                        class Seed {}
                        """));

        assertThat(run.compilation()).succeeded();
        assertThat(run.get("Seed")).containsExactly("@dependency.Repository", "@dependency.Service");
    }

    @Test
    void inheritsAnInheritedAnnotationThroughSeveralSuperclasses() {
        Run run = inherited("example.Marker", source("example.Hierarchy", """
                package example;

                @Mark
                @Marker("top")
                class Top {}

                @Mark
                class Middle extends Top {}

                @Mark
                class Bottom extends Middle {}
                """));

        assertThat(run.get("Top")).containsExactly("@example.Marker(\"top\")");
        assertThat(run.get("Middle")).containsExactly("@example.Marker(\"top\")");
        assertThat(run.get("Bottom")).containsExactly("@example.Marker(\"top\")");
    }

    @Test
    void takesTheAnnotationOfTheNearestClassThatDeclaresIt() {
        Run run = inherited("example.Marker", source("example.Hierarchy", """
                package example;

                @Marker("top")
                class Top {}

                @Marker("middle")
                class Middle extends Top {}

                @Mark
                class Bottom extends Middle {}

                @Mark
                @Marker("own")
                class Own extends Middle {}
                """));

        assertThat(run.get("Bottom")).containsExactly("@example.Marker(\"middle\")");
        assertThat(run.get("Own")).containsExactly("@example.Marker(\"own\")");
    }

    @Test
    void doesNotInheritAnAnnotationThatIsNotInherited() {
        Run run = inherited("example.Plain", source("example.Hierarchy", """
                package example;

                @interface Plain {}

                @Mark
                @Plain
                class Top {}

                @Mark
                class Bottom extends Top {}
                """));

        assertThat(run.get("Top")).containsExactly("@example.Plain");
        assertThat(run.get("Bottom")).isEmpty();
    }

    @Test
    void doesNotInheritFromInterfaces() {
        Run run = inherited("example.Marker", source("example.Hierarchy", """
                package example;

                @Mark
                @Marker("contract")
                interface Contract {}

                @Mark
                interface Extended extends Contract {}

                @Mark
                class Implementation implements Contract {}

                @Marker("base")
                abstract class Base {}

                @Mark
                class Both extends Base implements Contract {}

                @Mark
                enum Kind implements Contract {
                    ONE
                }

                @Mark
                record Point(int x) implements Contract {}
                """));

        assertThat(run.get("Contract")).containsExactly("@example.Marker(\"contract\")");
        assertThat(run.get("Extended")).isEmpty();
        assertThat(run.get("Implementation")).isEmpty();
        assertThat(run.get("Both")).containsExactly("@example.Marker(\"base\")");
        assertThat(run.get("Kind")).isEmpty();
        assertThat(run.get("Point")).isEmpty();
    }

    @Test
    void doesNotInheritTheAnnotationsOfOverriddenMethods() {
        Run run = lookUp(
                (context, type) -> {
                    ExecutableElement method =
                            ElementFilter.methodsIn(type.getEnclosedElements()).getFirst();
                    List<AnnotationReader> all = new ArrayList<>(context.annotations(method, "example.Marker"));
                    context.annotation(method, "example.Marker").ifPresent(all::add);
                    all.addAll(context.annotationsAnnotatedWith(method, "java.lang.annotation.Inherited"));
                    return all;
                },
                source("example.Hierarchy", """
                        package example;

                        class Base {
                            @Marker("base")
                            void run() {}
                        }

                        @Mark
                        class Overriding extends Base {
                            @Override
                            void run() {}
                        }
                        """));

        assertThat(run.get("Overriding")).isEmpty();
    }

    @Test
    void inheritsTheContainerOfARepeatedInheritedAnnotation() {
        JavaFileObject badges = source("example.Badge", """
                package example;

                import java.lang.annotation.Inherited;
                import java.lang.annotation.Repeatable;

                @Inherited
                @Repeatable(Badges.class)
                @interface Badge {
                    String value();
                }

                @Inherited
                @interface Badges {
                    Badge[] value();
                }
                """);
        JavaFileObject hierarchy = source("example.Hierarchy", """
                package example;

                @Mark
                @Badge("a")
                @Badge("b")
                class Repeated {}

                @Mark
                class Inheriting extends Repeated {}

                @Badge("single")
                class Single {}

                @Mark
                class InheritingSingle extends Single {}
                """);

        assertThat(inherited("example.Badge", badges, hierarchy).get("Inheriting"))
                .isEmpty();
        assertThat(inherited("example.Badge", badges, hierarchy).get("InheritingSingle"))
                .containsExactly("@example.Badge(\"single\")");
        assertThat(inherited("example.Badges", badges, hierarchy).get("Inheriting"))
                .containsExactly("@example.Badges({@example.Badge(\"a\"), @example.Badge(\"b\")})");
    }

    @Test
    void inheritsFromSuperclassesInClassFiles(@TempDir Path classOutput) {
        compileDependency(classOutput, source("dependency.Base", """
                package dependency;

                import example.Marker;

                @Marker("compiled")
                public class Base {}
                """));

        Run run = run(
                (context, element) -> asList(context.inheritedAnnotation((TypeElement) element, "example.Marker")),
                List.of("-classpath", classOutput.toString()),
                source("example.Derived", """
                        package example;

                        @Mark
                        class Derived extends dependency.Base {}
                        """));

        assertThat(run.compilation()).succeeded();
        assertThat(run.get("Derived")).containsExactly("@example.Marker(\"compiled\")");
    }

    @Test
    void ignoresAnnotationTypesAndSuperclassesJavacCouldNotResolve() {
        Run run = run(
                (context, ignored) -> {
                    TypeElement broken = type(context, "example.Broken");
                    List<AnnotationReader> all = new ArrayList<>();
                    all.addAll(context.annotations(broken, "example.Tag"));
                    all.addAll(context.annotations(broken, "Missing"));
                    all.addAll(context.annotations(broken, "example.Missing"));
                    all.addAll(context.annotationsAnnotatedWith(broken, "example.Qualifier"));
                    all.addAll(context.annotationsMetaAnnotatedWith(broken, "example.Qualifier"));
                    all.addAll(context.annotationsMetaAnnotatedWith(broken, "Missing"));
                    context.inheritedAnnotation(broken, "example.Marker").ifPresent(all::add);
                    context.inheritedAnnotation(broken, "Missing").ifPresent(all::add);
                    return all;
                },
                List.of(),
                source("example.Broken", """
                        package example;

                        @Missing
                        @Qualifier
                        @interface Odd {}

                        @Missing
                        @Odd
                        @Tag("a")
                        @Tag("b")
                        @Marker("own")
                        class Broken extends MissingBase {}
                        """),
                source("example.Seed", """
                        package example;

                        @Mark
                        class Seed {}
                        """));

        assertThat(run.compilation()).failed();
        assertThat(run.compilation()).hadErrorContaining("Missing");
        assertThat(run.get("Seed"))
                .containsExactly(
                        "@example.Tag(\"a\")",
                        "@example.Tag(\"b\")",
                        "@example.Odd",
                        "@example.Odd",
                        "@example.Marker(\"own\")");
    }

    @Test
    void findsNoAnnotationOfATypeJavacCouldNotResolveByTheNameItIsWrittenWith() {
        Map<String, Boolean> found = new LinkedHashMap<>();
        Run run = run(
                (context, ignored) -> {
                    TypeElement broken = type(context, "example.Broken");
                    for (String name : List.of(
                            "Missing", "example.Missing", "com.example.Missing", "example.Marker", "example.Tag")) {
                        found.put(name, context.annotation(broken, name).isPresent());
                    }
                    return asList(context.annotation(broken, "example.Marker"));
                },
                List.of(),
                source("example.Broken", """
                        package example;

                        @Missing
                        @com.example.Missing
                        @Marker("own")
                        @Tag("a")
                        class Broken {}
                        """),
                source("example.Seed", """
                        package example;

                        @Mark
                        class Seed {}
                        """));

        assertThat(run.compilation()).failed();
        assertThat(run.compilation()).hadErrorContaining("Missing");
        assertThat(found)
                .containsExactly(
                        Map.entry("Missing", false),
                        Map.entry("example.Missing", false),
                        Map.entry("com.example.Missing", false),
                        Map.entry("example.Marker", true),
                        Map.entry("example.Tag", true));
        assertThat(run.get("Seed")).containsExactly("@example.Marker(\"own\")");
    }

    /** The outcome of a compilation, and what the lookup gave for each element annotated with {@code @Mark}. */
    private record Run(Compilation compilation, Map<String, List<String>> byElement) {

        List<String> get(String element) {
            return Objects.requireNonNull(byElement.get(element), element);
        }
    }

    /** Runs a lookup on each element annotated with {@code @Mark}. */
    private static final class Looking extends StepProcessor {

        private final BiConsumer<ProcessingContext, Element> lookup;

        Looking(BiConsumer<ProcessingContext, Element> lookup) {
            this.lookup = lookup;
        }

        @Override
        protected List<Step> steps(ProcessingContext context) {
            return List.of(new Step() {
                @Override
                public Set<String> annotations() {
                    return Set.of("example.Mark");
                }

                @Override
                public Set<? extends Element> process(AnnotatedElements elements) {
                    elements.annotatedWith("example.Mark").forEach(element -> lookup.accept(context, element));
                    return Set.of();
                }
            });
        }
    }
}
