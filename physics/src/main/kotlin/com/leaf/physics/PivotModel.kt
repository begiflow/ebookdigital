package com.leaf.physics

/**
 * Binding-specific root constraint for [PageStrip] (docs/05-PHYSICS.md §2,
 * constraint 3; docs/03-RENDERER.md §4). The pivot is where a binding's
 * character lives: how the paper is allowed to leave the spine.
 */
sealed interface PivotModel {

    /**
     * Root particle pinned at the fold line (SEWN / STAPLED / GLUED).
     *
     * [rootBendStiffness] overrides the paper's own bending stiffness at the
     * root vertex; [foldAngle] is the rest fold there (radians, 0 = straight).
     * STAPLED = stiff + straight: the staple line resists folding, the page
     * turns as an arc and opens flat. GLUED = stiff + bent: glue clamps the
     * root into a permanent fold, so the near-spine region humps up and the
     * book never lies flat (docs/03 §4's V shape). Zeros = SEWN's soft
     * hinge — the exact reference behavior the golden traces are blessed on.
     */
    data class Hinge(
        val rootBendStiffness: Float = 0f,
        val foldAngle: Float = 0f,
    ) : PivotModel

    /**
     * SPIRAL: the root is the paper's punched-hole edge riding the wire
     * circle — distance from the coil axis at (x = 0, z = [centerZ]) is held
     * within [radius] ± [slack] (slack = holeRadius − wireRadius, the "loose
     * spiral page" slop, ~1–2 mm). Tangentially free: the hole slides around
     * the coil, so spiral pages travel a full 360° with no opening limit.
     */
    data class Wire(
        val centerZ: Float,
        val radius: Float,
        val slack: Float,
    ) : PivotModel
}
