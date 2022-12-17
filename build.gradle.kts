group = "cn.status102"
version = "0.2.10"

plugins {
	val kotlinVersion = "1.7.10"
	kotlin("jvm") version kotlinVersion
	kotlin("plugin.serialization") version kotlinVersion
	id("net.mamoe.mirai-console") version "2.12.0"
}


val osName: String = System.getProperty("os.name")
val targetOs = when {
	osName == "Mac OS X" -> "macos"
	osName.startsWith("Win") -> "windows"
	osName.startsWith("Linux") -> "linux"
	else -> error("Unsupported OS: $osName")
}

val osArch: String = System.getProperty("os.arch")
var targetArch = when (osArch) {
	"x86_64", "amd64" -> "x64"
	"aarch64" -> "arm64"
	else -> error("Unsupported arch: $osArch")
}

val skikoVersion = "0.7.32"
val target = "${targetOs}-${targetArch}"

dependencies {
	implementation("org.jetbrains.skiko:skiko-awt-runtime-$target:$skikoVersion")
	//implementation("io.github.g0dkar:qrcode-kotlin-jvm:3.2.0")
	//shadowLink("org.jetbrains.skiko:skiko-awt-runtime-windows-x64")
}

mirai {
	jvmTarget = JavaVersion.VERSION_11
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
	kotlinOptions.jvmTarget = "11"
}

repositories {
	google()
	gradlePluginPortal()
	mavenCentral()
	mavenLocal()
	maven("https://androidx.dev/storage/compose-compiler/repository/")
	maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
	//maven("https://maven.aliyun.com/repository/public")
}
/*
buildscript {
	repositories {
		mavenLocal()
		google()
		mavenCentral()
		maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
	}

	dependencies {
		// __KOTLIN_COMPOSE_VERSION__
		classpath(kotlin("gradle-plugin", version = "1.6.10"))
	}
}
*/