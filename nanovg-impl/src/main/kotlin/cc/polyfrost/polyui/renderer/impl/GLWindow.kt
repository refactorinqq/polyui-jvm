/*
 * This file is part of PolyUI
 * PolyUI - Fast and lightweight UI framework
 * Copyright (C) 2023 Polyfrost and its contributors. All rights reserved.
 *   <https://polyfrost.cc> <https://github.com/Polyfrost/polui-jvm>
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package cc.polyfrost.polyui.renderer.impl

import cc.polyfrost.polyui.PolyUI
import cc.polyfrost.polyui.input.KeyModifiers
import cc.polyfrost.polyui.input.Keys
import cc.polyfrost.polyui.renderer.Window
import cc.polyfrost.polyui.utils.getResourceStream
import cc.polyfrost.polyui.utils.toByteBuffer
import org.lwjgl.glfw.Callbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL.createCapabilities
import org.lwjgl.opengl.GL11.*
import org.lwjgl.stb.STBImage
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import kotlin.math.max

class GLWindow @JvmOverloads constructor(
    title: String,
    width: Int,
    height: Int,
    gl2: Boolean = false,
    resizeable: Boolean = true,
    decorated: Boolean = true
) :
    Window(width, height) {
    val handle: Long
    var contentScaleX = 1f
        private set
    var contentScaleY = 1f
        private set
    var pixelRatio = 1f
        private set
    lateinit var polyUI: PolyUI
        private set
    var fpsCap: Double = 0.0
        set(value) {
            field = if (value == 0.0) 0.0 else 1.0 / value.toInt()
        }

    var title = title
        set(new) {
            field = new
            glfwSetWindowTitle(handle, new)
        }

    init {
        GLFWErrorCallback.createPrint().set()
        if (!glfwInit()) throw RuntimeException("Failed to init GLFW")

        if (gl2) {
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1)
        } else {
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2)
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        }

        if (!resizeable) glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE)
        if (!decorated) glfwWindowHint(GLFW_DECORATED, GLFW_FALSE)

        glfwWindowHint(GLFW_SCALE_TO_MONITOR, GLFW_TRUE)

        handle = glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL)
        if (handle == MemoryUtil.NULL) {
            glfwTerminate()
            throw RuntimeException("Failed to create the window.")
        }
        setIcon("icon.png")

        glfwMakeContextCurrent(handle)
        createCapabilities()
        glfwSetTime(0.0)
    }

    override fun createCallbacks() {
        // Add some callbacks for window resizing and content scale
        glfwSetFramebufferSizeCallback(handle) { _, width, height ->
            this.width = width
            this.height = height
            polyUI.onResize((width / pixelRatio).toInt(), (height / pixelRatio).toInt(), pixelRatio)
            // decreases the wierd effects
            polyUI.render()
        }

        glfwSetWindowContentScaleCallback(handle) { _, xScale, yScale ->
            pixelRatio = max(xScale, yScale)
            polyUI.onResize((width / pixelRatio).toInt(), (height / pixelRatio).toInt(), pixelRatio)
            // decreases the wierd effects
            polyUI.render()
        }

        glfwSetMouseButtonCallback(handle) { _, button, action, _ ->
            if (action == GLFW_PRESS) {
                polyUI.eventManager.onMousePressed(button)
            } else if (action == GLFW_RELEASE) {
                polyUI.eventManager.onMouseReleased(button)
            }
        }

        glfwSetCursorPosCallback(handle) { _, x, y ->
            polyUI.eventManager.setMousePosAndUpdate(x.toFloat(), y.toFloat())
        }

        glfwSetKeyCallback(handle) { _, keyCode, _, action, mods ->
            // p.s. I have performance tested this; and it is very fast (doesn't even show up on profiler). kotlin is good at int ranges lol
            if (keyCode < 100) {
                if (mods > 1 && action != GLFW_RELEASE) {
                    polyUI.eventManager.onKeyTyped(
                        keyCode.toChar(),
                        action == GLFW_REPEAT
                    )
                }
            } else if (keyCode < 340) {
                val key: Keys = (
                    when (keyCode) {
                        // insert, pg down, etc
                        in 256..261 -> Keys.fromValue(keyCode - 156)
                        in 266..269 -> Keys.fromValue(keyCode - 160)
                        // arrows
                        in 262..265 -> Keys.fromValue(keyCode - 62)
                        // function keys
                        in 290..314 -> Keys.fromValue(keyCode - 289)
                        else -> Keys.UNKNOWN
                    }
                    )
                if (action != GLFW_RELEASE) polyUI.eventManager.onUnprintableKeyTyped(key, action == GLFW_REPEAT)
            } else {
                val key: KeyModifiers = (
                    when (keyCode) {
                        340 -> KeyModifiers.LSHIFT
                        341 -> KeyModifiers.LCONTROL
                        342 -> KeyModifiers.LALT
                        343 -> KeyModifiers.LMETA
                        344 -> KeyModifiers.RSHIFT
                        345 -> KeyModifiers.RCONTROL
                        346 -> KeyModifiers.RALT
                        347 -> KeyModifiers.RMETA
                        else -> KeyModifiers.UNKNOWN
                    }
                    )
                if (action == GLFW_PRESS) {
                    polyUI.eventManager.addModifier(key.value)
                } else if (action == GLFW_RELEASE) {
                    polyUI.eventManager.removeModifier(key.value)
                }
            }
        }

        glfwSetCharCallback(handle) { _, codepoint ->
            polyUI.eventManager.onKeyTyped(codepoint.toChar(), false)
        }

        glfwSetScrollCallback(handle) { _, x, y ->
            polyUI.eventManager.onMouseScrolled(x.toInt(), y.toInt())
        }

        // todo glfwGetClipboardString() ?

//        glfwSetDropCallback(handle) { _, count, names ->
//            val files = Array(count) {
//                File(GLFWDropCallback.getName(names, it))
//            }
//        }

        glfwSetWindowFocusCallback(handle) { _, focused ->
            if (polyUI.settings.unfocusedFPS != 0) {
                fpsCap = if (focused) polyUI.settings.maxFPS.toDouble() else polyUI.settings.unfocusedFPS.toDouble()
            }
        }
    }

    override fun videoSettingsChanged() {
        glfwSwapInterval(if (polyUI.renderer.settings.enableVSync) 1 else 0)
    }

    override fun open(polyUI: PolyUI): Window {
        this.polyUI = polyUI
        videoSettingsChanged()

        createCallbacks()

        MemoryStack.stackPush().use {
            val w = it.mallocInt(1)
            val h = it.mallocInt(1)
            val contentScaleX = it.mallocFloat(1)
            val contentScaleY = it.mallocFloat(1)
            glfwGetFramebufferSize(handle, w, h)
            glfwGetWindowContentScale(handle, contentScaleX, contentScaleY)

            this.contentScaleX = contentScaleX[0]
            this.contentScaleY = contentScaleY[0]

            this.width = w[0]
            this.height = h[0]

            polyUI.onResize(
                (this.width / this.contentScaleX).toInt(),
                (this.height / this.contentScaleY).toInt(),
                max(this.contentScaleX, this.contentScaleY).also { this.pixelRatio = it }
            )
        }

        var t = glfwGetTime()
        fpsCap = polyUI.settings.maxFPS.toDouble()
        while (!glfwWindowShouldClose(handle)) {
            glViewport(0, 0, width, height)
            val c = polyUI.colors.page.bg
            glClearColor(c.r / 255f, c.g / 255f, c.b / 255f, 0f)
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT or GL_STENCIL_BUFFER_BIT)

            this.polyUI.render()
            if (fpsCap != 0.0) {
                while (glfwGetTime() - t < fpsCap) {
                    glfwPollEvents()
                }
                t = glfwGetTime()
            } else {
                glfwPollEvents()
            }
            if (polyUI.drew) glfwSwapBuffers(handle)
        }

        polyUI.cleanup()
        GL.setCapabilities(null)
        Callbacks.glfwFreeCallbacks(handle)
        glfwTerminate()
        glfwSetErrorCallback(null)!!.free()
        return this
    }

    override fun close() = glfwSetWindowShouldClose(handle, true)

    /** set the icon of this window according to the given [icon] path. This should be a resource path that can be used by [getResourceStream].
     *
     * The icon should be a PNG, BMP or JPG, and in a 2x size (i.e. 16x16, 32x32, 128x128, etc)
     * @throws java.io.IOException if the image does not exist, or a different IO error occurs.
     *
     * [SIGSEGV](https://en.wikipedia.org/wiki/Segmentation_fault) - if something else goes wrong in this method. Your JVM will crash blaming `[libglfw.so+0x211xx]` or something.
     */
    fun setIcon(icon: String) {
        val w = IntArray(1)
        val h = IntArray(1)
        val data = STBImage.stbi_load_from_memory(getResourceStream(icon).toByteBuffer(), w, h, IntArray(1), 4)
            ?: throw Exception("error occurred while loading icon!")
        glfwSetWindowIcon(handle, GLFWImage.malloc(1).put(0, GLFWImage.malloc().set(w[0], h[0], data)))
    }

    fun fullscreen() {
        glfwGetVideoMode(glfwGetPrimaryMonitor())?.let {
            glfwSetWindowSize(handle, it.width(), it.height())
        }
    }
}
