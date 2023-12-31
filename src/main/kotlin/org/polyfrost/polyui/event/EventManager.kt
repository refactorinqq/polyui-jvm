/*
 * This file is part of PolyUI
 * PolyUI - Fast and lightweight UI framework
 * Copyright (C) 2023 Polyfrost and its contributors.
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

package org.polyfrost.polyui.event

import org.jetbrains.annotations.ApiStatus
import org.polyfrost.polyui.PolyUI
import org.polyfrost.polyui.PolyUI.Companion.INPUT_HOVERED
import org.polyfrost.polyui.PolyUI.Companion.INPUT_NONE
import org.polyfrost.polyui.PolyUI.Companion.INPUT_PRESSED
import org.polyfrost.polyui.component.Drawable
import org.polyfrost.polyui.input.KeyBinder
import org.polyfrost.polyui.input.KeyModifiers
import org.polyfrost.polyui.input.Keys
import org.polyfrost.polyui.input.Modifiers
import org.polyfrost.polyui.property.Settings
import org.polyfrost.polyui.utils.LinkedList
import java.io.File
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

/**
 * # EventManager
 * Handles all events and passes them to the correct components/layouts.
 * @param drawables the layout to create this event manager for. marked as internal as should not be accessed.
 * @param drawables the key binder to use for this event manager. marked as internal as should not be accessed.
 */
class EventManager @JvmOverloads constructor(
    @get:ApiStatus.Internal
    var drawables: LinkedList<Drawable>? = null,
    @get:ApiStatus.Internal
    var keyBinder: KeyBinder?,
    private val settings: Settings,
) {
    private val mouseOvers = LinkedList<Drawable>()
    val primaryCandidate get() = mouseOvers.lastOrNull()
    var mouseX: Float = 0f
        private set
    var mouseY: Float = 0f
        private set
    private var clickTimer: Long = 0L

    /** @see org.polyfrost.polyui.input.Modifiers */
    var keyModifiers: Short = 0
        private set

    /** amount of clicks in the current combo */
    private var clickAmount = 0

    /** tracker for the combo */
    private var clickedButton: Int = 0
    private var focused: Drawable? = null
    val hasFocused get() = focused != null

    /** weather or not the left button/primary click is DOWN (aka repeating) */
    var mouseDown = false
        private set

    fun with(drawables: LinkedList<Drawable>?): EventManager {
        this.drawables = drawables
        return this
    }

    /**
     * Call this method when files are dropped onto this window. The [Event.Focused.FileDrop] is then dispatched to the currently focused drawable.
     * @see focus
     * @since 1.0.3
     */
    fun filesDropped(files: Array<File>) {
        focused?.accept(Event.Focused.FileDrop(files))
    }

    /** This method should be called when a printable key is typed. This key should be **mapped to the user's keyboard layout!** */
    fun keyTyped(key: Char) {
        focused?.accept(Event.Focused.KeyTyped(key, keyModifiers))
    }

    /** This method should be called when a non-printable, but representable key is pressed. */
    fun keyDown(key: Keys) {
        val event = Event.Focused.KeyPressed(key, keyModifiers)
        if (keyBinder?.accept(event) == true) return
        focused?.accept(event)
    }

    /** This method should be called when a non-printable, but representable key is released. */
    fun keyUp(key: Keys) {
        val event = Event.Focused.KeyReleased(key, keyModifiers)
        if (keyBinder?.accept(event) == true) return
        focused?.accept(event)
    }

    /**
     * This method should be called when a non-representable key is pressed.
     *
     * This is used solely for keybinding, and so the key can be any value, as long as it is consistent, and unique to that key.
     */
    fun keyDown(code: Int) {
        if (keyBinder?.accept(code, true) == true) return
        focused?.accept(Event.Focused.UnmappedInput(code, true))
    }

    /**
     * This method should be called when a non-representable key is released.
     *
     * This is used solely for keybinding, and so the key can be any value, as long as it is consistent, and unique to that key.
     */
    fun keyUp(code: Int) {
        if (keyBinder?.accept(code, false) == true) return
        focused?.accept(Event.Focused.UnmappedInput(code, false))
    }

    /**
     * force the mouse position to be updated.
     * @since 0.18.5
     */
    fun recalculateMousePos() = mouseMoved(mouseX, mouseY)

    /**
     * Internal function that will forcefully drop the given drawable from event tracking.
     */
    @ApiStatus.Internal
    fun drop(drawable: Drawable) {
        mouseOvers.fastRemoveIfReversed {
            if (it === drawable || it.hasParentOf(drawable)) {
                it.inputState = INPUT_NONE
                true
            } else {
                false
            }
        }
    }

    @ApiStatus.Internal
    fun drop() {
        mouseOvers.clearing {
            it.inputState = INPUT_NONE
        }
    }

    /**
     * add a modifier to the current keyModifiers.
     * @see KeyModifiers
     */
    fun addModifier(modifier: Short) {
        keyModifiers = if (PolyUI.isOnMac && settings.commandActsAsControl && modifier == Modifiers.LMETA.value) {
            keyModifiers or Modifiers.LCONTROL.value
        } else {
            keyModifiers or modifier
        }
    }

    /**
     * remove a modifier from the current keyModifiers.
     * @see KeyModifiers
     */
    fun removeModifier(modifier: Short) {
        keyModifiers = if (PolyUI.isOnMac && settings.commandActsAsControl && modifier == Modifiers.LMETA.value) {
            keyModifiers and (Modifiers.LCONTROL.value).inv()
        } else {
            keyModifiers and modifier.inv()
        }
    }

    /**
     * Clear the current keyModifiers.
     * @see KeyModifiers
     * @see addModifier
     * @since 0.20.1
     */
    fun clearModifiers() {
        keyModifiers = 0
    }

    /**
     * Internal method for drawables, tracking it if it does not consume the events.
     *
     * This is used for event dispatching.
     */
    @ApiStatus.Internal
    fun processCandidate(drawable: Drawable, x: Float, y: Float): Boolean {
        if (!drawable.acceptsInput) return true
        return when (drawable.inputState) {
            INPUT_NONE -> {
                if (drawable.isInside(x, y)) {
                    drawable.inputState = INPUT_HOVERED
//                    if (mouseOvers.contains(drawable)) throw IllegalArgumentException()
                    mouseOvers.add(drawable)
                    true
                } else false
            }

            INPUT_HOVERED -> {
                if (!drawable.isInside(x, y)) {
                    drawable.inputState = INPUT_NONE
                    mouseOvers.remove(drawable)
                    false
                } else true
            }

            else -> true
        }
    }


    private fun processCandidates(drawables: LinkedList<Drawable>, x: Float, y: Float) {
        drawables.fastEachReversed a@{
            val inside = processCandidate(it, x, y)
            val children = it.children ?: return@a
            if (inside) {
                processCandidates(children, x, y)
            } else {
                drop(it)
            }
        }
    }

    /** call this function to update the mouse position. */
    fun mouseMoved(x: Float, y: Float) {
        mouseX = x
        mouseY = y
        processCandidates(drawables ?: return, x, y)
    }

    fun mousePressed(button: Int) {
        if (button == 0) mouseDown = true
        val event = Event.Mouse.Pressed(button, mouseX, mouseY, keyModifiers)
        dispatchPress(event, true)
    }

    /** call this function when a mouse button is released. */
    fun mouseReleased(button: Int) {
        if (button == 0) {
            mouseDown = false
        }
        if (clickedButton != button) {
            clickedButton = button
            clickAmount = 1
        } else {
            val curr = System.nanoTime()
            if (curr - clickTimer < settings.comboMaxInterval) {
                if (clickAmount < settings.maxComboSize) {
                    clickAmount++
                } else if (settings.clearComboWhenMaxed) {
                    clickAmount = 1
                }
            } else {
                clickAmount = 1
            }
            clickTimer = curr
        }
        if (focused?.inputState == INPUT_NONE) {
            unfocus()
        }
        val release = Event.Mouse.Released(button, mouseX, mouseY, keyModifiers)
        val click = Event.Mouse.Clicked(button, mouseX, mouseY, clickAmount, keyModifiers)
        dispatchPress(release, false)
        if (!dispatch(click) && button == 0) {
            mouseOvers.fastEachReversed { if (focusCatching(it)) return }
        }
    }

    /**
     * try to focus a drawable, without throwing an exception.
     * @return `true` if the drawable was focused successfully, and `false` if it is not [Drawable.focusable], or if it is already focused.
     * @see focus
     * @since 1.0.4
     */
    fun focusCatching(drawable: Drawable?): Boolean {
        if (drawable == null) return false
        if (drawable.focusable) {
            return focus(drawable)
        }
        return false
    }

    /** call this function when the mouse is scrolled. */
    @Suppress("NAME_SHADOWING")
    fun mouseScrolled(amountX: Float, amountY: Float) {
        var amountX = if (settings.naturalScrolling) amountX else -amountX
        var amountY = if (settings.naturalScrolling) amountY else -amountY
        if ((keyModifiers and KeyModifiers.LSHIFT.value).toInt() != 0) {
            val t = amountX
            amountX = amountY
            amountY = t
        }
        val (sx, sy) = settings.scrollMultiplier
        val event = Event.Mouse.Scrolled(amountX * sx, amountY * sy, keyModifiers)
        dispatch(event)
    }

    /**
     * Dispatch an event to this PolyUI instance, and it will be given to any tracked drawable.
     */
    fun dispatch(event: Event): Boolean {
        if (keyBinder?.accept(event) == true) return true
        mouseOvers.fastEachReversed {
            if (it.accept(event)) {
                return true
            }
        }
        return false
    }

    private fun dispatchPress(event: Event, state: Boolean): Boolean {
        if (keyBinder?.accept(event) == true) return true
        val mode = if (state) INPUT_PRESSED else INPUT_HOVERED
        mouseOvers.fastEachReversed {
            it.inputState = mode
            if (it.accept(event)) {
                return true
            }
        }
        return false
    }

    /**
     * Sets the focus to the specified focusable element, throwing an exception if the provided element is not focusable.
     *
     * @throws IllegalArgumentException if the provided drawable is not [Drawable.focusable]
     * @param focusable the element to set focus on
     * @return true if focus was successfully set, false if the provided focusable is already focused
     * @see focusCatching
     */
    fun focus(focusable: Drawable?): Boolean {
        if (focusable === focused) return false
        require(focusable?.focusable ?: true) { "Cannot focus un-focusable drawable!" }
        focused?.accept(Event.Focused.Lost)
        focused = focusable
        return focused?.accept(Event.Focused.Gained) == true
    }

    fun unfocus() = focus(null)
}
