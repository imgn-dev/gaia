package be.imgn.gaia.processor;

import be.imgn.gaia.processing.AnnotatedElements;
import be.imgn.gaia.processing.AnnotationReader;
import be.imgn.gaia.processing.Diagnostics;
import be.imgn.gaia.processing.MoreElements;
import be.imgn.gaia.processing.MoreTypes;
import be.imgn.gaia.processing.ProcessingContext;
import be.imgn.gaia.processing.Step;
import be.imgn.gaia.processing.Visibility;
import be.imgn.gaia.processing.source.Annotation;
import be.imgn.gaia.processing.source.ClassName;
import be.imgn.gaia.processing.source.JavaFile;
import be.imgn.gaia.processing.source.Method;
import be.imgn.gaia.processing.source.ParameterizedTypeName;
import be.imgn.gaia.processing.source.Type;
import be.imgn.gaia.processing.source.TypeName;
import be.imgn.gaia.processing.source.WildcardTypeName;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

/**
 * Checks the {@code @Subscribe} methods of each class, and those with a subscribing annotation, and generates the index
 * that subscribes them. Checks the declarations of subscribing annotations too.
 */
final class SubscribeStep implements Step {

    private static final String SUBSCRIBE = "be.imgn.gaia.core.event.Subscribe";
    private static final String SUBSCRIBING_ANNOTATION = "be.imgn.gaia.core.event.SubscribingAnnotation";
    private static final ClassName SUBSCRIBER_INDEX = ClassName.of("be.imgn.gaia.core.event.spi", "SubscriberIndex");
    private static final ClassName REGISTRAR = SUBSCRIBER_INDEX.nested("Registrar");

    private final ProcessingContext context;

    SubscribeStep(ProcessingContext context) {
        this.context = context;
        // gaia-maven-plugin adds the provides directives to module-info.class after compilation.
        context.services().skipModuleCheck(SUBSCRIBER_INDEX);
    }

    @Override
    public Set<String> annotations() {
        return Set.of(SUBSCRIBE, SUBSCRIBING_ANNOTATION);
    }

    @Override
    public Set<String> metaAnnotations() {
        return Set.of(SUBSCRIBING_ANNOTATION);
    }

    @Override
    public Set<? extends Element> process(AnnotatedElements elements) {
        context.forEach(ElementFilter.typesIn(elements.annotatedWith(SUBSCRIBING_ANNOTATION)), this::checkDeclaration);
        var annotated = new LinkedHashSet<Element>(elements.annotatedWith(SUBSCRIBE));
        for (var element : elements.withAnnotationsAnnotatedWith(SUBSCRIBING_ANNOTATION)) {
            if (element.getKind() == ElementKind.METHOD) {
                annotated.add(element);
            } else {
                context.diagnostics()
                        .on(element)
                        .error(String.join(" and ", subscribingAnnotations(element)) + " can only annotate methods");
            }
        }
        var classes = new LinkedHashSet<TypeElement>();
        for (var method : annotated) {
            classes.add(MoreElements.enclosingType(method).orElseThrow());
        }
        Map<TypeElement, List<ExecutableElement>> methods = new LinkedHashMap<>();
        var later = new LinkedHashSet<Element>();
        for (var type : classes) {
            var subscribers = ElementFilter.methodsIn(type.getEnclosedElements()).stream()
                    .filter(method -> !subscribingAnnotations(method).isEmpty())
                    .toList();
            if (!annotated.containsAll(subscribers)) {
                // The processor holds back a method until the types of its signature are generated. The class has one
                // index only, so it waits for all its methods.
                subscribers.stream().filter(annotated::contains).forEach(later::add);
                continue;
            }
            // In the order they are declared, which is the order they are subscribed in.
            methods.put(
                    type,
                    type.getEnclosedElements().stream()
                            .filter(annotated::contains)
                            .map(MoreElements::asExecutable)
                            .toList());
        }
        context.forEach(methods.entrySet(), entry -> generate(entry.getKey(), entry.getValue()));
        return later;
    }

    private void generate(TypeElement type, List<ExecutableElement> methods) {
        var errors = context.diagnostics().errorCount();
        var packageName = MoreElements.packageOf(type).getQualifiedName().toString();
        var module = context.elements().getModuleOf(type);
        for (var method : methods) {
            check(type, method, packageName, module);
        }
        if (context.diagnostics().errorCount() > errors) {
            return;
        }
        // Any name unique to the class would do: the event bus matches an index to its class by listenerType().
        var name = ClassName.of(type).generatedName("Subscribers");
        if (!context.sources().isAvailable(name)) {
            context.diagnostics().on(type).error("Cannot generate " + name + ": a type has that name already");
            return;
        }
        var subscribe = Method.newBuilder("subscribe")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.OBJECT, "listener")
                .addParameter(REGISTRAR, "registrar")
                .addStatement("var l = ($T) listener", listenerType(type));
        for (var method : methods) {
            var eventType = MoreTypes.asTypeElement(
                            method.getParameters().getFirst().asType())
                    .orElseThrow();
            var subscribing = context.annotationsAnnotatedWith(method, SUBSCRIBING_ANNOTATION);
            if (subscribing.isEmpty()) {
                subscribe.addStatement(
                        "registrar.subscribe($T.class, l::$N)",
                        eventType,
                        method.getSimpleName().toString());
            } else {
                var annotation = annotationType(subscribing.getFirst());
                subscribe.addStatement(
                        "registrar.subscribe($T.class, $T.class, $T::new, l::$N)",
                        eventType,
                        annotation,
                        MoreTypes.asTypeElement(executor(annotation)).orElseThrow(),
                        method.getSimpleName().toString());
            }
        }
        var index = Type.newClassBuilder(name)
                // Public for ServiceLoader alone: in an exported package, javac would otherwise warn that its supertype
                // comes from a module the listener's module does not require transitively.
                .addAnnotation(Annotation.newBuilder(SuppressWarnings.class)
                        .addMember("value", "$S", "exports")
                        .build())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(SUBSCRIBER_INDEX)
                .addMethod(Method.newBuilder("listenerType")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(ParameterizedTypeName.of(ClassName.of(Class.class), WildcardTypeName.unbounded()))
                        .addStatement("return $T.class", type)
                        .build())
                .addMethod(subscribe.build())
                .build();
        context.sources().write(JavaFile.newBuilder(packageName, index).build(), type);
        context.services().add(SUBSCRIBER_INDEX, name, type);
    }

    private void check(TypeElement type, ExecutableElement method, String packageName, ModuleElement module) {
        var report = context.diagnostics().on(method);
        var names = subscribingAnnotations(method);
        var name = names.getFirst();
        if (names.size() > 1) {
            report.error("A method can have one subscribing annotation only, but this one has "
                    + String.join(" and ", names));
        }
        if (type.getKind().isInterface()) {
            report.error(name + " methods must be in a class, not an interface");
            return;
        }
        if (method.getModifiers().contains(Modifier.STATIC)) {
            report.error(name + " methods must not be static");
        }
        if (MoreElements.visibility(method) == Visibility.PRIVATE) {
            report.error(name + " methods must not be private");
        } else if (!MoreElements.isAccessible(method, packageName)) {
            report.error(name + " methods must be accessible from their package, but " + privateEnclosingType(method)
                    + " is private");
        }
        if (method.getParameters().size() != 1) {
            report.error(name + " methods must take exactly one parameter, the event");
        } else {
            checkEventType(method.getParameters().getFirst(), packageName);
        }
        if (method.getReturnType().getKind() != TypeKind.VOID) {
            report.error(name + " methods must return void");
        }
        for (var thrown : method.getThrownTypes()) {
            if (!context.isSubtype(thrown, RuntimeException.class) && !context.isSubtype(thrown, Error.class)) {
                report.error(name + " methods must not declare checked exceptions: " + thrown);
            }
        }
        for (var subscribing : context.annotationsAnnotatedWith(method, SUBSCRIBING_ANNOTATION)) {
            checkAnnotation(report, annotationType(subscribing), packageName, module);
        }
        overriddenSubscriber(type, method)
                .ifPresent(overridden -> report.error("This method overrides the "
                        + subscribingAnnotations(overridden).getFirst()
                        + " method of "
                        + overridden.getEnclosingElement()
                        + ", which delivers the events to it already: remove "
                        + name));
    }

    /** Reports what keeps the generated code from referring to a subscribing annotation and creating its executor. */
    private void checkAnnotation(
            Diagnostics.Report report, TypeElement annotation, String packageName, ModuleElement module) {
        var where = "accessible from " + describe(packageName);
        if (!MoreElements.isAccessible(annotation, packageName)) {
            report.error("@" + annotation.getSimpleName() + " must be " + where + ", where the generated code refers to"
                    + " it");
        }
        checkExecutor(report, annotation, element -> MoreElements.isAccessible(element, packageName), where)
                .filter(executor -> !MoreElements.isExportedTo(executor, module))
                .ifPresent(executor -> report.error(executorOf(annotation, executor) + " must be exported to "
                        + (module.isUnnamed() ? "the unnamed module" : "module " + module.getQualifiedName())));
    }

    /**
     * Reports what keeps the generated code from creating the executor of a subscribing annotation, wherever the
     * annotation can be used: from any package if it is public, from its own package otherwise.
     */
    private void checkDeclaration(TypeElement annotation) {
        var report = context.diagnostics().on(annotation);
        context.annotation(annotation, "java.lang.annotation.Retention")
                .filter(retention -> retention.enumConstant("value").equals("SOURCE"))
                .ifPresent(retention -> report.annotation(retention.mirror())
                        .error("A subscribing annotation must be kept in class files, with a retention of CLASS or"
                                + " RUNTIME, so that the processor sees it on the methods of classes compiled before"));
        var executor = report.annotation(SUBSCRIBING_ANNOTATION).attribute("executor");
        if (MoreElements.effectiveVisibility(annotation) == Visibility.PUBLIC) {
            checkExecutor(
                    executor,
                    annotation,
                    element -> MoreElements.effectiveVisibility(element) == Visibility.PUBLIC,
                    "public");
        } else {
            var packageName =
                    MoreElements.packageOf(annotation).getQualifiedName().toString();
            checkExecutor(
                    executor,
                    annotation,
                    element -> MoreElements.isAccessible(element, packageName),
                    "accessible from " + describe(packageName));
        }
    }

    /**
     * Reports what keeps code from creating the executor of a subscribing annotation, where {@code accessible} says
     * what the code can access, as {@code where} describes it.
     *
     * @return the executor, if it is a type
     */
    private Optional<TypeElement> checkExecutor(
            Diagnostics.Report report, TypeElement annotation, Predicate<Element> accessible, String where) {
        var type = executor(annotation);
        var found = MoreTypes.asTypeElement(type);
        if (found.isEmpty()) {
            report.error("The executor of @" + annotation.getSimpleName() + ", " + type + ", cannot be found");
            return found;
        }
        var executor = found.get();
        var prefix = executorOf(annotation, executor);
        if (!executor.getKind().isClass() || executor.getKind() == ElementKind.ENUM) {
            report.error(prefix + " must be a class");
            return found;
        }
        if (executor.getModifiers().contains(Modifier.ABSTRACT)) {
            report.error(prefix + " must not be abstract");
        }
        if (MoreElements.isInnerClass(executor)) {
            report.error(prefix + " must not be an inner class");
        }
        if (!accessible.test(executor)) {
            report.error(prefix + " must be " + where);
        } else if (ElementFilter.constructorsIn(executor.getEnclosedElements()).stream()
                .noneMatch(constructor -> constructor.getParameters().isEmpty() && accessible.test(constructor))) {
            report.error(prefix + " must have a constructor without parameters that is " + where);
        }
        return found;
    }

    private static String executorOf(TypeElement annotation, TypeElement executor) {
        return "The executor of @" + annotation.getSimpleName() + ", " + executor.getQualifiedName() + ",";
    }

    /** Returns the class a subscribing annotation names as its executor. */
    private TypeMirror executor(TypeElement annotation) {
        return context.annotation(annotation, SUBSCRIBING_ANNOTATION)
                .orElseThrow()
                .type("executor");
    }

    /**
     * Returns the annotations that subscribe a method, as written in messages: {@code @Subscribe}, then those whose
     * type has {@code @SubscribingAnnotation}.
     */
    private List<String> subscribingAnnotations(Element method) {
        var names = new ArrayList<String>();
        if (MoreElements.isAnnotatedWith(method, SUBSCRIBE)) {
            names.add("@Subscribe");
        }
        for (var subscribing : context.annotationsAnnotatedWith(method, SUBSCRIBING_ANNOTATION)) {
            names.add("@" + annotationType(subscribing).getSimpleName());
        }
        return names;
    }

    private static TypeElement annotationType(AnnotationReader annotation) {
        return MoreElements.asType(annotation.mirror().getAnnotationType().asElement());
    }

    private static String describe(String packageName) {
        return packageName.isEmpty() ? "the unnamed package" : "package " + packageName;
    }

    private void checkEventType(VariableElement parameter, String packageName) {
        var report = context.diagnostics().on(parameter);
        var eventType = parameter.asType();
        var kind = eventType.getKind();
        if (kind.isPrimitive()) {
            report.error("The event type must not be primitive: " + eventType);
        } else if (kind == TypeKind.ARRAY) {
            report.error("The event type must not be an array: " + eventType);
        } else if (kind == TypeKind.TYPEVAR) {
            report.error("The event type must not be a type variable: " + eventType);
        } else if (kind == TypeKind.DECLARED) {
            if (isParameterized(MoreTypes.asDeclared(eventType))) {
                report.error("The event type must not be parameterized: " + eventType);
            } else if (!MoreElements.isAccessible(
                    MoreTypes.asTypeElement(eventType).orElseThrow(), packageName)) {
                report.error("The event type " + eventType + " must be accessible from " + describe(packageName));
            }
        }
    }

    private static boolean isParameterized(DeclaredType type) {
        if (!type.getTypeArguments().isEmpty()) {
            return true;
        }
        return type.getEnclosingType() instanceof DeclaredType enclosing && isParameterized(enclosing);
    }

    private static TypeElement privateEnclosingType(ExecutableElement method) {
        var current = MoreElements.enclosingType(method).orElseThrow();
        while (MoreElements.visibility(current) != Visibility.PRIVATE) {
            current = MoreElements.enclosingType(current).orElseThrow();
        }
        return current;
    }

    /** Returns the method of a superclass with a subscribing annotation that a method overrides, if any. */
    private Optional<ExecutableElement> overriddenSubscriber(TypeElement type, ExecutableElement method) {
        for (var superclass = superclass(type); superclass.isPresent(); superclass = superclass(superclass.get())) {
            for (var member : superclass.get().getEnclosedElements()) {
                if (member.getKind() == ElementKind.METHOD
                        && !subscribingAnnotations(member).isEmpty()
                        && context.elements().overrides(method, MoreElements.asExecutable(member), type)) {
                    return Optional.of(MoreElements.asExecutable(member));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<TypeElement> superclass(TypeElement type) {
        return MoreTypes.asTypeElement(type.getSuperclass());
    }

    /**
     * Returns the type the generated code casts a listener to: the class, with a wildcard for each type parameter of it
     * and of the classes an inner class is in, so that the cast is checked.
     */
    private TypeName listenerType(TypeElement type) {
        return TypeName.of(withWildcards(type));
    }

    private DeclaredType withWildcards(TypeElement type) {
        var types = context.types();
        var wildcards = type.getTypeParameters().stream()
                .map(parameter -> types.getWildcardType(null, null))
                .toArray(TypeMirror[]::new);
        if (MoreElements.isInnerClass(type)) {
            var outer = MoreElements.enclosingType(type).orElseThrow();
            return types.getDeclaredType(withWildcards(outer), type, wildcards);
        }
        return types.getDeclaredType(type, wildcards);
    }
}
