package com.leaf.domain.model

/**
 * Physical identity of a notebook. Profiles drive paper physics, spine
 * geometry, rest pose, lighting response, and opening behavior
 * (docs/01-PRD.md §5.3).
 */
data class NotebookProfile(
    val kind: ProfileKind,
    val binding: Binding,
    val paper: PaperSpec,
    val coverType: CoverType,
    val pageAspectRatio: Float,
)

enum class ProfileKind {
    VACCINATION,
    PASSPORT,
    MEDICAL,
    DIARY,
    RECIPE,
    STUDENT,
    WARRANTY,
    CUSTOM,
}

/** Binding dominates how a notebook opens; each value maps to a BindingStrategy in the renderer. */
enum class Binding {
    SEWN,
    STAPLED,
    SPIRAL,
    GLUED,
}

enum class CoverType {
    CARDBOARD,
    LEATHERETTE,
    GLOSS_LAMINATE,
    POLYCARBONATE,
}

enum class GrainKind {
    LAID,
    WOVEN,
    GLOSS,
}

/**
 * Paper parameters feeding the physics strip and the paper material.
 * [stiffness] and [translucency] are normalized 0..1.
 */
data class PaperSpec(
    val weightGsm: Int,
    val stiffness: Float,
    val translucency: Float,
    val grain: GrainKind,
    val tintArgb: Int,
)
