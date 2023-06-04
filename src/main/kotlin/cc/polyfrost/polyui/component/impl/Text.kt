/*
 * This file is part of PolyUI
 * PolyUI - Fast and lightweight UI framework
 * Copyright (C) 2023 Polyfrost and its contributors. All rights reserved.
 *   <https://polyfrost.cc> <https://github.com/Polyfrost/polui-jvm>
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package cc.polyfrost.polyui.component.impl

import cc.polyfrost.polyui.PolyUI
import cc.polyfrost.polyui.component.Component
import cc.polyfrost.polyui.event.Events
import cc.polyfrost.polyui.input.PolyText
import cc.polyfrost.polyui.input.PolyTranslator.Companion.localised
import cc.polyfrost.polyui.property.Properties
import cc.polyfrost.polyui.property.impl.TextProperties
import cc.polyfrost.polyui.renderer.Renderer
import cc.polyfrost.polyui.renderer.data.MultilineText
import cc.polyfrost.polyui.renderer.data.SingleText
import cc.polyfrost.polyui.renderer.data.Text
import cc.polyfrost.polyui.unit.*
import cc.polyfrost.polyui.unit.Unit
import kotlin.math.floor

open class Text @JvmOverloads constructor(
    properties: Properties? = null,
    private val txt: PolyText,
    at: Vec2<Unit>,
    val sized: Size<Unit>? = null,
    fontSize: Unit.Pixel? = null,
    val textAlign: TextAlign = TextAlign.Left,
    acceptInput: Boolean = false,
    vararg events: Events.Handler
) : Component(properties, at, null, acceptInput, *events) {
    /** Internally [txt] is stored as a [PolyText] object, which supports localization and object substitution */
    @JvmOverloads
    constructor(
        txt: String,
        at: Vec2<Unit>,
        size: Size<Unit>? = null,
        fontSize: Unit.Pixel? = null,
        textAlign: TextAlign = TextAlign.Left,
        acceptInput: Boolean = false,
        vararg events: Events.Handler
    ) : this(null, txt.localised(), at, size, fontSize, textAlign, acceptInput, *events)

    constructor(properties: Properties? = null, text: PolyText, fontSize: Unit.Pixel, at: Vec2<Unit>) :
        this(properties, text, at, null, fontSize)

    final override val properties: TextProperties
        get() = super.properties as TextProperties
    val fontSize = fontSize ?: this.properties.fontSize
    internal lateinit var str: Text
    var scaleX: Float = 1f
    val lines get() = str.lines
    val full get() = str.full
    val font get() = this.properties.font

    val textOffset get() = (str as? SingleText)?.textOffset ?: x

    override var size: Size<Unit>?
        get() = str.size
        set(value) {
            if (value != null) {
                str.size.a.px = value.a.px
                str.size.b.px = value.b.px
            }
        }
    var text
        get() = str.text
        set(value) {
            str.text = value
            str.calculate(renderer)
            if (autoSized) size = str.size
        }

    override fun render() {
        if (scaleX != 1f) renderer.scale(scaleX, 1f)
        str.render(at.a.px, at.b.px, color)
    }

    operator fun get(index: Int) = str[index]

    override fun reset() = str.text.reset()

    fun getByCharIndex(index: Int) = str.getByCharIndex(index)

    override fun rescale(scaleX: Float, scaleY: Float) {
        super.rescale(scaleX, scaleY)
        str.fontSize *= scaleY
        this.scaleX *= scaleX - (scaleY - 1f)
    }

    override fun setup(renderer: Renderer, polyui: PolyUI) {
        super.setup(renderer, polyui)
        str = if (floor((sized?.height ?: 0f) / this.fontSize.px).toInt() > 1) {
            MultilineText(txt, this.properties.font, this.fontSize.px, textAlign, sized ?: origin)
        } else {
            SingleText(txt, this.properties.font, this.fontSize.px, textAlign, sized ?: origin)
        }
        str.text.polyTranslator = polyui.translator
        str.calculate(renderer)
        size = str.size
    }

    override fun calculateSize(): Vec2<Unit>? {
        str.calculate(renderer)
        return size
    }
}
