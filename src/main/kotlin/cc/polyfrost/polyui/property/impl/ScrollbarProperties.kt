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

package cc.polyfrost.polyui.property.impl

import cc.polyfrost.polyui.animate.Animations
import cc.polyfrost.polyui.color.Color
import cc.polyfrost.polyui.unit.seconds
import cc.polyfrost.polyui.utils.radii
import cc.polyfrost.polyui.utils.rgba

open class ScrollbarProperties : DefaultBlockProperties() {
    override val color: Color = rgba(0.5f, 0.5f, 0.5f, 0.5f)
    override val hoverColor = rgba(0.5f, 0.5f, 0.5f, 0.75f)
    override val cornerRadii: FloatArray = 2f.radii()
    override val pressedColor = rgba(0.5f, 0.5f, 0.5f, 0.8f)
    open val padding = 2f
    open val thickness = 4f
    open val showAnim: Animations? = Animations.EaseOutExpo
    open val showAnimDuration: Long = .5.seconds
    open val timeToHide = 2L.seconds
}
