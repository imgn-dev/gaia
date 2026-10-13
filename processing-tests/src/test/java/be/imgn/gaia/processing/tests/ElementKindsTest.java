package be.imgn.gaia.processing.tests;

import static be.imgn.gaia.processing.testing.CompilationAssert.assertThat;
import static be.imgn.gaia.processing.testing.TestCompiler.javac;
import static be.imgn.gaia.processing.testing.TestCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.entry;

import be.imgn.gaia.processing.MoreElements;
import be.imgn.gaia.processing.testing.Compilation;
import be.imgn.gaia.processing.testing.CompilationExtension;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.Parameterizable;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/** Element casts and kind checks: {@code asType} and the other casts, {@code isRecord}, and how types are nested. */
@ExtendWith(CompilationExtension.class)
class ElementKindsTest {

    private static final Map<String, Function<Element, Element>> CASTS = casts();

    // An element of each kind but OTHER, local ones included.
    private static final JavaFileObject EVERYTHING = source("example.Everything", """
            package example;

            import java.io.StringReader;

            public class Everything<T> {
                static int counter;

                static {
                    int inStatic = 0;
                }

                {
                    int inInstance = 0;
                }

                public Everything() {}

                <U> void method(int parameter) {
                    int local = 0;
                    try (StringReader resource = new StringReader("")) {
                        local++;
                    } catch (RuntimeException exception) {
                        local--;
                    }
                    Object object = "";
                    if (object instanceof String binding) {
                        local += binding.length();
                    }
                    class Local {}
                    Runnable anonymous = new Runnable() {
                        @Override
                        public void run() {}
                    };
                }

                enum Kind {
                    PLAIN,
                    SPECIAL {}
                }

                interface Api {}

                @interface Note {}

                record Point(int x) {}
            }
            """);

    private static final JavaFileObject SEED = source("example.Seed", """
            package example;

            class Seed {}
            """);

    private static Map<String, Function<Element, Element>> casts() {
        Map<String, Function<Element, Element>> casts = new LinkedHashMap<>();
        casts.put("asType", MoreElements::asType);
        casts.put("asExecutable", MoreElements::asExecutable);
        casts.put("asVariable", MoreElements::asVariable);
        casts.put("asPackage", MoreElements::asPackage);
        casts.put("asTypeParameter", MoreElements::asTypeParameter);
        casts.put("asRecordComponent", MoreElements::asRecordComponent);
        casts.put("asModule", MoreElements::asModule);
        return casts;
    }

    // Passes every element declared in the sources to an action once javac has analyzed them: local and anonymous
    // classes, and local variables, only exist then. The package and module of each top-level type, and the
    // initializer a local variable is declared in, are included.
    private static void analyze(Consumer<Element> action, JavaFileObject... sources) {
        Compilation compilation = javac().withProcessors(new Analyzing(action)).compile(sources);
        assertThat(compilation).succeeded();
    }

    // A property of every element declared in the sources, by path.
    private static <T> Map<String, T> analyzed(Function<Element, T> property, JavaFileObject... sources) {
        Map<String, T> results = new LinkedHashMap<>();
        analyze(element -> results.putIfAbsent(path(element), property.apply(element)), sources);
        return results;
    }

    // The same, for the classes, interfaces, enums, records and annotation types only.
    private static <T> Map<String, T> analyzedTypes(Function<Element, T> property, JavaFileObject... sources) {
        Map<String, T> results = new LinkedHashMap<>();
        analyze(
                element -> {
                    if (element.getKind().isDeclaredType()) {
                        results.putIfAbsent(path(element), property.apply(element));
                    }
                },
                sources);
        return results;
    }

    // The result of a query on the elements of a compilation, made in its first round: elements read from class
    // files, such as those compiled into a directory on the class path, are complete then.
    private static <T> T query(Function<Elements, T> query, List<String> options, JavaFileObject... sources) {
        Querying<T> querying = new Querying<>(query);
        Compilation compilation = javac().withOptions(options.toArray(String[]::new))
                .withProcessors(querying)
                .compile(sources);
        assertThat(compilation).succeeded();
        return Objects.requireNonNull(querying.result, "the query ran");
    }

    // Compiles sources into a directory, to read them from class files.
    private static void compileInto(Path classOutput, JavaFileObject... sources) {
        assertThat(javac().withOptions("-d", classOutput.toString()).compile(sources))
                .succeeded();
    }

    // As Outer.Inner.method(int).parameter, from the top-level type.
    private static String path(Element element) {
        Element enclosing = element.getEnclosingElement();
        return enclosing == null || enclosing.getKind() == ElementKind.PACKAGE || isNamespace(element)
                ? label(element)
                : path(enclosing) + "." + label(element);
    }

    private static boolean isNamespace(Element element) {
        return element.getKind() == ElementKind.PACKAGE || element.getKind() == ElementKind.MODULE;
    }

    private static String label(Element element) {
        return switch (element.getKind()) {
            case PACKAGE -> "package " + element;
            case MODULE -> "module " + element;
            case TYPE_PARAMETER -> "<" + element.getSimpleName() + ">";
            case RECORD_COMPONENT -> element.getSimpleName() + " component";
            case STATIC_INIT -> "static {}";
            case INSTANCE_INIT -> "{}";
            case CONSTRUCTOR, METHOD -> {
                ExecutableElement executable = (ExecutableElement) element;
                String name = element.getKind() == ElementKind.CONSTRUCTOR
                        ? label(Objects.requireNonNull(element.getEnclosingElement()))
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

    // What each cast does with an element: "ok" when it returns the element, the message of its exception otherwise.
    private static Map<String, String> castOutcomes(Element element) {
        Map<String, String> outcomes = new LinkedHashMap<>();
        CASTS.forEach((name, cast) -> outcomes.put(name, castOutcome(element, cast)));
        return outcomes;
    }

    private static String castOutcome(Element element, Function<Element, Element> cast) {
        try {
            Element result = cast.apply(element);
            return result.equals(element) ? "ok" : "another element: " + result;
        } catch (IllegalArgumentException e) {
            return String.valueOf(e.getMessage());
        }
    }

    // Where a type is: "top-level", "inner" or "static nested", or what the checks say when they disagree.
    private static String nesting(Element element) {
        boolean topLevel = MoreElements.isTopLevel(element);
        boolean inner = MoreElements.isInnerClass(element);
        boolean staticNested = MoreElements.isStaticNested(element);
        if (topLevel && !inner && !staticNested) {
            return "top-level";
        }
        if (inner && !topLevel && !staticNested) {
            return "inner";
        }
        if (staticNested && !topLevel && !inner) {
            return "static nested";
        }
        return "topLevel=" + topLevel + ", inner=" + inner + ", staticNested=" + staticNested;
    }

    // Each component of a record, as "first: A first() explicit", with the accessor it is read through: an explicit
    // one is annotated with @Explicit.
    private static List<String> components(TypeElement record) {
        List<ExecutableElement> methods = ElementFilter.methodsIn(record.getEnclosedElements());
        List<String> components = new ArrayList<>();
        for (RecordComponentElement component : record.getRecordComponents()) {
            ExecutableElement accessor = component.getAccessor();
            assertThat(MoreElements.asRecordComponent(component)).isSameAs(component);
            assertThat(component.getEnclosingElement()).isEqualTo(record);
            assertThat(methods).contains(accessor);
            assertThat(accessor.getReturnType().toString())
                    .isEqualTo(component.asType().toString());
            components.add(component.getSimpleName() + ": " + component.asType() + " " + label(accessor)
                    + (MoreElements.isAnnotatedWith(accessor, "example.Explicit") ? " explicit" : " implicit"));
        }
        return components;
    }

    private static TypeElement type(Elements elements, String qualifiedName) {
        return Objects.requireNonNull(elements.getTypeElement(qualifiedName), qualifiedName);
    }

    private static ExecutableElement method(TypeElement type, String name) {
        return ElementFilter.methodsIn(type.getEnclosedElements()).stream()
                .filter(method -> method.getSimpleName().contentEquals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void castsEachKindOfElementToItsOwnInterfaceOnly() {
        Map<ElementKind, Map<String, String>> outcomes = new LinkedHashMap<>();
        analyze(element -> outcomes.putIfAbsent(element.getKind(), castOutcomes(element)), EVERYTHING);
        Map<String, Set<ElementKind>> succeeded = new LinkedHashMap<>();
        outcomes.forEach((kind, byCast) -> byCast.forEach((cast, outcome) -> {
            Set<ElementKind> kinds = succeeded.computeIfAbsent(cast, ignored -> EnumSet.noneOf(ElementKind.class));
            if (outcome.equals("ok")) {
                kinds.add(kind);
            } else {
                assertThat(outcome).startsWith(kind + " ").containsPattern(" is not an? [a-z ]+$");
            }
        }));

        assertThat(outcomes).containsOnlyKeys(EnumSet.complementOf(EnumSet.of(ElementKind.OTHER)));
        assertThat(succeeded)
                .containsExactly(
                        entry(
                                "asType",
                                EnumSet.of(
                                        ElementKind.CLASS,
                                        ElementKind.INTERFACE,
                                        ElementKind.ENUM,
                                        ElementKind.RECORD,
                                        ElementKind.ANNOTATION_TYPE)),
                        entry(
                                "asExecutable",
                                EnumSet.of(
                                        ElementKind.METHOD,
                                        ElementKind.CONSTRUCTOR,
                                        ElementKind.STATIC_INIT,
                                        ElementKind.INSTANCE_INIT)),
                        entry(
                                "asVariable",
                                EnumSet.of(
                                        ElementKind.FIELD,
                                        ElementKind.ENUM_CONSTANT,
                                        ElementKind.PARAMETER,
                                        ElementKind.LOCAL_VARIABLE,
                                        ElementKind.EXCEPTION_PARAMETER,
                                        ElementKind.RESOURCE_VARIABLE,
                                        ElementKind.BINDING_VARIABLE)),
                        entry("asPackage", EnumSet.of(ElementKind.PACKAGE)),
                        entry("asTypeParameter", EnumSet.of(ElementKind.TYPE_PARAMETER)),
                        entry("asRecordComponent", EnumSet.of(ElementKind.RECORD_COMPONENT)),
                        entry("asModule", EnumSet.of(ElementKind.MODULE)));
    }

    @Test
    void castsEveryKindOfTypeToATypeElement() {
        Map<String, String> kinds = analyzedTypes(
                element -> MoreElements.asType(element).equals(element)
                        ? element.getKind().toString()
                        : "not cast",
                EVERYTHING);

        // The body of an enum constant is an anonymous class, which javac gives the kind of the enum.
        assertThat(kinds)
                .containsExactly(
                        entry("Everything", "CLASS"),
                        entry("Everything.method(int).Local", "CLASS"),
                        entry("Everything.method(int).new Runnable", "CLASS"),
                        entry("Everything.Kind", "ENUM"),
                        entry("Everything.Kind.SPECIAL.new Kind", "ENUM"),
                        entry("Everything.Api", "INTERFACE"),
                        entry("Everything.Note", "ANNOTATION_TYPE"),
                        entry("Everything.Point", "RECORD"));
    }

    @Test
    void namesTheElementAndItsKindWhenACastFails() {
        Map<String, Map<String, String>> outcomes = analyzed(ElementKindsTest::castOutcomes, EVERYTHING);

        assertThat(outcomes.get("Everything"))
                .containsEntry("asExecutable", "CLASS example.Everything is not an executable")
                .containsEntry("asVariable", "CLASS example.Everything is not a variable")
                .containsEntry("asPackage", "CLASS example.Everything is not a package")
                .containsEntry("asTypeParameter", "CLASS example.Everything is not a type parameter")
                .containsEntry("asRecordComponent", "CLASS example.Everything is not a record component")
                .containsEntry("asModule", "CLASS example.Everything is not a module");
        assertThat(outcomes.get("Everything.Kind"))
                .containsEntry("asVariable", "ENUM example.Everything.Kind is not a variable");
        assertThat(outcomes.get("Everything.Api"))
                .containsEntry("asExecutable", "INTERFACE example.Everything.Api is not an executable");
        assertThat(outcomes.get("Everything.Note"))
                .containsEntry("asExecutable", "ANNOTATION_TYPE example.Everything.Note is not an executable");
        assertThat(outcomes.get("Everything.Point"))
                .containsEntry("asRecordComponent", "RECORD example.Everything.Point is not a record component");
        assertThat(outcomes.get("Everything.Kind.PLAIN"))
                .containsEntry("asExecutable", "ENUM_CONSTANT PLAIN in example.Everything.Kind is not an executable");
        assertThat(outcomes.get("Everything.counter"))
                .containsEntry("asRecordComponent", "FIELD counter in example.Everything is not a record component");
        assertThat(outcomes.get("Everything.<T>"))
                .containsEntry("asType", "TYPE_PARAMETER T in example.Everything is not a type");
        assertThat(outcomes.get("Everything.Everything()"))
                .containsEntry("asType", "CONSTRUCTOR Everything() in example.Everything is not a type");
        assertThat(outcomes.get("Everything.method(int)"))
                .containsEntry("asVariable", "METHOD <U>method(int) in example.Everything is not a variable");
        assertThat(outcomes.get("Everything.method(int).<U>"))
                .containsEntry("asType", "TYPE_PARAMETER U in <U>method(int) is not a type");
        assertThat(outcomes.get("Everything.method(int).parameter"))
                .containsEntry("asTypeParameter", "PARAMETER parameter in <U>method(int) is not a type parameter");
        assertThat(outcomes.get("Everything.method(int).local"))
                .containsEntry("asType", "LOCAL_VARIABLE local in <U>method(int) is not a type");
        assertThat(outcomes.get("Everything.method(int).resource"))
                .containsEntry("asType", "RESOURCE_VARIABLE resource in <U>method(int) is not a type");
        assertThat(outcomes.get("Everything.method(int).exception"))
                .containsEntry("asType", "EXCEPTION_PARAMETER exception in <U>method(int) is not a type");
        assertThat(outcomes.get("Everything.method(int).binding"))
                .containsEntry("asType", "BINDING_VARIABLE binding in <U>method(int) is not a type");
        assertThat(outcomes.get("Everything.method(int).Local"))
                .containsEntry("asVariable", "CLASS Local in <U>method(int) is not a variable");
        assertThat(outcomes.get("Everything.method(int).new Runnable"))
                .containsEntry(
                        "asVariable", "CLASS <anonymous example.Everything$1> in <U>method(int) is not a variable");
        assertThat(outcomes.get("Everything.Point.x component"))
                .containsEntry("asVariable", "RECORD_COMPONENT x in example.Everything.Point is not a variable");
        assertThat(outcomes.get("Everything.Point.x"))
                .containsEntry("asRecordComponent", "FIELD x in example.Everything.Point is not a record component");
        assertThat(outcomes.get("Everything.static {}"))
                .containsEntry("asType", "STATIC_INIT in example.Everything is not a type");
        assertThat(outcomes.get("Everything.{}"))
                .containsEntry("asVariable", "INSTANCE_INIT in example.Everything is not a variable");
        assertThat(outcomes.get("Everything.static {}.inStatic"))
                .containsEntry("asType", "LOCAL_VARIABLE inStatic in STATIC_INIT in example.Everything is not a type");
        assertThat(outcomes.get("package example"))
                .containsEntry("asModule", "PACKAGE example is not a module")
                .containsEntry("asType", "PACKAGE example is not a type");
        assertThat(outcomes.get("module unnamed module"))
                .containsEntry("asPackage", "MODULE unnamed module is not a package");
    }

    @Test
    void castsElementsReadFromClassFiles(Elements elements) {
        TypeElement map = type(elements, "java.util.Map");
        ExecutableElement get = method(map, "get");
        VariableElement parameter = get.getParameters().getFirst();
        VariableElement maxValue =
                ElementFilter.fieldsIn(type(elements, "java.lang.Integer").getEnclosedElements()).stream()
                        .filter(field -> field.getSimpleName().contentEquals("MAX_VALUE"))
                        .findFirst()
                        .orElseThrow();
        PackageElement util = Objects.requireNonNull(elements.getPackageElement("java.util"));
        TypeParameterElement key = map.getTypeParameters().getFirst();
        ModuleElement base = Objects.requireNonNull(elements.getModuleElement("java.base"));

        assertThat(MoreElements.asType(map)).isSameAs(map);
        assertThat(MoreElements.asExecutable(get)).isSameAs(get);
        assertThat(MoreElements.asVariable(maxValue)).isSameAs(maxValue);
        assertThat(MoreElements.asVariable(parameter)).isSameAs(parameter);
        assertThat(MoreElements.asPackage(util)).isSameAs(util);
        assertThat(MoreElements.asTypeParameter(key)).isSameAs(key);
        assertThat(MoreElements.asModule(base)).isSameAs(base);
        assertThat(MoreElements.asModule(Objects.requireNonNull(util.getEnclosingElement())))
                .isSameAs(base);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MoreElements.asType(get))
                .withMessage("METHOD get(java.lang.Object) in java.util.Map is not a type");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MoreElements.asExecutable(maxValue))
                .withMessage("FIELD MAX_VALUE in java.lang.Integer is not an executable");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MoreElements.asVariable(key))
                .withMessage("TYPE_PARAMETER K in java.util.Map is not a variable");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MoreElements.asPackage(base))
                .withMessage("MODULE java.base is not a package");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MoreElements.asModule(map))
                .withMessage("INTERFACE java.util.Map is not a module");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MoreElements.asRecordComponent(util))
                .withMessage("PACKAGE java.util is not a record component");
    }

    @Test
    void tellsTopLevelInnerAndStaticNestedMemberTypesApart() {
        Map<String, String> nestings = analyzedTypes(ElementKindsTest::nesting, source("example.Outer", """
                package example;

                public class Outer {
                    class Inner {
                        class InnerInInner {}

                        static class StaticInInner {}

                        interface InterfaceInInner {}

                        record RecordInInner() {}

                        enum EnumInInner {
                            ONE
                        }
                    }

                    static class Nested {
                        class InnerInNested {}

                        static class StaticInNested {}
                    }

                    private final class PrivateInner {}

                    protected abstract static class ProtectedNested {}

                    interface Api {
                        class ClassInInterface {}

                        static class StaticClassInInterface {}

                        interface InterfaceInInterface {}

                        enum EnumInInterface {
                            ONE
                        }

                        record RecordInInterface() {}

                        @interface AnnotationInInterface {}
                    }

                    @interface Note {
                        class ClassInAnnotation {}
                    }

                    enum Kind {
                        PLAIN,
                        SPECIAL {
                            class InConstantBody {}
                        };

                        class InnerInEnum {}

                        static class StaticInEnum {}
                    }

                    record Point(int x) {
                        class InnerInRecord {}

                        static class StaticInRecord {}

                        interface InterfaceInRecord {}
                    }
                }

                interface TopLevelInterface {
                    class ClassInTopLevelInterface {}
                }

                enum TopLevelEnum {
                    ONE
                }

                record TopLevelRecord() {}

                @interface TopLevelAnnotation {}
                """));

        assertThat(nestings)
                .containsOnly(
                        entry("Outer", "top-level"),
                        entry("Outer.Inner", "inner"),
                        entry("Outer.Inner.InnerInInner", "inner"),
                        entry("Outer.Inner.StaticInInner", "static nested"),
                        entry("Outer.Inner.InterfaceInInner", "static nested"),
                        entry("Outer.Inner.RecordInInner", "static nested"),
                        entry("Outer.Inner.EnumInInner", "static nested"),
                        entry("Outer.Nested", "static nested"),
                        entry("Outer.Nested.InnerInNested", "inner"),
                        entry("Outer.Nested.StaticInNested", "static nested"),
                        entry("Outer.PrivateInner", "inner"),
                        entry("Outer.ProtectedNested", "static nested"),
                        entry("Outer.Api", "static nested"),
                        entry("Outer.Api.ClassInInterface", "static nested"),
                        entry("Outer.Api.StaticClassInInterface", "static nested"),
                        entry("Outer.Api.InterfaceInInterface", "static nested"),
                        entry("Outer.Api.EnumInInterface", "static nested"),
                        entry("Outer.Api.RecordInInterface", "static nested"),
                        entry("Outer.Api.AnnotationInInterface", "static nested"),
                        entry("Outer.Note", "static nested"),
                        entry("Outer.Note.ClassInAnnotation", "static nested"),
                        entry("Outer.Kind", "static nested"),
                        entry("Outer.Kind.SPECIAL.new Kind", "inner"),
                        entry("Outer.Kind.SPECIAL.new Kind.InConstantBody", "inner"),
                        entry("Outer.Kind.InnerInEnum", "inner"),
                        entry("Outer.Kind.StaticInEnum", "static nested"),
                        entry("Outer.Point", "static nested"),
                        entry("Outer.Point.InnerInRecord", "inner"),
                        entry("Outer.Point.StaticInRecord", "static nested"),
                        entry("Outer.Point.InterfaceInRecord", "static nested"),
                        entry("TopLevelInterface", "top-level"),
                        entry("TopLevelInterface.ClassInTopLevelInterface", "static nested"),
                        entry("TopLevelEnum", "top-level"),
                        entry("TopLevelRecord", "top-level"),
                        entry("TopLevelAnnotation", "top-level"));
    }

    @Test
    void makesLocalAndAnonymousClassesInnerEvenInStaticContextsButNotLocalEnumsRecordsAndInterfaces() {
        Map<String, String> nestings = analyzedTypes(ElementKindsTest::nesting, source("example.Locals", """
                package example;

                import java.util.function.Supplier;

                public class Locals {
                    static Runnable staticField = new Runnable() {
                        @Override
                        public void run() {}
                    };

                    Runnable instanceField = new Runnable() {
                        @Override
                        public void run() {}
                    };

                    static {
                        class InStaticInitializer {}
                    }

                    {
                        class InInstanceInitializer {}
                    }

                    Locals() {
                        class InConstructor {}
                    }

                    void instanceMethod() {
                        class LocalClass {
                            class MemberOfLocal {}

                            static class StaticMemberOfLocal {}

                            record RecordInLocal() {}
                        }
                        record LocalRecord() {
                            class InnerInLocalRecord {}
                        }
                        enum LocalEnum {
                            ONE
                        }
                        interface LocalInterface {}
                        Object anonymous = new Object() {
                            class MemberOfAnonymous {}
                        };
                        Supplier<Object> lambda = () -> {
                            class InLambda {}
                            return new InLambda();
                        };
                    }

                    static void staticMethod() {
                        class LocalInStatic {}
                        record LocalRecordInStatic() {}
                        Runnable anonymous = new Runnable() {
                            @Override
                            public void run() {}
                        };
                    }
                }
                """));

        assertThat(nestings)
                .containsOnly(
                        entry("Locals", "top-level"),
                        entry("Locals.staticField.new Runnable", "inner"),
                        entry("Locals.instanceField.new Runnable", "inner"),
                        entry("Locals.static {}.InStaticInitializer", "inner"),
                        entry("Locals.{}.InInstanceInitializer", "inner"),
                        entry("Locals.Locals().InConstructor", "inner"),
                        entry("Locals.instanceMethod().LocalClass", "inner"),
                        entry("Locals.instanceMethod().LocalClass.MemberOfLocal", "inner"),
                        entry("Locals.instanceMethod().LocalClass.StaticMemberOfLocal", "static nested"),
                        entry("Locals.instanceMethod().LocalClass.RecordInLocal", "static nested"),
                        entry("Locals.instanceMethod().LocalRecord", "static nested"),
                        entry("Locals.instanceMethod().LocalRecord.InnerInLocalRecord", "inner"),
                        entry("Locals.instanceMethod().LocalEnum", "static nested"),
                        entry("Locals.instanceMethod().LocalInterface", "static nested"),
                        entry("Locals.instanceMethod().new Object", "inner"),
                        entry("Locals.instanceMethod().new Object.MemberOfAnonymous", "inner"),
                        entry("Locals.instanceMethod().InLambda", "inner"),
                        entry("Locals.staticMethod().LocalInStatic", "inner"),
                        entry("Locals.staticMethod().LocalRecordInStatic", "static nested"),
                        entry("Locals.staticMethod().new Runnable", "inner"));
    }

    @Test
    void tellsNestedTypesApartInClassFiles(@TempDir Path classOutput) {
        compileInto(classOutput, source("dependency.Compiled", """
                package dependency;

                public class Compiled {
                    public class Inner {
                        public class InnerInInner {}
                    }

                    public static class Nested {}

                    public interface Api {
                        class ClassInInterface {}
                    }

                    public @interface Note {
                        class ClassInAnnotation {}
                    }

                    public enum Kind {
                        ONE;

                        public class InnerInEnum {}
                    }

                    public record Point() {
                        public class InnerInRecord {}
                    }
                }
                """));
        List<String> names = List.of(
                "dependency.Compiled",
                "dependency.Compiled.Inner",
                "dependency.Compiled.Inner.InnerInInner",
                "dependency.Compiled.Nested",
                "dependency.Compiled.Api",
                "dependency.Compiled.Api.ClassInInterface",
                "dependency.Compiled.Note",
                "dependency.Compiled.Note.ClassInAnnotation",
                "dependency.Compiled.Kind",
                "dependency.Compiled.Kind.InnerInEnum",
                "dependency.Compiled.Point",
                "dependency.Compiled.Point.InnerInRecord",
                "java.util.Map",
                "java.util.Map.Entry",
                "java.lang.Thread.State");

        Map<String, String> nestings = query(
                elements -> {
                    Map<String, String> results = new LinkedHashMap<>();
                    names.forEach(name -> results.put(name, nesting(type(elements, name))));
                    return results;
                },
                List.of("-classpath", classOutput.toString()),
                SEED);

        assertThat(nestings)
                .containsExactly(
                        entry("dependency.Compiled", "top-level"),
                        entry("dependency.Compiled.Inner", "inner"),
                        entry("dependency.Compiled.Inner.InnerInInner", "inner"),
                        entry("dependency.Compiled.Nested", "static nested"),
                        entry("dependency.Compiled.Api", "static nested"),
                        entry("dependency.Compiled.Api.ClassInInterface", "static nested"),
                        entry("dependency.Compiled.Note", "static nested"),
                        entry("dependency.Compiled.Note.ClassInAnnotation", "static nested"),
                        entry("dependency.Compiled.Kind", "static nested"),
                        entry("dependency.Compiled.Kind.InnerInEnum", "inner"),
                        entry("dependency.Compiled.Point", "static nested"),
                        entry("dependency.Compiled.Point.InnerInRecord", "inner"),
                        entry("java.util.Map", "top-level"),
                        entry("java.util.Map.Entry", "static nested"),
                        entry("java.lang.Thread.State", "static nested"));
    }

    @Test
    void findsNoNestingAndNoRecordInElementsOtherThanTypes() {
        Map<String, String> checks = analyzed(
                element -> MoreElements.isTopLevel(element)
                                || MoreElements.isInnerClass(element)
                                || MoreElements.isStaticNested(element)
                                || MoreElements.isRecord(element)
                        ? "checked"
                        : "none",
                EVERYTHING);

        assertThat(checks)
                .containsOnly(
                        entry("Everything", "checked"),
                        entry("Everything.<T>", "none"),
                        entry("Everything.counter", "none"),
                        entry("Everything.static {}", "none"),
                        entry("Everything.static {}.inStatic", "none"),
                        entry("Everything.{}", "none"),
                        entry("Everything.{}.inInstance", "none"),
                        entry("Everything.Everything()", "none"),
                        entry("Everything.method(int)", "none"),
                        entry("Everything.method(int).<U>", "none"),
                        entry("Everything.method(int).parameter", "none"),
                        entry("Everything.method(int).local", "none"),
                        entry("Everything.method(int).resource", "none"),
                        entry("Everything.method(int).exception", "none"),
                        entry("Everything.method(int).object", "none"),
                        entry("Everything.method(int).binding", "none"),
                        entry("Everything.method(int).Local", "checked"),
                        entry("Everything.method(int).Local.Local()", "none"),
                        entry("Everything.method(int).anonymous", "none"),
                        entry("Everything.method(int).new Runnable", "checked"),
                        entry("Everything.method(int).new Runnable.new Runnable()", "none"),
                        entry("Everything.method(int).new Runnable.run()", "none"),
                        entry("Everything.Kind", "checked"),
                        entry("Everything.Kind.Kind()", "none"),
                        entry("Everything.Kind.PLAIN", "none"),
                        entry("Everything.Kind.SPECIAL", "none"),
                        entry("Everything.Kind.SPECIAL.new Kind", "checked"),
                        entry("Everything.Kind.SPECIAL.new Kind.new Kind()", "none"),
                        entry("Everything.Api", "checked"),
                        entry("Everything.Note", "checked"),
                        entry("Everything.Point", "checked"),
                        entry("Everything.Point.x component", "none"),
                        entry("Everything.Point.Point(int)", "none"),
                        entry("Everything.Point.Point(int).x", "none"),
                        entry("Everything.Point.x", "none"),
                        entry("package example", "none"),
                        entry("module unnamed module", "none"));
    }

    @Test
    void recognizesRecordsWhereverTheyAreDeclared(@TempDir Path classOutput) {
        Map<String, Boolean> records = analyzedTypes(MoreElements::isRecord, source("example.Shapes", """
                package example;

                public record Shapes(int count) {
                    record Nested() {}

                    class NotARecord {}

                    enum Kind {
                        ONE
                    }

                    interface Shape {}

                    @interface Note {}

                    static void method() {
                        record Local() {}
                        Object anonymous = new Object() {};
                    }
                }

                abstract class RecordLike {
                    abstract int count();
                }
                """));
        compileInto(classOutput, source("dependency.Compiled", """
                package dependency;

                public record Compiled() {
                    public record Nested() {}
                }
                """));
        List<String> names = List.of("dependency.Compiled", "dependency.Compiled.Nested", "java.lang.Record");

        Map<String, Boolean> compiled = query(
                elements -> {
                    Map<String, Boolean> results = new LinkedHashMap<>();
                    names.forEach(name -> results.put(name, MoreElements.isRecord(type(elements, name))));
                    return results;
                },
                List.of("-classpath", classOutput.toString()),
                SEED);

        assertThat(records)
                .containsOnly(
                        entry("Shapes", true),
                        entry("Shapes.Nested", true),
                        entry("Shapes.NotARecord", false),
                        entry("Shapes.Kind", false),
                        entry("Shapes.Shape", false),
                        entry("Shapes.Note", false),
                        entry("Shapes.method().Local", true),
                        entry("Shapes.method().new Object", false),
                        entry("RecordLike", false));
        assertThat(compiled)
                .containsExactly(
                        entry("dependency.Compiled", true),
                        entry("dependency.Compiled.Nested", true),
                        entry("java.lang.Record", false));
    }

    @Test
    void readsRecordComponentsWithTheirAccessorsInDeclarationOrder(@TempDir Path classOutput) {
        JavaFileObject explicit = source("example.Explicit", """
                package example;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.CLASS)
                @Target(ElementType.METHOD)
                public @interface Explicit {}
                """);
        JavaFileObject pair = source("example.Pair", """
                package example;

                import java.util.List;
                import java.util.Objects;

                public record Pair<A>(A first, int second, List<? extends A> rest, String... names) {
                    public Pair {
                        Objects.requireNonNull(first);
                        names = names.clone();
                    }

                    public Pair(A first) {
                        this(first, 0, List.of());
                    }

                    public int second(int offset) {
                        return second + offset;
                    }

                    @Explicit
                    @Override
                    public String[] names() {
                        return names.clone();
                    }

                    public static <B> Pair<B> of(B first) {
                        return new Pair<>(first);
                    }

                    @Explicit
                    @Override
                    public A first() {
                        return first;
                    }

                    public record Empty() {}
                }
                """);
        compileInto(classOutput, explicit, pair);
        Function<Elements, Map<String, List<String>>> components = elements -> Map.of(
                "Pair", components(type(elements, "example.Pair")),
                "Empty", components(type(elements, "example.Pair.Empty")),
                "Object", components(type(elements, "java.lang.Object")));
        List<String> expected = List.of(
                "first: A first() explicit",
                "second: int second() implicit",
                "rest: java.util.List<? extends A> rest() implicit",
                "names: java.lang.String[] names() explicit");

        Map<String, List<String>> fromSources = query(components, List.of(), explicit, pair);
        Map<String, List<String>> fromClassFiles =
                query(components, List.of("-classpath", classOutput.toString()), SEED);

        assertThat(fromSources)
                .containsOnly(entry("Pair", expected), entry("Empty", List.of()), entry("Object", List.of()));
        assertThat(fromClassFiles).isEqualTo(fromSources);
    }

    /**
     * Passes every element declared in each top-level type, once javac has analyzed it, to an action, along with the
     * package and module of that type, and the initializer each local variable of one is in.
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
                    TypeElement type = event.getTypeElement();
                    if (event.getKind() == TaskEvent.Kind.ANALYZE && type != null) {
                        new DeclarationScanner(trees, action).scan(trees.getPath(type), null);
                        action.accept(environment.getElementUtils().getPackageOf(type));
                        action.accept(environment.getElementUtils().getModuleOf(type));
                    }
                }
            });
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            return false;
        }
    }

    private static final class DeclarationScanner extends TreePathScanner<Void, Void> {

        private final Trees trees;
        private final Consumer<Element> action;

        DeclarationScanner(Trees trees, Consumer<Element> action) {
            this.trees = trees;
            this.action = action;
        }

        @Override
        public Void visitClass(ClassTree tree, Void unused) {
            TypeElement type = (TypeElement) trees.getElement(getCurrentPath());
            action.accept(type);
            type.getTypeParameters().forEach(action);
            type.getRecordComponents().forEach(action);
            return super.visitClass(tree, null);
        }

        @Override
        public Void visitMethod(MethodTree tree, Void unused) {
            Element method = trees.getElement(getCurrentPath());
            action.accept(method);
            ((Parameterizable) method).getTypeParameters().forEach(action);
            return super.visitMethod(tree, null);
        }

        @Override
        public Void visitVariable(VariableTree tree, Void unused) {
            Element variable = trees.getElement(getCurrentPath());
            Element enclosing = variable.getEnclosingElement();
            if (enclosing != null
                    && (enclosing.getKind() == ElementKind.STATIC_INIT
                            || enclosing.getKind() == ElementKind.INSTANCE_INIT)) {
                action.accept(enclosing);
            }
            action.accept(variable);
            return super.visitVariable(tree, null);
        }
    }

    /** Runs a query on the elements in the first round, and keeps its result. */
    @SupportedAnnotationTypes("*")
    private static final class Querying<T> extends AbstractProcessor {

        private final Function<Elements, T> query;
        private @Nullable T result;

        Querying(Function<Elements, T> query) {
            this.query = query;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            if (result == null) {
                result = query.apply(processingEnv.getElementUtils());
            }
            return false;
        }
    }
}
