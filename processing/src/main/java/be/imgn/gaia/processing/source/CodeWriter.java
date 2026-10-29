package be.imgn.gaia.processing.source;

import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import org.jspecify.annotations.Nullable;

/**
 * Writes declarations, code blocks and type names as source code. A {@link JavaFile} is written twice: first to collect
 * the types it refers to, then with the imports and simple names those allow.
 */
final class CodeWriter {

    private static final String JAVA_LANG = "java.lang";
    private static final int CONTINUATION = 2;
    private static final int COLUMN_LIMIT = 100;
    private static final Set<Modifier> INTERFACE_METHOD = EnumSet.of(Modifier.PUBLIC, Modifier.ABSTRACT);
    private static final Set<Modifier> INTERFACE_FIELD = EnumSet.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
    private static final Set<Modifier> BODY_MODIFIERS = EnumSet.of(Modifier.DEFAULT, Modifier.STATIC, Modifier.PRIVATE);
    private static final Method PUBLIC_CONSTRUCTOR =
            Method.newConstructorBuilder().addModifiers(Modifier.PUBLIC).build();

    private final StringBuilder out = new StringBuilder();
    private final LineWrapper wrapper = new LineWrapper(out, COLUMN_LIMIT);
    private final String indentUnit;
    private final Map<String, ClassName> visible;
    private final @Nullable Set<ClassName> referenced;
    private final Set<String> staticImports;
    private final @Nullable ClassName fileType;
    private final Deque<Scope> scopes = new ArrayDeque<>();
    private int indentLevel;
    private int indentFloor;
    private int extraIndent;
    private int statementDepth;
    private int statementLine = -1;
    private boolean lineStart = true;
    private String linePrefix = "";

    private CodeWriter(
            String indentUnit,
            Map<String, ClassName> visible,
            @Nullable Set<ClassName> referenced,
            Set<String> staticImports,
            @Nullable ClassName fileType) {
        this.indentUnit = indentUnit;
        this.visible = visible;
        this.referenced = referenced;
        this.staticImports = staticImports;
        this.fileType = fileType;
    }

    static String standalone(Object value) {
        CodeWriter writer = new CodeWriter("    ", Map.of(), null, Set.of(), null);
        writer.emitLiteral(value);
        return writer.result();
    }

    static String write(JavaFile file) {
        Set<ClassName> referenced = new LinkedHashSet<>();
        new CodeWriter(file.indent(), Map.of(), referenced, file.staticImports(), file.className())
                .emitFile(file, List.of());
        Map<String, ClassName> visible = visibleNames(file, referenced);
        List<String> imports = visible.values().stream()
                .filter(type -> !type.packageName().isEmpty()
                        && !type.packageName().equals(file.packageName())
                        && !type.packageName().equals(JAVA_LANG))
                .map(ClassName::canonicalName)
                .sorted()
                .toList();
        CodeWriter writer = new CodeWriter(file.indent(), visible, null, file.staticImports(), file.className());
        writer.emitFile(file, imports);
        return writer.result();
    }

    private String result() {
        wrapper.close();
        return out.toString();
    }

    static String stringLiteral(String value) {
        StringBuilder literal = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            literal.append(c == '\'' ? "'" : escape(c));
        }
        return literal.append('"').toString();
    }

    static String characterLiteral(char value) {
        return "'" + (value == '"' ? "\"" : escape(value)) + "'";
    }

    private static String escape(char c) {
        return switch (c) {
            case '\b' -> "\\b";
            case '\t' -> "\\t";
            case '\n' -> "\\n";
            case '\f' -> "\\f";
            case '\r' -> "\\r";
            case '"' -> "\\\"";
            case '\'' -> "\\'";
            case '\\' -> "\\\\";
            default -> Character.isISOControl(c) ? String.format(Locale.ROOT, "\\u%04x", (int) c) : String.valueOf(c);
        };
    }

    // A referenced top-level type gets its simple name unless another referenced type has it too, or something else
    // the file declares, inherits or imports statically may have it. The types of the file itself never need an import.
    private static Map<String, ClassName> visibleNames(JavaFile file, Set<ClassName> referenced) {
        Set<String> taken = new HashSet<>(file.alwaysQualifiedNames());
        declaredNames(file.type(), taken);
        file.staticImports().stream()
                .map(member -> member.substring(member.lastIndexOf('.') + 1))
                .filter(member -> !member.equals("*"))
                .forEach(taken::add);
        Map<String, List<ClassName>> bySimpleName = referenced.stream()
                .collect(Collectors.groupingBy(ClassName::simpleName, LinkedHashMap::new, Collectors.toList()));
        Map<String, ClassName> visible = new HashMap<>();
        bySimpleName.forEach((simpleName, candidates) -> {
            if (candidates.size() == 1 && !taken.contains(simpleName)) {
                visible.put(simpleName, candidates.getFirst());
            }
        });
        return visible;
    }

    private static void declaredNames(Type type, Set<String> names) {
        String name = type.name();
        if (name != null) {
            names.add(name);
        }
        names.addAll(type.alwaysQualifiedNames());
        type.typeVariables().forEach(variable -> names.add(variable.name()));
        type.methods().forEach(method -> method.typeVariables().forEach(variable -> names.add(variable.name())));
        type.types().forEach(nested -> declaredNames(nested, names));
        type.enumConstants().values().forEach(constant -> declaredNames(constant, names));
    }

    private static Set<Modifier> nestedTypeImplicit(Type.Kind enclosing, Type.Kind nested) {
        Set<Modifier> implicit = EnumSet.noneOf(Modifier.class);
        if (enclosing.isInterface()) {
            implicit.add(Modifier.PUBLIC);
            implicit.add(Modifier.STATIC);
        }
        if (nested != Type.Kind.CLASS) {
            implicit.add(Modifier.STATIC);
        }
        return implicit;
    }

    // Modifiers implied by the kind of type, wherever it is declared: records and enums are final, interfaces and
    // annotation types abstract. A final enum does not even compile.
    private static Set<Modifier> kindImplicit(Type.Kind kind, Set<Modifier> implicit) {
        Set<Modifier> all = EnumSet.noneOf(Modifier.class);
        all.addAll(implicit);
        if (kind == Type.Kind.RECORD || kind == Type.Kind.ENUM) {
            all.add(Modifier.FINAL);
        }
        if (kind.isInterface()) {
            all.add(Modifier.ABSTRACT);
        }
        return all;
    }

    private static boolean hasMembers(Type type) {
        return !type.fields().isEmpty()
                || !type.methods().isEmpty()
                || !type.types().isEmpty()
                || !type.staticBlock().isEmpty()
                || !type.initializerBlock().isEmpty();
    }

    private static boolean isBodyless(Method method, Type.Kind enclosing) {
        Set<Modifier> modifiers = method.modifiers();
        if (modifiers.contains(Modifier.ABSTRACT) || modifiers.contains(Modifier.NATIVE)) {
            return true;
        }
        return enclosing.isInterface() && modifiers.stream().noneMatch(BODY_MODIFIERS::contains);
    }

    // The Javadoc of a method or record, followed by the @param tags of its documented parameters or components.
    private static CodeBlock withParameterTags(CodeBlock javadoc, List<Parameter> parameters) {
        List<Parameter> documented = parameters.stream()
                .filter(parameter -> !parameter.javadoc().isEmpty())
                .toList();
        if (documented.isEmpty()) {
            return javadoc;
        }
        CodeBlock.Builder builder = javadoc.toBuilder();
        if (!javadoc.isEmpty()) {
            builder.add(endsWithNewLine(javadoc) ? "\n" : "\n\n");
        }
        for (Parameter parameter : documented) {
            builder.add("@param $L ", parameter.name()).add(parameter.javadoc());
            if (!endsWithNewLine(parameter.javadoc())) {
                builder.add("\n");
            }
        }
        return builder.build();
    }

    private static boolean endsWithNewLine(CodeBlock block) {
        List<CodeBlock.Part> parts = block.parts();
        return !parts.isEmpty()
                && parts.getLast() instanceof CodeBlock.Text text
                && text.text().endsWith("\n");
    }

    private static Set<String> names(Stream<String> names) {
        return names.collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> hiddenNames(Type type) {
        return names(Stream.concat(
                type.typeVariables().stream().map(TypeVariableName::name), type.alwaysQualifiedNames().stream()));
    }

    private static Set<String> memberTypeNames(Type type) {
        return names(type.types().stream().map(nested -> requireNonNull(nested.name())));
    }

    private void emitFile(JavaFile file, List<String> imports) {
        if (!file.fileComment().isEmpty()) {
            linePrefix = "// ";
            emitCode(file.fileComment());
            endLine();
            linePrefix = "";
            emit("\n");
        }
        if (!file.packageName().isEmpty()) {
            emit("package " + file.packageName() + ";\n\n");
        }
        if (!file.staticImports().isEmpty()) {
            file.staticImports().stream().sorted().forEach(member -> emit("import static " + member + ";\n"));
            emit("\n");
        }
        if (!imports.isEmpty()) {
            imports.forEach(name -> emit("import " + name + ";\n"));
            emit("\n");
        }
        emitType(file.type(), Set.of(), file.className());
    }

    private void emitLiteral(@Nullable Object value) {
        switch (value) {
            case CodeBlock block -> emitBlock(block);
            case TypeName name -> emitTypeName(name);
            case Annotation annotation -> emitAnnotation(annotation);
            case Parameter parameter -> emitParameter(parameter, false);
            case Field field -> emitField(field, Set.of());
            case Method method -> emitMethod(method, "Constructor", Type.Kind.CLASS);
            case Type type when type.isAnonymous() -> emitAnonymous(type);
            case Type type -> emitType(type, Set.of(), ClassName.of("", requireNonNull(type.name())));
            case null -> emit("null");
            default -> emit(value.toString());
        }
    }

    // A code block that stands on its own, such as a method body: it must not unindent below where it starts, and must
    // end where it started, with its statements ended.
    private void emitCode(CodeBlock code) {
        int floor = indentFloor;
        int level = indentLevel;
        int depth = statementDepth;
        indentFloor = indentLevel;
        emitBlock(code);
        if (indentLevel != level) {
            unbalanced("indents more than it unindents: a $> or a control flow is not closed");
            indentLevel = level;
        }
        if (statementDepth != depth) {
            unbalanced("starts a statement with $[ that it does not end with $]");
            statementDepth = depth;
            statementLine = depth > 0 ? Math.max(0, statementLine) : -1;
        }
        indentFloor = floor;
    }

    // A file must be valid Java, but toString() of a part should show whatever it has.
    private void unbalanced(String problem) {
        if (fileType != null) {
            throw new IllegalStateException("A code block " + problem);
        }
    }

    private void emitBlock(CodeBlock block) {
        List<CodeBlock.Part> parts = block.parts();
        int i = 0;
        while (i < parts.size()) {
            String rest = i + 1 < parts.size() ? staticMemberAccess(parts.get(i), parts.get(i + 1)) : null;
            if (rest != null) {
                emit(rest);
                i += 2;
            } else {
                emitPart(parts.get(i));
                i++;
            }
        }
    }

    // $T followed by ".member", where the member is statically imported, is written "member".
    private @Nullable String staticMemberAccess(CodeBlock.Part part, CodeBlock.Part next) {
        if (!(part instanceof CodeBlock.Argument argument
                && argument.kind() == 'T'
                && argument.value() instanceof ClassName type
                && type.annotations().isEmpty()
                && next instanceof CodeBlock.Text text
                && text.text().startsWith("."))) {
            return null;
        }
        String rest = text.text().substring(1);
        String member = leadingIdentifier(rest);
        String canonicalName = type.canonicalName();
        boolean imported = !member.isEmpty()
                && !SourceVersion.isKeyword(member)
                && (staticImports.contains(canonicalName + "." + member)
                        || staticImports.contains(canonicalName + ".*"));
        return imported ? rest : null;
    }

    private static String leadingIdentifier(String text) {
        if (text.isEmpty() || !Character.isJavaIdentifierStart(text.charAt(0))) {
            return "";
        }
        int end = 1;
        while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) {
            end++;
        }
        return text.substring(0, end);
    }

    private void emitPart(CodeBlock.Part part) {
        switch (part) {
            case CodeBlock.Text text -> emit(text.text());
            case CodeBlock.Indent indent -> indent(indent.delta());
            case CodeBlock.Argument argument -> emitArgument(argument);
            case CodeBlock.Statement statement -> statement(statement.start());
            case CodeBlock.Wrap wrap -> wrap(wrap.space());
        }
    }

    // A line breaks at a wrap point onto a continuation line; in Javadoc or a comment, onto a line of its own.
    private void wrap(boolean space) {
        if (lineStart) {
            return;
        }
        String indentation = linePrefix.isEmpty()
                ? indentUnit.repeat(indentLevel + continuation() + CONTINUATION)
                : indentUnit.repeat(indentLevel) + linePrefix;
        if (space) {
            wrapper.wrappingSpace(indentation);
        } else {
            wrapper.zeroWidthSpace(indentation);
        }
    }

    private int continuation() {
        return (statementLine > 0 ? CONTINUATION : 0) + extraIndent;
    }

    private void indent(int delta) {
        int level = indentLevel + delta;
        if (level < indentFloor) {
            unbalanced("unindents more than it indents: a $< or a control flow is not opened");
            level = indentFloor;
        }
        indentLevel = level;
    }

    private void statement(boolean start) {
        if (start) {
            if (statementDepth++ == 0) {
                statementLine = 0;
            }
        } else if (statementDepth == 0) {
            unbalanced("ends a statement with $] that it did not start with $[");
        } else if (--statementDepth == 0) {
            statementLine = -1;
        }
    }

    private void emitArgument(CodeBlock.Argument argument) {
        Object value = argument.value();
        switch (argument.kind()) {
            case 'T' -> emitTypeName((TypeName) requireNonNull(value));
            case 'N' -> emit(String.valueOf(value));
            case 'S' -> emitString(value == null ? null : value.toString());
            default -> emitLiteral(value);
        }
    }

    // A string of several lines is written as one literal per line, joined with +, on continuation lines.
    private void emitString(@Nullable String value) {
        int newLine = value == null ? -1 : value.indexOf('\n');
        if (value == null || newLine < 0 || newLine == value.length() - 1 || !linePrefix.isEmpty()) {
            emit(value == null ? "null" : stringLiteral(value));
            return;
        }
        emit(stringLiteral(value.substring(0, newLine + 1)));
        int continuation = statementDepth > 0 ? 0 : CONTINUATION;
        extraIndent += continuation;
        int start = newLine + 1;
        for (int i = value.indexOf('\n', start); i >= 0 && i < value.length() - 1; i = value.indexOf('\n', i + 1)) {
            emit("\n+ " + stringLiteral(value.substring(start, i + 1)));
            start = i + 1;
        }
        emit("\n+ " + stringLiteral(value.substring(start)));
        extraIndent -= continuation;
    }

    private void emitTypeName(TypeName name) {
        switch (name) {
            case PrimitiveTypeName primitive -> {
                emitTypeAnnotations(primitive.annotations());
                emit(primitive.keyword());
            }
            case ClassName className -> emitClassName(className);
            case ParameterizedTypeName parameterized -> emitParameterized(parameterized);
            case ArrayTypeName array -> emitArray(array, false);
            case TypeVariableName variable -> {
                emitTypeAnnotations(variable.annotations());
                emit(variable.name());
            }
            case WildcardTypeName wildcard -> emitWildcard(wildcard);
        }
    }

    private void emitParameterized(ParameterizedTypeName parameterized) {
        TypeName owner = parameterized.ownerType();
        if (owner == null) {
            emitClassName(parameterized.rawType().annotated(parameterized.annotations()));
        } else {
            emitTypeName(owner);
            emit(".");
            emitTypeAnnotations(parameterized.annotations());
            emit(parameterized.rawType().simpleName());
        }
        if (!parameterized.typeArguments().isEmpty()) {
            emit("<");
            emitTypeNames(parameterized.typeArguments(), ", ");
            emit(">");
        }
    }

    // The types of the file are written by the shortest name that refers to them where they are used; the others from
    // their top-level type, imported or qualified.
    private void emitClassName(ClassName name) {
        ClassName top = name.topLevel();
        int first = 0;
        if (top.equals(fileType)) {
            int start = scopedStart(name);
            if (start < 0) {
                emitPackage(top);
            }
            first = Math.max(0, start);
        } else {
            if (referenced != null) {
                referenced.add(top);
            }
            if (!top.equals(visible.get(top.simpleName()))) {
                emitPackage(top);
            }
        }
        List<String> simpleNames = name.simpleNames();
        for (int i = first; i < simpleNames.size(); i++) {
            if (i > first) {
                emit(".");
            }
            if (i == simpleNames.size() - 1) {
                emitTypeAnnotations(name.annotations());
            }
            emit(simpleNames.get(i));
        }
    }

    private void emitPackage(ClassName top) {
        if (!top.packageName().isEmpty()) {
            emit(top.packageName() + ".");
        }
    }

    // The index of the first simple name to write, the shortest one that refers to the type here; -1 if none does.
    private int scopedStart(ClassName name) {
        List<String> simpleNames = name.simpleNames();
        for (int i = simpleNames.size() - 1; i >= 0; i--) {
            ClassName prefix = new ClassName(name.packageName(), simpleNames.subList(0, i + 1), List.of());
            if (prefix.equals(resolve(simpleNames.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    // The type a simple name refers to here, if it is a type of the file: the innermost scope that knows it decides.
    private @Nullable ClassName resolve(String simpleName) {
        for (Scope scope : scopes) {
            if (scope.hidden().contains(simpleName)) {
                return null;
            }
            if (scope.members().contains(simpleName)) {
                ClassName type = scope.type();
                return type == null ? null : type.nested(simpleName);
            }
        }
        return fileType != null && fileType.simpleName().equals(simpleName) ? fileType : null;
    }

    // The dimensions of String @A [] @B [] are written from the outermost array type, @A, to the innermost, @B; a
    // varargs parameter writes its innermost dimension as "...".
    private void emitArray(ArrayTypeName array, boolean varargs) {
        List<ArrayTypeName> dimensions = new ArrayList<>();
        TypeName element = array;
        while (element instanceof ArrayTypeName dimension) {
            dimensions.add(dimension);
            element = dimension.componentType();
        }
        emitTypeName(element);
        for (int i = 0; i < dimensions.size(); i++) {
            ArrayTypeName dimension = dimensions.get(i);
            if (!dimension.annotations().isEmpty()) {
                emit(" ");
                emitTypeAnnotations(dimension.annotations());
            }
            emit(varargs && i == dimensions.size() - 1 ? "..." : "[]");
        }
    }

    private void emitWildcard(WildcardTypeName wildcard) {
        emitTypeAnnotations(wildcard.annotations());
        emit("?");
        TypeName upperBound = wildcard.upperBound();
        TypeName lowerBound = wildcard.lowerBound();
        if (upperBound != null) {
            emit(" extends ");
            emitTypeName(upperBound);
        } else if (lowerBound != null) {
            emit(" super ");
            emitTypeName(lowerBound);
        }
    }

    private void emitTypeNames(List<? extends TypeName> names, String separator) {
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                emit(separator);
            }
            emitTypeName(names.get(i));
        }
    }

    private void emitTypeAnnotations(List<Annotation> annotations) {
        for (Annotation annotation : annotations) {
            emitAnnotation(annotation);
            emit(" ");
        }
    }

    private void emitAnnotationLines(List<Annotation> annotations) {
        for (Annotation annotation : annotations) {
            emitAnnotation(annotation);
            emit("\n");
        }
    }

    private void emitAnnotation(Annotation annotation) {
        emit("@");
        emitClassName(annotation.type());
        Map<String, List<CodeBlock>> members = annotation.members();
        if (!members.isEmpty()) {
            emit("(");
            if (members.size() == 1 && members.containsKey("value")) {
                emitAnnotationValue(members.get("value"));
            } else {
                emitAnnotationMembers(members);
            }
            emit(")");
        }
    }

    private void emitAnnotationMembers(Map<String, List<CodeBlock>> members) {
        boolean first = true;
        for (Map.Entry<String, List<CodeBlock>> member : members.entrySet()) {
            if (!first) {
                emit(", ");
            }
            first = false;
            emit(member.getKey() + " = ");
            emitAnnotationValue(member.getValue());
        }
    }

    private void emitAnnotationValue(@Nullable List<CodeBlock> values) {
        List<CodeBlock> present = values == null ? List.of() : values;
        if (present.size() == 1) {
            emitCode(present.getFirst());
        } else {
            emit("{");
            emitCode(CodeBlock.join(present, ", "));
            emit("}");
        }
    }

    private void emitModifiers(Set<Modifier> modifiers, Set<Modifier> implicit) {
        for (Modifier modifier : modifiers) {
            if (!implicit.contains(modifier)) {
                emit(modifier + " ");
            }
        }
    }

    private void emitTypeVariableDeclarations(List<TypeVariableName> variables) {
        emit("<");
        for (int i = 0; i < variables.size(); i++) {
            TypeVariableName variable = variables.get(i);
            if (i > 0) {
                emit(", ");
            }
            emitTypeAnnotations(variable.annotations());
            emit(variable.name());
            if (!variable.bounds().isEmpty()) {
                emit(" extends ");
                emitTypeNames(variable.bounds(), " & ");
            }
        }
        emit(">");
    }

    private void emitJavadoc(CodeBlock javadoc) {
        if (!javadoc.isEmpty()) {
            emit("/**\n");
            linePrefix = " * ";
            emitCode(javadoc);
            endLine();
            linePrefix = "";
            emit(" */\n");
        }
    }

    private void emitType(Type type, Set<Modifier> implicit, ClassName className) {
        boolean record = type.kind() == Type.Kind.RECORD;
        emitJavadoc(record ? withParameterTags(type.javadoc(), type.recordComponents()) : type.javadoc());
        emitAnnotationLines(type.annotations());
        emitModifiers(type.modifiers(), kindImplicit(type.kind(), implicit));
        emit(type.kind().keyword() + " " + type.name());
        // The members of a record are in scope in its header; those of other types only in their body.
        scopes.push(new Scope(className, record ? memberTypeNames(type) : Set.of(), hiddenNames(type)));
        if (!type.typeVariables().isEmpty()) {
            emitTypeVariableDeclarations(type.typeVariables());
        }
        if (record) {
            emit("(");
            emitParameters(type.recordComponents(), type.isVarargs());
            emit(")");
        }
        emitSupertypes(type);
        scopes.pop();
        emit(" {\n");
        // javac's missing-explicit-ctor lint, part of -Xlint:all, warns about a public class of an exported package
        // that relies on the default constructor.
        boolean explicitConstructor = type.kind() == Type.Kind.CLASS
                && (type.modifiers().contains(Modifier.PUBLIC) || implicit.contains(Modifier.PUBLIC))
                && type.methods().stream().noneMatch(Method::isConstructor);
        emitMembers(type, className, explicitConstructor);
        emit("}\n");
    }

    private void emitSupertypes(Type type) {
        TypeName superclass = type.superclass();
        if (superclass != null && !superclass.equals(ClassName.OBJECT)) {
            emit(" extends ");
            emitTypeName(superclass);
        }
        if (!type.superinterfaces().isEmpty()) {
            emit(type.kind() == Type.Kind.INTERFACE ? " extends " : " implements ");
            emitTypeNames(type.superinterfaces(), ", ");
        }
        if (!type.permittedSubclasses().isEmpty()) {
            emit(" permits ");
            emitTypeNames(type.permittedSubclasses(), ", ");
        }
    }

    // The body of an anonymous class starts a new block: the statement it is part of resumes after it.
    private void emitAnonymous(Type type) {
        TypeName superclass = type.superclass();
        TypeName supertype = superclass != null
                ? superclass
                : type.superinterfaces().isEmpty()
                        ? ClassName.OBJECT
                        : type.superinterfaces().getFirst();
        emit("new ");
        emitTypeName(supertype);
        emit("(");
        emitCode(type.anonymousArguments());
        emit(") {\n");
        int depth = statementDepth;
        int line = statementLine;
        int extra = extraIndent;
        statementDepth = 0;
        statementLine = -1;
        extraIndent = 0;
        emitMembers(type, null, false);
        emit("}");
        statementDepth = depth;
        statementLine = line;
        extraIndent = extra;
    }

    private void emitMembers(Type type, @Nullable ClassName className, boolean explicitConstructor) {
        scopes.push(new Scope(className, memberTypeNames(type), hiddenNames(type)));
        indentLevel++;
        Member previous = null;
        for (Member member : members(type, className, explicitConstructor)) {
            if (previous != null && !(previous.isField() && member.isField())) {
                emit("\n");
            }
            member.writer().run();
            previous = member;
        }
        indentLevel--;
        scopes.pop();
    }

    private List<Member> members(Type type, @Nullable ClassName className, boolean explicitConstructor) {
        Type.Kind kind = type.kind();
        List<Member> members = new ArrayList<>();
        if (kind == Type.Kind.ENUM && (!type.enumConstants().isEmpty() || hasMembers(type))) {
            members.add(new Member(false, () -> emitEnumConstants(type)));
        }
        Set<Modifier> fieldImplicit = kind.isInterface() ? INTERFACE_FIELD : Set.of();
        type.fields().forEach(field -> members.add(new Member(true, () -> emitField(field, fieldImplicit))));
        if (!type.staticBlock().isEmpty()) {
            members.add(new Member(false, () -> emitInitializer("static ", type.staticBlock())));
        }
        if (!type.initializerBlock().isEmpty()) {
            members.add(new Member(false, () -> emitInitializer("", type.initializerBlock())));
        }
        String name = type.name() == null ? "" : type.name();
        type.methods().stream()
                .filter(Method::isConstructor)
                .forEach(method -> members.add(new Member(false, () -> emitMethod(method, name, kind))));
        if (explicitConstructor) {
            members.add(new Member(false, () -> emitMethod(PUBLIC_CONSTRUCTOR, name, kind)));
        }
        type.methods().stream()
                .filter(method -> !method.isConstructor())
                .forEach(method -> members.add(new Member(false, () -> emitMethod(method, name, kind))));
        for (Type nested : type.types()) {
            String nestedName = requireNonNull(nested.name());
            ClassName nestedClassName = className == null ? ClassName.of("", nestedName) : className.nested(nestedName);
            members.add(new Member(
                    false, () -> emitType(nested, nestedTypeImplicit(kind, nested.kind()), nestedClassName)));
        }
        return members;
    }

    private void emitEnumConstants(Type type) {
        List<Map.Entry<String, Type>> constants =
                new ArrayList<>(type.enumConstants().entrySet());
        for (int i = 0; i < constants.size(); i++) {
            emitEnumConstant(constants.get(i).getKey(), constants.get(i).getValue());
            if (i < constants.size() - 1) {
                emit(",\n");
            }
        }
        emit(hasMembers(type) ? ";\n" : "\n");
    }

    private void emitEnumConstant(String name, Type body) {
        emitJavadoc(body.javadoc());
        emitAnnotationLines(body.annotations());
        emit(name);
        if (!body.anonymousArguments().isEmpty()) {
            emit("(");
            emitCode(body.anonymousArguments());
            emit(")");
        }
        if (hasMembers(body)) {
            emit(" {\n");
            emitMembers(body, null, false);
            emit("}");
        }
    }

    private void emitInitializer(String prefix, CodeBlock code) {
        emit(prefix + "{\n");
        indentLevel++;
        emitCode(code);
        endLine();
        indentLevel--;
        emit("}\n");
    }

    private void emitField(Field field, Set<Modifier> implicit) {
        emitJavadoc(field.javadoc());
        emitAnnotationLines(field.annotations());
        statement(true);
        emitModifiers(field.modifiers(), implicit);
        emitTypeName(field.type());
        emit(" " + field.name());
        if (!field.initializer().isEmpty()) {
            emit(" = ");
            emitCode(field.initializer());
        }
        emit(";\n");
        statement(false);
    }

    private void emitMethod(Method method, String enclosingName, Type.Kind enclosing) {
        emitJavadoc(withParameterTags(method.javadoc(), method.parameters()));
        emitAnnotationLines(method.annotations());
        emitModifiers(method.modifiers(), enclosing.isInterface() ? INTERFACE_METHOD : Set.of());
        scopes.push(
                new Scope(null, Set.of(), names(method.typeVariables().stream().map(TypeVariableName::name))));
        if (!method.typeVariables().isEmpty()) {
            emitTypeVariableDeclarations(method.typeVariables());
            emit(" ");
        }
        TypeName returnType = method.returnType();
        if (method.isConstructor() || returnType == null) {
            emit(enclosingName);
        } else {
            emitTypeName(returnType);
            emit(" " + method.name());
        }
        if (!method.isCompactConstructor()) {
            emit("(");
            emitParameters(method.parameters(), method.isVarargs());
            emit(")");
        }
        if (!method.exceptions().isEmpty()) {
            emit(" throws ");
            emitTypeNames(method.exceptions(), ", ");
        }
        if (!method.defaultValue().isEmpty()) {
            emit(" default ");
            emitCode(method.defaultValue());
        }
        emitMethodBody(method, enclosing);
        scopes.pop();
    }

    private void emitMethodBody(Method method, Type.Kind enclosing) {
        if (isBodyless(method, enclosing)) {
            emit(";\n");
        } else {
            emit(" {\n");
            indentLevel++;
            emitCode(method.code());
            endLine();
            indentLevel--;
            emit("}\n");
        }
    }

    private void emitParameters(List<Parameter> parameters, boolean varargs) {
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                emit(", ");
            }
            emitParameter(parameters.get(i), varargs && i == parameters.size() - 1);
        }
    }

    private void emitParameter(Parameter parameter, boolean varargs) {
        emitTypeAnnotations(parameter.annotations());
        emitModifiers(parameter.modifiers(), Set.of());
        if (varargs && parameter.type() instanceof ArrayTypeName array) {
            emitArray(array, true);
        } else {
            emitTypeName(parameter.type());
        }
        emit(" " + parameter.name());
    }

    private void endLine() {
        if (!lineStart) {
            emit("\n");
        }
    }

    // Line breaks are written \n, whether they were \n, \r\n or \r.
    private void emit(String text) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                continue;
            }
            if (c == '\n' || c == '\r') {
                wrapper.append(line);
                line.setLength(0);
                newLine();
            } else {
                if (lineStart) {
                    line.append(indentUnit.repeat(indentLevel + continuation())).append(linePrefix);
                    lineStart = false;
                }
                line.append(c);
            }
        }
        wrapper.append(line);
    }

    private void newLine() {
        if (lineStart && !linePrefix.isEmpty()) {
            wrapper.append(indentUnit.repeat(indentLevel) + linePrefix.stripTrailing());
        }
        wrapper.append("\n");
        lineStart = true;
        if (statementDepth > 0) {
            statementLine++;
        }
    }

    /**
     * A member of a type body, and how to write it.
     *
     * @param isField whether it is a field, which needs no blank line before another field
     * @param writer writes the member
     */
    private record Member(boolean isField, Runnable writer) {}

    /**
     * What simple names refer to in a part of the file: a type body or header, or a method.
     *
     * @param type the type whose body or header it is, if it has a name
     * @param members the simple names of its member types, which refer to them
     * @param hidden the simple names that refer to something else: type variables, or inherited member types
     */
    private record Scope(@Nullable ClassName type, Set<String> members, Set<String> hidden) {}
}
