# Annotation processing

`gaia-processing` is for writing annotation processors: it handles the rounds, reads annotations, reports errors,
generates code and registers services.

```xml
<dependency>
  <groupId>be.imgn.gaia</groupId>
  <artifactId>gaia-processing</artifactId>
</dependency>
```

## Steps

A [`StepProcessor`](api:be.imgn.gaia.processing.StepProcessor) is a list of [`Step`](api:be.imgn.gaia.processing.Step)s,
each processing the elements annotated with some annotations, or with any annotation that carries one of its
`metaAnnotations()`:

```java
public final class MapperProcessor extends StepProcessor {
    @Override
    protected List<Step> steps(ProcessingContext context) {
        return List.of(new MapperStep(context));
    }
}

final class MapperStep implements Step {
    private final ProcessingContext context;

    MapperStep(ProcessingContext context) {
        this.context = context;
    }

    @Override
    public Set<String> annotations() {
        return Set.of("com.example.Mapper");
    }

    @Override
    public Set<? extends Element> process(AnnotatedElements elements) {
        for (Element mapper : elements.annotatedWith("com.example.Mapper")) {
            AnnotationReader annotation = context.annotation(mapper, "com.example.Mapper").orElseThrow();
            TypeMirror target = annotation.type("target");
            if (target.getKind() != TypeKind.DECLARED) {
                context.diagnostics().on(mapper).annotation("com.example.Mapper").attribute("target")
                        .error("must be a class or an interface");
            }
            // generate the mapper
        }
        return Set.of();
    }
}
```

Rounds are handled for you. An element whose types another processor has yet to generate is held back until they
exist, and reported as an error if they never do. A step can also hand elements back for the next round, by returning
them, as long as it would get them again: elements with its annotations, or its root elements. It finishes in
`processingOver()` once all rounds are done.

A step that returns `true` from `processesRootElements()` also gets every root element of each round, annotated or
not: the top-level types, and the packages and modules declared in `package-info` and `module-info` files. That
suits the processing of classes found by what they implement or extend rather than by an annotation. A root element
whose types never exist is not reported by the processor: javac already reports them.

```java
@Override
public boolean processesRootElements() {
    return true;
}

@Override
public Set<? extends Element> process(AnnotatedElements elements) {
    TypeMirror view = context.elements().getTypeElement("com.example.View").asType();
    for (TypeElement type : ElementFilter.typesIn(elements.rootElements())) {
        if (context.types().isAssignable(type.asType(), view)) {
            // process the view
        }
    }
    return Set.of();
}
```

[`AnnotatedElements`](api:be.imgn.gaia.processing.AnnotatedElements) are the elements a step gets in a round, by
annotation and by meta-annotation, and its root elements. [`ProcessingContext`](api:be.imgn.gaia.processing.ProcessingContext) is everything
a step works with: elements, types, the filer, options, and the helpers below.

With `-Agaia.debug`, every step processor notes what each step did in each round, and how long it took, naming each
step by its `name()`.

## Reading annotations

[`AnnotationReader`](api:be.imgn.gaia.processing.AnnotationReader) reads annotation values with their types, defaults
included. `Class` values come back as types, without `MirroredTypeException`. The context finds annotations with
repeated annotations unwrapped from their container, by meta-annotation (such as qualifiers), or inherited through
`@Inherited`.

## Reporting errors

[`Diagnostics`](api:be.imgn.gaia.processing.Diagnostics) reports errors, warnings and notes on an element, one of its
annotations, or one attribute of it, and counts the errors. A step can also throw a
[`ProcessingException`](api:be.imgn.gaia.processing.ProcessingException), reported as an error before processing goes
on with the next element (`context.forEach(...)`) or the next step.

## Options

A [`ProcessorOption`](api:be.imgn.gaia.processing.ProcessorOption) is a typed processor option (`-Aname=value`) with
a default: text, boolean, integer or enum. Declare the options in `StepProcessor.options()` and read them with
`context.option(...)`. An invalid value is reported as an error.

## Generating code

The `source` package models code: [`Type`](api:be.imgn.gaia.processing.source.Type) (classes, interfaces, enums,
records, annotation types, sealed hierarchies, anonymous classes), [`Method`](api:be.imgn.gaia.processing.source.Method),
[`Field`](api:be.imgn.gaia.processing.source.Field), [`Parameter`](api:be.imgn.gaia.processing.source.Parameter),
[`Annotation`](api:be.imgn.gaia.processing.source.Annotation) and
[`CodeBlock`](api:be.imgn.gaia.processing.source.CodeBlock), with type names as
[`TypeName`](api:be.imgn.gaia.processing.source.TypeName)s, type annotations such as `@Nullable` included:

```java
Type point = Type.newRecordBuilder("Point")
        .addModifiers(Modifier.PUBLIC)
        .addRecordComponent(int.class, "x")
        .addRecordComponent(int.class, "y")
        .addMethod(Method.newCompactConstructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .beginControlFlow("if (x < 0 || y < 0)")
                .addStatement("throw new $T($S)", IllegalArgumentException.class, "negative")
                .endControlFlow()
                .build())
        .build();
context.sources().write(JavaFile.newBuilder("com.example", point).build(), originatingElement);
```

[`JavaFile`](api:be.imgn.gaia.processing.source.JavaFile) writes the code with the imports worked out, qualifying
names only where they would clash, including with the member types a supertype brings. It uses static imports, wraps
long lines at `$W`, and indents multi-line statements. Outside a processor, `writeTo(directory)` writes it under a
source root.

- `Method.overriding(...)` starts an override from an element.
- [`NameAllocator`](api:be.imgn.gaia.processing.source.NameAllocator) hands out identifiers that don't clash.
- `TypeName.of(...)` names a `Class`, a `TypeMirror` or a generic `java.lang.reflect.Type`.

[`GeneratedSources`](api:be.imgn.gaia.processing.GeneratedSources) writes the generated files, as a `JavaFile` or as
text:

- It adds `@Generated` only where the generated code can see it, so a module that doesn't read `java.compiler` still
  compiles.
- It qualifies the `java.lang` types that a type of the same package hides.
- Writing the same file twice returns `false` instead of failing.
- Files are written in ASCII, other characters as Unicode escapes, so they compile the same under any `-encoding`.

[`ClassName`](api:be.imgn.gaia.processing.source.ClassName) names generated types after what they are generated
for, and `isAvailable(name)` tells whether a type has that name already. Each name ends with `$$` and the kind of the
generated type:

| For | Call | Name |
|---|---|---|
| A type | `ClassName.of(type).generatedName("Mapper")` | `$Outer$Inner$$Mapper` |
| A package | `ClassName.generatedName("com.example", "Mappers")` | `com.example.$$Mappers` |
| A method | `ClassName.generatedName(method, "Factory")` | `$Outer$$create$$Factory`, `$Outer$$create$2$$Factory` for the second overload |
| A type, in another package | `ClassName.of(type).generatedNameIn("org.app", "Impl")` | `org.app.$$com$example$$Named$$Impl` |

No two types, packages, methods or kinds get the same name: a dollar sign within a name is written `$0`.

## Service files

[`ServiceFiles`](api:be.imgn.gaia.processing.ServiceFiles) registers generated service providers:
`context.services().add(service, implementation)` collects `META-INF/services` entries over all rounds. They are
checked, merged with the file already in the class output (minus classes that no longer exist), and written after the
last round. `discard(service)` withdraws a service's entries, so that its file isn't written.

On the module path, a provider must also be declared in `module-info.java`, which a processor cannot write. The
[Maven plugin](maven-plugin.md) adds the `provides` directives after compilation; a processor that relies on it calls
`skipModuleCheck(service)`, which silences the warning about the missing directive.

## Types and elements

[`MoreTypes`](api:be.imgn.gaia.processing.MoreTypes), [`MoreElements`](api:be.imgn.gaia.processing.MoreElements) and
[`TypeKey`](api:be.imgn.gaia.processing.TypeKey):

- check a type against a class or a name;
- navigate to an element's package or enclosing type;
- cast an element or a type, with a clear error if it is of another kind;
- tell inner classes from static nested ones;
- tell whether generated code can access an element: its [`Visibility`](api:be.imgn.gaia.processing.Visibility),
  protected access from a subclass, and module exports;
- use types as keys in sets and maps.

The context works with types too:

- `context.type(List.class, String.class)` builds `List<String>`.
- `context.isSameType(...)` compares types, ignoring type annotations such as `@Nullable`.
- `context.isSubtype(type, List.class)` checks subtypes, ignoring type arguments, and
  `context.asSupertype(type, List.class)` gives the supertype with the type arguments the hierarchy gives it, such as
  `List<String>`.
- `context.superclass(type)` gives the direct superclass with its type arguments, leaving out `Object`.
- `context.asMemberOf(type, variable)` gives a field or a parameter the type it has as a member of a type.
- `context.methods(type)` lists a type's methods as the compiler sees them, inherited ones included, and
  `context.abstractMethods(type)` the ones an implementation still has to write.
- `context.isFromSource(element)` tells code compiled from this compilation's sources, generated ones included, from
  code compiled before.

`MoreElements.firstUnresolvedType(element)` names a type javac could not resolve in an element's signature, and
`MoreTypes.isCastFromObjectUnchecked(type)` tells whether generated code casting to a type needs a reason it is safe.

## Using a processor

The applications that use a processor put it on their annotation processor path, not their class path, so
`gaia-processing` runs inside javac and never ships with them:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <annotationProcessorPaths>
      <path>
        <groupId>com.example</groupId>
        <artifactId>example-processor</artifactId>
        <version>1.0.0</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```

Since `maven-compiler-plugin` 3.12.0, the version can come from `dependencyManagement` instead of being repeated:
add `<annotationProcessorPathsUseDepMgmt>true</annotationProcessorPathsUseDepMgmt>` to the configuration and leave
out `<version>`. Declare the processor path explicitly rather than relying on processors found on the class path:
since JDK 23, javac no longer runs those unless asked to with `-proc:full`.

The processor itself registers so that javac finds it: list it in
`META-INF/services/javax.annotation.processing.Processor`, and on the module path also declare
`provides javax.annotation.processing.Processor with com.example.MapperProcessor;` in `module-info.java`.

Its tests compile sources through it with [`gaia-processing-testing`](testing.md#gaia-processing-testing).
