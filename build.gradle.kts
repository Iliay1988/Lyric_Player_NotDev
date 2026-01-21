plugins {
    id("java")
    id("application")
    // Плагин для создания Fat JAR (все зависимости внутри)
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "NotDev"
version = "v6.1"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // Основная библиотека медиа-инфо
    implementation("dev.redstones.mediaplayerinfo:media-player-info:0.1.0")

    // Необходимые зависимости для работы медиа-библиотеки
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    implementation("com.github.hypfvieh:dbus-java-core:5.0.0")
    runtimeOnly("com.github.hypfvieh:dbus-java-transport-jnr-unixsocket:5.0.0")
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.6.3")

    // JNA обязателен для Windows SMTC (получение треков из системы)
    implementation("net.java.dev.jna:jna:5.13.0")
    implementation("net.java.dev.jna:jna-platform:5.13.0")

    // Твои зависимости для поиска текста
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("org.json:json:20231013")
}

application {
    mainClass.set("NotDev.LyricSynchronizer")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// Настройка ShadowJar: как будет называться готовый файл
tasks.shadowJar {
    archiveBaseName.set("LyricPlayer")
    archiveClassifier.set("all")
    archiveVersion.set("v6.1")
}