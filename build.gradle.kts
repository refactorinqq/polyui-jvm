/*
 * This file is part of PolyUI.
 * Copyright (C) 2022-2023 Polyfrost and its contributors.
 * All rights reserved.
 * PolyUI - Fast and lightweight UI framework https://polyfrost.cc https://github.com/Polyfrost/polui-jvm
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    kotlin("jvm") version "1.8.10" //ktVersion https://github.com/gradle/gradle/issues/22797
    `maven-publish`
}

val ktVersion = "1.8.10"
group = "cc.polyfrost"
version = "1.0.2"

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "kotlin")

    group = rootProject.group.toString()
    version = rootProject.version.toString()

    dependencies {
        api(project(":"))
    }
}

allprojects {
    repositories {
        mavenCentral()
        maven("https://repo.polyfrost.cc/releases")
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    tasks {
        withType(KotlinCompile::class).all {
            kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs = listOf(
                    "-Xjvm-default=all-compatibility",
                    "-Xuse-k2",         // use k2 because why not :)
                    "-Xno-call-assertions",
                    "-Xno-receiver-assertions",
                    "-Xno-param-assertions"
                )
            }
        }
    }
}

dependencies {
    implementation("org.slf4j", "slf4j-api", "1.6.1")
    implementation("org.slf4j", "slf4j-simple", "1.6.1")
    implementation("org.jetbrains.kotlin", "kotlin-stdlib-jdk8", ktVersion)
    implementation("org.jetbrains.kotlin", "kotlin-reflect", ktVersion)

    implementation("org.jetbrains", "annotations", "24.0.0")
}