// Pure Kotlin (no Android): timestamp and frame layout (draw lists for any backend).
// Runs in the app and, unchanged, in JVM unit tests on any computer:  gradle -p studio test
plugins { id("org.jetbrains.kotlin.jvm") }
// bytecode for Java 17 (what the app uses), whichever JDK runs the build
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.test { testLogging { events("failed"); showStandardStreams = false }; systemProperty("hikari.root", rootDir.absolutePath); systemProperty("studio.update", System.getProperty("studio.update") ?: "false") }
