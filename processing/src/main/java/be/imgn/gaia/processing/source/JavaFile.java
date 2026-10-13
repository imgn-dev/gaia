package be.imgn.gaia.processing.source;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.SourceVersion;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import org.jspecify.annotations.Nullable;

/**
 * A Java source file: a package, one top-level type, and the imports they need, which are worked out when the file is
 * written.
 *
 * <p>A type is imported unless it is in {@code java.lang} or in the package of the file, and written by its simple
 * name. It is written fully qualified instead when its simple name is ambiguous: when two referenced types share it; a
 * type or type variable declared in the file has it; a member type inherited by a type of the file has it, as far as
 * {@link Type#alwaysQualifiedNames()} tells; a static import names a member with it; or it is one of the
 * {@link Builder#alwaysQualify(String...) always qualified names}. The types declared in the file are written by the
 * shortest name that refers to them where they are used, such as {@code Builder} rather than {@code Outer.Builder}
 * inside {@code Outer}.
 *
 * <p>Code that refers to a statically imported member through its type, as {@code $T.MINUTES}, is written with the
 * member alone, {@code MINUTES}.
 *
 * <p>Files are values: two files written the same are equal.
 */
public final class JavaFile {

    private final String packageName;
    private final Type type;
    private final CodeBlock fileComment;
    private final Set<String> staticImports;
    private final String indent;
    private final Set<String> alwaysQualifiedNames;

    private JavaFile(Builder builder) {
        this.packageName = builder.packageName;
        this.type = builder.type;
        this.fileComment = builder.fileComment.build();
        this.staticImports = Set.copyOf(builder.staticImports);
        this.indent = builder.indent;
        this.alwaysQualifiedNames = Collections.unmodifiableSet(new TreeSet<>(builder.alwaysQualifiedNames));
    }

    /**
     * Returns a builder of files.
     *
     * @param packageName the package, empty for the unnamed package
     * @param type the top-level type
     * @return a new builder
     * @throws IllegalArgumentException if the package name is not valid, or the type is anonymous
     */
    public static Builder newBuilder(String packageName, Type type) {
        if (!packageName.isEmpty() && !SourceVersion.isName(packageName)) {
            throw new IllegalArgumentException("Not a package name: " + packageName);
        }
        return new Builder(packageName, checkNamed(type));
    }

    private static Type checkNamed(Type type) {
        if (type.isAnonymous()) {
            throw new IllegalArgumentException("The type of a file must have a name");
        }
        return type;
    }

    /**
     * Returns the package.
     *
     * @return the package name, empty for the unnamed package
     */
    public String packageName() {
        return packageName;
    }

    /**
     * Returns the top-level type.
     *
     * @return the type
     */
    public Type type() {
        return type;
    }

    /**
     * Returns the name of the top-level type.
     *
     * @return the class name
     */
    public ClassName className() {
        String name = type.name();
        return ClassName.of(packageName, name == null ? "Anonymous" : name);
    }

    /**
     * Returns the static imports.
     *
     * @return the imported members, such as {@code java.util.Objects.requireNonNull}
     */
    public Set<String> staticImports() {
        return staticImports;
    }

    /**
     * Returns the simple names whose types are always written qualified in this file.
     *
     * @return the names, sorted
     */
    public Set<String> alwaysQualifiedNames() {
        return alwaysQualifiedNames;
    }

    /**
     * Returns this file as a source the Java compiler can read, as when compiling generated code in a test.
     *
     * @return the source, named {@code <package path>/<simple name>.java}
     */
    public JavaFileObject toJavaFileObject() {
        String path = className().canonicalName().replace('.', '/') + JavaFileObject.Kind.SOURCE.extension;
        String source = toString();
        return new SimpleJavaFileObject(URI.create(path), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
    }

    /**
     * Writes the file.
     *
     * @param out where to write it
     * @throws IOException if {@code out} fails
     */
    public void writeTo(Appendable out) throws IOException {
        out.append(toString());
    }

    /**
     * Returns a builder that starts with this file.
     *
     * @return a new builder
     */
    public Builder toBuilder() {
        Builder builder = new Builder(packageName, type);
        builder.fileComment.add(fileComment);
        builder.staticImports.addAll(staticImports);
        builder.indent = indent;
        builder.alwaysQualifiedNames.addAll(alwaysQualifiedNames);
        return builder;
    }

    CodeBlock fileComment() {
        return fileComment;
    }

    String indent() {
        return indent;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof JavaFile file && toString().equals(file.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    /**
     * Returns the source code of the file.
     *
     * @return the source code
     * @throws IllegalStateException if a code block unindents more than it indents, or indents more than it unindents
     */
    @Override
    public String toString() {
        return CodeWriter.write(this);
    }

    /** A builder of {@link JavaFile}s. */
    public static final class Builder {

        private final String packageName;
        private Type type;
        private final CodeBlock.Builder fileComment = CodeBlock.newBuilder();
        private final Set<String> staticImports = new TreeSet<>();
        private String indent = "    ";
        private final Set<String> alwaysQualifiedNames = new TreeSet<>();

        private Builder(String packageName, Type type) {
            this.packageName = packageName;
            this.type = type;
        }

        /**
         * Replaces the top-level type.
         *
         * @param type the top-level type
         * @return this builder
         * @throws IllegalArgumentException if the type is anonymous
         */
        public Builder type(Type type) {
            this.type = checkNamed(type);
            return this;
        }

        /**
         * Adds text to the comment at the top of the file, written as {@code //} lines.
         *
         * @param format the text, with placeholders
         * @param args the arguments of the placeholders
         * @return this builder
         * @throws IllegalArgumentException if the format and the arguments do not match
         */
        public Builder addFileComment(String format, @Nullable Object... args) {
            fileComment.add(format, args);
            return this;
        }

        /**
         * Imports static members of a type, so that code can name them without their type.
         *
         * @param type the type
         * @param members the names of the members, or {@code *} for all of them
         * @return this builder
         * @throws IllegalArgumentException if a member name is not a valid identifier or {@code *}
         */
        public Builder addStaticImport(ClassName type, String... members) {
            for (String member : members) {
                if (!"*".equals(member) && !SourceVersion.isIdentifier(member)) {
                    throw new IllegalArgumentException("Not a member name: " + member);
                }
                staticImports.add(type.withoutAnnotations().canonicalName() + "." + member);
            }
            return this;
        }

        /**
         * Imports static members of a class, so that code can name them without their class.
         *
         * @param type the class
         * @param members the names of the members, or {@code *} for all of them
         * @return this builder
         * @throws IllegalArgumentException if the class has no canonical name, or a member name is not a valid
         *     identifier or {@code *}
         */
        public Builder addStaticImport(Class<?> type, String... members) {
            return addStaticImport(ClassName.of(type), members);
        }

        /**
         * Imports an enum constant, so that code can name it without its enum.
         *
         * @param constant the constant
         * @return this builder
         */
        public Builder addStaticImport(Enum<?> constant) {
            return addStaticImport(ClassName.of(constant.getDeclaringClass()), constant.name());
        }

        /**
         * Writes the types of these simple names qualified, wherever they are referred to in the file: for names that a
         * type outside the file, such as one in the same package, hides.
         *
         * @param simpleNames the simple names
         * @return this builder
         */
        public Builder alwaysQualify(String... simpleNames) {
            alwaysQualifiedNames.addAll(List.of(simpleNames));
            return this;
        }

        /**
         * Sets the indentation of one level.
         *
         * @param unit the indentation, four spaces by default
         * @return this builder
         * @throws IllegalArgumentException if the indentation is not made of spaces and tabs
         */
        public Builder indent(String unit) {
            if (!unit.chars().allMatch(c -> c == ' ' || c == '\t')) {
                throw new IllegalArgumentException("An indentation is made of spaces and tabs");
            }
            indent = unit;
            return this;
        }

        /**
         * Builds the file.
         *
         * @return the file
         */
        public JavaFile build() {
            return new JavaFile(this);
        }
    }
}
