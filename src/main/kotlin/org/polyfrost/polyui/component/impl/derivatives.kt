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

@file:JvmName("Derivatives")
@file:Suppress("FunctionName")

package org.polyfrost.polyui.component.impl

import org.polyfrost.polyui.PolyUI
import org.polyfrost.polyui.animate.Animations
import org.polyfrost.polyui.component.*
import org.polyfrost.polyui.event.Event
import org.polyfrost.polyui.operations.*
import org.polyfrost.polyui.renderer.data.Font
import org.polyfrost.polyui.renderer.data.PolyImage
import org.polyfrost.polyui.unit.*
import org.polyfrost.polyui.utils.LinkedList
import org.polyfrost.polyui.utils.mapToArray
import org.polyfrost.polyui.utils.radii
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

fun Button(leftImage: PolyImage? = null, text: String? = null, rightImage: PolyImage? = null, fontSize: Float = 12f, font: Font? = null, radii: FloatArray = 8f.radii(), padding: Vec2 = Vec2(12f, 6f), at: Vec2? = null, size: Vec2? = null): Block {
    return Block(
        alignment = Align(main = Align.Main.Center, padding = padding),
        at = at,
        size = size,
        radii = radii,
        children = arrayOf(
            if (leftImage != null) Image(leftImage) else null,
            if (text != null) Text(text, fontSize = fontSize, font = font) else null,
            if (rightImage != null) Image(rightImage) else null,
        ),
    ).onInit { _ ->
        this.children?.fastEach {
            (it as? Image)?.size?.max(32f, 32f)
        }
    }.withStates().namedId("Button")
}

fun Switch(at: Vec2? = null, size: Float, padding: Float = 3f, state: Boolean = false, lateralStretch: Float = 1.8f): Block {
    val circleSize = size - (padding + padding)
    return Block(
        at = at,
        size = Vec2(size * lateralStretch, size),
        alignment = Align(main = Align.Main.Start, padding = Vec2(padding, 0f)),
        radii = (size / 2f).radii(),
        children = arrayOf(
            Block(size = Vec2(circleSize, circleSize), radii = (circleSize / 2f).radii()).setPalette { text.primary },
        ),
    ).withStates().events {
        var switched = state
        Event.Mouse.Clicked(0) then {
            val ev = Event.Change.State(switched)
            accept(ev)
            if (ev.cancelled) return@then false
            val circle = this[0]
            val target = this.height * (lateralStretch - 1f)
            switched = !switched
            setPalette(if (switched) polyUI.colors.brand.fg else polyUI.colors.component.bg)
            Move(circle, if (switched) target else -target, 0f, true, Animations.EaseInOutQuad.create(0.2.seconds)).add()
            false
        }
    }.namedId("Switch").also {
        if (state) {
            it.setPalette { brand.fg }
            it.afterParentInit(2) {
                this[0].x += this.height * (lateralStretch - 1f)
            }
        }
    }
}

/**
 * For images, use `Image` for each entry.
 *
 * For both strings and images, use `Image to "string"`.
 */
fun Radiobutton(vararg entries: String, at: Vec2? = null, initial: Int = 0, fontSize: Float = 12f, optionLateralPadding: Float = 6f, optionVerticalPadding: Float = 6f): Block {
    return Radiobutton(entries = entries.mapToArray { null to it }, at, initial, fontSize, optionLateralPadding, optionVerticalPadding)
}

/**
 * For strings, use `"string"` for each entry.
 *
 * For both strings and images, use `Image to "string"`.
 */
fun Radiobutton(vararg entries: PolyImage, at: Vec2? = null, initial: Int = 0, fontSize: Float = 12f, optionLateralPadding: Float = 6f, optionVerticalPadding: Float = 6f): Block {
    return Radiobutton(entries = entries.mapToArray { it to null }, at, initial, fontSize, optionLateralPadding, optionVerticalPadding)
}

/**
 * For just strings, use `"string"` for each entry.
 *
 * For just images, use `Image` for each entry.
 *
 * `null to null` is not supported, and will throw an exception.
 */
fun Radiobutton(vararg entries: Pair<PolyImage?, String?>, at: Vec2? = null, initial: Int = 0, fontSize: Float = 12f, optionLateralPadding: Float = 6f, optionVerticalPadding: Float = 6f): Block {
    val optAlign = Align(Align.Main.Center, padding = Vec2(optionLateralPadding, optionVerticalPadding))
    val buttons: LinkedList<Drawable> = entries.mapTo(LinkedList()) { (img, text) ->
        require(img != null || text != null) { "image and text cannot both be null on Radiobutton" }
        Group(
            alignment = optAlign,
            children = arrayOf(
                if (img != null) Image(img) else null,
                if (text != null) Text(text, fontSize = fontSize).withStates() else null,
            ),
        ).events {
            Event.Lifetime.Init then {
                val maxImageSize = fontSize * 1.3f
                (this[0] as? Image)?.size?.max(maxImageSize, maxImageSize)
                true
            }
            Event.Mouse.Clicked(0) then { _ ->
                val children = parent!!.children!!
                val ev = Event.Change.Number(children.indexOf(this) - 1)
                accept(ev)
                if (ev.cancelled) return@then false
                val it = children.first()
                Move(it, this.x, add = false, animation = Animations.EaseInOutQuad.create(0.15.seconds)).add()
                Resize(it, this.width, add = false, animation = Animations.EaseInOutQuad.create(0.15.seconds)).add()
                true
            }
        }
    }
    return Block(
        at = at,
        children = buttons.apply {
            add(0, Block(size = 1f by 1f))
        }.toTypedArray(),
    ).afterInit { _ ->
        val it = this[0]
        val target = this[initial + 1]
        it.x = target.x
        it.y = target.y
        it.size.set(target.size)
        it.setPalette(polyUI.colors.brand.fg)
    }.namedId("Radiobutton")
}

/**
 * For images, use `Image to "string"` for each entry.
 */
fun Dropdown(vararg entries: String, at: Vec2? = null, fontSize: Float = 12f, initial: Int = 0, padding: Float = 12f): Block {
    return Dropdown(entries = entries.mapToArray { null to it }, at, fontSize, initial, padding)
}

fun Dropdown(vararg entries: Pair<PolyImage?, String>, at: Vec2? = null, fontSize: Float = 12f, initial: Int = 0, padding: Float = 12f): Block {
    var heightTracker = 0f
    val it = Block(
        Text("", fontSize = fontSize),
        Image(PolyImage("chevron-down.svg")),
        at = at,
        focusable = true,
        alignment = Align(main = Align.Main.SpaceBetween, padding = Vec2(12f, 6f)),
    ).withStates().withBoarder()
    val dropdown = Block(
        alignment = Align(mode = Align.Mode.Vertical, padding = Vec2(padding, 6f)),
        children = entries.mapToArray { (img, text) ->
            Group(
                children = arrayOf(
                    if (img != null) Image(img) else null,
                    Text(text, fontSize = fontSize).withStates(),
                ),
            ).events {
                Event.Mouse.Clicked(0) then { _ ->
                    val title = (it[0] as Text)
                    val self = ((if (children!!.size == 2) this[1] else this[0]) as Text).text
                    if (title.text == self) return@then false
                    val ev = Event.Change.Number(parent!!.children!!.indexOf(this))
                    it.accept(ev)
                    if (ev.cancelled) return@then false
                    title.text = self
                    true
                }
            }
        },
    ).namedId("DropdownMenu").hide().disable()
    it.afterParentInit(Int.MAX_VALUE) {
        polyUI.master.addChild(dropdown, reposition = false)
    }
    return it.events {
        Event.Focused.Gained then {
            dropdown.x = this.x
            dropdown.y = this.y + this.size.y
            if (dropdown.height != 0f) heightTracker = dropdown.height
            dropdown.height = 0f
            dropdown.renders = true
            dropdown.enabled = true
            prioritize()
            Resize(dropdown, height = heightTracker, add = false, animation = Animations.EaseInOutQuad.create(0.15.seconds)).add()
            Rotate(this[1], PI, add = false, animation = Animations.EaseInOutQuad.create(0.15.seconds)).add()
        }
        Event.Focused.Lost then {
            if (dropdown.height != 0f) heightTracker = dropdown.height
            Resize(dropdown, height = 0f, add = false, animation = Animations.EaseInOutQuad.create(0.15.seconds)) {
                dropdown.enabled = false
                dropdown.renders = false
            }.add()
            Rotate(this[1], 0.0, add = false, animation = Animations.EaseInOutQuad.create(0.15.seconds)).add()
        }
        Event.Lifetime.Init then {
            dropdown.setup(polyUI)
            if (polyUI.master.initialized) polyUI.master.addChild(dropdown, reposition = false)
            dropdown.width += padding
            dropdown.recalculateChildren()
            this.width = dropdown.width
            this.height = fontSize + alignment.padding.y * 2f

            val totalSx = polyUI.size.x / polyUI.iSize.x
            val totalSy = polyUI.size.y / polyUI.iSize.y
            dropdown.rescale(totalSx, totalSy, position = true)
            dropdown.tryMakeScrolling()

            val first = dropdown[initial]
            (this[0] as Text).text = ((if (first.children!!.size == 2) first[1] else first[0]) as Text).text
        }
        Event.Mouse.Clicked(0) then {
            if (polyUI.inputManager.focused != null) {
                polyUI.unfocus()
                true
            } else false
        }
    }.namedId("Dropdown")
}

/**
 * Note that slider change events cannot be cancelled.
 */
fun Slider(at: Vec2? = null, min: Float = 0f, max: Float = 100f, initialValue: Float = 0f, ptrSize: Float = 24f, scaleFactor: Float = 2f, floating: Boolean = true, instant: Boolean = false): Drawable {
    val barHeight = ptrSize / 2.8f
    val barWidth = (max - min) * scaleFactor
    val size = Vec2(barWidth + ptrSize, ptrSize)
    val rad = (barHeight / 2f).radii()

    return Group(
        at = at,
        size = size,
        alignment = Align(Align.Main.Start, padding = Vec2.ZERO),
        children = arrayOf(
            Block(
                size = Vec2(barWidth, barHeight),
                alignment = Align(Align.Main.Start, padding = Vec2.ZERO),
                radii = rad,
                children = arrayOf(
                    Block(
                        size = Vec2(1f, barHeight),
                        radii = rad,
                    ).setPalette { brand.fg },
                ),
            ),
            Block(
                size = ptrSize.vec,
                radii = (ptrSize / 2f).radii(),
            ).setPalette { text.primary }.withStates().draggable(withY = false, onDrag = {
                val bar = this.parent!![0]
                val half = this.size.x / 2f
                this.x = this.x.coerceIn(bar.x - half, bar.x + bar.size.x - half)
                bar[0].width = x - bar.x + half
                if (instant) {
                    val progress = (this.x + half - bar.x) / size.x
                    var value = (max - min) * progress
                    if (!floating) value = value.toInt().toFloat()
                    accept(Event.Change.Number(value))
                }
            }).events {
                val op = object : DrawableOp.Animatable<Block>(self, Animations.EaseInOutQuad.create(0.15.seconds, 1f, 0f)) {
                    override fun apply(value: Float) {}

                    override fun unapply(value: Float) {
                        self.apply {
                            val maxRadius = this.size.x / 2.8f
                            val rads = maxRadius * value
                            val circleSize = rads * 2f
                            val offset = (this.size.x - circleSize) / 2f
                            renderer.rect(x + offset, y + offset, circleSize, circleSize, polyUI.colors.brand.fg.normal, rads)
                        }
                    }

                    override fun unapply(): Boolean {
                        super.unapply()
                        isFinished = false
                        // asm: don't finish by itself
                        return false
                    }
                }
                op.add()
                Event.Mouse.Exited then {
                    op.reverse()
                }
                Event.Mouse.Entered then {
                    op.reverse()
                }
            },
        ),
    ).apply {
        addEventHandler(Event.Mouse.Clicked(0)) {
            val bar = this[0]
            val ptr = this[1]
            val half = ptr.size.x / 2f
            ptr.x = it.x - half
            ptr.x = ptr.x.coerceIn(bar.x - half, bar.x + bar.size.x - half)
            bar[0].width = ptr.x - bar.x + half

            val progress = (ptr.x + half - bar.x) / size.x
            var value = (max - min) * progress
            if (!floating) value = value.toInt().toFloat()
            accept(Event.Change.Number(value))
        }
        addEventHandler(Event.Lifetime.PostInit) {
            val bar = this[0]
            val ptr = this[1]
            ptr.x = bar.x + barWidth * (initialValue / (max - min))
            bar.x += ptrSize / 2f
            bar[0].width = ptr.x - bar.x + (ptrSize / 2f)
        }
    }.namedId("Slider")
}

fun Checkbox(at: Vec2? = null, size: Float, state: Boolean = false): Drawable {
    return Block(
        at = at,
        size = Vec2(size, size),
        alignment = Align(padding = ((size - size / 1.25f) / 2f).vec),
        children = arrayOf(
            Image(
                image = PolyImage("check.svg"),
                size = (size / 1.25f).vec,
            ).disable(!state).also {
                if (!state) it.alpha = 0f
            },
        ),
    ).events {
        var checked = state
        Event.Mouse.Clicked(0) then {
            val ev = Event.Change.State(checked)
            accept(ev)
            if (ev.cancelled) return@then false
            val check = this[0]
            checked = !checked
            setPalette(if (checked) polyUI.colors.brand.fg else polyUI.colors.component.bg)
            if (checked) {
                check.enabled = true
                Fade(check, 1f, false, Animations.EaseInOutQuad.create(0.1.seconds)).add()
            } else {
                Fade(check, 0f, false, Animations.EaseInOutQuad.create(0.1.seconds)) {
                    check.enabled = false
                }.add()
            }
            false
        }
    }.withStates().namedId("Checkbox").also {
        if (state) it.setPalette { brand.fg }
    }
}

fun BoxedTextInput(
    image: PolyImage? = null,
    placeholder: String = "",
    initialValue: String = "",
    size: Vec2? = null,
): Drawable {
    return Block(
        size = size,
        children = arrayOf(
            if (image != null) Image(image) else null,
            TextInput(placeholder = placeholder, text = initialValue, wrap = size?.x ?: 0f),
        ),
    ).withBoarder()
}

/**
 * Spawn a menu at the mouse position.
 * @param polyUI an instance of PolyUI. If `null`, [openNow] must be `false`, or else an exception will be thrown.
 * @param openNow if `true`, the menu is opened immediately. else, call [PolyUI.focus] on the return value to open it.
 */
fun PopupMenu(vararg children: Drawable, size: Vec2? = null, align: Align = AlignDefault, polyUI: PolyUI?, openNow: Boolean = true, position: Point = Point.At): Block {
    val it = Block(
        focusable = true,
        size = size,
        alignment = align,
        children = children,
    ).events {
        Event.Focused.Gained then {
            when (position) {
                Point.At -> {
                    x = max(min(this.polyUI.mouseX, this.polyUI.size.x - this.size.x), 0f)
                    y = max(min(this.polyUI.mouseY, this.polyUI.size.y - this.size.y), 0f)
                }

                Point.Above -> {
                    x = max(min(this.polyUI.mouseX - (this.size.x / 2f), this.polyUI.size.x), 0f)
                    y = max(min(this.polyUI.mouseY - this.size.y - 12f, this.polyUI.size.y), 0f)
                }

                Point.Below -> {
                    x = max(min(this.polyUI.mouseX - (this.size.x / 2f), this.polyUI.size.x - this.size.x), 0f)
                    y = max(min(this.polyUI.mouseY + 12f, this.polyUI.size.y - this.size.y), 0f)
                }
            }
            Fade(this, 1f, false, Animations.EaseInOutQuad.create(0.2.seconds)).add()
        }
        Event.Focused.Lost then {
            Fade(this, 0f, false, Animations.EaseInOutQuad.create(0.2.seconds)) {
                this.polyUI.master.removeChild(this)
            }.add()
        }
    }
    it.alpha = 0f
    if (openNow) {
        polyUI ?: throw IllegalArgumentException("polyUI cannot be null if openNow is true")
        polyUI.master.addChild(it, reposition = false)
        polyUI.focus(it)
    }
    return it
}
