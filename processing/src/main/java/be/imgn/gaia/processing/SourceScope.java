package be.imgn.gaia.processing;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import org.jspecify.annotations.Nullable;

/**
 * The names a generated source file uses, and the imports they need: a {@link GeneratedSources#write} body gets one to
 * name the types it refers to.
 *
 * <p>{@code name(...)} returns the name to write in the source, and records the import it needs. Types in
 * {@code java.lang} and in the generated file's own package need none. A type whose simple name is already taken, by
 * another import or by the generated type itself, is written fully qualified instead.
 */
public final class SourceScope {

    private static final String JAVA_LANG = "java.lang";

    private final Elements elements;
    private final String packageName;
    private final String simpleName;
    private final @Nullable String generated;
    private final Map<String, String> visibleNames = new TreeMap<>();
    private final Set<String> imports = new TreeSet<>();

    SourceScope(Elements elements, String qualifiedName, @Nullable String generated) {
        this.elements = elements;
        int dot = qualifiedName.lastIndexOf('.');
        this.packageName = dot < 0 ? "" : qualifiedName.substring(0, dot);
        this.simpleName = qualifiedName.substring(dot + 1);
        this.generated = generated;
        visibleNames.put(simpleName, qualifiedName);
    }

    /**
     * Returns the package of the generated file.
     *
     * @return the package name, empty for the unnamed package
     */
    public String packageName() {
        return packageName;
    }

    /**
     * Returns the simple name of the generated top-level type.
     *
     * @return the simple name
     */
    public String simpleName() {
        return simpleName;
    }

    /**
     * Returns the {@code @Generated} annotation to put on the generated type, naming the processor. It is empty when
     * {@code javax.annotation.processing.Generated} is not visible to the generated code: when the module the code is
     * generated in does not read {@code java.compiler}.
     *
     * @return the annotation, such as {@code @Generated("com.example.MapperProcessor")}, or an empty string
     */
    public String generated() {
        if (generated == null) {
            return "";
        }
        return "@" + name("javax.annotation.processing.Generated") + "(\"" + generated + "\")";
    }

    /**
     * Returns the name to write for a type, with its type arguments, recording the imports it needs.
     *
     * @param type the type
     * @return the name, such as {@code Map<String, List<Integer>>} or {@code Map.Entry<K, V>}
     */
    public String name(TypeMirror type) {
        return switch (type.getKind()) {
            case DECLARED -> declared((DeclaredType) type);
            case ARRAY -> name(((ArrayType) type).getComponentType()) + "[]";
            case TYPEVAR -> ((TypeVariable) type).asElement().getSimpleName().toString();
            case WILDCARD -> wildcard((WildcardType) type);
            default -> type.toString();
        };
    }

    /**
     * Returns the name to write for a class, recording the import it needs.
     *
     * @param type the class, which may be primitive or an array
     * @return the name, such as {@code List} or {@code int[]}
     */
    public String name(Class<?> type) {
        if (type.isArray()) {
            return name(type.getComponentType()) + "[]";
        }
        return type.isPrimitive() ? type.getName() : name(type.getCanonicalName());
    }

    /**
     * Returns the name to write for a type known by its canonical name, recording the import it needs.
     *
     * @param qualifiedName the canonical name, such as {@code java.util.Map.Entry}
     * @return the name, such as {@code Map.Entry}
     */
    public String name(String qualifiedName) {
        TypeElement type = elements.getTypeElement(qualifiedName);
        if (type != null) {
            return name(type);
        }
        int dot = qualifiedName.lastIndexOf('.');
        return reference(dot < 0 ? "" : qualifiedName.substring(0, dot), qualifiedName.substring(dot + 1), "");
    }

    /**
     * Returns the name to write for a type element, recording the import it needs.
     *
     * @param type the type
     * @return the name, such as {@code List} or {@code Map.Entry}
     */
    public String name(TypeElement type) {
        Deque<String> path = new ArrayDeque<>();
        Element current = type;
        while (current.getEnclosingElement() instanceof TypeElement enclosing) {
            path.push(current.getSimpleName().toString());
            current = enclosing;
        }
        String nested = path.stream().map(name -> "." + name).collect(Collectors.joining());
        return reference(
                MoreElements.packageOf(current).getQualifiedName().toString(),
                current.getSimpleName().toString(),
                nested);
    }

    private String declared(DeclaredType type) {
        String name = name((TypeElement) type.asElement());
        if (type.getTypeArguments().isEmpty()) {
            return name;
        }
        return type.getTypeArguments().stream().map(this::name).collect(Collectors.joining(", ", name + "<", ">"));
    }

    private String wildcard(WildcardType type) {
        TypeMirror extendsBound = type.getExtendsBound();
        TypeMirror superBound = type.getSuperBound();
        if (extendsBound != null) {
            return "? extends " + name(extendsBound);
        }
        return superBound == null ? "?" : "? super " + name(superBound);
    }

    // Names the top-level type topPackage.topName, then its nested path, importing the top-level type if possible.
    private String reference(String topPackage, String topName, String nested) {
        String qualified = topPackage.isEmpty() ? topName : topPackage + "." + topName;
        String visible = visibleNames.putIfAbsent(topName, qualified);
        if (visible != null && !visible.equals(qualified)) {
            return qualified + nested;
        }
        if (!topPackage.equals(packageName) && !topPackage.equals(JAVA_LANG) && !topPackage.isEmpty()) {
            imports.add(qualified);
        }
        return topName + nested;
    }

    String assemble(String body) {
        StringBuilder source = new StringBuilder();
        if (!packageName.isEmpty()) {
            source.append("package ").append(packageName).append(";\n\n");
        }
        imports.forEach(name -> source.append("import ").append(name).append(";\n"));
        if (!imports.isEmpty()) {
            source.append('\n');
        }
        return source.append(body).toString();
    }
}
