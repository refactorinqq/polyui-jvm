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

package cc.polyfrost.polyui.component

import cc.polyfrost.polyui.PolyUI
import cc.polyfrost.polyui.PolyUI.Companion.INIT_COMPLETE
import cc.polyfrost.polyui.PolyUI.Companion.INIT_NOT_STARTED
import cc.polyfrost.polyui.PolyUI.Companion.INIT_SETUP
import cc.polyfrost.polyui.animate.Animation
import cc.polyfrost.polyui.animate.Animations
import cc.polyfrost.polyui.animate.KeyFrames
import cc.polyfrost.polyui.color.Color
import cc.polyfrost.polyui.color.Colors
import cc.polyfrost.polyui.event.Event
import cc.polyfrost.polyui.event.MousePressed
import cc.polyfrost.polyui.event.MouseReleased
import cc.polyfrost.polyui.layout.Layout
import cc.polyfrost.polyui.property.Properties
import cc.polyfrost.polyui.renderer.Renderer
import cc.polyfrost.polyui.unit.*
import cc.polyfrost.polyui.unit.Unit

/**
 * A component is a drawable object that can be interacted with. <br>
 *
 * It has a [properties] attached to it, which contains various pieces of
 * information about how this component should look, and its default responses
 * to event.
 */
abstract class Component @JvmOverloads constructor(
    properties: Properties? = null,
    /** position relative to this layout. */
    at: Point<Unit>,
    override var size: Size<Unit>? = null,
    rawResize: Boolean = false,
    acceptInput: Boolean = true,
    vararg events: Event.Handler
) : Drawable(at, rawResize, acceptInput) {

    @PublishedApi
    internal var p: Properties? = properties
        private set

    /** properties for the component. This is `open` so you can cast it, like so:
     * ```
     * final override val properties
     *     get() = super.properties as YourProperties
     * ```
     * @see Properties
     */
    open val properties get() = p!!

    /** the color of this component. */
    lateinit var color: Color.Animated

    /**
     * represents the hue value to return to when a chroma color is animated to something else, and is set back.
     */
    private var hueToReturnTo = 0f
    protected var autoSized = false
    protected var finishColorFunc: (Component.() -> kotlin.Unit)? = null
    override lateinit var layout: Layout
        internal set
    protected var keyframes: KeyFrames? = null

    /**
     * Note that this method will return the [x] if this method is called before the component is added to a layout.
     * @see [Drawable.trueX]
     */
    override fun trueX(): Float {
        var x = this.x
        if (!::layout.isInitialized) return x
        var parent: Layout? = this.layout
        while (parent != null) {
            x += parent.x
            parent = parent.layout
        }
        return x
    }

    /**
     * Note that this method will return the [y] if this method is called before the component is added to a layout.
     * @see [Drawable.trueY]
     */
    override fun trueY(): Float {
        var y = this.y
        if (!::layout.isInitialized) return y
        var parent: Layout? = this.layout
        while (parent != null) {
            y += parent.y
            parent = parent.layout
        }
        return y
    }

    init {
        addEventHandlers(*events)
    }

    override fun accept(event: Event): Boolean {
        if (super.accept(event)) return true
        return properties.eventHandlers[event]?.let { it(this, event) } == true
    }

    /**
     * Add event handlers to this drawable.
     * @since 0.18.5
     */
    fun addEventHandlers(vararg handlers: Event.Handler) {
        for (handler in handlers) {
            eventHandlers[handler.event] = handler.handler
        }
    }

    /**
     * Changes the color of the component to the specified color.
     * Supports animation and callback function on finish.
     *
     * If the color is a [Color.Gradient], this component's color will be changed to a gradient between the current color and the specified color.
     *
     * If the color is a [Color.Chroma], this component's color will be changed to a chroma color, keeping its current hue for consistency.
     * **Note:** This will look wierd if the current color is a gradient, and the brightness, saturation and alpha are NOT animated, so will change instantly.
     *
     * @param toColor The color to change the component to.
     * @param animation The type of animation to use.
     * @param durationNanos The duration of the animation in nanoseconds.
     * @param onFinish The callback function to execute when the color change animation finishes.
     * @since 0.19.1
     */
    open fun recolor(
        toColor: Color,
        animation: Animation.Type? = null,
        durationNanos: Long = 1L.seconds,
        onFinish: (Component.() -> kotlin.Unit)? = null
    ) {
        var finishFunc = onFinish
        when (toColor) {
            is Color.Gradient -> {
                if (color !is Color.Gradient || (color as Color.Gradient).type != toColor.type) {
                    color = Color.Gradient(color, color.clone(), toColor.type)
                }
                val c = color as Color.Gradient
                c.recolor(0, toColor[0], animation, durationNanos)
                c.recolor(1, toColor[1], animation, durationNanos)
            }
            is Color.Chroma -> {
                if (color !is Color.Chroma) {
                    color = Color.Chroma(toColor.speedNanos, toColor.brightness, toColor.saturation, toColor.alpha, color.hue)
                } else {
                    val c = color as Color.Chroma
                    c.speedNanos = toColor.speedNanos
                    c.brightness = toColor.brightness
                    c.saturation = toColor.saturation
                    c.alpha = toColor.alpha
                }
                color.hue = hueToReturnTo
            }
            else -> {
                if (color is Color.Gradient) {
                    val c = color as Color.Gradient
                    c.recolor(0, toColor, animation, durationNanos)
                    c.recolor(1, toColor, animation, durationNanos)
                    finishFunc = {
                        color = toColor.toAnimatable()
                    }
                } else {
                    if (color is Color.Chroma) {
                        color = (color as Color.Chroma).freeze()
                        hueToReturnTo = color.hue
                    }
                    color.recolor(toColor, animation, durationNanos)
                }
            }
        }
        wantRedraw()
        finishColorFunc = finishFunc
    }

    override fun onColorsChanged(colors: Colors) {
        properties.colors = colors
        recolor(properties.palette.normal)
    }

    override fun calculateBounds() {
        if (initStage == INIT_NOT_STARTED) throw IllegalStateException("${this.simpleName} has not been setup, but calculateBounds() was called!")
        if (size == null) {
            size = if (properties.size != null) {
                properties.size!!.clone()
            } else {
                autoSized = true
                calculateSize()
                    ?: throw UnsupportedOperationException("calculateSize() not implemented for ${this.simpleName}!")
            }
        }
        doDynamicSize()
        if (initStage != INIT_COMPLETE) {
            initStage = INIT_COMPLETE
            onInitComplete()
        }
    }

    override fun addOperation(drawableOp: DrawableOp, onFinish: (Drawable.() -> kotlin.Unit)?) {
        if (::layout.isInitialized) {
            wantRedraw()
        }
        operations.add(drawableOp to onFinish)
    }

    /** change the properties attached to this component.
     * @see Properties
     */
    fun setProperties(properties: Properties) {
        if (polyUI.settings.debug) PolyUI.LOGGER.info("{}'s properties set to {}", this.simpleName, properties)
        properties.colors = p!!.colors
        p = properties
        recolor(properties.palette.normal, Animations.Linear, 150L.milliseconds)
        wantRedraw()
    }

    override fun setup(renderer: Renderer, polyUI: PolyUI) {
        super.setup(renderer, polyUI)
        if (p == null) {
            p = layout.propertyManager.get(this)
        } else {
            p!!.colors = layout.colors
        }
        color = properties.palette.normal.toAnimatable()
        initStage = INIT_SETUP
    }

    override fun preRender(deltaTimeNanos: Long) {
        super.preRender(deltaTimeNanos)
        if (keyframes != null) {
            if (keyframes!!.update(deltaTimeNanos)) {
                keyframes = null
            } else {
                wantRedraw()
            }
        }

        if (color.update(deltaTimeNanos)) {
            finishColorFunc?.invoke(this)
            finishColorFunc = null
        }
        if (color.updating || color.alwaysUpdates) wantRedraw()
    }

    override fun canBeRemoved(): Boolean = super.canBeRemoved() && !color.updating && keyframes == null

    override fun toString(): String {
        return when (initStage) {
            INIT_COMPLETE -> "$simpleName(${trueX}x$trueY, ${width}x${height}${if (autoSized) " (auto)" else ""}${if (operations.isNotEmpty()) ", operating" else ""})"
            else -> simpleName
        }
    }

    /**
     * Make this component draggable.
     * @param consumesEvent weather beginning/ending dragging should cancel the corresponding mouse event
     */
    fun draggable(consumesEvent: Boolean = false): Component {
        var hovered = false
        var mx = 0f
        var my = 0f
        addEventHandlers(
            MousePressed(0) to {
                hovered = true
                mx = polyUI.eventManager.mouseX - x
                my = polyUI.eventManager.mouseY - y
                consumesEvent
            },
            MouseReleased(0) to {
                hovered = false
                consumesEvent
            }
        )
        addOperation(object : DrawableOp.Persistent(this) {
            override fun apply(renderer: Renderer) {
                if (hovered) {
                    if (!polyUI.eventManager.mouseDown) {
                        hovered = false
                        return
                    }
                    x = polyUI.eventManager.mouseX - mx
                    y = polyUI.eventManager.mouseY - my
                }
            }
        })
        return this
    }

    fun addKeyframes(k: KeyFrames) {
        keyframes = k
        wantRedraw()
    }

    /**
     * add a function that is called every [nanos] nanoseconds.
     * @since 0.17.1
     */
    fun every(nanos: Long, repeats: Int = 0, func: Component.() -> kotlin.Unit): Component {
        polyUI.every(nanos, repeats) {
            func(this)
        }
        return this
    }

    fun wantRedraw() {
        layout.needsRedraw = true
    }
}
