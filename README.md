![Travis-CI Status](https://travis-ci.org/java-native/jssc.svg?branch=master)

A small, single Java library for working with serial ports across various systems based on the work from [scream3r/java-simple-serial-connector](https://github.com/scream3r/java-simple-serial-connector).

### Usage
* [Download `.jar` manually](../../releases) or add using maven
```xml
<dependency>
    <groupId>io.github.java-native</groupId>
    <artifactId>jssc</artifactId>
    <version>2.10.4</version>
</dependency>
```
* or Gradle (KTS)
```gradle
repositories {
    mavenCentral()
}
dependencies {
    implementation("io.github.java-native:jssc:2.10.4")
}
```
* or Gradle (Groovy)
```gradle
repositories {
    mavenCentral()
}
dependencies {
    implementation 'io.github.java-native:jssc:2.10.4'
}
```
* [API code examples](../../wiki/examples)
* JSSC has no runtime dependencies (since 2.11.0). The native library is extracted to `java.io.tmpdir` unless it is found on `java.library.path` or in `-Djssc.boot.library.path=<dir>`.

### Upgrading to 2.11.0
* `org.scijava:native-lib-loader` and its transitive `slf4j-api` are no longer dependencies. If your code used slf4j only because JSSC pulled it in, add it yourself. The `SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder"` warning is gone.
* `jssc.DefaultJniExtractorStub` was removed. It only worked together with native-lib-loader. Use `-Djssc.boot.library.path=<dir>` (unchanged) to load the library from a fixed directory without extraction.
* The JPMS module `jssc` no longer `requires transitive org.scijava.nativelib`.
* The native library is extracted to `<java.io.tmpdir>/jssc-<version>-<user>/<platform>/` and reused by later runs, instead of a new `nativelib-loader_*` directory per run. Nothing is extracted when `java.library.path` or `jssc.boot.library.path` provides the library. Leftover `nativelib-loader_*` directories from older versions can be deleted.
* 32-bit ARM Linux: the loader detects hard-float/soft-float and prefers `natives/linux_armhf` or `natives/linux_armsf`, falling back to `natives/linux_arm` as before. Custom builds: `mvn -Parmhf` / `mvn -Parmsf` now write to those directories.

### Support
* [Report a bug or request a feature](../../issues/new)

### Developers
* [Compile this project from source](../../wiki/compiling)
* [Chat on Discord](https://discord.gg/RBsUfE9sX9)
