plugins {
	val kotlinVersion = "1.7.10"
	kotlin("jvm") version kotlinVersion
	kotlin("plugin.serialization") version kotlinVersion
	id("net.mamoe.mirai-console") version "2.12.0"
}

group = "cn.status102"
version = "0.1.0"

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
	//implementation("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.7.32")



}

repositories {
	maven("https://maven.aliyun.com/repository/public")
	mavenCentral()
	maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}
mirai {
	jvmTarget = JavaVersion.VERSION_11
}