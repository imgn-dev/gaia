# Maven plugin

`gaia-maven-plugin` has one goal, `register-services`, for projects that run on the module path.

## Registering service providers

An annotation processor can list the service providers it generates in `META-INF/services`, which `ServiceLoader`
reads on the class path. On the module path, `ServiceLoader` only finds the providers that `module-info.java` declares
with `provides`, which a processor cannot add. The `register-services` goal adds them to the compiled
`module-info.class`, after compilation:

```xml
<plugin>
  <groupId>be.imgn.gaia</groupId>
  <artifactId>gaia-maven-plugin</artifactId>
  <version>${gaia.version}</version>
  <executions>
    <execution>
      <goals>
        <goal>register-services</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

It runs in the `process-classes` phase:

- It reads every file in `META-INF/services` of the compiled classes, in the format of `ServiceLoader`: one provider
  per line, with `#` comments and blank lines ignored.
- For each service, the providers in its file replace those that `module-info.java` declares, if any. A file that
  lists no provider removes the service's `provides` directive, even one added by an earlier build. The `provides`
  directives of other services stay as they are.
- Running it again changes nothing, and it never writes to the sources.
- Each provider must be a class of the module, or the build fails: the JVM would refuse to start a module that
  provides a class it doesn't contain.
- Without `module-info.class`, it does nothing: on the class path, `ServiceLoader` reads `META-INF/services` itself.

The module still needs a `uses` directive where it calls `ServiceLoader.load`.

A processor built on [`gaia-processing`](processing.md#service-files) that relies on the goal calls
`skipModuleCheck(service)`, so that it doesn't warn about the missing `provides` directive.

## Parameters

| Parameter          | Property                     | Default                             | Description                                     |
|--------------------|------------------------------|-------------------------------------|-------------------------------------------------|
| `classesDirectory` |                              | `${project.build.outputDirectory}`  | The compiled classes, with `module-info.class`  |
| `services`         |                              | every file in `META-INF/services`   | The binary names of the services to register    |
| `skip`             | `gaia.registerServices.skip` | `false`                             | Whether to skip the goal                        |

To register only some services, list them. A listed service without a file in `META-INF/services` fails the build,
as a misspelled name would otherwise be ignored:

```xml
<configuration>
  <services>
    <service>com.example.Plugin</service>
  </services>
</configuration>
```

The plugin requires Maven 3.10.0 or later, and Java 21 or later.
