/*
 * This file is part of PolyUI
 * PolyUI - Fast and lightweight UI framework
 * Copyright (C) 2023 Polyfrost and its contributors. All rights reserved.
 *   <https://polyfrost.cc> <https://github.com/Polyfrost/polui-jvm>
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

@file:JvmName("IOUtils")

package cc.polyfrost.polyui.utils

import cc.polyfrost.polyui.PolyUI
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun getResourceStream(fileName: String): InputStream =
    getResourceStreamNullable(fileName)
        ?: throw FileNotFoundException(
            "Resource $fileName not found " +
                    "(check your Properties, and make sure the file " +
                    "is in the resources folder/on classpath)"
        )

/** get all resources matching the given path and (optionally) extension. Keep empty to ignore extensions. */
fun getResources(path: String, extension: String = ""): List<Pair<String, InputStream>> {
    val resources = PolyUI::class.java.classLoader.getResources(path)
    val out = ArrayList<Pair<String, InputStream>>()
    while (resources.hasMoreElements()) {
        val resource = resources.nextElement()
        if (extension.isEmpty() || resource.path.endsWith(extension)) out.add(
            Pair(
                resource.path,
                resource.openStream()
            )
        )
    }
    return out
}

fun getResourceStreamNullable(fileName: String): InputStream? =
    PolyUI::class.java.getResourceAsStream(fileName)
        ?: PolyUI::class.java.getResourceAsStream("/$fileName")
        ?: PolyUI::class.java.getResourceAsStream("/resources/$fileName")

fun InputStream.toByteBuffer(): ByteBuffer {
    val bytes = this.readBytes()
    this.close()
    return ByteBuffer.allocateDirect(bytes.size)
        .order(ByteOrder.nativeOrder())
        .put(bytes)
        .also { it.flip() }
}

