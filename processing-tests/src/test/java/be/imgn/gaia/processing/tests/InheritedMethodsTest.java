package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.MoreElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.StepProcessor;
import be.imgn.gaia.processing.source.JavaFile;
import be.imgn.gaia.processing.source.Method;
import be.imgn.gaia.processing.source.Type;
import be.imgn.gaia.processing.testing.Compilation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

/**
 * The methods of a type as the compiler sees them: {@code methods} and {@code abstractMethods}, and how
 * {@code Elements.overrides} decides overriding from a type.
 */
class InheritedMethodsTest {

    private static final JavaFileObject MARK = source("example.Mark", """
            package example;

            public @interface Mark {}
            """);

    private static Compilation process(BiConsumer<ProcessingContext, TypeElement> step, JavaFileObject... sources) {
        List<JavaFileObject> all = new ArrayList<>(List.of(MARK));
        all.addAll(List.of(sources));
        return javac().withProcessors(new Running(step)).compile(all);
    }

    // The methods the query gives for each type annotated with @Mark, by simple name, as Owner.method(parameters).
    private static Found query(
            BiFunction<ProcessingContext, TypeElement, List<ExecutableElement>> query, JavaFileObject... sources) {
        Map<String, List<String>> results = new LinkedHashMap<>();
        Compilation compilation = process(
                (context, type) -> results.put(
                        type.getSimpleName().toString(),
                        query.apply(context, type).stream()
                                .map(InheritedMethodsTest::describe)
                                .toList()),
                sources);
        assertThat(compilation).succeeded();
        return new Found(results);
    }

    private static Found methods(JavaFileObject... sources) {
        return query(ProcessingContext::methods, sources);
    }

    private static Found abstractMethods(JavaFileObject... sources) {
        return query(ProcessingContext::abstractMethods, sources);
    }

    private static String describe(ExecutableElement method) {
        return MoreElements.enclosingType(method).orElseThrow().getSimpleName() + "." + method;
    }

    private static List<String> withoutObject(List<String> methods) {
        return methods.stream().filter(method -> !method.startsWith("Object.")).toList();
    }

    private static ExecutableElement method(TypeElement type, String name) {
        return ElementFilter.methodsIn(type.getEnclosedElements()).stream()
                .filter(method -> method.getSimpleName().contentEquals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void listsDeclaredMethodsFirstThenInheritedOnesWithoutOverriddenOnes() {
        Found methods = methods(source("example.Child", """
                package example;

                interface Named {
                    String name();

                    default String describe() {
                        return name();
                    }
                }

                interface Sized {
                    int size();
                }

                abstract class Base implements Sized {
                    public abstract String name();

                    void reset() {}

                    public String describe() {
                        return "base";
                    }
                }

                @Mark
                abstract class Child extends Base implements Named {
                    public int size() {
                        return 0;
                    }

                    void extra() {}
                }
                """));

        assertThat(withoutObject(methods.get("Child")))
                .containsExactly(
                        "Child.size()",
                        "Child.extra()",
                        "Base.name()",
                        "Base.reset()",
                        "Base.describe()",
                        "Named.name()");
    }

    @Test
    void givesAClassTheMethodsOfObjectItDoesNotOverride() {
        List<String> methods = methods(source("example.Plain", """
                        package example;

                        @Mark
                        class Plain {
                            @Override
                            public String toString() {
                                return "plain";
                            }
                        }
                        """)).get("Plain");

        assertThat(methods)
                .startsWith("Plain.toString()")
                .contains(
                        "Object.equals(java.lang.Object)",
                        "Object.hashCode()",
                        "Object.getClass()",
                        "Object.clone()",
                        "Object.finalize()",
                        "Object.wait(long)")
                .doesNotContain("Object.toString()")
                .doesNotHaveDuplicates();
    }

    @Test
    void leavesThePrivateMethodsOfObjectOut() {
        List<ExecutableElement> methods = new ArrayList<>();
        Compilation compilation =
                process((context, type) -> methods.addAll(context.methods(type)), source("example.Empty", """
                        package example;

                        @Mark
                        class Empty {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(methods)
                .isNotEmpty()
                .allSatisfy(method -> assertThat(method.getModifiers()).doesNotContain(Modifier.PRIVATE));
    }

    @Test
    void givesAnInterfaceThePublicMethodsOfObjectLast() {
        Found methods = methods(source("example.Shape", """
                package example;

                @Mark
                interface Shape {
                    boolean equals(Object other);

                    double area();
                }

                @Mark
                interface Square extends Shape {
                    double side();
                }
                """));

        assertThat(methods.get("Shape"))
                .startsWith("Shape.equals(java.lang.Object)", "Shape.area()")
                .contains("Object.hashCode()", "Object.toString()", "Object.getClass()", "Object.notifyAll()")
                .doesNotContain("Object.equals(java.lang.Object)", "Object.clone()", "Object.finalize()")
                .doesNotHaveDuplicates();
        assertThat(methods.get("Shape").subList(2, methods.get("Shape").size()))
                .allSatisfy(method -> assertThat(method).startsWith("Object."));
        assertThat(methods.get("Square"))
                .startsWith("Square.side()", "Shape.equals(java.lang.Object)", "Shape.area()")
                .doesNotContain("Object.equals(java.lang.Object)");
    }

    @Test
    void leavesOutAbstractMethodsOfAnInterfaceThatObjectImplements() {
        Found abstracts = abstractMethods(source("example.Contract", """
                package example;

                import java.util.Comparator;

                @Mark
                interface Contract<T> extends Comparator<T> {
                    @Override
                    String toString();

                    @Override
                    boolean equals(Object other);

                    Object clone();

                    void extra();

                    default void helper() {}

                    static void utility() {}

                    private void hidden() {}
                }

                @Mark
                interface Inheriting extends Contract<String> {}
                """));

        assertThat(abstracts.get("Contract"))
                .containsExactly("Contract.clone()", "Contract.extra()", "Comparator.compare(T,T)");
        assertThat(abstracts.get("Inheriting"))
                .containsExactly("Contract.clone()", "Contract.extra()", "Comparator.compare(T,T)");
    }

    @Test
    void substitutesTheTypeArgumentsOfGenericSupertypes() {
        JavaFileObject sources = source("example.Boxes", """
                package example;

                interface Box<T> {
                    T get();

                    void set(T value);

                    default boolean isEmpty() {
                        return get() == null;
                    }
                }

                @Mark
                abstract class StringBox implements Box<String> {
                    public String get() {
                        return "";
                    }
                }

                @Mark
                class IntegerBox implements Box<Integer> {
                    public Integer get() {
                        return 0;
                    }

                    public void set(Integer value) {}
                }

                class Holder<T> {
                    void accept(T value) {}

                    void accept(Integer number, T value) {}
                }

                @Mark
                class TextHolder extends Holder<String> {
                    void accept(String value) {}

                    void accept(Long number) {}
                }

                abstract class Mapper<A> implements java.util.function.Function<A, String> {}

                @Mark
                class Upper extends Mapper<Integer> {
                    public String apply(Integer value) {
                        return "";
                    }
                }

                @Mark
                abstract class Lower extends Mapper<Long> {}
                """);

        Found methods = methods(sources);
        Found abstracts = abstractMethods(sources);

        assertThat(withoutObject(methods.get("StringBox")))
                .containsExactly("StringBox.get()", "Box.set(T)", "Box.isEmpty()");
        assertThat(withoutObject(methods.get("IntegerBox")))
                .containsExactly("IntegerBox.get()", "IntegerBox.set(java.lang.Integer)", "Box.isEmpty()");
        assertThat(withoutObject(methods.get("TextHolder")))
                .containsExactly(
                        "TextHolder.accept(java.lang.String)",
                        "TextHolder.accept(java.lang.Long)",
                        "Holder.accept(java.lang.Integer,T)");
        assertThat(abstracts.get("StringBox")).containsExactly("Box.set(T)");
        assertThat(abstracts.get("IntegerBox")).isEmpty();
        assertThat(abstracts.get("TextHolder")).isEmpty();
        assertThat(abstracts.get("Upper")).isEmpty();
        assertThat(abstracts.get("Lower")).containsExactly("Function.apply(T)");
    }

    @Test
    void recognisesOverridesThatJavacBridges() {
        Found methods = methods(source("example.Versions", """
                package example;

                @Mark
                class Version implements Comparable<Version> {
                    public int compareTo(Version other) {
                        return 0;
                    }
                }

                interface Converter {
                    <T> T convert(Object value, Class<T> type);
                }

                @Mark
                class Caster implements Converter {
                    public <U> U convert(Object value, Class<U> type) {
                        return type.cast(value);
                    }
                }

                @SuppressWarnings("rawtypes")
                @Mark
                class Legacy implements Comparable {
                    public int compareTo(Object other) {
                        return 0;
                    }
                }

                abstract class Producer<T> {
                    abstract T produce();
                }

                @Mark
                class Covariant extends Producer<CharSequence> {
                    String produce() {
                        return "";
                    }
                }
                """));

        assertThat(withoutObject(methods.get("Version"))).containsExactly("Version.compareTo(example.Version)");
        assertThat(withoutObject(methods.get("Caster")))
                .containsExactly("Caster.<U>convert(java.lang.Object,java.lang.Class<U>)");
        assertThat(withoutObject(methods.get("Legacy"))).containsExactly("Legacy.compareTo(java.lang.Object)");
        assertThat(withoutObject(methods.get("Covariant"))).containsExactly("Covariant.produce()");
    }

    @Test
    void listsAMethodInheritedThroughSeveralInterfacesOnce() {
        Found methods = methods(source("example.Diamond", """
                package example;

                interface Top {
                    void x();

                    default void y() {}
                }

                interface Left extends Top {}

                interface Middle extends Top {}

                interface Right extends Top {
                    @Override
                    default void x() {}
                }

                @Mark
                interface Bottom extends Left, Middle {}

                @Mark
                class Joined implements Left, Right {}

                @Mark
                abstract class Partial implements Left, Middle, Top {}
                """));

        assertThat(withoutObject(methods.get("Bottom"))).containsExactly("Top.x()", "Top.y()");
        assertThat(withoutObject(methods.get("Joined"))).containsExactly("Top.y()", "Right.x()");
        assertThat(withoutObject(methods.get("Partial"))).containsExactly("Top.x()", "Top.y()");
    }

    @Test
    void requiresImplementingADefaultMethodThatASubtypeMadeAbstractAgain() {
        JavaFileObject sources = source("example.Checks", """
                package example;

                interface Lenient {
                    default void check() {}
                }

                interface Strict extends Lenient {
                    @Override
                    void check();
                }

                @Mark
                abstract class Checker implements Lenient, Strict {}

                @Mark
                interface Stricter extends Strict {}

                abstract class Reset implements Lenient {
                    @Override
                    public abstract void check();
                }

                @Mark
                abstract class Again extends Reset {}

                @Mark
                class Relaxed implements Lenient {}
                """);

        Found methods = methods(sources);
        Found abstracts = abstractMethods(sources);

        assertThat(withoutObject(methods.get("Checker"))).containsExactly("Strict.check()");
        assertThat(abstracts.get("Checker")).containsExactly("Strict.check()");
        assertThat(abstracts.get("Stricter")).containsExactly("Strict.check()");
        assertThat(abstracts.get("Again")).containsExactly("Reset.check()");
        assertThat(abstracts.get("Relaxed")).isEmpty();
    }

    @Test
    void neitherInheritsNorOverridesPackagePrivateMethodsOfAnotherPackage() {
        List<String> facts = new ArrayList<>();
        Map<String, List<String>> methods = new LinkedHashMap<>();
        Map<String, List<String>> abstracts = new LinkedHashMap<>();
        Compilation compilation = process(
                (context, type) -> {
                    String name = type.getSimpleName().toString();
                    methods.put(
                            name,
                            withoutObject(context.methods(type).stream()
                                    .map(InheritedMethodsTest::describe)
                                    .toList()));
                    abstracts.put(
                            name,
                            context.abstractMethods(type).stream()
                                    .map(InheritedMethodsTest::describe)
                                    .toList());
                    if ("Fancy".equals(name)) {
                        TypeElement widget = context.elements().getTypeElement("example.other.Widget");
                        Elements elements = context.elements();
                        facts.add("paint: " + elements.overrides(method(type, "paint"), method(widget, "paint"), type));
                        facts.add("layout: "
                                + elements.overrides(method(type, "layout"), method(widget, "layout"), type));
                    }
                },
                source("example.other.Widget", """
                        package example.other;

                        public class Widget {
                            void paint() {}

                            protected void layout() {}

                            public void show() {}

                            private void secret() {}
                        }
                        """),
                source("example.other.Template", """
                        package example.other;

                        public abstract class Template {
                            abstract void step();

                            public abstract void run();
                        }
                        """),
                source("example.other.Near", """
                        package example.other;

                        @example.Mark
                        class Near extends Widget {}
                        """),
                source("example.Fancy", """
                        package example;

                        import example.other.Widget;

                        @Mark
                        class Fancy extends Widget {
                            void paint() {}

                            @Override
                            protected void layout() {}
                        }
                        """),
                source("example.Job", """
                        package example;

                        @Mark
                        abstract class Job extends example.other.Template {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(methods.get("Fancy")).containsExactly("Fancy.paint()", "Fancy.layout()", "Widget.show()");
        assertThat(methods.get("Near")).containsExactly("Widget.paint()", "Widget.layout()", "Widget.show()");
        assertThat(abstracts.get("Job")).containsExactly("Template.run()");
        assertThat(facts).containsExactly("paint: false", "layout: true");
    }

    @Test
    void hidesStaticMethodsInsteadOfOverridingThem() {
        List<String> facts = new ArrayList<>();
        Map<String, List<String>> methods = new LinkedHashMap<>();
        Compilation compilation = process(
                (context, type) -> {
                    methods.put(
                            type.getSimpleName().toString(),
                            withoutObject(context.methods(type).stream()
                                    .map(InheritedMethodsTest::describe)
                                    .toList()));
                    if (type.getSimpleName().contentEquals("Special")) {
                        TypeElement factory = context.elements().getTypeElement("example.Factory");
                        facts.add("create: "
                                + context.elements()
                                        .overrides(method(type, "create"), method(factory, "create"), type));
                    }
                },
                source("example.Factory", """
                        package example;

                        class Factory {
                            static Factory create() {
                                return new Factory();
                            }

                            static void log() {}

                            void run() {}
                        }

                        @Mark
                        class Special extends Factory {
                            static Factory create() {
                                return new Special();
                            }
                        }

                        @Mark
                        interface Creator {
                            static Creator make() {
                                return null;
                            }

                            void go();

                            private void helper() {}
                        }

                        @Mark
                        abstract class Maker implements Creator {}

                        @Mark
                        interface Creating extends Creator {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(methods.get("Special")).containsExactly("Special.create()", "Factory.log()", "Factory.run()");
        assertThat(methods.get("Creator")).containsExactly("Creator.make()", "Creator.go()", "Creator.helper()");
        assertThat(methods.get("Maker")).containsExactly("Creator.go()");
        assertThat(methods.get("Creating")).containsExactly("Creator.go()");
        assertThat(facts).containsExactly("create: false");
    }

    @Test
    void letsAClassMethodImplementOrBeatAnInterfaceMethod() {
        Found methods = methods(source("example.Runner", """
                package example;

                class Runner {
                    public void run() {}
                }

                @Mark
                class Task extends Runner implements Runnable {}

                interface Polite {
                    default void run() {}
                }

                @Mark
                class Mannered extends Runner implements Polite {}
                """));

        assertThat(withoutObject(methods.get("Task"))).containsExactly("Runner.run()");
        assertThat(withoutObject(methods.get("Mannered"))).containsExactly("Runner.run()");
    }

    @Test
    void decidesOverridesFromTheGivenType() {
        List<Boolean> facts = new ArrayList<>();
        Compilation compilation = process(
                (context, type) -> {
                    TypeElement runner = context.elements().getTypeElement("example.Runner");
                    ExecutableElement runnerRun = method(runner, "run");
                    ExecutableElement runnableRun =
                            method(context.elements().getTypeElement("java.lang.Runnable"), "run");
                    TypeElement version = context.elements().getTypeElement("example.Version");
                    ExecutableElement compareTo = method(version, "compareTo");
                    ExecutableElement comparableCompareTo =
                            method(context.elements().getTypeElement("java.lang.Comparable"), "compareTo");
                    facts.addAll(List.of(
                            context.elements().overrides(runnerRun, runnableRun, type),
                            context.elements().overrides(runnerRun, runnableRun, runner),
                            context.elements().overrides(compareTo, comparableCompareTo, version),
                            context.elements().overrides(compareTo, compareTo, version),
                            context.elements().overrides(comparableCompareTo, compareTo, version)));
                },
                source("example.Task", """
                        package example;

                        class Runner {
                            public void run() {}
                        }

                        @Mark
                        class Task extends Runner implements Runnable {}

                        class Version implements Comparable<Version> {
                            public int compareTo(Version other) {
                                return 0;
                            }
                        }
                        """));

        assertThat(compilation).succeeded();
        assertThat(facts).containsExactly(true, false, true, false, false);
    }

    @Test
    void returnsOneOfSeveralOverrideEquivalentAbstractMethods() {
        JavaFileObject sources = source("example.Readers", """
                package example;

                import java.io.IOException;

                abstract class Source {
                    public abstract Object read() throws Exception;
                }

                interface Reader {
                    String read();
                }

                @Mark
                abstract class Both extends Source implements Reader {}

                abstract class Checked {
                    public abstract CharSequence fetch() throws IOException;
                }

                interface Fetcher {
                    CharSequence fetch();
                }

                @Mark
                abstract class Either extends Checked implements Fetcher {}

                class FirstException extends Exception {}

                class SecondException extends Exception {}

                abstract class First {
                    public abstract void close() throws FirstException;
                }

                interface Second {
                    void close() throws SecondException;
                }

                @Mark
                abstract class Torn extends First implements Second {}

                interface Plain {
                    void close();
                }

                @Mark
                abstract class Unchecked extends First implements Plain, Second {}
                """);

        Found methods = methods(sources);
        Found abstracts = abstractMethods(sources);

        assertThat(withoutObject(methods.get("Both"))).containsExactly("Source.read()", "Reader.read()");
        assertThat(abstracts.get("Both")).containsExactly("Reader.read()");
        assertThat(abstracts.get("Either")).containsExactly("Fetcher.fetch()");
        assertThat(abstracts.get("Torn")).containsExactly("First.close()");
        assertThat(abstracts.get("Unchecked")).containsExactly("Plain.close()");
    }

    @Test
    void listsTheAbstractMethodsOfAnAnnotationType() {
        assertThat(abstractMethods(source("example.Tagged", """
                                package example;

                                @Mark
                                @interface Tagged {
                                    String value();

                                    int weight() default 1;
                                }
                                """)).get("Tagged"))
                .containsExactly("Tagged.value()", "Tagged.weight()", "Annotation.annotationType()");
    }

    @Test
    void returnsNothingAbstractForAConcreteClass() {
        assertThat(abstractMethods(source("example.Done", """
                                package example;

                                import java.util.ArrayList;

                                @Mark
                                class Done extends ArrayList<String> {}
                                """)).get("Done")).isEmpty();
    }

    @Test
    void generatesACompilingImplementationOfTheAbstractMethods() {
        List<String> implemented = new ArrayList<>();
        Compilation compilation = javac().withProcessors(new Running((context, type) -> {
                    DeclaredType supertype = (DeclaredType) type.asType();
                    Type.Builder implementation = Type.newClassBuilder(type.getSimpleName() + "Implementation");
                    if (type.getKind().isInterface()) {
                        implementation.addSuperinterface(supertype);
                    } else {
                        implementation.superclass(supertype);
                    }
                    for (ExecutableElement method : context.abstractMethods(type)) {
                        implemented.add(type.getSimpleName() + ": " + describe(method));
                        implementation.addMethod(Method.overriding(method, supertype, context.types())
                                .addStatement("throw new $T()", UnsupportedOperationException.class)
                                .build());
                    }
                    context.sources()
                            .write(
                                    JavaFile.newBuilder("example", implementation.build())
                                            .build(),
                                    type);
                }))
                .withOptions("-Xlint:all,-processing", "-Werror")
                .compile(
                        MARK,
                        source("example.Target", """
                        package example;

                        interface Box<T> {
                            T get();

                            void set(T value);

                            default boolean isEmpty() {
                                return get() == null;
                            }
                        }

                        interface Lenient {
                            default void check() {}
                        }

                        interface Strict extends Lenient {
                            @Override
                            void check();
                        }

                        interface Reader {
                            String read();
                        }

                        abstract class Source {
                            public abstract Object read() throws Exception;

                            protected abstract <E extends Exception> void fail(E error) throws E;
                        }

                        @Mark
                        abstract class Target extends Source
                                implements Box<String>, Lenient, Strict, Reader, Comparable<Target>,
                                        Repository<Integer, String> {
                            @Override
                            public String get() {
                                return "";
                            }
                        }
                        """),
                        source("example.Repository", """
                        package example;

                        import java.io.IOException;

                        interface Repository<K, V> extends Iterable<V> {
                            V find(K key) throws IOException;

                            void save(String... keys);
                        }
                        """),
                        source("example.Service", """
                        package example;

                        import java.util.Comparator;

                        @Mark
                        interface Service extends Comparator<String>, Repository<Long, String> {}
                        """));

        assertThat(compilation).succeeded().hadNoWarnings();
        assertThat(implemented)
                .containsExactlyInAnyOrder(
                        "Target: Source.<E>fail(E)",
                        "Target: Box.set(T)",
                        "Target: Strict.check()",
                        "Target: Reader.read()",
                        "Target: Comparable.compareTo(T)",
                        "Target: Repository.find(K)",
                        "Target: Repository.save(java.lang.String...)",
                        "Target: Iterable.iterator()",
                        "Service: Comparator.compare(T,T)",
                        "Service: Repository.find(K)",
                        "Service: Repository.save(java.lang.String...)",
                        "Service: Iterable.iterator()");
    }

    /** The methods a query gave for each type annotated with {@code @Mark}, by simple name. */
    private record Found(Map<String, List<String>> byType) {

        List<String> get(String type) {
            return requireNonNull(byType.get(type), type);
        }
    }

    /** Runs a step on each type annotated with {@code @Mark}. */
    private static final class Running extends StepProcessor {

        private final BiConsumer<ProcessingContext, TypeElement> step;

        Running(BiConsumer<ProcessingContext, TypeElement> step) {
            this.step = step;
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
                    ElementFilter.typesIn(elements.annotatedWith("example.Mark"))
                            .forEach(type -> step.accept(context, type));
                    return Set.of();
                }
            });
        }
    }
}
