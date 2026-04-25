import java.io.FileInputStream
import java.util.Properties

plugins {
    `java-library`
}

val repoRoot = projectDir.parentFile.parentFile.parentFile
val localProperties = Properties()
val localPropertiesFile = repoRoot.resolve("local.properties")
if (localPropertiesFile.isFile) {
    FileInputStream(localPropertiesFile).use { localProperties.load(it) }
}

fun resolveAndroidJar(): File {
    val sdkDir = localProperties.getProperty("sdk.dir")
        ?.replace("\\:", ":")
        ?.replace("\\\\", "\\")
        ?.let(::File)
    val platformsDir = sdkDir?.resolve("platforms")
    if (platformsDir != null && platformsDir.isDirectory) {
        val androidJar = platformsDir
            .walkTopDown()
            .firstOrNull { it.isFile && it.name == "android.jar" }
        if (androidJar != null) {
            return androidJar
        }
    }
    error("android.jar not found from local.properties sdk.dir")
}

val androidJar = resolveAndroidJar()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
}

dependencies {
    compileOnly(files(androidJar))
    implementation("com.android.tools.build:apksig:8.1.0")
    implementation("com.github.Sable:axml:2.0.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78")
    implementation("org.json:json:20240303")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

val runtimeFatJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Build a self-contained JVM jar for the APK reverse helper runtime."
    archiveBaseName.set("apk-reverse-helper-runtime-jvm")
    archiveVersion.set("")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES")
}
