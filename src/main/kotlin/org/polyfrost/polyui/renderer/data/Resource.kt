/*
 * This file is part of PolyUI
 * PolyUI - Fast and lightweight UI framework
 * Copyright (C) 2023-2024 Polyfrost and its contributors.
 *   <https://polyfrost.org> <https://github.com/Polyfrost/polui-jvm>
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *     PolyUI is licensed under the terms of version 3 of the GNU Lesser
 * General Public License as published by the Free Software Foundation,
 * AND the simple request that you adequately accredit us if you use PolyUI.
 * See details here <https://github.com/Polyfrost/polyui-jvm/ACCREDITATION.md>.
 *     This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU Lesser General Public
 * License.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.polyfrost.polyui.renderer.data

import org.jetbrains.annotations.ApiStatus
import org.polyfrost.polyui.utils.LinkedList
import org.polyfrost.polyui.utils.getResourceStream
import org.polyfrost.polyui.utils.getResourceStreamNullable
import java.io.InputStream
import java.util.concurrent.CompletableFuture

/**
 * Abstract representation of a resource. This is used to lazily load resources.
 *
 * @since 0.21.1
 */
abstract class Resource(val resourcePath: String) : AutoCloseable {
    var init = false
        private set(value) {
            field = value
            if (value) runCallbacks()
        }

    private var initting = false

    var errored = false
        protected set

    var resettable = false
        private set

    private var _stream: InputStream? = null

    private var completionCallbacks: LinkedList<Resource.() -> Unit>? = null

    val stream: InputStream?
        get() {
            if (!init) {
                val s = getResourceStreamNullable(resourcePath)
                if (s == null) {
                    errored = true
                    return null
                }
                _stream = s.apply {
                    if (markSupported()) {
                        mark(0)
                        resettable = true
                    }
                }
                init = true
            }
            return _stream
        }

    fun get() = stream ?: throw NullPointerException("Resource $resourcePath not found!")

    fun reset() {
        if (resettable) {
            _stream?.reset()
        } else {
            _stream?.close()
            _stream = null
            initting = false
            init = false
            errored = false
        }
    }

    override fun close() {
        _stream?.close()
        _stream = null
        initting = false
    }

    @ApiStatus.Experimental
    fun getAsync(exceptionHandler: (Throwable) -> Unit = { it.printStackTrace() }, callback: InputStream.() -> Unit) {
        if (!init) {
            if (!initting) {
                initting = true
                CompletableFuture.supplyAsync {
                    getResourceStream(resourcePath)
                }.thenAcceptAsync {
                    _stream = it
                    init = true
                    callback(it)
                }.exceptionally {
                    exceptionHandler(it)
                    initting = false
                    null
                }
            }
        } else {
            callback(_stream ?: throw IllegalStateException("stream should not be null"))
        }
    }

    @ApiStatus.Experimental
    fun getAsyncNullable(exceptionHandler: (Throwable) -> Unit = { it.printStackTrace() }, callback: InputStream?.() -> Unit) {
        if (!init) {
            if (!initting) {
                initting = true
                CompletableFuture.supplyAsync {
                    getResourceStreamNullable(resourcePath)
                }.thenAcceptAsync {
                    _stream = it
                    init = true
                    callback(it)
                    runCallbacks()
                }.exceptionally {
                    exceptionHandler(it)
                    initting = false
                    null
                }
            }
        } else {
            callback(_stream)
        }
    }

    fun addCompletionCallback(callback: Resource.() -> Unit) {
        if (init) {
            callback()
        } else {
            val c = completionCallbacks ?: LinkedList<Resource.() -> Unit>().also { completionCallbacks = it }
            c.add(callback)
        }
    }

    private fun runCallbacks() {
        if(resettable) {
            completionCallbacks?.fastEach { it() }
        } else {
            completionCallbacks?.clearing { it() }
        }
    }

    override fun toString() = "Resource(file=$resourcePath, init=$init)"

    override fun hashCode() = resourcePath.hashCode()

    override fun equals(other: Any?) = resourcePath == other
}
