package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import be.imgn.gaia.processing.MoreElements;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.Visibility;
import be.imgn.gaia.processing.testing.Compilation;
import be.imgn.gaia.processing.testing.CompilationExtension;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.Parameterizable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The visibility of elements, as declared and given the types that enclose them. */
@ExtendWith(CompilationExtension.class)
class VisibilityTest {

    private static final JavaFileObject MARK = source("example.Mark", """
            package example;

            public @interface Mark {}
            """);

    private static Compilation process(BiConsumer<ProcessingContext, Element> step, JavaFileObject... sources) {
        List<JavaFileObject> all = new ArrayList<>(List.of(MARK));
        all.addAll(List.of(sources));
        return javac().withProcessors(new Running(step)).compile(all);
    }

    // The visibility of each type annotated with @Mark and of everything in it, by path, as "PUBLIC", or as
    // "PUBLIC, effectively PRIVATE" when the effective visibility differs.
    private static Map<String, String> visibilities(JavaFileObject... sources) {
        Map<String, String> results = new LinkedHashMap<>();
        Compilation compilation = process(
                (context, marked) -> walk(marked, element -> results.put(path(element), describe(element))), sources);
        assertThat(compilation).succeeded();
        return results;
    }

    // The property of each local or anonymous class and of everything in it, and of each local variable or parameter,
    // by label: those are attributed after processing, when javac has analyzed the code.
    private static <T> Map<String, T> locals(Function<Element, T> property, JavaFileObject... sources) {
        Map<String, T> results = new LinkedHashMap<>();
        Compilation compilation = javac().withProcessors(
                        new Analyzing(element -> results.put(label(element), property.apply(element))))
                .compile(sources);
        assertThat(compilation).succeeded();
        return results;
    }

    private static String describe(Element element) {
        Visibility own = MoreElements.visibility(element);
        Visibility effective = MoreElements.effectiveVisibility(element);
        return own == effective ? own.name() : own + ", effectively " + effective;
    }

    // The element, its type parameters, its parameters, and everything it encloses.
    private static void walk(Element element, Consumer<Element> action) {
        action.accept(element);
        if (element instanceof Parameterizable parameterizable) {
            parameterizable.getTypeParameters().forEach(parameter -> walk(parameter, action));
        }
        if (element instanceof ExecutableElement executable) {
            executable.getParameters().forEach(parameter -> walk(parameter, action));
        }
        element.getEnclosedElements().forEach(enclosed -> walk(enclosed, action));
    }

    // As Outer.Inner.method(int).parameter, from the top-level type.
    private static String path(Element element) {
        Element enclosing = element.getEnclosingElement();
        return enclosing == null || enclosing.getKind() == ElementKind.PACKAGE
                ? label(element)
                : path(enclosing) + "." + label(element);
    }

    private static String label(Element element) {
        return switch (element.getKind()) {
            case TYPE_PARAMETER -> "<" + element.getSimpleName() + ">";
            case RECORD_COMPONENT -> element.getSimpleName() + " component";
            case CONSTRUCTOR, METHOD -> {
                ExecutableElement executable = (ExecutableElement) element;
                String name = element.getKind() == ElementKind.CONSTRUCTOR
                        ? label(requireNonNull(element.getEnclosingElement()))
                        : element.getSimpleName().toString();
                yield executable.getParameters().stream()
                        .map(parameter -> parameter.asType().toString())
                        .collect(Collectors.joining(",", name + "(", ")"));
            }
            default -> element.getSimpleName().isEmpty() ? anonymous((TypeElement) element) : name(element);
        };
    }

    private static String name(Element element) {
        return element.getSimpleName().toString();
    }

    // An anonymous class as "new Runnable", after the interface it implements or the class it extends.
    private static String anonymous(TypeElement type) {
        TypeMirror supertype = type.getInterfaces().isEmpty()
                ? type.getSuperclass()
                : type.getInterfaces().getFirst();
        return "new " + name(((DeclaredType) supertype).asElement());
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
    void ordersVisibilitiesFromTheMostRestrictiveToTheMostOpen() {
        assertThat(Visibility.values())
                .containsExactly(Visibility.PRIVATE, Visibility.PACKAGE, Visibility.PROTECTED, Visibility.PUBLIC);
        assertThat(Visibility.PRIVATE).isLessThan(Visibility.PACKAGE);
        assertThat(Visibility.PROTECTED).isGreaterThan(Visibility.PACKAGE);
    }

    @Test
    void takesTheVisibilityOfTheAccessModifier() {
        Map<String, String> visibilities = visibilities(source("example.Modifiers", """
                package example;

                @Mark
                public class Modifiers {
                    public int publicField;
                    protected int protectedField;
                    int packageField;
                    private int privateField;

                    public Modifiers() {}
                    protected Modifiers(int value) {}
                    Modifiers(long value) {}
                    private Modifiers(String value) {}

                    public void publicMethod() {}
                    protected static void protectedMethod() {}
                    void packageMethod() {}
                    private void privateMethod() {}

                    public class PublicInner {}
                    protected static class ProtectedNested {}
                    interface PackageNested {}
                    private enum PrivateNested {}
                }
                """));

        assertThat(visibilities)
                .containsOnly(
                        entry("Modifiers", "PUBLIC"),
                        entry("Modifiers.publicField", "PUBLIC"),
                        entry("Modifiers.protectedField", "PROTECTED"),
                        entry("Modifiers.packageField", "PACKAGE"),
                        entry("Modifiers.privateField", "PRIVATE"),
                        entry("Modifiers.Modifiers()", "PUBLIC"),
                        entry("Modifiers.Modifiers(int)", "PROTECTED"),
                        entry("Modifiers.Modifiers(int).value", "PRIVATE"),
                        entry("Modifiers.Modifiers(long)", "PACKAGE"),
                        entry("Modifiers.Modifiers(long).value", "PRIVATE"),
                        entry("Modifiers.Modifiers(java.lang.String)", "PRIVATE"),
                        entry("Modifiers.Modifiers(java.lang.String).value", "PRIVATE"),
                        entry("Modifiers.publicMethod()", "PUBLIC"),
                        entry("Modifiers.protectedMethod()", "PROTECTED"),
                        entry("Modifiers.packageMethod()", "PACKAGE"),
                        entry("Modifiers.privateMethod()", "PRIVATE"),
                        entry("Modifiers.PublicInner", "PUBLIC"),
                        entry("Modifiers.PublicInner.PublicInner()", "PUBLIC"),
                        entry("Modifiers.ProtectedNested", "PROTECTED"),
                        entry("Modifiers.ProtectedNested.ProtectedNested()", "PROTECTED"),
                        entry("Modifiers.PackageNested", "PACKAGE"),
                        entry("Modifiers.PrivateNested", "PRIVATE"),
                        entry("Modifiers.PrivateNested.values()", "PUBLIC, effectively PRIVATE"),
                        entry("Modifiers.PrivateNested.valueOf(java.lang.String)", "PUBLIC, effectively PRIVATE"),
                        entry("Modifiers.PrivateNested.valueOf(java.lang.String).name", "PRIVATE"),
                        entry("Modifiers.PrivateNested.PrivateNested()", "PRIVATE"));
    }

    @Test
    void restrictsAnElementToTheVisibilityOfTheTypesEnclosingIt() {
        Map<String, String> visibilities = visibilities(source("example.Outer", """
                package example;

                @Mark
                public class Outer {
                    private static class PrivateNested {
                        public void open() {}

                        public static class PublicInPrivate {
                            public int field;
                        }
                    }

                    static class PackageNested {
                        public void open() {}

                        protected void guarded() {}

                        protected static class ProtectedInPackage {
                            public void open() {}
                        }
                    }

                    protected static class ProtectedNested {
                        public void open() {}

                        void closed() {}

                        public class PublicInProtected {
                            public void open() {}
                        }
                    }

                    public static class PublicNested {
                        public static class PublicInPublic {
                            public void open() {}
                        }
                    }
                }

                @Mark
                class Hidden {
                    public void open() {}

                    public static class PublicInHidden {
                        public void open() {}
                    }
                }
                """));

        assertThat(visibilities)
                .contains(
                        entry("Outer", "PUBLIC"),
                        entry("Outer.PrivateNested", "PRIVATE"),
                        entry("Outer.PrivateNested.PrivateNested()", "PRIVATE"),
                        entry("Outer.PrivateNested.open()", "PUBLIC, effectively PRIVATE"),
                        entry("Outer.PrivateNested.PublicInPrivate", "PUBLIC, effectively PRIVATE"),
                        entry("Outer.PrivateNested.PublicInPrivate.field", "PUBLIC, effectively PRIVATE"),
                        entry("Outer.PackageNested.open()", "PUBLIC, effectively PACKAGE"),
                        entry("Outer.PackageNested.guarded()", "PROTECTED, effectively PACKAGE"),
                        entry("Outer.PackageNested.ProtectedInPackage", "PROTECTED, effectively PACKAGE"),
                        entry("Outer.PackageNested.ProtectedInPackage.open()", "PUBLIC, effectively PACKAGE"),
                        entry("Outer.ProtectedNested.open()", "PUBLIC, effectively PROTECTED"),
                        entry("Outer.ProtectedNested.closed()", "PACKAGE"),
                        entry("Outer.ProtectedNested.PublicInProtected.open()", "PUBLIC, effectively PROTECTED"),
                        entry("Outer.PublicNested.PublicInPublic", "PUBLIC"),
                        entry("Outer.PublicNested.PublicInPublic.PublicInPublic()", "PUBLIC"),
                        entry("Outer.PublicNested.PublicInPublic.open()", "PUBLIC"),
                        entry("Hidden", "PACKAGE"),
                        entry("Hidden.Hidden()", "PACKAGE"),
                        entry("Hidden.open()", "PUBLIC, effectively PACKAGE"),
                        entry("Hidden.PublicInHidden.open()", "PUBLIC, effectively PACKAGE"));
    }

    @Test
    void makesTheMembersOfAnInterfacePublicUnlessTheyArePrivate() {
        Map<String, String> visibilities = visibilities(source("example.Api", """
                package example;

                @Mark
                public interface Api {
                    int CONSTANT = 1;

                    void run();

                    default void defaulted() {}

                    static void create() {}

                    private void helper() {}

                    private static void staticHelper() {}

                    class Implementation {
                        void packageInClass() {}
                    }

                    interface Nested {
                        void nested();
                    }

                    enum Kind {
                        ONE
                    }

                    @interface Note {
                        String value();
                    }
                }

                @Mark
                class Internal {
                    interface Hidden {
                        void run();
                    }

                    private interface Secret {
                        int VALUE = 1;
                    }
                }
                """));

        assertThat(visibilities)
                .contains(
                        entry("Api.CONSTANT", "PUBLIC"),
                        entry("Api.run()", "PUBLIC"),
                        entry("Api.defaulted()", "PUBLIC"),
                        entry("Api.create()", "PUBLIC"),
                        entry("Api.helper()", "PRIVATE"),
                        entry("Api.staticHelper()", "PRIVATE"),
                        entry("Api.Implementation", "PUBLIC"),
                        entry("Api.Implementation.Implementation()", "PUBLIC"),
                        entry("Api.Implementation.packageInClass()", "PACKAGE"),
                        entry("Api.Nested", "PUBLIC"),
                        entry("Api.Nested.nested()", "PUBLIC"),
                        entry("Api.Kind", "PUBLIC"),
                        entry("Api.Kind.ONE", "PUBLIC"),
                        entry("Api.Kind.Kind()", "PRIVATE"),
                        entry("Api.Note", "PUBLIC"),
                        entry("Api.Note.value()", "PUBLIC"),
                        entry("Internal.Hidden", "PACKAGE"),
                        entry("Internal.Hidden.run()", "PUBLIC, effectively PACKAGE"),
                        entry("Internal.Secret", "PRIVATE"),
                        entry("Internal.Secret.VALUE", "PUBLIC, effectively PRIVATE"));
    }

    @Test
    void makesEnumConstructorsPrivateAndEnumConstantsPublic() {
        Map<String, String> visibilities = visibilities(source("example.Planet", """
                package example;

                @Mark
                public enum Planet {
                    MERCURY(1),
                    VENUS(2);

                    private final int order;

                    Planet(int order) {
                        this.order = order;
                    }

                    public int order() {
                        return order;
                    }

                    enum Moon {
                        LUNA
                    }

                    private enum Secret {
                        HIDDEN;

                        private Secret() {}
                    }
                }
                """));

        assertThat(visibilities)
                .contains(
                        entry("Planet", "PUBLIC"),
                        entry("Planet.MERCURY", "PUBLIC"),
                        entry("Planet.VENUS", "PUBLIC"),
                        entry("Planet.order", "PRIVATE"),
                        entry("Planet.Planet(int)", "PRIVATE"),
                        entry("Planet.order()", "PUBLIC"),
                        entry("Planet.values()", "PUBLIC"),
                        entry("Planet.Moon", "PACKAGE"),
                        entry("Planet.Moon.LUNA", "PUBLIC, effectively PACKAGE"),
                        entry("Planet.Moon.Moon()", "PRIVATE"),
                        entry("Planet.Secret.HIDDEN", "PUBLIC, effectively PRIVATE"),
                        entry("Planet.Secret.Secret()", "PRIVATE"));
    }

    @Test
    void makesRecordComponentsPublicAndTheFieldsHoldingThemPrivate() {
        Map<String, String> visibilities = visibilities(source("example.Point", """
                package example;

                @Mark
                public record Point(int x, int y) {
                    @Override
                    public int y() {
                        return y;
                    }

                    record Pair(String left, String right) {
                        Pair {}
                    }

                    private record Secret(long value) {}
                }
                """));

        assertThat(visibilities)
                .contains(
                        entry("Point", "PUBLIC"),
                        entry("Point.x component", "PUBLIC"),
                        entry("Point.x", "PRIVATE"),
                        entry("Point.x()", "PUBLIC"),
                        entry("Point.y()", "PUBLIC"),
                        entry("Point.Point(int,int)", "PUBLIC"),
                        entry("Point.Point(int,int).x", "PRIVATE"),
                        entry("Point.Pair.left component", "PUBLIC, effectively PACKAGE"),
                        entry("Point.Pair.left()", "PUBLIC, effectively PACKAGE"),
                        entry("Point.Pair.Pair(java.lang.String,java.lang.String)", "PACKAGE"),
                        entry("Point.Secret.value component", "PUBLIC, effectively PRIVATE"),
                        entry("Point.Secret.value", "PRIVATE"),
                        entry("Point.Secret.value()", "PUBLIC, effectively PRIVATE"),
                        entry("Point.Secret.Secret(long)", "PRIVATE"));
    }

    @Test
    void givesClassesWithoutConstructorsOneOfTheirOwnVisibility() {
        Map<String, String> visibilities = visibilities(source("example.Defaults", """
                package example;

                @Mark
                public class Defaults {
                    public static class PublicNested {}

                    protected static class ProtectedNested {}

                    static class PackageNested {}

                    private static class PrivateNested {}
                }

                @Mark
                class PackageDefaults {}
                """));

        assertThat(visibilities)
                .containsOnly(
                        entry("Defaults", "PUBLIC"),
                        entry("Defaults.Defaults()", "PUBLIC"),
                        entry("Defaults.PublicNested", "PUBLIC"),
                        entry("Defaults.PublicNested.PublicNested()", "PUBLIC"),
                        entry("Defaults.ProtectedNested", "PROTECTED"),
                        entry("Defaults.ProtectedNested.ProtectedNested()", "PROTECTED"),
                        entry("Defaults.PackageNested", "PACKAGE"),
                        entry("Defaults.PackageNested.PackageNested()", "PACKAGE"),
                        entry("Defaults.PrivateNested", "PRIVATE"),
                        entry("Defaults.PrivateNested.PrivateNested()", "PRIVATE"),
                        entry("PackageDefaults", "PACKAGE"),
                        entry("PackageDefaults.PackageDefaults()", "PACKAGE"));
    }

    @Test
    void makesTypeParametersAndParametersPrivate() {
        Map<String, String> visibilities = visibilities(source("example.Box", """
                package example;

                @Mark
                public class Box<T> {
                    public <U extends T> void put(U value) {}

                    public static class Pair<L, R> {}
                }
                """));

        assertThat(visibilities)
                .containsOnly(
                        entry("Box", "PUBLIC"),
                        entry("Box.<T>", "PRIVATE"),
                        entry("Box.Box()", "PUBLIC"),
                        entry("Box.put(U)", "PUBLIC"),
                        entry("Box.put(U).<U>", "PRIVATE"),
                        entry("Box.put(U).value", "PRIVATE"),
                        entry("Box.Pair", "PUBLIC"),
                        entry("Box.Pair.<L>", "PRIVATE"),
                        entry("Box.Pair.<R>", "PRIVATE"),
                        entry("Box.Pair.Pair()", "PUBLIC"));
    }

    @Test
    void makesPackagesAndModulesPublic(Elements elements) {
        TypeElement entry = elements.getTypeElement("java.util.Map.Entry");
        Element utilPackage = MoreElements.packageOf(entry);
        Element base = elements.getModuleOf(entry);

        assertThat(MoreElements.visibility(utilPackage)).isEqualTo(Visibility.PUBLIC);
        assertThat(MoreElements.effectiveVisibility(utilPackage)).isEqualTo(Visibility.PUBLIC);
        assertThat(MoreElements.visibility(base)).isEqualTo(Visibility.PUBLIC);
        assertThat(MoreElements.effectiveVisibility(base)).isEqualTo(Visibility.PUBLIC);
        assertThat(MoreElements.effectiveVisibility(entry)).isEqualTo(Visibility.PUBLIC);
    }

    @Test
    void readsTheVisibilityOfCompiledClassesFromTheirClassFiles(Elements elements) {
        TypeElement builder = elements.getTypeElement("java.lang.AbstractStringBuilder");
        ExecutableElement length = method(builder, "length");
        TypeElement object = elements.getTypeElement("java.lang.Object");

        assertThat(MoreElements.visibility(builder)).isEqualTo(Visibility.PACKAGE);
        assertThat(MoreElements.visibility(length)).isEqualTo(Visibility.PUBLIC);
        assertThat(MoreElements.effectiveVisibility(length)).isEqualTo(Visibility.PACKAGE);
        assertThat(MoreElements.visibility(method(object, "clone"))).isEqualTo(Visibility.PROTECTED);
        assertThat(MoreElements.visibility(method(elements.getTypeElement("java.util.Map.Entry"), "getKey")))
                .isEqualTo(Visibility.PUBLIC);
        assertThat(MoreElements.visibility(method(elements.getTypeElement("java.lang.Thread.State"), "values")))
                .isEqualTo(Visibility.PUBLIC);
    }

    @Test
    void makesLocalAndAnonymousClassesAndWhatTheyDeclarePrivate() {
        Map<String, String> visibilities = locals(VisibilityTest::describe, source("example.Locals", """
                package example;

                import java.io.StringReader;
                import java.util.function.IntUnaryOperator;

                public class Locals {
                    public Runnable run(Object input) {
                        class Local {
                            public void localOpen() {}

                            private int localField;
                        }
                        record LocalRecord(int part) {}
                        enum LocalEnum {
                            CONSTANT
                        }
                        interface LocalInterface {
                            void localAbstract();
                        }
                        int count = 0;
                        try (StringReader reader = new StringReader("")) {
                            if (input instanceof String text) {
                                count += text.length();
                            }
                        } catch (RuntimeException failure) {
                            count--;
                        }
                        IntUnaryOperator lambda = lambdaParameter -> lambdaParameter + 1;
                        return new Runnable() {
                            @Override
                            public void run() {}
                        };
                    }

                    public enum WithBody {
                        CONSTANT {
                            @Override
                            void act() {}
                        };

                        void act() {}
                    }
                }
                """));

        assertThat(visibilities)
                .contains(
                        entry("input", "PRIVATE"),
                        entry("Local", "PRIVATE"),
                        entry("Local()", "PACKAGE, effectively PRIVATE"),
                        entry("localOpen()", "PUBLIC, effectively PRIVATE"),
                        entry("localField", "PRIVATE"),
                        entry("LocalRecord", "PRIVATE"),
                        entry("part component", "PUBLIC, effectively PRIVATE"),
                        entry("part()", "PUBLIC, effectively PRIVATE"),
                        entry("LocalEnum", "PRIVATE"),
                        entry("CONSTANT", "PUBLIC, effectively PRIVATE"),
                        entry("LocalEnum()", "PRIVATE"),
                        entry("LocalInterface", "PRIVATE"),
                        entry("localAbstract()", "PUBLIC, effectively PRIVATE"),
                        entry("count", "PRIVATE"),
                        entry("reader", "PRIVATE"),
                        entry("text", "PRIVATE"),
                        entry("failure", "PRIVATE"),
                        entry("lambda", "PRIVATE"),
                        entry("lambdaParameter", "PRIVATE"),
                        entry("new Runnable", "PRIVATE"),
                        entry("run()", "PUBLIC, effectively PRIVATE"),
                        entry("new WithBody", "PRIVATE"),
                        entry("act()", "PACKAGE, effectively PRIVATE"));
        assertThat(locals(element -> MoreElements.isAccessible(element, "example"), source("example.Locals", """
                        package example;

                        public class Locals {
                            public Object create() {
                                class Local {
                                    public void localOpen() {}
                                }
                                return new Local() {
                                    @Override
                                    public void localOpen() {}
                                };
                            }
                        }
                        """)))
                .containsOnlyKeys("Local", "Local()", "localOpen()", "new Local", "new Local()")
                .doesNotContainValue(true);
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

    /**
     * Passes each local or anonymous class, everything in them, and each local variable or parameter, to an action once
     * javac has analyzed the code: until then, javac has no elements for them.
     */
    @SupportedAnnotationTypes("*")
    private static final class Analyzing extends AbstractProcessor {

        private final Consumer<Element> action;

        Analyzing(Consumer<Element> action) {
            this.action = action;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public synchronized void init(ProcessingEnvironment environment) {
            super.init(environment);
            JavacTask task = JavacTask.instance(environment);
            Trees trees = Trees.instance(task);
            task.addTaskListener(new TaskListener() {
                @Override
                public void finished(TaskEvent event) {
                    if (event.getKind() == TaskEvent.Kind.ANALYZE && event.getTypeElement() != null) {
                        new LocalScanner(trees, action).scan(trees.getPath(event.getTypeElement()), null);
                    }
                }
            });
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            return false;
        }
    }

    private static final class LocalScanner extends TreePathScanner<Void, Void> {

        private final Trees trees;
        private final Consumer<Element> action;

        LocalScanner(Trees trees, Consumer<Element> action) {
            this.trees = trees;
            this.action = action;
        }

        @Override
        public Void visitClass(ClassTree tree, Void unused) {
            if (trees.getElement(getCurrentPath()) instanceof TypeElement type
                    && type.getNestingKind() != NestingKind.TOP_LEVEL
                    && type.getNestingKind() != NestingKind.MEMBER) {
                walk(type, action);
            }
            return super.visitClass(tree, null);
        }

        @Override
        public Void visitVariable(VariableTree tree, Void unused) {
            Element variable = trees.getElement(getCurrentPath());
            if (variable.getKind() != ElementKind.FIELD && variable.getKind() != ElementKind.ENUM_CONSTANT) {
                action.accept(variable);
            }
            return super.visitVariable(tree, null);
        }
    }
}
