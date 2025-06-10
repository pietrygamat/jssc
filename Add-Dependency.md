### Maven

Add to `pom.xml`

```xml
<dependency>
    <groupId>io.github.java-native</groupId>
    <artifactId>jssc</artifactId>
    <version>2.10.2</version>
</dependency>
```

### Gradle (KTS)
```gradle
repositories {
    mavenCentral()
}
dependencies {
    implementation("io.github.java-native:jssc:2.10.2")
}
```

### Gradle (Groovy)
```gradle
repositories {
    mavenCentral()
}
dependencies {
    implementation 'io.github.java-native:jssc:2.10.2'
}
```