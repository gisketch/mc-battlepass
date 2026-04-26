import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.0"
    id("net.neoforged.moddev") version "2.0.141"
    id("maven-publish")
}

val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val minecraftVersionRange = providers.gradleProperty("minecraft_version_range").get()
val neoVersion = providers.gradleProperty("neo_version").get()
val neoVersionRange = providers.gradleProperty("neo_version_range").get()
val loaderVersionRange = providers.gradleProperty("loader_version_range").get()
val architecturyApiVersion = providers.gradleProperty("architectury_api_version").get()
val architecturyVersionRange = providers.gradleProperty("architectury_version_range").get()
val kotlinForForgeVersion = providers.gradleProperty("kotlin_for_forge_version").get()
val kotlinForForgeVersionRange = providers.gradleProperty("kotlin_for_forge_version_range").get()
val modId = providers.gradleProperty("mod_id").get()
val modName = providers.gradleProperty("mod_name").get()
val modLicense = providers.gradleProperty("mod_license").get()
val modVersion = providers.gradleProperty("mod_version").get()
val modGroupId = providers.gradleProperty("mod_group_id").get()
val modAuthors = providers.gradleProperty("mod_authors").get()
val modDescription = providers.gradleProperty("mod_description").get()
val modIconFile = providers.gradleProperty("mod_icon_file").orElse("").get()

version = modVersion
group = modGroupId

base {
    archivesName.set(modId)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.architectury.dev/")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
}

neoForge {
    version = neoVersion
    validateAccessTransformers = true

    runs {
        create("client") {
            client()
            gameDirectory = file("runs/client")
            systemProperty("forge.logging.console.level", "debug")
        }

        create("server") {
            server()
            gameDirectory = file("runs/server")
            programArgument("--nogui")
            systemProperty("forge.logging.console.level", "debug")
        }

        create("data") {
            data()
            gameDirectory = file("runs/data")
            programArguments.addAll(
                "--mod", modId,
                "--all",
                "--output", file("src/generated/resources/").absolutePath,
                "--existing", file("src/main/resources/").absolutePath,
            )
            systemProperty("forge.logging.console.level", "debug")
        }
    }

    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

sourceSets {
    main {
        resources.srcDir("src/generated/resources")
    }
}

dependencies {
    implementation("thedarkcolour:kotlinforforge-neoforge:$kotlinForForgeVersion")
    implementation("dev.architectury:architectury-neoforge:$architecturyApiVersion")

    testImplementation(kotlin("test"))
}

tasks.processResources {
    val replacements = mapOf(
        "minecraft_version" to minecraftVersion,
        "minecraft_version_range" to minecraftVersionRange,
        "neo_version" to neoVersion,
        "neo_version_range" to neoVersionRange,
        "loader_version_range" to loaderVersionRange,
        "architectury_api_version" to architecturyApiVersion,
        "architectury_version_range" to architecturyVersionRange,
        "kotlin_for_forge_version" to kotlinForForgeVersion,
        "kotlin_for_forge_version_range" to kotlinForForgeVersionRange,
        "mod_id" to modId,
        "mod_name" to modName,
        "mod_license" to modLicense,
        "mod_version" to modVersion,
        "mod_authors" to modAuthors,
        "mod_description" to modDescription,
        "mod_icon_file" to modIconFile,
    )

    inputs.properties(replacements)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(replacements)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

tasks.test {
    useJUnitPlatform()
}