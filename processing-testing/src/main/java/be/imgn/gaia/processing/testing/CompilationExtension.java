package be.imgn.gaia.processing.testing;

import be.imgn.gaia.processing.testing.internal.RunningCompilation;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Gives tests the {@link Elements} and {@link Types} of a running compilation, to test code that works on
 * {@code javax.lang.model} without writing an annotation processor around it.
 *
 * <pre>{@code
 * @ExtendWith(CompilationExtension.class)
 * class MyTest {
 *     @Test
 *     void stringIsACharSequence(Elements elements, Types types) {
 *         TypeMirror string = elements.getTypeElement("java.lang.String").asType();
 *         TypeMirror charSequence = elements.getTypeElement("java.lang.CharSequence").asType();
 *         assertThat(types.isAssignable(string, charSequence)).isTrue();
 *     }
 * }
 * }</pre>
 *
 * <p>Each test that asks for them gets its own compilation, started by the first parameter that needs it and finished
 * when the test ends. A compilation asked for by a constructor or {@code @BeforeAll} parameter is shared by the tests
 * of the class, and so is the one of a class annotated with {@link SharedCompilation}. While the test runs, javac waits
 * inside an annotation processor, so the elements and types stay valid; they must not be kept beyond the test. The
 * compilation sees the JDK, and the class path and module path of the running JVM.
 */
public final class CompilationExtension implements BeforeAllCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(CompilationExtension.class);

    /** Creates the extension; JUnit does, through {@code @ExtendWith}. */
    public CompilationExtension() {}

    @Override
    public void beforeAll(ExtensionContext context) {
        if (context.getRequiredTestClass().isAnnotationPresent(SharedCompilation.class)) {
            compilation(context);
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        return type == Elements.class || type == Types.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        RunningCompilation compilation = compilation(extensionContext);
        Class<?> type = parameterContext.getParameter().getType();
        return type == Elements.class ? compilation.elements() : compilation.types();
    }

    // The store finds a compilation started by an enclosing context, such as the class's, before starting one in
    // this context; JUnit closes it when the context that started it ends.
    private static RunningCompilation compilation(ExtensionContext context) {
        return context.getStore(NAMESPACE)
                .computeIfAbsent(RunningCompilation.class, key -> RunningCompilation.start(), RunningCompilation.class);
    }
}
