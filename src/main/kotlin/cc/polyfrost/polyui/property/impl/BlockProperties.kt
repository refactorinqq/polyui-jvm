/*
 * This file is part of PolyUI
 * PolyUI - Fast and lightweight UI framework
 * Copyright (C) 2023 Polyfrost and its contributors. All rights reserved.
 *   <https://polyfrost.cc> <https://github.com/Polyfrost/polui-jvm>
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package cc.polyfrost.polyui.property.impl

import cc.polyfrost.polyui.animate.Animations
import cc.polyfrost.polyui.color.Color
import cc.polyfrost.polyui.event.Events
import cc.polyfrost.polyui.property.Properties
import cc.polyfrost.polyui.property.State
import cc.polyfrost.polyui.unit.seconds
import cc.polyfrost.polyui.utils.radii

/**
 * @param cornerRadii The corner radii of the block. The order is top-left, top-right, bottom-right, bottom-left.
 * @param outlineThickness The thickness of this component. If you set it to something other than 0, it will become hollow.
 */
open class BlockProperties @JvmOverloads constructor(
    val ccolor: Color? = null,
    open val cornerRadii: FloatArray = 0f.radii(),
    open val outlineThickness: Float = 0f
) : Properties() {
    override val color get() = ccolor ?: colors.component.bg
    open val hoverColor get() = colors.component.bgHovered
    open val pressedColor get() = colors.component.bgPressed
    open val disabledColor get() = colors.component.bgDisabled
    open val pressedAnimation: Animations? = Animations.EaseOutExpo
    open val hoverAnimation: Animations? = Animations.EaseOutExpo
    open val pressedAnimationDuration: Long = 0.25.seconds
    open val hoverAnimationDuration: Long = 0.5.seconds
    override val padding: Float = 0F

    init {
        addEventHandlers(
            Events.MousePressed(0) to {
                recolor(pressedColor, pressedAnimation, pressedAnimationDuration)
                false
            },
            Events.MouseReleased(0) to {
                recolor(hoverColor, pressedAnimation, pressedAnimationDuration)
                false
            },
            Events.MouseEntered {
                recolor(hoverColor, hoverAnimation, hoverAnimationDuration)
                false
            },
            Events.MouseExited {
                recolor(properties.color, hoverAnimation, hoverAnimationDuration)
                false
            }
        )
    }
}

open class PrimaryBlockProperties @JvmOverloads constructor(
    cornerRadii: FloatArray = 0f.radii()
) : BlockProperties(null, cornerRadii) {
    override val color: Color get() = colors.brand.fg
    override val hoverColor: Color get() = colors.brand.fgHovered
    override val pressedColor: Color get() = colors.brand.fgPressed
    override val disabledColor: Color get() = colors.brand.fgDisabled
}

open class StateBlockProperties @JvmOverloads constructor(
    val state: State = State.Success,
    cornerRadii: FloatArray = 0f.radii()
) : BlockProperties(null, cornerRadii) {
    override val color: Color get() = when (state) {
        State.Success -> colors.state.success
        State.Warning -> colors.state.warning
        State.Danger -> colors.state.danger
    }
    override val hoverColor: Color get() = when (state) {
        State.Success -> colors.state.successHovered
        State.Warning -> colors.state.warningHovered
        State.Danger -> colors.state.dangerHovered
    }
    override val pressedColor: Color get() = when (state) {
        State.Success -> colors.state.successPressed
        State.Warning -> colors.state.warningPressed
        State.Danger -> colors.state.dangerPressed
    }
    override val disabledColor: Color get() = when (state) {
        State.Success -> colors.state.successDisabled
        State.Warning -> colors.state.warningDisabled
        State.Danger -> colors.state.dangerDisabled
    }
}
