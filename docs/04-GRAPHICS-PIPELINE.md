# LEAF — Graphics Pipeline (`:filament` + material design)

## 1. Filament integration

- `SurfaceView` hosted in Compose via `AndroidView`; Filament `UiHelper` manages the swapchain.
- Frame pacing: Choreographer callback → (physics, extrusion, upload) → `renderer.render(frameTimeNanos)`. Target display refresh; degrade per ladder in 02-ARCHITECTURE §7.
- Engine/Scene/View owned by `:filament` wrapper (`FilamentHost`), which exposes typed handles — `:renderer` never touches raw Filament statics. Materials compiled offline (`matc`) into module assets.

## 2. Materials

### 2.1 Paper (`paper.mat` — lit, custom)
- **baseColor:** the page KTX2 texture (sRGB).
- **normal:** two sources combined — (a) *geometric* normals from the deformed mesh (the curl), (b) tiled paper-grain normal map (per `GrainKind`: laid, woven, gloss; ~512², tiling ~6×). Grain gives paper its micro-response to light.
- **roughness:** high (0.75–0.9), map modulated by grain; gloss profiles lower.
- **Show-through (locked approach):** faint, blurred, horizontally mirrored sample of the *other side's* texture, weighted by `paper.translucency`, strongest where light is behind the page relative to camera (cheap wrap term). One extra texture sample; both sides of a sheet are always resident together.
- **Backface:** single mesh, two materials via two primitives (front/back faces) — each side of a sheet is its own texture (locked: backs always exist).
- Edge tint darkening at page borders (subtle AO-ish ring baked into material math, not texture).

### 2.2 Cover (`cover.mat`)
CoverType variants: cardboard, leatherette, gloss laminate, polycarbonate (passport). Captured cover as baseColor; type sets normal detail + roughness + clearCoat (laminate/passport use Filament clearCoat).

### 2.3 Page-block edges (`stack_edge.mat`)
Procedural striation normal + slight per-line tint noise on wedge side faces. Sells thickness at near-zero cost.

## 3. Lighting & shadow

- **Rig:** one key directional (top-left reading light, ~35° elevation) + low-res indoor IBL for ambient (baked KTX, ~1MB). No dynamic light position in v1; subtle key sway (~1°) tied to device tilt (optional toggle) makes paper grain shimmer — big realism win, trivial cost.
- **Cast shadow:** directional shadow map, PCF, tight ortho bounds around the book only → high effective resolution. In-flight pages are casters+receivers; resting spread and stacks receivers. The moving shadow of a turning page on the page below is a hero effect — tune bias to keep contact hardening near the spine.
- **Contact shadows** (screen-space) for the book-on-surface grounding and cover-slightly-open cracks.
- **Self-shadow of the curl** onto its own page comes free via the shadow map; validate acne/peter-panning across curl extremes in the benchmark scene.

## 4. Texture streaming

- Residency policy (engine-owned, pull via `TextureProvider`):
  - Full res + mips: current spread ±4 sheets (both faces — show-through needs the other side).
  - Low mip only: ±16. Beyond: nothing (thumbnails are UI-side, not engine).
  - Prefetch biased by turn direction and riffle velocity.
- KTX2/ASTC loaded off-thread to staging; GPU upload amortized ≤2 textures/frame on render thread; never a full-res decode on the render thread.
- Pool: page textures reused by rebinding (fixed budget ≈ 24 × 2K ASTC ≈ manageable on 29+ devices; exact budget set in benchmark).
- Fallback while streaming: lower mip of the same page (never gray, never wrong page).

## 5. Color & fidelity

- Everything sRGB end-to-end; no tone-mapping surprises — Filament color grading set to neutral, `Tonemapper` linear-ish preset so scanned paper white stays paper white. This is a scanner-fidelity product; punchy ACES defaults would falsify colors.
- Zoom path: engine at ≤2K derivative; beyond a zoom threshold the app overlays the *original* via a crossfaded 2D layer (Coil, tile-decoded). Full fidelity without blowing GPU memory.

## 6. Asset budget

IBL 1MB + grain normals (3×512² ASTC) + materials + paper sounds (~12 samples) + spiral wire mesh ≈ **≤ 8 MB** against the 15 MB PRD budget.
