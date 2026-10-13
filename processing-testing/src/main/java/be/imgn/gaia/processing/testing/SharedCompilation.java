package be.imgn.gaia.processing.testing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes {@link CompilationExtension} share one compilation across all the tests of the annotated class, and its nested
 * classes, instead of starting one per test. The compilation starts before the first test and finishes after the last
 * one.
 *
 * <pre>{@code
 * @ExtendWith(CompilationExtension.class)
 * @SharedCompilation
 * class ManyTypeTests { ... }
 * }</pre>
 *
 * <p>Sharing saves a javac start per test. Since the tests then see the same {@code Elements} and {@code Types}, they
 * must not depend on each other's use of them.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SharedCompilation {}
