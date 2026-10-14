package be.imgn.gaia.processing.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DeclarationValidationTest {

    @Nested
    class Types {

        @Test
        void rejectsMembersThatDoNotFitTheKind() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newInterfaceBuilder("I")
                            .superclass(Object.class)
                            .build())
                    .withMessage("Only a class has a superclass");
            assertThatIllegalStateException()
                    .isThrownBy(
                            () -> Type.newClassBuilder("C").addEnumConstant("A").build())
                    .withMessage("Only an enum has constants");
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newClassBuilder("C")
                            .addRecordComponent(int.class, "x")
                            .build())
                    .withMessage("Only a record has components");
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newClassBuilder("C")
                            .addMethod(Method.newCompactConstructorBuilder().build())
                            .build())
                    .withMessage("Only a record has a compact constructor");
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newInterfaceBuilder("I")
                            .addInitializerBlock(CodeBlock.of("x();\n"))
                            .build())
                    .withMessage("An interface has no instance initializer");
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newAnnotationBuilder("A")
                            .addSuperinterface(Runnable.class)
                            .build())
                    .withMessage("An annotation type extends nothing");
        }

        @Test
        void rejectsAnInterfaceMethodWithCodeThatIsNotDefaultStaticOrPrivate() {
            Method run = Method.newBuilder("run").addStatement("work()").build();

            assertThatIllegalStateException()
                    .isThrownBy(
                            () -> Type.newInterfaceBuilder("I").addMethod(run).build())
                    .withMessageStartingWith("A method of an interface with code must be default, static or private");
            assertThat(Type.newInterfaceBuilder("I")
                            .addMethod(run.toBuilder()
                                    .addModifiers(Modifier.DEFAULT)
                                    .build())
                            .build()
                            .methods())
                    .hasSize(1);
        }

        @Test
        void rejectsMalformedAnonymousClasses() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newAnonymousClassBuilder("")
                            .addModifiers(Modifier.PUBLIC)
                            .build());
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newAnonymousClassBuilder("")
                            .superclass(Thread.class)
                            .addSuperinterface(Runnable.class)
                            .build());
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newAnonymousClassBuilder("")
                            .addSuperinterface(Runnable.class)
                            .addSuperinterface(AutoCloseable.class)
                            .build());
        }

        @Test
        void rejectsInvalidNamesAndNesting() {
            assertThatIllegalArgumentException().isThrownBy(() -> Type.newClassBuilder("record-1"));
            assertThatIllegalArgumentException().isThrownBy(() -> Type.newRecordBuilder("int"));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Type.newEnumBuilder("E").addEnumConstant("1"));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Type.newClassBuilder("C")
                            .addType(Type.newAnonymousClassBuilder("").build()));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Type.newEnumBuilder("E")
                            .addEnumConstant("A", Type.newClassBuilder("Named").build()));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> JavaFile.newBuilder(
                            "com.example", Type.newAnonymousClassBuilder("").build()));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> JavaFile.newBuilder(
                            "com..example", Type.newClassBuilder("C").build()));
        }

        @Test
        void copiesThroughToBuilder() {
            Type original = Type.newRecordBuilder("Point")
                    .addModifiers(Modifier.PUBLIC)
                    .addJavadoc("A point.\n")
                    .addRecordComponent(int.class, "x")
                    .addSuperinterface(Runnable.class)
                    .addMethod(Method.newBuilder("run")
                            .addModifiers(Modifier.PUBLIC)
                            .build())
                    .build();

            Type copy = original.toBuilder().build();

            assertThat(copy).hasToString(original.toString());
            assertThat(original.toBuilder()
                            .addRecordComponent(int.class, "y")
                            .build()
                            .recordComponents())
                    .extracting(Parameter::name)
                    .containsExactly("x", "y");
            assertThat(original.recordComponents()).hasSize(1);
        }
    }

    @Nested
    class Methods {

        @Test
        void rejectsInconsistentMethods() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> Method.newBuilder("of")
                            .addParameter(int.class, "values")
                            .varargs(true)
                            .build());
            assertThatIllegalStateException()
                    .isThrownBy(() -> Method.newBuilder("of").varargs(true).build());
            assertThatIllegalStateException()
                    .isThrownBy(() -> Method.newBuilder("run")
                            .addModifiers(Modifier.ABSTRACT)
                            .addStatement("work()")
                            .build());
            assertThatIllegalStateException()
                    .isThrownBy(() -> Method.newCompactConstructorBuilder()
                            .addParameter(int.class, "x")
                            .build());
            assertThatIllegalStateException()
                    .isThrownBy(() -> Method.newConstructorBuilder().returns(int.class));
            assertThatIllegalArgumentException().isThrownBy(() -> Method.newBuilder("new"));
        }

        @Test
        void exposesWhatWasBuilt() {
            Method method = Method.newBuilder("join")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addTypeVariable(TypeVariableName.of("T"))
                    .returns(String.class)
                    .addParameter(ArrayTypeName.of(TypeVariableName.of("T")), "parts")
                    .varargs(true)
                    .addException(IllegalStateException.class)
                    .addStatement("return $S", "")
                    .build();

            assertThat(method.name()).isEqualTo("join");
            assertThat(method.isConstructor()).isFalse();
            assertThat(method.modifiers()).containsExactly(Modifier.PUBLIC, Modifier.STATIC);
            assertThat(method.returnType()).isEqualTo(ClassName.of(String.class));
            assertThat(method.parameters()).extracting(Parameter::name).containsExactly("parts");
            assertThat(method.isVarargs()).isTrue();
            assertThat(method.exceptions()).containsExactly(ClassName.of(IllegalStateException.class));
            assertThat(method.code()).hasToString("return \"\";\n");
            assertThat(method.toBuilder().build()).hasToString(method.toString());
            assertThat(Method.newConstructorBuilder().build().isConstructor()).isTrue();
            assertThat(Method.newCompactConstructorBuilder().build().isCompactConstructor())
                    .isTrue();
            assertThat(Method.newConstructorBuilder().build().returnType()).isNull();
        }
    }

    @Nested
    class FieldsAndParameters {

        @Test
        void rejectsModifiersThatDoNotApply() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Field.of(PrimitiveTypeName.INT, "x", Modifier.ABSTRACT));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Parameter.of(PrimitiveTypeName.INT, "x", Modifier.STATIC));
        }

        @Test
        void rejectsVoidAndInvalidNames() {
            assertThatIllegalArgumentException().isThrownBy(() -> Field.of(PrimitiveTypeName.VOID, "x"));
            assertThatIllegalArgumentException().isThrownBy(() -> Parameter.of(PrimitiveTypeName.VOID, "x"));
            assertThatIllegalArgumentException().isThrownBy(() -> Field.of(PrimitiveTypeName.INT, "class"));
            assertThatIllegalArgumentException().isThrownBy(() -> Parameter.of(PrimitiveTypeName.INT, "a b"));
        }

        @Test
        void writesThemStandalone() {
            assertThat(Field.newBuilder(
                                    ClassName.of(List.class).parameterized(ClassName.of(String.class)),
                                    "names",
                                    Modifier.PRIVATE,
                                    Modifier.FINAL)
                            .initializer("$T.of()", List.class)
                            .build())
                    .hasToString("private final java.util.List<java.lang.String> names = java.util.List.of();\n");
            assertThat(Parameter.of(String.class, "name", Modifier.FINAL)).hasToString("final java.lang.String name");
        }

        @Test
        void copiesThroughToBuilder() {
            Field field = Field.newBuilder(int.class, "x", Modifier.PRIVATE)
                    .initializer("1")
                    .build();
            Parameter parameter = Parameter.newBuilder(int.class, "x")
                    .addAnnotation(Deprecated.class)
                    .build();

            assertThat(field.toBuilder().build()).hasToString(field.toString());
            assertThat(parameter.toBuilder().build()).hasToString(parameter.toString());
            assertThat(field.initializer()).hasToString("1");
            assertThat(parameter.annotations()).containsExactly(Annotation.of(Deprecated.class));
        }
    }

    @Nested
    class Rules {

        @Test
        void rejectsASuperclassSetTwiceOrNotAClass() {
            Type.Builder builder = Type.newClassBuilder("C").superclass(Thread.class);

            assertThatIllegalStateException()
                    .isThrownBy(() -> builder.superclass(Object.class))
                    .withMessageContaining("already set");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Type.newClassBuilder("C").superclass(PrimitiveTypeName.INT));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Type.newClassBuilder("C").superclass(TypeVariableName.of("T")));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Type.newClassBuilder("C").superclass(ArrayTypeName.of(ClassName.OBJECT)));
        }

        @Test
        void rejectsAFieldInitializerSetTwice() {
            Field.Builder builder = Field.newBuilder(int.class, "x").initializer("1");

            assertThatIllegalStateException()
                    .isThrownBy(() -> builder.initializer("2"))
                    .withMessageContaining("already set");
        }

        @Test
        void rejectsCodeInNativeAndAbstractMethods() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> Method.newBuilder("call")
                            .addModifiers(Modifier.NATIVE)
                            .addStatement("work()")
                            .build())
                    .withMessageContaining("native");
        }

        @Test
        void rejectsMethodsThatDoNotFitTheKindOfType() {
            Method abstractMethod =
                    Method.newBuilder("run").addModifiers(Modifier.ABSTRACT).build();
            Method defaultMethod = Method.newBuilder("run")
                    .addModifiers(Modifier.DEFAULT)
                    .addStatement("work()")
                    .build();
            Method withDefaultValue = Method.newBuilder("value")
                    .returns(int.class)
                    .defaultValue("1")
                    .build();

            assertThatIllegalStateException()
                    .isThrownBy(() ->
                            Type.newClassBuilder("C").addMethod(abstractMethod).build())
                    .withMessageContaining("abstract");
            assertThat(Type.newClassBuilder("C")
                            .addModifiers(Modifier.ABSTRACT)
                            .addMethod(abstractMethod)
                            .build()
                            .methods())
                    .hasSize(1);
            assertThatIllegalStateException()
                    .isThrownBy(() ->
                            Type.newClassBuilder("C").addMethod(defaultMethod).build())
                    .withMessageContaining("default");
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newInterfaceBuilder("I")
                            .addMethod(withDefaultValue)
                            .build())
                    .withMessageContaining("default value");
            assertThat(Type.newAnnotationBuilder("A")
                            .addMethod(withDefaultValue)
                            .build()
                            .methods())
                    .hasSize(1);
        }

        @Test
        void rejectsPrivateMethodsThatAreAlsoPublicAbstractOrDefault() {
            for (Modifier modifier : List.of(Modifier.PUBLIC, Modifier.ABSTRACT, Modifier.DEFAULT)) {
                Method.Builder method = Method.newBuilder("helper").addModifiers(Modifier.PRIVATE);

                assertThatIllegalArgumentException()
                        .as(modifier.toString())
                        .isThrownBy(() -> method.addModifiers(modifier));
            }
        }

        @Test
        void rejectsPermittedSubclassesOfTypesThatCannotBeSealed() {
            ClassName sub = ClassName.of("com.example", "Sub");

            for (Type.Builder builder :
                    List.of(Type.newEnumBuilder("E"), Type.newRecordBuilder("R"), Type.newAnnotationBuilder("A"))) {
                assertThatIllegalStateException()
                        .isThrownBy(() -> builder.addPermittedSubclass(sub).build())
                        .withMessageContaining("permits");
            }
        }

        @Test
        void rejectsVarargsRecordsWithoutAnArrayLast() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newRecordBuilder("R")
                            .addRecordComponent(int.class, "x")
                            .varargs(true)
                            .build());
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newClassBuilder("C").varargs(true).build());
        }

        @Test
        void rejectsMisplacedReceiverParameters() {
            Parameter receiver = Parameter.of(ClassName.of("com.example", "C"), "this");

            assertThatIllegalStateException()
                    .isThrownBy(() -> Method.newBuilder("run")
                            .addParameter(int.class, "x")
                            .addParameter(receiver)
                            .build());
            assertThatIllegalStateException()
                    .isThrownBy(() -> Method.newBuilder("run")
                            .addModifiers(Modifier.STATIC)
                            .addParameter(receiver)
                            .build());
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Parameter.of(ClassName.of("com.example", "C"), "this", Modifier.FINAL));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Parameter.of(ClassName.of("com.example", "C"), "a.b.this"));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Parameter.of(ClassName.of("com.example", "C"), "class.this"));
        }

        @Test
        void addsEachExceptionOnce() {
            Method method = Method.newBuilder("read")
                    .addException(IOException.class)
                    .addExceptions(List.of(ClassName.of(IOException.class), ClassName.of(InterruptedException.class)))
                    .addException(InterruptedException.class)
                    .build();

            assertThat(method.exceptions())
                    .containsExactly(ClassName.of(IOException.class), ClassName.of(InterruptedException.class));
        }
    }

    @Nested
    class ModifierRules {

        @Test
        void rejectsRepeatedModifiersOfFieldsAndParameters() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Field.of(PrimitiveTypeName.INT, "x", Modifier.PRIVATE, Modifier.PRIVATE))
                    .withMessage("A field cannot have private twice");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Parameter.of(PrimitiveTypeName.INT, "x", Modifier.FINAL, Modifier.FINAL))
                    .withMessage("A parameter cannot have final twice");
        }

        @Test
        void rejectsConflictingModifiersOfFields() {
            Field.Builder field = Field.newBuilder(int.class, "x", Modifier.PUBLIC, Modifier.FINAL);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> field.addModifiers(Modifier.PRIVATE))
                    .withMessage("A field cannot be both public and private");
            assertThatIllegalArgumentException().isThrownBy(() -> field.addModifiers(Modifier.VOLATILE));
            assertThat(field.build().modifiers()).containsExactly(Modifier.PUBLIC, Modifier.FINAL);
        }

        @Test
        void rejectsModifiersThatNoMethodOrConstructorCanHave() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Method.newBuilder("m").addModifiers(Modifier.TRANSIENT))
                    .withMessageStartingWith("A method cannot be transient");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Method.newConstructorBuilder().addModifiers(Modifier.STATIC))
                    .withMessageStartingWith("A constructor cannot be static");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Method.newCompactConstructorBuilder().addModifiers(Modifier.FINAL));
            assertThat(Method.newConstructorBuilder()
                            .addModifiers(Modifier.PRIVATE)
                            .build()
                            .modifiers())
                    .containsExactly(Modifier.PRIVATE);
        }

        @Test
        void rejectsConflictingModifiersOfMethods() {
            List<List<Modifier>> conflicts = List.of(
                    List.of(Modifier.PUBLIC, Modifier.PROTECTED),
                    List.of(Modifier.ABSTRACT, Modifier.FINAL),
                    List.of(Modifier.ABSTRACT, Modifier.STATIC),
                    List.of(Modifier.ABSTRACT, Modifier.SYNCHRONIZED),
                    List.of(Modifier.DEFAULT, Modifier.STATIC),
                    List.of(Modifier.DEFAULT, Modifier.FINAL),
                    List.of(Modifier.NATIVE, Modifier.STRICTFP));
            for (List<Modifier> conflict : conflicts) {
                Method.Builder method = Method.newBuilder("m").addModifiers(conflict.getFirst());

                assertThatIllegalArgumentException()
                        .as(conflict.toString())
                        .isThrownBy(() -> method.addModifiers(conflict.getLast()))
                        .withMessageContaining("cannot be both");
            }
            assertThat(Method.newBuilder("m")
                            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL, Modifier.SYNCHRONIZED)
                            .build()
                            .modifiers())
                    .hasSize(4);
        }

        @Test
        void rejectsModifiersThatTheKindOfTypeCannotHave() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Type.newInterfaceBuilder("I").addModifiers(Modifier.FINAL))
                    .withMessageStartingWith("An interface cannot be final");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Type.newEnumBuilder("E").addModifiers(Modifier.ABSTRACT));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Type.newRecordBuilder("R").addModifiers(Modifier.SEALED));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Type.newAnnotationBuilder("A").addModifiers(Modifier.NON_SEALED));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Type.newClassBuilder("C").addModifiers(Modifier.SYNCHRONIZED));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Type.newClassBuilder("C").addModifiers(Modifier.DEFAULT));
        }

        @Test
        void rejectsConflictingModifiersOfTypes() {
            Type.Builder type = Type.newClassBuilder("C").addModifiers(Modifier.PUBLIC, Modifier.SEALED);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> type.addModifiers(Modifier.FINAL))
                    .withMessage("A class cannot be both sealed and final");
            assertThatIllegalArgumentException().isThrownBy(() -> type.addModifiers(Modifier.NON_SEALED));
            assertThatIllegalArgumentException().isThrownBy(() -> type.addModifiers(Modifier.PRIVATE));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Type.newClassBuilder("C").addModifiers(Modifier.ABSTRACT, Modifier.FINAL));
            assertThat(type.build().modifiers()).containsExactly(Modifier.PUBLIC, Modifier.SEALED);
        }
    }

    @Nested
    class MemberRules {

        @Test
        void rejectsMembersThatDoNotFitTheKindWhenTheyAreAdded() {
            Type.Builder type = Type.newClassBuilder("C");
            Type.Builder anInterface = Type.newInterfaceBuilder("I");

            assertThatIllegalStateException().isThrownBy(() -> type.addEnumConstant("A"));
            assertThatIllegalStateException().isThrownBy(() -> type.addRecordComponent(int.class, "x"));
            assertThatIllegalStateException().isThrownBy(() -> type.varargs(true));
            assertThatIllegalStateException().isThrownBy(() -> anInterface.superclass(Object.class));
            assertThatIllegalStateException().isThrownBy(() -> anInterface.addStaticBlock(CodeBlock.of("x();\n")));
            assertThatIllegalStateException().isThrownBy(() -> anInterface.addInitializerBlock(CodeBlock.of("x();\n")));
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newRecordBuilder("R").addInitializerBlock(CodeBlock.of("x();\n")))
                    .withMessage("A record has no instance initializer");
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newEnumBuilder("E").addTypeVariable(TypeVariableName.of("T")))
                    .withMessage("An enum has no type variables");
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newAnnotationBuilder("A").addTypeVariable(TypeVariableName.of("T")));
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newRecordBuilder("R").addPermittedSubclass(ClassName.of("a", "B")));
            assertThat(type.build()).isEqualTo(Type.newClassBuilder("C").build());
        }

        @Test
        void rejectsConstructorsWhereThereAreNone() {
            Method constructor = Method.newConstructorBuilder().build();

            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newInterfaceBuilder("I").addMethod(constructor))
                    .withMessage("An interface has no constructor");
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newAnnotationBuilder("A").addMethod(constructor))
                    .withMessage("An annotation type has no constructor");
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newAnonymousClassBuilder("").addMethod(constructor))
                    .withMessage("An anonymous class has no constructor");
        }

        @Test
        void rejectsInterfaceMethodsThatAreProtectedFinalSynchronizedOrNative() {
            for (Modifier modifier :
                    List.of(Modifier.PROTECTED, Modifier.FINAL, Modifier.SYNCHRONIZED, Modifier.NATIVE)) {
                Method method = Method.newBuilder("m").addModifiers(modifier).build();

                assertThatIllegalStateException()
                        .as(modifier.toString())
                        .isThrownBy(() -> Type.newInterfaceBuilder("I").addMethod(method));
            }
        }

        @Test
        void rejectsDuplicateAndMalformedEnumConstants() {
            Type.Builder type = Type.newEnumBuilder("E").addEnumConstant("A");
            Type withSupertype = Type.newAnonymousClassBuilder("")
                    .addSuperinterface(Runnable.class)
                    .build();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> type.addEnumConstant("A"))
                    .withMessage("Duplicate enum constant: A");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> type.addEnumConstant("1"))
                    .withMessage("Not an enum constant name: 1");
            assertThatIllegalArgumentException().isThrownBy(() -> type.addEnumConstant("B", withSupertype));
            assertThat(type.build().enumConstants()).containsOnlyKeys("A");
        }

        @Test
        void rejectsRecordComponentsWithModifiersOrThatAreReceivers() {
            Type.Builder type = Type.newRecordBuilder("R");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> type.addRecordComponent(Parameter.of(int.class, "x", Modifier.FINAL)))
                    .withMessageStartingWith("A record component has no modifiers");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> type.addRecordComponent(Parameter.of(ClassName.of("a", "R"), "this")));
            assertThat(type.build().recordComponents()).isEmpty();
        }

        @Test
        void rejectsSuperclassesThatCannotBeExtended() {
            for (Class<?> type :
                    List.of(Runnable.class, Thread.State.class, Point.class, String.class, Enum.class, int[].class)) {
                assertThatIllegalArgumentException()
                        .as(type.toString())
                        .isThrownBy(() -> Type.newClassBuilder("C").superclass(type))
                        .withMessageStartingWith("Not a class that can be extended");
            }
            assertThat(Type.newClassBuilder("C")
                            .superclass(Number.class)
                            .build()
                            .superclass())
                    .isEqualTo(ClassName.of(Number.class));
        }

        @Test
        void rejectsSuperinterfacesThatAreNotInterfaces() {
            Type.Builder type = Type.newClassBuilder("C");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> type.addSuperinterface(Object.class))
                    .withMessageStartingWith("Not an interface");
            assertThatIllegalArgumentException().isThrownBy(() -> type.addSuperinterface(TypeVariableName.of("T")));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> type.addSuperinterfaces(
                            List.of(ClassName.of(Runnable.class), ArrayTypeName.of(ClassName.of(Runnable.class)))));
            assertThat(type.build().superinterfaces()).isEmpty();
        }

        @Test
        void rejectsPermittedSubclassesThatAreNotPlainClassNames() {
            Type.Builder type = Type.newInterfaceBuilder("I").addModifiers(Modifier.SEALED);
            ClassName sub = ClassName.of("com.example", "Sub");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> type.addPermittedSubclass(sub.parameterized(ClassName.OBJECT)));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> type.addPermittedSubclass(sub.annotated(Annotation.of(Deprecated.class))));
        }

        @Test
        void rejectsPermittedSubclassesOfATypeThatIsNotSealed() {
            Type.Builder type = Type.newClassBuilder("C").addPermittedSubclass(ClassName.of("com.example", "Sub"));

            assertThatIllegalStateException()
                    .isThrownBy(type::build)
                    .withMessage("C permits subclasses but is not sealed");
        }

        @Test
        void addsNothingFromABulkAddThatFails() {
            Type.Builder type = Type.newClassBuilder("C");
            Type named = Type.newClassBuilder("Nested").build();
            Type anonymous = Type.newAnonymousClassBuilder("").build();

            assertThatIllegalArgumentException().isThrownBy(() -> type.addTypes(List.of(named, anonymous)));
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newAnonymousClassBuilder("")
                            .addSuperinterfaces(List.of(ClassName.of(Runnable.class), ClassName.of(Cloneable.class))));
            assertThat(type.build().types()).isEmpty();
        }

        @Test
        void namesTheTypeInTheMessagesOfBuild() {
            Method abstractMethod =
                    Method.newBuilder("run").addModifiers(Modifier.ABSTRACT).build();

            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newClassBuilder("Task")
                            .addMethod(abstractMethod)
                            .build())
                    .withMessage("Only an abstract class has abstract methods: Task");
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newRecordBuilder("Values")
                            .addRecordComponent(int.class, "x")
                            .varargs(true)
                            .build())
                    .withMessage("The record Values is varargs but its last component is not an array");
        }
    }

    @Nested
    class MethodRules {

        @Test
        void rejectsCodeInAbstractOrNativeMethodsWhenItIsAdded() {
            Method.Builder abstractMethod = Method.newBuilder("run").addModifiers(Modifier.ABSTRACT);
            Method.Builder withCode = Method.newBuilder("run").addStatement("work()");

            assertThatIllegalStateException()
                    .isThrownBy(() -> abstractMethod.addStatement("work()"))
                    .withMessage("A method that is abstract has no code: run");
            assertThatIllegalStateException().isThrownBy(() -> withCode.addModifiers(Modifier.NATIVE));
            assertThat(abstractMethod.build().code().isEmpty()).isTrue();
        }

        @Test
        void rejectsMisplacedReceiverParametersWhenTheyAreAdded() {
            Parameter receiver = Parameter.of(ClassName.of("com.example", "C"), "this");
            Method.Builder withParameter = Method.newBuilder("run").addParameter(int.class, "x");
            Method.Builder withReceiver = Method.newBuilder("run").addParameter(receiver);

            assertThatIllegalStateException().isThrownBy(() -> withParameter.addParameter(receiver));
            assertThatIllegalStateException().isThrownBy(() -> withReceiver.addModifiers(Modifier.STATIC));
            assertThat(withReceiver.addParameter(int.class, "x").build().parameters())
                    .hasSize(2);
        }

        @Test
        void rejectsWhatACompactConstructorCannotHave() {
            Method.Builder constructor = Method.newCompactConstructorBuilder();

            assertThatIllegalStateException().isThrownBy(() -> constructor.addTypeVariable(TypeVariableName.of("T")));
            assertThatIllegalStateException().isThrownBy(() -> constructor.addException(Exception.class));
            assertThatIllegalStateException().isThrownBy(() -> constructor.addParameter(int.class, "x"));
            assertThatIllegalStateException()
                    .isThrownBy(() -> Method.newConstructorBuilder().defaultValue("1"))
                    .withMessage("A constructor has no default value");
        }

        @Test
        void rejectsExceptionsThatAreNotClassesOrTypeVariables() {
            Method.Builder method = Method.newBuilder("run");

            assertThatIllegalArgumentException()
                    .isThrownBy(
                            () -> method.addException(ClassName.of(List.class).parameterized(ClassName.OBJECT)));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> method.addExceptions(List.of(
                            ClassName.of(IOException.class), ArrayTypeName.of(ClassName.of(IOException.class)))));
            assertThat(method.addException(TypeVariableName.of("E")).build().exceptions())
                    .containsExactly(TypeVariableName.of("E"));
        }

        @Test
        void rejectsWildcardsAsTheTypesOfDeclarations() {
            WildcardTypeName wildcard = WildcardTypeName.unbounded();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Method.newBuilder("m").returns(wildcard));
            assertThatIllegalArgumentException().isThrownBy(() -> Field.of(wildcard, "x"));
            assertThatIllegalArgumentException().isThrownBy(() -> Parameter.of(wildcard, "x"));
        }
    }

    @Nested
    class Balance {

        @Test
        void rejectsUnbalancedCodeWhereItIsGivenWhole() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Field.newBuilder(int.class, "x").initializer("$>1"))
                    .withMessage("The initializer of x indents more than it unindents: a $> or a control flow is"
                            + " not closed");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Method.newBuilder("value").defaultValue("$]1"))
                    .withMessageContaining("$]");
            assertThatIllegalArgumentException().isThrownBy(() -> Type.newAnonymousClassBuilder("$>"));
        }

        @Test
        void rejectsUnbalancedCodeOfABuilderWhenItIsBuilt() {
            assertThatIllegalStateException()
                    .isThrownBy(() ->
                            Field.newBuilder(int.class, "x").addJavadoc("$>").build())
                    .withMessageStartingWith("The Javadoc of x");
            assertThatIllegalStateException()
                    .isThrownBy(() -> Parameter.newBuilder(int.class, "x")
                            .addJavadoc("$<")
                            .build())
                    .withMessageStartingWith("The Javadoc of x");
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newClassBuilder("C")
                            .addStaticBlock(CodeBlock.of("$>"))
                            .build())
                    .withMessageStartingWith("The static initializer of C");
            assertThatIllegalStateException()
                    .isThrownBy(() -> Type.newClassBuilder("C").addJavadoc("$[").build())
                    .withMessageStartingWith("The Javadoc of C");
            assertThatIllegalStateException()
                    .isThrownBy(() -> JavaFile.newBuilder(
                                    "a", Type.newClassBuilder("C").build())
                            .addFileComment("$<")
                            .build())
                    .withMessageStartingWith("The file comment");
        }

        @Test
        void countsCodeBlocksWrittenWithL() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> Method.newBuilder("run")
                            .addStatement("run($L)", CodeBlock.of("$>"))
                            .build());
            assertThat(Method.newBuilder("run")
                            .addCode(CodeBlock.of("if (ready) {\n$>"))
                            .addCode("$L", CodeBlock.of("run();\n$<}\n"))
                            .build()
                            .code())
                    .hasToString("if (ready) {\n    run();\n}\n");
        }
    }

    @Nested
    class Files {

        @Test
        void rejectsTopLevelTypesWithTheModifiersOfMemberTypes() {
            for (Modifier modifier : List.of(Modifier.PROTECTED, Modifier.PRIVATE, Modifier.STATIC)) {
                Type type = Type.newClassBuilder("C").addModifiers(modifier).build();

                assertThatIllegalArgumentException()
                        .as(modifier.toString())
                        .isThrownBy(() -> JavaFile.newBuilder("a", type))
                        .withMessage("A top-level type cannot be " + modifier + ": C");
                assertThatIllegalArgumentException()
                        .isThrownBy(() -> JavaFile.newBuilder(
                                        "a", Type.newClassBuilder("D").build())
                                .type(type));
            }
        }

        @Test
        void rejectsKeywordsAsStaticImportsWithoutAddingAny() {
            JavaFile.Builder file =
                    JavaFile.newBuilder("a", Type.newClassBuilder("C").build());

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> file.addStaticImport(ClassName.of(Math.class), "max", "class"))
                    .withMessage("Not a member name: class");
            assertThat(file.build().staticImports()).isEmpty();
        }
    }

    private record Point(int x) {}

    @Nested
    class Values {

        @Test
        void comparesSpecsByWhatTheyWrite() {
            assertThat(Field.of(PrimitiveTypeName.INT, "x"))
                    .isEqualTo(Field.of(PrimitiveTypeName.INT, "x"))
                    .hasSameHashCodeAs(Field.of(PrimitiveTypeName.INT, "x"))
                    .isNotEqualTo(Field.of(PrimitiveTypeName.LONG, "x"));
            assertThat(Method.newBuilder("run").build())
                    .isEqualTo(Method.newBuilder("run").build())
                    .hasSameHashCodeAs(Method.newBuilder("run").build())
                    .isNotEqualTo(Method.newBuilder("walk").build());
            assertThat(Type.newClassBuilder("C").build())
                    .isEqualTo(Type.newClassBuilder("C").build())
                    .hasSameHashCodeAs(Type.newClassBuilder("C").build())
                    .isNotEqualTo(
                            Type.newClassBuilder("C").alwaysQualify("List").build());
            assertThat(JavaFile.newBuilder("a", Type.newClassBuilder("C").build())
                            .build())
                    .isEqualTo(
                            JavaFile.newBuilder("a", Type.newClassBuilder("C").build())
                                    .build())
                    .isNotEqualTo(
                            JavaFile.newBuilder("b", Type.newClassBuilder("C").build())
                                    .build());
        }

        @Test
        void comparesParametersWithTheirJavadoc() {
            Parameter plain = Parameter.of(int.class, "x");
            Parameter documented = plain.toBuilder().addJavadoc("the x").build();

            assertThat(plain).isEqualTo(Parameter.of(int.class, "x")).hasSameHashCodeAs(Parameter.of(int.class, "x"));
            assertThat(documented).isNotEqualTo(plain).hasToString(plain.toString());
            assertThat(documented.toBuilder().build()).isEqualTo(documented);
        }

        @Test
        void renamesACopiedMethod() {
            Method original = Method.newBuilder("first")
                    .returns(int.class)
                    .addStatement("return 1")
                    .build();

            Method renamed = original.toBuilder().name("second").build();

            assertThat(renamed.name()).isEqualTo("second");
            assertThat(renamed.code()).isEqualTo(original.code());
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> original.toBuilder().name("class"));
            assertThatIllegalStateException()
                    .isThrownBy(() -> Method.newConstructorBuilder().name("second"));
        }

        @Test
        void addsInBulkWhatItAddsOneByOne() {
            Annotation deprecated = Annotation.of(Deprecated.class);
            TypeVariableName t = TypeVariableName.of("T");
            Field field = Field.of(PrimitiveTypeName.INT, "x");
            Method method = Method.newBuilder("run").build();
            Type nested = Type.newClassBuilder("Nested").build();
            ClassName runnable = ClassName.of(Runnable.class);

            Type one = Type.newClassBuilder("C")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(deprecated)
                    .addTypeVariable(t)
                    .addSuperinterface(runnable)
                    .addField(field)
                    .addMethod(method)
                    .addType(nested)
                    .build();
            Type bulk = Type.newClassBuilder(ClassName.of("com.example", "C"))
                    .addModifiers(Set.of(Modifier.PUBLIC))
                    .addAnnotations(List.of(deprecated))
                    .addTypeVariables(List.of(t))
                    .addSuperinterfaces(List.of(runnable))
                    .addFields(List.of(field))
                    .addMethods(List.of(method))
                    .addTypes(List.of(nested))
                    .build();

            assertThat(bulk).isEqualTo(one);
            assertThat(Method.newBuilder("m")
                            .addAnnotations(List.of(deprecated))
                            .addTypeVariables(List.of(t))
                            .build())
                    .isEqualTo(Method.newBuilder("m")
                            .addAnnotation(Deprecated.class)
                            .addTypeVariable(t)
                            .build());
            assertThat(Type.newRecordBuilder("R")
                            .addRecordComponents(List.of(Parameter.of(int.class, "x")))
                            .build())
                    .isEqualTo(Type.newRecordBuilder("R")
                            .addRecordComponent(int.class, "x")
                            .build());
            assertThat(Type.newInterfaceBuilder("I")
                            .addModifiers(Modifier.SEALED)
                            .addPermittedSubclasses(List.of(ClassName.of("com.example", "A")))
                            .build()
                            .permittedSubclasses())
                    .containsExactly(ClassName.of("com.example", "A"));
        }

        @Test
        void annotatesWithAClassName() {
            ClassName marker = ClassName.of("com.example", "Marker");

            assertThat(Type.newClassBuilder("C").addAnnotation(marker).build().annotations())
                    .containsExactly(Annotation.of(marker));
            assertThat(Method.newBuilder("m").addAnnotation(marker).build().annotations())
                    .containsExactly(Annotation.of(marker));
            assertThat(Field.newBuilder(int.class, "x")
                            .addAnnotation(marker)
                            .build()
                            .annotations())
                    .containsExactly(Annotation.of(marker));
            assertThat(Parameter.newBuilder(int.class, "x")
                            .addAnnotation(marker)
                            .build()
                            .annotations())
                    .containsExactly(Annotation.of(marker));
        }

        @Test
        void givesAFileToTheCompiler() throws IOException {
            JavaFile file = JavaFile.newBuilder(
                            "com.example", Type.newClassBuilder("C").build())
                    .build();

            assertThat(file.toJavaFileObject().toUri()).hasToString("com/example/C.java");
            assertThat(file.toJavaFileObject().getKind()).isEqualTo(JavaFileObject.Kind.SOURCE);
            assertThat(file.toJavaFileObject().getCharContent(true)).hasToString(file.toString());
            assertThat(JavaFile.newBuilder("", Type.newClassBuilder("C").build())
                            .build()
                            .toJavaFileObject()
                            .toUri())
                    .hasToString("C.java");
        }
    }
}
