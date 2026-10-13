package be.imgn.gaia.processing.tests;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

/**
 * Generates a {@code <Type>Info} class naming each class it compiles, records it in a resource, and reports it in a
 * note on the class.
 */
@SupportedAnnotationTypes("*")
final class InfoProcessor extends AbstractProcessor {

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement type : ElementFilter.typesIn(roundEnv.getRootElements())) {
            if (type.getKind() == ElementKind.CLASS
                    && !type.getSimpleName().toString().endsWith("Info")) {
                generateInfo(type);
            }
        }
        return false;
    }

    private void generateInfo(TypeElement type) {
        String packageName = processingEnv
                .getElementUtils()
                .getPackageOf(type)
                .getQualifiedName()
                .toString();
        String infoName = packageName + "." + type.getSimpleName() + "Info";
        try (Writer writer =
                processingEnv.getFiler().createSourceFile(infoName, type).openWriter()) {
            writer.write("""
                    package %s;

                    final class %sInfo {
                        static final String NAME = "%s";
                    }
                    """.formatted(packageName, type.getSimpleName(), type.getQualifiedName()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try (Writer writer = processingEnv
                .getFiler()
                .createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/infos/" + type.getQualifiedName(), type)
                .openWriter()) {
            writer.write(infoName);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Generated " + infoName, type);
    }
}
