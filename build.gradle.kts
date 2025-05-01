plugins {
    id("java") // Keep Java plugin for compiling Java code
    // id("org.jetbrains.kotlin.jvm") version "1.9.25" // REMOVED Kotlin plugin
    id("org.jetbrains.intellij") version "1.17.4" // Keep IntelliJ plugin
}

// Use your actual group ID
group = "com.yourcompany.catalyst" // CHANGED from org.zoho
version = "1.0-SNAPSHOT" // Or your desired initial version

repositories {
    mavenCentral() // Standard repository for dependencies
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    // The version of the IntelliJ Platform SDK to build against.
    // This should match the target IDE version (e.g., 2024.1.x builds).
    // The wizard likely set a specific build number like "241.14494.240" based on your IDE.
    // Using a more general version like "2024.1" might also work but check compatibility.
    version.set("2024.1") // Or keep the specific build number if preferred (e.g., "241.14494.240")

    // Target IDE type: IC for Community, IU for Ultimate, etc.
    type.set("IC") // Or "IU"

    // List plugins from the target platform your plugin depends on.
    // Common ones include 'com.intellij.java' for Java support, 'com.intellij.modules.xml' for XML.
    plugins.set(listOf())
}

tasks {
    // Set the JVM compatibility versions for Java compilation
    withType<JavaCompile> {
        sourceCompatibility = "17" // Use Java 17 source features
        targetCompatibility = "17" // Generate Java 17 bytecode
    }
    // REMOVED KotlinCompile block as we are not using Kotlin
    // withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    //     kotlinOptions.jvmTarget = "17"
    // }

    // Patch the plugin.xml file with the correct since/until build numbers
    patchPluginXml {
        // Set the range of IDE builds your plugin is compatible with.
        // Use build numbers (e.g., 241.xxxxx) from:
        // https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html
        sinceBuild.set("241") // Corresponds to 2024.1
        untilBuild.set("241.*") // Compatible with all 2024.1.x patch releases
    }

    // Optional: Signing configuration for publishing to Marketplace
    // Keep these commented out or remove if not publishing/signing yet
    // signPlugin {
    //    certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
    //    privateKey.set(System.getenv("PRIVATE_KEY"))
    //    password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    // }

    // Optional: Publishing configuration for deploying to Marketplace
    // Keep commented out or remove if not publishing yet
    // publishPlugin {
    //    token.set(System.getenv("PUBLISH_TOKEN"))
    // }
}

// Add your external library dependencies here
dependencies {
    // HTTP Client (Choose one)
    implementation("org.apache.httpcomponents.client5:httpclient5:5.3") // Apache HttpClient 5

    // JSON Parsing
    implementation("com.google.code.gson:gson:2.10.1") // Gson

    // BCrypt (Only if really needed by the plugin itself)
    // implementation("org.mindrot:jbcrypt:0.4")

    // Test framework (Example)
    // testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    // testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
}