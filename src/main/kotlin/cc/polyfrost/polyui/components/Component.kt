package cc.polyfrost.polyui.components

import cc.polyfrost.polyui.animate.Animation
import cc.polyfrost.polyui.color.Color
import cc.polyfrost.polyui.events.ComponentEvent
import cc.polyfrost.polyui.events.ComponentEvents
import cc.polyfrost.polyui.layouts.Layout
import cc.polyfrost.polyui.properties.Properties
import cc.polyfrost.polyui.renderer.Renderer
import cc.polyfrost.polyui.units.Box
import cc.polyfrost.polyui.units.Point
import cc.polyfrost.polyui.units.Size
import cc.polyfrost.polyui.units.Unit
import cc.polyfrost.polyui.utils.Clock
import cc.polyfrost.polyui.utils.forEachNoAlloc
import cc.polyfrost.polyui.utils.removeIfNoAlloc
import java.util.*

/** A component is a drawable object that can be interacted with. <br>
 * It has a [properties] attached to it, which contains various pieces of information about how this component should look, and its default responses to events. <br>*/
abstract class Component(
    val properties: Properties,
    /** position relative to this layout. */
    override val at: Point<Unit>,
    override var sized: Size<Unit>? = null,
    vararg events: ComponentEvent.Handler
) : Drawable {
    override var onAdded: (Drawable.() -> kotlin.Unit)? = null
    override var onRemoved: (Drawable.() -> kotlin.Unit)? = null
    private val eventHandlers: EnumMap<ComponentEvent.Type, Component.() -> kotlin.Unit> =
        EnumMap(ComponentEvent.Type::class.java)

    private val animations: ArrayList<Animation> = ArrayList()
    private val transforms: ArrayList<TransformOp> = ArrayList()
    var scaleX: Float = 1F
    var scaleY: Float = 1F

    /** current rotation of this component (radians). */
    var rotation: Double = 0.0
    val color: Color.Mutable = properties.color.toMutable()
    private val clock = Clock()
    final override lateinit var renderer: Renderer
    final override lateinit var layout: Layout
    private lateinit var boundingBox: Box<Unit>

    /** weather or not the mouse is currently over this component. DO NOT modify this value. It is managed automatically by [cc.polyfrost.polyui.events.EventManager]. */
    var mouseOver = false
        internal set

    init {
        events.forEach {
            addEventHandler(it.type, it.handler)
        }
    }


    /**
     * Called when an event is received by this component.
     *
     * **make sure to call super [Component.accept]!**
     */
    open fun accept(event: ComponentEvent) {
        properties.eventHandlers[event.type]?.let { it(this) }
        when (event.type) {
            ComponentEvents.Added -> {
                onAdded?.invoke(this)
            }

            ComponentEvents.Removed -> {
                onRemoved?.invoke(this)
            }

            else -> eventHandlers[event.type]?.let { it(this) }
        }
    }

    fun addEventHandler(type: ComponentEvent.Type, handler: Component.() -> kotlin.Unit) {
        when (type) {
            ComponentEvents.Added -> {
                onAdded = handler as Drawable.() -> kotlin.Unit
            }

            ComponentEvents.Removed -> {
                onRemoved = handler as Drawable.() -> kotlin.Unit
            }

            else -> this.eventHandlers[type] = handler
        }
    }


    /** Add a [TransformOp] to this component. */
    open fun transform(transformOp: TransformOp) {
        transforms.add(transformOp)
    }

    /**
     * Scale this component by the given amount, in the X and Y dimensions.
     *
     * Please note that this ignores all bounds, and will simply scale this component, meaning that it can be clipped by its layout, and overlap nearby components.
     */
    fun scale(byX: Float, byY: Float, animation: Animation.Type? = null, durationMillis: Long = 0L) {
        transform(TransformOp.Scale(byX, byY, this, animation, durationMillis))
    }

    /**
     * Rotate this component by the given amount, in degrees.
     *
     * Please note that this ignores all bounds, and will simply scale this component, meaning that it can be clipped by its layout, and overlap nearby components.
     */
    fun rotate(degrees: Double, animation: Animation.Type? = null, durationMillis: Long = 0L) {
        transform(TransformOp.Rotate(Math.toRadians(degrees), this, animation, durationMillis))
    }

    /**
     * move this component by the given amount, in the X and Y dimensions.
     *
     * Please note that this ignores all bounds, and will simply scale this component, meaning that it can be clipped by its layout, and overlap nearby components.
     */
    // todo this might change ^
    fun move(byX: Float, byY: Float, animation: Animation.Type? = null, durationMillis: Long = 0L) {
        transform(TransformOp.Translate(byX, byY, this, animation, durationMillis))
    }

    open fun animate(animation: Animation) {
        animations.add(animation)
    }

    open fun recolor(toColor: Color, animation: Animation.Type, durationMillis: Long) {
        if (color.equals(toColor)) return
        color.recolor(toColor, animation, durationMillis)
    }

    override fun calculateBounds() {
        if (sized == null) sized =
            getSize() ?: throw UnsupportedOperationException("getSize() not implemented for ${this::class.simpleName}!")
        boundingBox = Box(at, sized!!).expand(properties.padding)
    }

    fun wantRedraw() {
        layout.needsRedraw = true
    }

    fun wantRecalculation() {
        layout.needsRecalculation = true
    }

    /**
     * Called before rendering.
     *
     * **make sure to call super [Component.preRender]!**
     */
    override fun preRender() {
        animations.removeIfNoAlloc { it.finished }
        transforms.removeIfNoAlloc { it.finished }

        val delta = clock.getDelta()
        animations.forEachNoAlloc {
            it.update(delta)
            if (!it.finished) wantRedraw()
        }
        transforms.forEachNoAlloc {
            it.update(delta)
            if (!it.finished) wantRedraw()
            it.apply(renderer)
        }
        if (scaleX != 1f && scaleY != 1f) renderer.scale(scaleX, scaleY)
        if (rotation != 0.0) renderer.rotate(rotation)
        color.update(delta)
    }

    /**
     * Called after rendering.
     *
     * **make sure to call super [Component.postRender]!**
     */
    override fun postRender() {
        if (scaleX != 1f && scaleY != 1f) renderer.scale(-scaleX, -scaleY)
        if (rotation != 0.0) renderer.rotate(-rotation)
    }

    override fun canBeRemoved(): Boolean {
        return animations.size == 0 && transforms.size == 0 && !color.isRecoloring()
    }


}


