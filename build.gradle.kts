plugins {
    id("java")
    id("application")
}

group = "NotDev"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") } // Иногда зависимости медиа-плеера тянутся отсюда
}

dependencies {
// Source: https://mvnrepository.com/artifact/dev.redstones.mediaplayerinfo/media-player-info
    implementation("dev.redstones.mediaplayerinfo:media-player-info:0.1.0")
    // Source: https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-stdlib
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    // Source: https://mvnrepository.com/artifact/com.github.hypfvieh/dbus-java-core
    implementation("com.github.hypfvieh:dbus-java-core:5.0.0")
    // Source: https://mvnrepository.com/artifact/com.github.hypfvieh/dbus-java-transport-jnr-unixsocket
    runtimeOnly("com.github.hypfvieh:dbus-java-transport-jnr-unixsocket:5.0.0")
    // Source: https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-serialization-core-jvm
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.6.3")

    // Наши инструменты для текста
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("org.json:json:20231013")

    // Для работы библиотеки в Windows нужны JNA
    implementation("net.java.dev.jna:jna:5.13.0")
    implementation("net.java.dev.jna:jna-platform:5.13.0")
}

application {
    mainClass.set("NotDev.LyricSynchronizer")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<JavaExec> {
    // Принудительная установка UTF-8 для ввода и вывода
    jvmArgs("-Dfile.encoding=UTF-8", "-Dconsole.encoding=UTF-8")
    standardInput = System.`in`
}

tasks.withType<JavaExec> {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dconsole.encoding=UTF-8")
    standardInput = System.`in`
}