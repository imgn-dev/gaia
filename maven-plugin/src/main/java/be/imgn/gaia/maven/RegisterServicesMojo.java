package be.imgn.gaia.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * Registers the service providers listed in {@code META-INF/services} in the compiled {@code module-info.class}, as
 * {@code provides} directives, so that {@link java.util.ServiceLoader} finds them on the module path too.
 *
 * <p>An annotation processor can list the providers it generates in {@code META-INF/services}, but cannot add them to
 * {@code module-info.java}. This goal, bound to {@code process-classes}, does it in the compiled descriptor:
 *
 * <ul>
 *   <li>The providers listed in the file of a service replace the {@code provides} directive of that service, if
 *       {@code module-info.java} declares one. Directives for other services are kept.
 *   <li>Running the goal again changes nothing, and sources are never written.
 *   <li>Each provider must be a class in {@link #classesDirectory}, or the build fails: the JVM would refuse to start a
 *       module that provides a class it does not contain.
 *   <li>Without {@code module-info.class}, the goal does nothing: on the class path, {@code ServiceLoader} reads
 *       {@code META-INF/services} itself.
 * </ul>
 *
 * @since 0.1.2
 */
@Mojo(name = "register-services", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true)
public final class RegisterServicesMojo extends AbstractMojo {

    /** The directory of compiled classes, holding {@code module-info.class} and {@code META-INF/services}. */
    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private @Nullable File classesDirectory;

    /**
     * The binary names of the services to register, such as {@code com.example.Plugin}. By default, every file in
     * {@code META-INF/services} is registered.
     */
    @Parameter
    private List<String> services = new ArrayList<>();

    /** Whether to skip the goal. */
    @Parameter(property = "gaia.registerServices.skip", defaultValue = "false")
    private boolean skip;

    /** Creates the goal. Maven calls it. */
    public RegisterServicesMojo() {}

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping the registration of services");
            return;
        }
        if (classesDirectory == null) {
            throw new MojoExecutionException("The classesDirectory parameter is missing");
        }
        var classes = classesDirectory.toPath();
        var moduleInfo = classes.resolve(ModuleInfo.FILE_NAME);
        if (!Files.isRegularFile(moduleInfo)) {
            getLog().debug("No " + moduleInfo + ": the class path reads " + ProviderFiles.DIRECTORY + " itself");
            return;
        }
        try {
            var providers = ProviderFiles.read(classes, services);
            if (providers.isEmpty()) {
                getLog().debug("No service providers in " + classes.resolve(ProviderFiles.DIRECTORY));
            } else if (ModuleInfo.provide(moduleInfo, providers)) {
                getLog().info("Registered the providers of " + String.join(", ", providers.keySet()) + " in "
                        + moduleInfo);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Could not register services in " + moduleInfo, e);
        }
    }
}
