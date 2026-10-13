package be.imgn.gaia.processing;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;
import org.jspecify.annotations.Nullable;

/**
 * A {@link TypeMirror} with the {@code equals} and {@code hashCode} of the type it denotes, for use in sets and maps:
 * {@code TypeMirror} itself does not define them, and two mirrors of {@code List<String>} need not be equal.
 *
 * <p>Two keys are equal when their types are the same type, as {@link ProcessingContext#isSameType(TypeMirror,
 * TypeMirror)} says, ignoring annotations on types at any depth: {@code List<@Nullable String>} has the key of
 * {@code List<String>}. That is, when their types have the same kind and:
 *
 * <ul>
 *   <li>for declared types, the same class or interface, equal type arguments and equal enclosing types:
 *       {@code Outer<String>.Inner} is not {@code Outer<Integer>.Inner}, and a raw type is neither a parameterization
 *       of its class nor the type of its declaration, {@code List<E>};
 *   <li>for arrays, equal components;
 *   <li>for type variables, the same type parameter of the same class, method or constructor;
 *   <li>for wildcards, equal bounds, an unbounded wildcard being bounded by {@code Object}, as javac compares type
 *       arguments: {@code ?} is {@code ? extends Object}, but not {@code ? super Object};
 *   <li>for intersection types, equal bounds, in any order;
 *   <li>for executable types, equal type variables, parameter types and return type, ignoring thrown types;
 *   <li>for primitive types, {@code void} and the other kinds of types, nothing more.
 * </ul>
 *
 * <p>As {@code equals} must, keys are equal by an equivalence relation, and they need no javac to compare, so they
 * differ from {@code isSameType} for these types:
 *
 * <ul>
 *   <li>a type javac could not resolve, of kind {@link TypeKind#ERROR}, is the same type as any type for
 *       {@code isSameType}, which is no equivalence relation. Its key is equal to the key of an unresolved type only,
 *       with the same name as written in the source, without its annotations, the same type arguments and the same
 *       enclosing type: {@code @Nullable Missing} has the key of {@code Missing}, but {@code foo.Missing} does not.
 *       Some JDKs, as JDK 21, keep neither the name nor the type arguments of an unresolved parameterized type, which
 *       they give as {@code <any>}: {@code Missing<String>} then has the key of {@code Other<Integer>};
 *   <li>a generic executable type is the same type for {@code isSameType} as another one that only names its type
 *       variables differently, as the types of {@code <A> void a(A)} and {@code <B> void b(B)}, while their keys
 *       compare their type variables as any other and differ;
 *   <li>a union type, the type of a multi-catch parameter, is compared by {@code isSameType} by the least upper bound
 *       of its alternatives, while its key compares its alternatives, in any order.
 * </ul>
 *
 * <p>Classes and interfaces are known by their canonical name, and type variables by their name and that of their
 * class, method or constructor. Those with no canonical name, as local and anonymous classes and the classes nested in
 * them, and the type variables of captured types, are known by their element: compare their keys within a round.
 */
public final class TypeKey {

    private static final String OBJECT = "java.lang.Object";

    private final TypeMirror type;
    private final int hash;

    private TypeKey(TypeMirror type) {
        this.type = type;
        this.hash = hash(type);
    }

    /**
     * Returns the key of a type.
     *
     * @param type the type
     * @return its key
     */
    public static TypeKey of(TypeMirror type) {
        return new TypeKey(type);
    }

    private static int hash(@Nullable TypeMirror type) {
        if (type == null) {
            return 0;
        }
        return switch (type.getKind()) {
            case DECLARED, ERROR -> hashDeclared((DeclaredType) type);
            case ARRAY -> Objects.hash(TypeKind.ARRAY, hash(((ArrayType) type).getComponentType()));
            case TYPEVAR -> declaration((TypeVariable) type).hashCode();
            case WILDCARD -> Objects.hash(hash(lowerBound((WildcardType) type)), hash(upperBound((WildcardType) type)));
            case INTERSECTION, UNION -> Objects.hash(type.getKind(), keys(members(type)));
            case EXECUTABLE -> hashExecutable((ExecutableType) type);
            case MODULE, PACKAGE, OTHER -> type.toString().hashCode();
            default -> type.getKind().hashCode();
        };
    }

    private static int hashDeclared(DeclaredType type) {
        return Objects.hash(
                declaration(type.asElement()), hashes(type.getTypeArguments()), hash(type.getEnclosingType()));
    }

    private static int hashExecutable(ExecutableType type) {
        return Objects.hash(
                hashes(type.getTypeVariables()), hashes(type.getParameterTypes()), hash(type.getReturnType()));
    }

    private static List<Integer> hashes(List<? extends TypeMirror> types) {
        return types.stream().map(TypeKey::hash).toList();
    }

    private static boolean same(@Nullable TypeMirror left, @Nullable TypeMirror right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        if (left.getKind() != right.getKind()) {
            return false;
        }
        return switch (left.getKind()) {
            case DECLARED, ERROR -> sameDeclared((DeclaredType) left, (DeclaredType) right);
            case ARRAY -> same(((ArrayType) left).getComponentType(), ((ArrayType) right).getComponentType());
            case TYPEVAR -> declaration((TypeVariable) left).equals(declaration((TypeVariable) right));
            case WILDCARD -> sameWildcard((WildcardType) left, (WildcardType) right);
            case INTERSECTION, UNION -> sameMembers(members(left), members(right));
            case EXECUTABLE -> sameExecutable((ExecutableType) left, (ExecutableType) right);
            case MODULE, PACKAGE, OTHER -> left.toString().equals(right.toString());
            default -> true;
        };
    }

    private static boolean same(List<? extends TypeMirror> left, List<? extends TypeMirror> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!same(left.get(i), right.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameDeclared(DeclaredType left, DeclaredType right) {
        return declaration(left.asElement()).equals(declaration(right.asElement()))
                && same(left.getTypeArguments(), right.getTypeArguments())
                && same(left.getEnclosingType(), right.getEnclosingType());
    }

    private static boolean sameWildcard(WildcardType left, WildcardType right) {
        return same(lowerBound(left), lowerBound(right)) && same(upperBound(left), upperBound(right));
    }

    // The bounds of an intersection type or the alternatives of a union type, which neither javac nor keys order.
    private static boolean sameMembers(List<? extends TypeMirror> left, List<? extends TypeMirror> right) {
        return left.size() == right.size() && keys(left).equals(keys(right));
    }

    private static boolean sameExecutable(ExecutableType left, ExecutableType right) {
        return same(left.getTypeVariables(), right.getTypeVariables())
                && same(left.getParameterTypes(), right.getParameterTypes())
                && same(left.getReturnType(), right.getReturnType());
    }

    private static Set<TypeKey> keys(List<? extends TypeMirror> types) {
        Set<TypeKey> keys = new HashSet<>();
        for (TypeMirror member : types) {
            keys.add(of(member));
        }
        return keys;
    }

    private static List<? extends TypeMirror> members(TypeMirror type) {
        return type.getKind() == TypeKind.UNION
                ? ((UnionType) type).getAlternatives()
                : ((IntersectionType) type).getBounds();
    }

    private static @Nullable TypeMirror lowerBound(WildcardType wildcard) {
        return wildcard.getSuperBound();
    }

    // The upper bound of a wildcard, or null for Object, which bounds an unbounded wildcard as well.
    private static @Nullable TypeMirror upperBound(WildcardType wildcard) {
        TypeMirror bound = wildcard.getExtendsBound();
        if (bound == null
                || (bound.getKind() == TypeKind.DECLARED
                        && OBJECT.equals(declaration(((DeclaredType) bound).asElement())))) {
            return null;
        }
        return bound;
    }

    // A class or interface by its qualified name, as written in the source for an unresolved one. One with no
    // canonical name, as a local class, by itself: javac gives a local class its simple name as qualified name.
    private static Object declaration(Element type) {
        for (Element enclosing = type;
                enclosing instanceof TypeElement nested;
                enclosing = nested.getEnclosingElement()) {
            if (nested.getNestingKind() == NestingKind.LOCAL || nested.getNestingKind() == NestingKind.ANONYMOUS) {
                return type;
            }
        }
        return type instanceof TypeElement named ? named.getQualifiedName().toString() : type;
    }

    // A type variable by its type parameter: its name and its class, method or constructor. A method is known by its
    // class and its signature, so that the type variables of methods of the same signature in different classes
    // differ. Another type variable, as one of a captured type, by its type parameter itself.
    private static Object declaration(TypeVariable variable) {
        Element parameter = variable.asElement();
        Object generic = switch (parameter.getEnclosingElement()) {
            case TypeElement type -> declaration(type);
            case ExecutableElement method ->
                List.of(declaration(Objects.requireNonNull(method.getEnclosingElement())), method.toString());
            case null, default -> parameter;
        };
        return List.of(generic, parameter.getSimpleName().toString());
    }

    /**
     * Returns the type of this key.
     *
     * @return the type
     */
    public TypeMirror type() {
        return type;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof TypeKey key && hash == key.hash && same(type, key.type);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return type.toString();
    }
}
