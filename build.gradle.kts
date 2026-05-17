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
val kotlinForForgeVersion = providers.gradleProperty("kotlin_for_forge_version").get()
val kotlinForForgeVersionRange = providers.gradleProperty("kotlin_for_forge_version_range").get()
val owoLibVersion = providers.gradleProperty("owo_lib_version").get()
val owoVersionRange = providers.gradleProperty("owo_version_range").get()
val geckoLibVersion = providers.gradleProperty("geckolib_version").get()
val geckoLibVersionRange = providers.gradleProperty("geckolib_version_range").get()
val smartBrainLibVersion = providers.gradleProperty("smartbrainlib_version").get()
val smartBrainLibVersionRange = providers.gradleProperty("smartbrainlib_version_range").get()
val betterCombatVersion = providers.gradleProperty("bettercombat_version").get()
val betterCombatVersionRange = providers.gradleProperty("bettercombat_version_range").get()
val playerAnimatorVersion = providers.gradleProperty("playeranimator_version").get()
val playerAnimatorVersionRange = providers.gradleProperty("playeranimator_version_range").get()
val mobPlayerAnimatorVersion = providers.gradleProperty("mobplayeranimator_version").get()
val mobPlayerAnimatorVersionRange = providers.gradleProperty("mobplayeranimator_version_range").get()
val clothConfigVersion = providers.gradleProperty("cloth_config_version").get()
val clothConfigVersionRange = providers.gradleProperty("cloth_config_version_range").get()
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
    maven("https://thedarkcolour.github.io/KotlinForForge/")
    maven("https://api.modrinth.com/maven")
    maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
    exclusiveContent {
        forRepository {
            maven("https://dl.cloudsmith.io/public/tslat/sbl/maven/") {
                name = "SmartBrainLib"
            }
        }
        filter {
            includeGroup("net.tslat.smartbrainlib")
        }
    }
}

neoForge {
    version = neoVersion
    validateAccessTransformers = true

    runs {
        create("client") {
            client()
            gameDirectory = file("runs/client")
            jvmArgument("-Xmx8G")
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
    implementation("maven.modrinth:owo-lib:$owoLibVersion")
    implementation("software.bernie.geckolib:geckolib-neoforge-$minecraftVersion:$geckoLibVersion")
    implementation("net.tslat.smartbrainlib:SmartBrainLib-neoforge-$minecraftVersion:$smartBrainLibVersion")
    implementation("maven.modrinth:better-combat:$betterCombatVersion")
    implementation("maven.modrinth:playeranimator:$playerAnimatorVersion")
    compileOnly("maven.modrinth:mob-player-animator-neo:$mobPlayerAnimatorVersion")
    implementation("maven.modrinth:cloth-config:$clothConfigVersion")
    compileOnly("maven.modrinth:jade:15.9.3+neoforge")

    testImplementation(kotlin("test"))
}

tasks.processResources {
    val replacements = mapOf(
        "minecraft_version" to minecraftVersion,
        "minecraft_version_range" to minecraftVersionRange,
        "neo_version" to neoVersion,
        "neo_version_range" to neoVersionRange,
        "loader_version_range" to loaderVersionRange,
        "kotlin_for_forge_version" to kotlinForForgeVersion,
        "kotlin_for_forge_version_range" to kotlinForForgeVersionRange,
        "owo_version_range" to owoVersionRange,
        "geckolib_version_range" to geckoLibVersionRange,
        "smartbrainlib_version_range" to smartBrainLibVersionRange,
        "bettercombat_version_range" to betterCombatVersionRange,
        "playeranimator_version_range" to playerAnimatorVersionRange,
        "mobplayeranimator_version_range" to mobPlayerAnimatorVersionRange,
        "cloth_config_version_range" to clothConfigVersionRange,
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
