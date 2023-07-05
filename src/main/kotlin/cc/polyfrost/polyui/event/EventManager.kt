/*
 * This file is part of PolyUI
 * PolyUI - Fast and lightweight UI framework
 * Copyright (C) 2023 Polyfrost and its contributors.
 *   <https://polyfrost.cc> <https://github.com/Polyfrost/polui-jvm>
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

package cc.polyfrost.polyui.event

import cc.polyfrost.polyui.PolyUI
import cc.polyfrost.polyui.component.Drawable
import cc.polyfrost.polyui.component.Focusable
import cc.polyfrost.polyui.input.KeyModifiers
import cc.polyfrost.polyui.input.Keys
import cc.polyfrost.polyui.layout.Layout
import cc.polyfrost.polyui.utils.fastEachReversed
import cc.polyfrost.polyui.utils.fastRemoveIf
import org.jetbrains.annotations.ApiStatus
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.experimental.xor

/**
 * # EventManager
 * Handles all events and passes them to the correct components/layouts.
 * @param polyUI The PolyUI instance to use.
 */
class EventManager(private val polyUI: PolyUI) {
    private val mouseOverDrawables = ArrayList<Drawable>()
    var mouseX: Float = 0f
        private set
    var mouseY: Float = 0f
        private set
    private var clickTimer: Long = 0L

    /** @see cc.polyfrost.polyui.input.Modifiers */
    var keyModifiers: Short = 0
        private set

    /** amount of clicks in the current combo */
    private var clickAmount = 0

    /** tracker for the combo */
    private var clickedButton: Int = 0

    /**
     * acts as a BooleanRef to avoid malloc() during event dispatch
     */
    private var cancelled = false

    /** weather or not the left button/primary click is DOWN (aka repeating) */
    var mouseDown = false
        private set

    /** This method should be called when a printable key is typed. This key should be **mapped to the user's keyboard layout!** */
    fun onKeyTyped(key: Char, isRepeat: Boolean) {
        val event = FocusedEvents.KeyTyped(key, keyModifiers, isRepeat)
        if (!isRepeat) {
            if (polyUI.keyBinder.accept(event)) return
        }
        polyUI.focused?.accept(event)
    }

    /** This method should be called when a non-printable key is pressed. */
    fun onUnprintableKeyTyped(key: Keys, isRepeat: Boolean) {
        val event = FocusedEvents.KeyPressed(key, keyModifiers, isRepeat)
        if (!isRepeat) {
            if (polyUI.keyBinder.accept(event)) return
        }
        polyUI.focused?.accept(event)
    }

    /**
     * Internal function that will force the mouse position to be updated.
     * @since 0.18.5
     */
    @ApiStatus.Internal
    @Suppress("NOTHING_TO_INLINE")
    inline fun recalculateMousePos() = setMousePosAndUpdate(mouseX, mouseY)

    /**
     * add a modifier to the current keyModifiers.
     * @see KeyModifiers
     */
    fun addModifier(modifier: Short) {
        keyModifiers = keyModifiers or modifier
    }

    /**
     * remove a modifier from the current keyModifiers.
     * @see KeyModifiers
     */
    fun removeModifier(modifier: Short) {
        keyModifiers = keyModifiers xor modifier
    }

    /** call this function to update the mouse position. It also will update all necessary mouse over flags. */
    fun setMousePosAndUpdate(x: Float, y: Float) {
        mouseX = x
        mouseY = y
        cancelled = false
        if (!mouseDown) {
            onApplicableDrawablesUnsafe(x, y) loop@{
                // e: return is not allowed here
                if (cancelled) return@loop
                if (isInside(x, y) && acceptsInput) {
                    if (!mouseOver) {
                        mouseOverDrawables.add(this)
                        mouseOver = true
                        if (accept(Events.MouseEntered)) {
                            cancelled = true
                            return@loop
                        }
                    }
                }
            }
        }
        mouseOverDrawables.fastRemoveIf {
            (!it.isInside(x, y)).also { b ->
                if (b) {
                    it.accept(Events.MouseExited)
                    it.mouseOver = false
                } else {
                    it.accept(Events.MouseMoved)
                }
            }
        }
    }

    private inline fun onApplicableLayouts(x: Float, y: Float, crossinline func: Layout.() -> Unit) {
        polyUI.master.onAllLayouts(true) {
            if (isInside(x, y)) {
                func(this)
            }
        }
    }

    private inline fun onApplicableDrawables(x: Float, y: Float, crossinline func: Drawable.() -> Unit) {
        onApplicableLayouts(x, y) {
            components.fastEachReversed { if (it.isInside(x, y)) func(it) }
            if (acceptsInput) func(this)
        }
    }

    /** This method doesn't check if the drawable is inside the mouse, this is unsafe and should only be used in [setMousePosAndUpdate] */
    private inline fun onApplicableDrawablesUnsafe(x: Float, y: Float, crossinline func: Drawable.() -> Unit) {
        onApplicableLayouts(x, y) {
            components.fastEachReversed { func(it) }
            if (acceptsInput) func(this)
        }
    }

    fun onMousePressed(button: Int) {
        if (button == 0) mouseDown = true
        val event = Events.MousePressed(button, mouseX, mouseY, keyModifiers)
        dispatch(event)
    }

    /** call this function when a mouse button is released. */
    fun onMouseReleased(button: Int) {
        if (button == 0) {
            mouseDown = false
        }
        if (clickedButton != button) {
            clickedButton = button
            clickAmount = 1
        } else {
            val curr = System.nanoTime()
            if (curr - clickTimer < polyUI.renderer.settings.comboMaxInterval) {
                if (clickAmount < polyUI.renderer.settings.maxComboSize) {
                    clickAmount++
                } else if (polyUI.settings.clearComboWhenMaxed) {
                    clickAmount = 1
                }
            } else {
                clickAmount = 1
            }
            clickTimer = curr
            if (polyUI.focused != null) {
                if (!(polyUI.focused as Drawable).isInside(mouseX, mouseY)) {
                    polyUI.unfocus()
                }
            }
        }
        val event = Events.MouseReleased(button, mouseX, mouseY, keyModifiers)
        var releaseCancelled = false
        var clickedCancelled = false
        val event2 = Events.MouseClicked(button, mouseX, mouseY, clickAmount, keyModifiers)
        if (polyUI.keyBinder.accept(event)) return
        if (polyUI.keyBinder.accept(event2)) return
        onApplicableDrawables(mouseX, mouseY) {
            if (mouseOver) {
                if (button == 0 && this is Focusable) {
                    if (polyUI.focus(this)) {
                        clickedCancelled = true
                        releaseCancelled = true
                    }
                }
                if (!releaseCancelled) {
                    if (accept(event)) releaseCancelled = true
                }
                if (!clickedCancelled) {
                    if (accept(event2)) clickedCancelled = true
                }
            }
        }
    }

    /** call this function when the mouse is scrolled. */
    @Suppress("NAME_SHADOWING")
    fun onMouseScrolled(amountX: Int, amountY: Int) {
        var amountX = if (polyUI.settings.naturalScrolling) amountX else -amountX
        var amountY = if (polyUI.settings.naturalScrolling) amountY else -amountY
        if ((keyModifiers and KeyModifiers.LSHIFT.value).toInt() != 0) {
            (amountX to amountY).let {
                amountX = it.second
                amountY = it.first
            }
        }
        val (sx, sy) = polyUI.settings.scrollMultiplier
        val event = Events.MouseScrolled(amountX * sx, amountY * sy, keyModifiers)
        dispatch(event)
    }

    private fun dispatch(event: Events) {
        if (polyUI.keyBinder.accept(event)) return
        cancelled = false
        onApplicableDrawables(mouseX, mouseY) loop@{
            if (mouseOver) {
                if (cancelled) return@loop
                if (accept(event)) {
                    cancelled = true
                }
            }
        }
    }
}
