/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.depth

import android.graphics.PointF

/** One step of a [Selection]: what a saved project keeps, in the order it was made. */
sealed interface SelectionOp {
    val add: Boolean

    /**
     * An object the model found from a tap or a box, added or taken away: its coverage, 0..1, on
     * the model's square grid ([SIDE]²) over the whole picture.
     */
    class Region(override val add: Boolean, val coverage: FloatArray) : SelectionOp

    /** Painted by hand, with a soft round brush of [radius] (fraction of the width). */
    class Brush(override val add: Boolean, val points: List<PointF>, val radius: Float) : SelectionOp

    /**
     * A finished matte, [width] x [height] over the whole picture, taken as is: the depth the lock
     * screen already had when the project started (made before the library existed, or kept by
     * SystemUI after Laras' data was cleared), so that work is not lost.
     */
    class Matte(override val add: Boolean, val width: Int, val height: Int, val coverage: FloatArray) : SelectionOp

    /**
     * What the depth build found for the picture (docs/specs/0013 §4): its alpha at the work
     * size, finer than the editing grid. Kept in the library as the picture's `build/`.
     */
    class Auto(val build: DepthBuild) : SelectionOp {
        override val add get() = true
    }

    /** The build's [index]th prop (1-based) taken away, or brought back when [add]. */
    class Part(override val add: Boolean, val index: Int, val build: DepthBuild) : SelectionOp

    companion object {
        /** Side of a [Region]'s grid: the segmentation model's mask. */
        const val SIDE = 256
    }
}
