# Gaia

Gaia is a family of Java libraries: utilities the JDK doesn't provide, including a cross-platform API, an in-memory
file system, and helpers for writing annotation processors. Each library has a matching testing library for testing
the code that uses it.

The libraries require Java 21 or later, and are released together under one version. Every class is documented in
the [API documentation](https://imgn-dev.github.io/gaia/api/latest/).

## Libraries

| Artifact                  | Module                            | What it does                                           |
|---------------------------|-----------------------------------|--------------------------------------------------------|
| `gaia-core`               | `be.imgn.gaia.core`               | [Extensions to the JDK](core.md), including [the platform the JVM runs on](core.md#platform) |
| `gaia-memfs`              | `be.imgn.gaia.memfs`              | [An in-memory file system](memfs.md)                   |
| `gaia-processing`         | `be.imgn.gaia.processing`         | [Helpers for writing annotation processors](processing.md) |
| `gaia-core-testing`       | `be.imgn.gaia.core.testing`       | [Test helpers for `gaia-core`](testing.md#gaia-core-testing) |
| `gaia-processing-testing` | `be.imgn.gaia.processing.testing` | [Compiling through annotation processors in tests](testing.md#gaia-processing-testing) |

The [Maven plugin](maven-plugin.md), `gaia-maven-plugin`, registers the service providers an annotation processor
generates in the compiled `module-info.class`.

## Setting up

Import the BOM, then declare the libraries you use without their versions:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>be.imgn.gaia</groupId>
      <artifactId>gaia-bom</artifactId>
      <version>${gaia.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>be.imgn.gaia</groupId>
    <artifactId>gaia-core</artifactId>
  </dependency>
  <dependency>
    <groupId>be.imgn.gaia</groupId>
    <artifactId>gaia-core-testing</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

The BOM manages gaia's own artifacts only, so importing it doesn't change the versions of your other dependencies.

On the module path, require the modules you use, such as `requires be.imgn.gaia.core;`.

## Nulls

Every package of gaia is annotated with JSpecify's `@NullMarked`: a parameter or a return value is never null unless
it is annotated `@Nullable`. A method given null where it doesn't accept it throws `NullPointerException`, naming the
parameter.
