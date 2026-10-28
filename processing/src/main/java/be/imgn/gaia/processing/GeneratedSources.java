package be.imgn.gaia.processing;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.processing.internal.UnicodeEscapes;
import be.imgn.gaia.processing.source.Annotation;
import be.imgn.gaia.processing.source.ClassName;
import be.imgn.gaia.processing.source.JavaFile;
import be.imgn.gaia.processing.source.Type;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import org.jspecify.annotations.Nullable;

/**
 * Writes the Java source files a processor generates, with their package declaration and imports: modelled as a
 * {@link JavaFile}, or written as text.
 *
 * <pre>{@code
 * Type mapper = Type.newClassBuilder("FooMapper")
 *         .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
 *         .addSuperinterface(TypeName.of(mapperType))
 *         .addMethod(...)
 *         .build();
 * context.sources().write(JavaFile.newBuilder("com.example", mapper).build(), mapperElement);
 * }</pre>
 *
 * <p>The same as text, naming each type through a {@link SourceScope}, which works out the imports:
 *
 * <pre>{@code
 * context.sources().write("com.example.FooMapper", scope -> """
 *         %s
 *         public final class FooMapper implements %s {
 *             public %s map(%s source) { ... }
 *         }
 *         """.formatted(scope.generated(), scope.name(mapperType), scope.name(targetType), scope.name(sourceType)),
 *         mapperElement);
 * }</pre>
 *
 * <p>The body is the source after the imports: the type declaration, with its annotations and Javadoc. Every type it
 * refers to should be named through the {@link SourceScope}, which works out the imports.
 *
 * <p>Either way, the file is written in ASCII only: every character after U+007F, in identifiers, comments, Javadoc and
 * literals alike, is written as a Unicode escape, as <code>&#92;u00e9</code> for {@code é}, and each half of a
 * supplementary character such as an emoji as an escape of its own. The filer encodes a source with the
 * {@code -encoding} of the compilation, which would otherwise replace the characters it cannot represent, so that
 * {@code "日"} and {@code "月"} would both become {@code "?"}. Javac translates the escapes back before anything else, so
 * the code means exactly what it would with the characters, under any encoding. Only the file is escaped:
 * {@link JavaFile#toString()} and the text of a body keep the characters as they are.
 */
public final class GeneratedSources {

    private static final String GENERATED = "javax.annotation.processing.Generated";

    private final Elements elements;
    private final Filer filer;
    private final String generator;
    private final Set<String> written = new HashSet<>();

    GeneratedSources(Elements elements, Filer filer, String generator) {
        this.elements = elements;
        this.filer = filer;
        this.generator = generator;
    }

    /**
     * Writes a source file, unless it was already created. Its top-level type gets a {@code @Generated} annotation
     * naming the processor, unless it has one already or the generated code cannot see it. The types of
     * {@code java.lang} that a type of the package hides, such as {@code Override} next to a {@code example.Override},
     * are written qualified. The file is written in ASCII only, with Unicode escapes.
     *
     * @param file the file
     * @param originatingElements the elements the file is generated from, so that incremental builds regenerate or
     *     delete it when they change; the first one also decides whether {@code @Generated} is visible
     * @return {@code true} if the file was written, {@code false} if it had already been created, by this processor or
     *     another one
     * @throws UncheckedIOException if the file cannot be written
     */
    public boolean write(JavaFile file, Element... originatingElements) {
        requireNonNull(file, "file");
        requireNonNullElements(originatingElements);
        String qualifiedName = file.className().canonicalName();
        String generated = generatedValue(originatingElements);
        Type type = file.type();
        boolean annotated = type.annotations().stream()
                .anyMatch(annotation -> annotation.type().canonicalName().equals(GENERATED));
        JavaFile complete = generated == null || annotated
                ? file
                : file.toBuilder()
                        .type(type.toBuilder()
                                .addAnnotation(Annotation.newBuilder(ClassName.bestGuess(GENERATED))
                                        .addMember("value", "$S", generated)
                                        .build())
                                .build())
                        .build();
        JavaFile qualified = qualifyHiddenJavaLang(complete);
        return create(qualifiedName, qualified::toString, originatingElements);
    }

    /**
     * Writes a source file, unless it was already created. The file is written in ASCII only, with Unicode escapes. If
     * {@code body} fails, nothing is written, and the file can be written again.
     *
     * @param qualifiedName the fully qualified name of the top-level type the file declares
     * @param body writes the source after the imports, naming types through the given scope
     * @param originatingElements the elements the file is generated from, so that incremental builds regenerate or
     *     delete it when they change; the first one also decides whether {@code @Generated} is visible
     * @return {@code true} if the file was written, {@code false} if it had already been created, by this processor or
     *     another one
     * @throws IllegalArgumentException if {@code qualifiedName} is not a qualified name: Java identifiers, none of them
     *     a keyword, separated by dots
     * @throws UncheckedIOException if the file cannot be written
     */
    public boolean write(String qualifiedName, Function<SourceScope, String> body, Element... originatingElements) {
        requireNonNull(qualifiedName, "qualifiedName");
        requireNonNull(body, "body");
        requireNonNullElements(originatingElements);
        checkArgument(
                SourceVersion.isName(qualifiedName),
                "Invalid qualified name %s: it must be Java identifiers, none of them a keyword, separated by dots",
                qualifiedName);
        return create(
                qualifiedName,
                () -> {
                    SourceScope scope = new SourceScope(elements, qualifiedName, generatedValue(originatingElements));
                    return scope.assemble(requireNonNull(body.apply(scope), "body returned null"));
                },
                originatingElements);
    }

    /**
     * Returns whether a type can be generated with a name: no type of the compilation has it, in any module, whether
     * from the sources, the class or module path, the platform, or the files generated in the previous rounds; nor did
     * this object write a file for it, in this round or an earlier one.
     *
     * <p>Generating a type whose name another type has fails the compilation with a duplicate class, or hides the other
     * type. A processor that names the types it generates by a convention checks the name first, to report the clash on
     * the element it generates for, or to leave alone a type the user wrote by hand:
     *
     * <pre>{@code
     * ClassName name = ClassName.of(type).generatedName("Mapper");
     * if (!context.sources().isAvailable(name)) {
     *     context.diagnostics().on(type).error("Cannot generate " + name + ": a type with that name already exists");
     *     return;
     * }
     * context.sources().write(JavaFile.newBuilder(name.packageName(), mapper(name)).build(), type);
     * }</pre>
     *
     * <p>Another processor's files of the current round are not known until the next round: writing one of them returns
     * {@code false}. Checking a name does not reserve it.
     *
     * @param name the name of the type, such as one {@link ClassName#generatedName(String)} gives
     * @return {@code true} if no type has that name and this object wrote no file for it
     */
    public boolean isAvailable(ClassName name) {
        requireNonNull(name, "name");
        String canonicalName = name.canonicalName();
        return !written.contains(canonicalName)
                && elements.getAllTypeElements(canonicalName).isEmpty();
    }

    // Types of java.lang are referred to by their simple names, which the types of the package take over.
    private JavaFile qualifyHiddenJavaLang(JavaFile file) {
        PackageElement pkg = elements.getPackageElement(file.packageName());
        if (pkg == null) {
            return file;
        }
        String[] hidden = ElementFilter.typesIn(pkg.getEnclosedElements()).stream()
                .map(type -> type.getSimpleName().toString())
                .filter(name -> elements.getTypeElement("java.lang." + name) != null)
                .toArray(String[]::new);
        return hidden.length == 0
                ? file
                : file.toBuilder().alwaysQualify(hidden).build();
    }

    private static void requireNonNullElements(Element[] originatingElements) {
        requireNonNull(originatingElements, "originatingElements");
        for (Element element : originatingElements) {
            requireNonNull(element, "originatingElements");
        }
    }

    // The name is only taken once the source is ready, so that a body that fails leaves it free.
    private boolean create(String qualifiedName, Supplier<String> source, Element... originatingElements) {
        if (written.contains(qualifiedName)) {
            return false;
        }
        String text = UnicodeEscapes.escapeNonAscii(source.get());
        written.add(qualifiedName);
        try (Writer writer =
                filer.createSourceFile(qualifiedName, originatingElements).openWriter()) {
            writer.write(text);
            return true;
        } catch (FilerException e) {
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the source file of " + qualifiedName, e);
        }
    }

    private @Nullable String generatedValue(Element... originatingElements) {
        if (elements.getTypeElement(GENERATED) == null) {
            return null;
        }
        if (originatingElements.length == 0) {
            return generator;
        }
        ModuleElement module = elements.getModuleOf(originatingElements[0]);
        return module == null || module.isUnnamed() || readsJavaCompiler(module) ? generator : null;
    }

    // Whether a module reads java.compiler: requires it, or requires a module that requires it transitively.
    private static boolean readsJavaCompiler(ModuleElement module) {
        Deque<ModuleElement> pending = new ArrayDeque<>();
        Set<ModuleElement> seen = new HashSet<>();
        requiredBy(module, false, pending);
        while (!pending.isEmpty()) {
            ModuleElement required = pending.pop();
            if (required.getQualifiedName().contentEquals("java.compiler")) {
                return true;
            }
            if (seen.add(required)) {
                requiredBy(required, true, pending);
            }
        }
        return false;
    }

    private static void requiredBy(ModuleElement module, boolean transitiveOnly, Deque<ModuleElement> pending) {
        for (ModuleElement.RequiresDirective requires : ElementFilter.requiresIn(module.getDirectives())) {
            if (!transitiveOnly || requires.isTransitive()) {
                pending.push(requires.getDependency());
            }
        }
    }
}
