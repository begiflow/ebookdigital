package com.leaf.renderer

/**
 * Physical feedback moments emitted by the engine (M8, docs/07 §M8: sound +
 * haptics are part of the feel, not polish). The scene emits; the renderer
 * host maps them to synthesized paper sounds and haptics via [FeelFeedback].
 */
enum class FeelEvent {
    PAGE_GRAB,
    PAGE_FLICK,
    PAGE_LAND_SOFT,
    PAGE_LAND_FLICK,
    RIFFLE_TICK,
    COVER_OPEN,
    COVER_CLOSE,
}
