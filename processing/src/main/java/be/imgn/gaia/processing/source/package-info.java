/**
 * A model of the Java source code a processor generates: type names, code blocks, annotations, fields, methods and
 * types, written out as a {@link be.imgn.gaia.processing.source.JavaFile} whose imports are worked out for it.
 *
 * <pre>{@code
 * Method greet = Method.newBuilder("greet")
 *         .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
 *         .returns(String.class)
 *         .addParameter(String.class, "name")
 *         .addStatement("return $S + $N", "Hello, ", "name")
 *         .build();
 * Type greeter = Type.newClassBuilder("Greeter")
 *         .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
 *         .addMethod(greet)
 *         .build();
 * JavaFile file = JavaFile.newBuilder("com.example", greeter).build();
 * }</pre>
 *
 * <p>Code is written with placeholders: {@code $T} for a type, imported as needed; {@code $N} for the name of a
 * parameter, field, method or type; {@code $S} for a string literal; {@code $L} for anything written as is, such as
 * a number or another code block; {@code $$} for a dollar sign; {@code $>} and {@code $<} to indent and unindent;
 * {@code $[} and {@code $]} around a statement; {@code $W} and {@code $Z} where a long line may break. Arguments are
 * taken in order, by position with {@code $1T}, {@code $2N} and so on, or by name with {@code $type:T}; see
 * {@link be.imgn.gaia.processing.source.CodeBlock}.
 */
@NullMarked
package be.imgn.gaia.processing.source;

import org.jspecify.annotations.NullMarked;
