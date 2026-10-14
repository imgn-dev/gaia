package be.imgn.gaia.processing;

import be.imgn.gaia.processing.internal.ElementKey;
import be.imgn.gaia.processing.source.ClassName;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Writes the service files {@link java.util.ServiceLoader} reads on the class path:
 * {@code META-INF/services/<service>}, listing the implementations of a service, one binary name per line.
 *
 * <p>A processor registers implementations as it finds or generates them, in any round:
 *
 * <pre>{@code
 * ClassName mapper = ClassName.of(type).generatedNameWithSuffix("Mapper");
 * context.sources().write(JavaFile.newBuilder(mapper.packageName(), mapperType).build(), type);
 * context.services().add(ClassName.of("com.example", "Mapper"), mapper, type);
 * }</pre>
 *
 * <p>The filer creates each file once per compilation, so the files are written all at once, after the last round: a
 * {@link StepProcessor} does it for its steps; any other processor calls {@link #write()} when
 * {@link javax.annotation.processing.RoundEnvironment#processingOver()} is {@code true}. A file that is already in the
 * class output, as after an incremental build, keeps its entries, except those of a class that no longer exists. Each
 * file lists every implementation once, sorted. Until then, {@link #discard(ClassName)} withdraws the implementations
 * of a service, so that its file is not written.
 *
 * <p>When it writes them, it checks that {@code ServiceLoader} can load each registered implementation: a public class,
 * not abstract, top-level or static, that has a public constructor without parameters and is a subtype of the service.
 * Each problem is reported as an error on the implementation. A service file is only read on the class path: a static
 * {@code provider()} method, which only a module can use, does not replace the constructor.
 *
 * <p>On the module path, {@code ServiceLoader} ignores service files and reads the {@code provides} directives of the
 * module declaration instead, which a processor cannot write. An implementation in a named module whose declaration
 * does not provide it is reported as a warning, so that the module works on both paths. A processor that adds the
 * {@code provides} directives itself after compilation, as by rewriting {@code module-info.class}, turns that check off
 * with {@link #skipModuleCheck(ClassName)}.
 */
public final class ServiceFiles {

    private static final String DIRECTORY = "META-INF/services/";

    private final Elements elements;
    private final Types types;
    private final Filer filer;
    private final Diagnostics diagnostics;
    private final Map<String, Service> pending = new TreeMap<>();
    private final Set<String> written = new HashSet<>();
    private final Set<String> unchecked = new HashSet<>();

    ServiceFiles(Elements elements, Types types, Filer filer, Diagnostics diagnostics) {
        this.elements = elements;
        this.types = types;
        this.filer = filer;
        this.diagnostics = diagnostics;
    }

    /**
     * Registers an implementation of a service, to list in the service's file when the files are written. Registering
     * it again only adds originating elements. Neither type needs to exist yet: the implementation may be generated in
     * a later round.
     *
     * @param service the service, the interface or class {@code ServiceLoader} loads implementations of
     * @param implementation the implementation; a nested class is written with its binary name, as
     *     {@code com.example.Outer$Inner}
     * @param originatingElements the elements the registration comes from, so that incremental builds rewrite the file
     *     when they change
     * @throws IllegalStateException if the file of the service was already written
     */
    public void add(ClassName service, ClassName implementation, Element... originatingElements) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(implementation, "implementation");
        Objects.requireNonNull(originatingElements, "originatingElements");
        for (Element element : originatingElements) {
            Objects.requireNonNull(element, "originatingElements");
        }
        String file = DIRECTORY + service.reflectionName();
        if (written.contains(file)) {
            throw new IllegalStateException(
                    file + " was already written: register its implementations before writing the service files");
        }
        Service entries = pending.computeIfAbsent(file, ignored -> new Service(service.withoutAnnotations()));
        entries.implementations.putIfAbsent(implementation.reflectionName(), implementation.withoutAnnotations());
        for (Element element : originatingElements) {
            ElementKey.of(element).ifPresentOrElse(entries.keys::add, () -> entries.unkeyed.add(element));
        }
    }

    /**
     * Turns off the check that the module of each implementation of a service declares {@code provides} for it. No
     * warning is reported then for an implementation in a named module whose declaration lacks the directive: for a
     * processor that adds the directives after compilation, as by rewriting {@code module-info.class} with a bytecode
     * tool. The other checks of the implementations still apply. Call it before the files are written, in any round,
     * whether the service has implementations registered yet or not.
     *
     * @param service the service whose implementations are not checked against their module declaration
     * @throws IllegalStateException if the file of the service was already written, and so checked
     */
    public void skipModuleCheck(ClassName service) {
        Objects.requireNonNull(service, "service");
        String file = DIRECTORY + service.reflectionName();
        if (written.contains(file)) {
            throw new IllegalStateException(
                    file + " was already written: skip the module check before writing the service files");
        }
        unchecked.add(file);
    }

    /**
     * Discards the implementations registered for a service, so that its file is not written: for a processor that
     * finds out, as late as in {@link Step#processingOver()}, that it must not write the file, as when the user wrote
     * one by hand. An implementation registered after this is registered anew, and its service's file written.
     *
     * <pre>{@code
     * public void processingOver() {
     *     if (hasHandWrittenServiceFile()) {
     *         context.services().discard(FACTORY);
     *         context.diagnostics().error("Remove " + FACTORY_FILE + ": the processor writes it");
     *     }
     * }
     * }</pre>
     *
     * <p>A file of the service already in the class output, as from an earlier build, is left as it is, neither pruned
     * nor deleted. The module check stays skipped if {@link #skipModuleCheck(ClassName)} skipped it. Discarding a
     * service without implementations registered does nothing.
     *
     * @param service the service whose implementations are discarded
     * @throws IllegalStateException if the file of the service was already written
     */
    public void discard(ClassName service) {
        Objects.requireNonNull(service, "service");
        String file = DIRECTORY + service.reflectionName();
        if (written.contains(file)) {
            throw new IllegalStateException(
                    file + " was already written: discard its implementations before writing the service files");
        }
        pending.remove(file);
    }

    /**
     * Checks the implementations registered since the files were last written, then writes the file of each service
     * they implement. Call it after the last round: the filer refuses to create a file twice, so an implementation
     * registered after its service's file is written cannot be added to it. A {@link StepProcessor} calls it for its
     * steps.
     *
     * <p>The entries of a file already in the class output are kept, and every entry is listed once, in the order of
     * {@link String#compareTo(String)}. A file another processor has created in this compilation cannot be read nor
     * written, which is reported as an error.
     *
     * <p>An entry of that file is dropped if its class no longer exists, as after an incremental build deleted it,
     * since {@code ServiceLoader} would fail on it: when no type of the compilation, from its sources, class path or
     * module path, has its binary name, and the class output has no class file for it either. An entry whose class file
     * is still in the class output is kept, even when the class output is not on the class path of the compilation.
     * Only the file of a service with implementations registered in the compilation is written, and so pruned.
     *
     * @throws UncheckedIOException if a file cannot be read or written
     */
    public void write() {
        pending.forEach(this::write);
        pending.clear();
    }

    private void write(String file, Service service) {
        written.add(file);
        List<Element> origins = service.origins(elements);
        boolean checkModule = !unchecked.contains(file);
        service.implementations
                .values()
                .forEach(implementation -> check(service.name, implementation, origins, checkModule));
        try {
            SortedSet<String> entries = new TreeSet<>(service.implementations.keySet());
            for (String entry : read(file)) {
                if (entries.contains(entry) || exists(entry)) {
                    entries.add(entry);
                }
            }
            create(file, entries, origins);
        } catch (FilerException e) {
            diagnostics.error("Cannot write " + file + ": it was already created in this compilation");
        }
    }

    private void check(
            ClassName serviceName, ClassName implementationName, List<Element> origins, boolean checkModule) {
        TypeElement service = elements.getTypeElement(serviceName.canonicalName());
        TypeElement implementation = elements.getTypeElement(implementationName.canonicalName());
        String missing = service == null ? serviceName.canonicalName() : implementationName.canonicalName();
        if (service == null || implementation == null) {
            String message = "Cannot register " + implementationName.canonicalName() + " as an implementation of "
                    + serviceName.canonicalName() + ": type " + missing + " does not exist";
            if (origins.isEmpty()) {
                diagnostics.error(message);
            } else {
                diagnostics.on(origins.getFirst()).error(message);
            }
            return;
        }
        List<String> problems = problems(implementation, service);
        if (!problems.isEmpty()) {
            diagnostics
                    .on(implementation)
                    .error("ServiceLoader cannot load " + implementation.getQualifiedName()
                            + " as an implementation of " + service.getQualifiedName() + ": "
                            + String.join(", ", problems));
        }
        ModuleElement module = elements.getModuleOf(implementation);
        if (checkModule && module != null && !module.isUnnamed() && !provides(module, service, implementation)) {
            diagnostics
                    .on(implementation)
                    .warning("Module " + module.getQualifiedName() + " does not declare provides "
                            + service.getQualifiedName() + " with " + implementation.getQualifiedName()
                            + ": ServiceLoader ignores META-INF/services in a named module on the module path");
        }
    }

    private List<String> problems(TypeElement implementation, TypeElement service) {
        List<String> problems = new ArrayList<>();
        boolean isClass =
                implementation.getKind() == ElementKind.CLASS || implementation.getKind() == ElementKind.RECORD;
        if (!isClass) {
            problems.add("it is not a class");
        }
        Set<Modifier> modifiers = implementation.getModifiers();
        if (!modifiers.contains(Modifier.PUBLIC)) {
            problems.add("it is not public");
        }
        if (isClass && modifiers.contains(Modifier.ABSTRACT)) {
            problems.add("it is abstract");
        }
        if (implementation.getNestingKind().isNested() && !modifiers.contains(Modifier.STATIC)) {
            problems.add("it is an inner class, not a static one");
        }
        if (isClass && !hasPublicConstructor(implementation)) {
            problems.add("it has no public constructor without parameters");
        }
        if (!types.isSubtype(types.erasure(implementation.asType()), types.erasure(service.asType()))) {
            problems.add("it is not a subtype of " + service.getQualifiedName());
        }
        return problems;
    }

    // The constructor javac adds to a class that declares none is as visible as the class, which is checked apart.
    private boolean hasPublicConstructor(TypeElement type) {
        return ElementFilter.constructorsIn(type.getEnclosedElements()).stream()
                .anyMatch(constructor -> constructor.getParameters().isEmpty()
                        && (constructor.getModifiers().contains(Modifier.PUBLIC)
                                || elements.getOrigin(constructor) == Elements.Origin.MANDATED));
    }

    private static boolean provides(ModuleElement module, TypeElement service, TypeElement implementation) {
        return ElementFilter.providesIn(module.getDirectives()).stream()
                .anyMatch(directive -> directive.getService().equals(service)
                        && directive.getImplementations().contains(implementation));
    }

    // Whether an entry kept from the existing file may still name a class: a type of the compilation has that binary
    // name, or the class output has its class file, as when it is not on the class path. An entry that is not a
    // binary name is left alone.
    private boolean exists(String entry) {
        return !SourceVersion.isName(entry) || isType(entry) || hasClassFile(entry);
    }

    // Each $ of a binary name either separates a member type from its enclosing type or belongs to a simple name: the
    // top-level type is the part before one of them, or the whole name.
    private boolean isType(String binaryName) {
        int end = binaryName.indexOf('$');
        while (end >= 0) {
            if (hasMember(binaryName.substring(0, end), binaryName)) {
                return true;
            }
            end = binaryName.indexOf('$', end + 1);
        }
        return hasMember(binaryName, binaryName);
    }

    private boolean hasMember(String topLevelName, String binaryName) {
        return elements.getAllTypeElements(topLevelName).stream().anyMatch(type -> isOrEncloses(type, binaryName));
    }

    private boolean isOrEncloses(TypeElement type, String binaryName) {
        String name = elements.getBinaryName(type).toString();
        return name.equals(binaryName)
                || (binaryName.startsWith(name + "$")
                        && ElementFilter.typesIn(type.getEnclosedElements()).stream()
                                .anyMatch(member -> isOrEncloses(member, binaryName)));
    }

    // A file the filer refuses to read, such as one created in this compilation, is taken to exist.
    private boolean hasClassFile(String binaryName) {
        int dot = binaryName.lastIndexOf('.');
        String packageName = dot < 0 ? "" : binaryName.substring(0, dot);
        try {
            filer.getResource(StandardLocation.CLASS_OUTPUT, packageName, binaryName.substring(dot + 1) + ".class")
                    .openInputStream()
                    .close();
            return true;
        } catch (FileNotFoundException | NoSuchFileException e) {
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    // The filer refuses to read a file created in this compilation, as it refuses to create it again.
    private Set<String> read(String file) throws FilerException {
        Set<String> entries = new LinkedHashSet<>();
        try (InputStream in =
                filer.getResource(StandardLocation.CLASS_OUTPUT, "", file).openInputStream()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : text.split("\\R", -1)) {
                int comment = line.indexOf('#');
                String entry = (comment < 0 ? line : line.substring(0, comment)).strip();
                if (!entry.isEmpty()) {
                    entries.add(entry);
                }
            }
        } catch (FileNotFoundException | NoSuchFileException e) {
            return Set.of();
        } catch (FilerException e) {
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the existing " + file, e);
        }
        return entries;
    }

    private void create(String file, Set<String> entries, List<Element> origins) throws FilerException {
        StringBuilder text = new StringBuilder();
        entries.forEach(entry -> text.append(entry).append('\n'));
        try {
            FileObject resource =
                    filer.createResource(StandardLocation.CLASS_OUTPUT, "", file, origins.toArray(Element[]::new));
            try (OutputStream out = resource.openOutputStream()) {
                out.write(text.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (FilerException e) {
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    /** The implementations registered for a service, and the elements they come from. */
    private static final class Service {

        private final ClassName name;
        private final Map<String, ClassName> implementations = new TreeMap<>();
        private final Set<ElementKey> keys = new LinkedHashSet<>();
        private final Set<Element> unkeyed = new LinkedHashSet<>();

        Service(ClassName name) {
            this.name = name;
        }

        // Elements of an earlier round are found again by their keys; those that have none are kept as they are.
        List<Element> origins(Elements elements) {
            Set<Element> origins = new LinkedHashSet<>();
            for (ElementKey key : keys) {
                Element element = key.resolve(elements);
                if (element != null) {
                    origins.add(element);
                }
            }
            origins.addAll(unkeyed);
            return List.copyOf(origins);
        }
    }
}
