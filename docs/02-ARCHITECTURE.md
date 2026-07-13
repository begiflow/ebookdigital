# LEAF — System Architecture

## 1. Shape of the system

Clean Architecture, but with one deliberate asymmetry: the **engine cluster** (`renderer`, `physics`, `filament`) is *not* a Clean Architecture "layer" — it is a reusable library with zero knowledge of the app, no Hilt, no Room, no Compose. The app's presentation layer *drives* the engine through a narrow API.

```
┌─────────────────────────── app ───────────────────────────┐
│  Compose UI · navigation · Hilt graph                     │
├───────────── feature modules (presentation) ──────────────┤
│  bookshelf · editor · camera(ui) · search                 │
├──────────────────────── domain ───────────────────────────┤
│  entities · use cases · repository interfaces  (pure KT)  │
├───────────────────────── data ────────────────────────────┤
│  repository impls · Room · file store · texture pipeline  │
└───────────────────────────────────────────────────────────┘
        │ drives (narrow API, no upward deps)
┌────── engine cluster (reusable, Android-min, no Hilt) ────┐
│  renderer  →  physics (pure Kotlin, no Android)           │
│  renderer  →  filament (thin wrapper: engine, materials)  │
└───────────────────────────────────────────────────────────┘
   core (Result, logging, dispatchers) · designsystem (Compose theme)
   benchmark (Macrobenchmark + engine microbench) · sample (engine-only demo app)
```

Dependency rules (enforced, see 06-MODULES):
- `physics` depends on nothing (pure Kotlin + kotlin-math). Unit-testable on JVM.
- `renderer` depends on `physics` + `filament` only. No Compose, no Hilt, no Room.
- `domain` is pure Kotlin. `data` implements `domain` interfaces.
- Feature modules see `domain` + `designsystem` + engine API; never `data` directly.
- Nothing depends on `app`.

## 2. Domain model

```kotlin
Notebook(id, title, profile: NotebookProfile, createdAt, shelfPosition,
         cover: CoverSet, sheets: List<SheetRef>)   // ordered

NotebookProfile(kind: ProfileKind, binding: Binding, paper: PaperSpec,
                coverType: CoverType, pageSize: AspectSpec)
Binding = SEWN | STAPLED | SPIRAL | GLUED
PaperSpec(weightGsm, stiffness, tint, translucency, grain: GrainKind)

CoverSet(front: Page, back: Page?, insideFront: Page?, insideBack: Page?)
// null → generated material from profile

Sheet(id, notebookId, index, front: Page, back: Page)   // back never null:
// real capture OR BlankBack(profile-generated). Locked decision 2026-07-11.

Page(id, original: ImageRef, edits: EditParams, texture: TextureRef,
     thumbnail: ImageRef, capturedAt)
EditParams(cropQuad, rotationDeg, brightness, contrast)  // non-destructive
```

Key invariants: sheet order is the single source of page order; `original` is write-once; `texture`/`thumbnail` are derived artifacts regenerable from `original + edits`.

## 3. Data layer

- **Room:** notebooks, sheets, pages, edit_params, shelf. Images are NOT in Room — file paths + content hashes only.
- **File store** (app-private):
  - `originals/{pageId}.jpg` — as-captured, write-once, never recompressed.
  - `textures/{pageId}.ktx2` — ≤2K, mipmapped, ASTC (ETC2 fallback), regenerated when edits change.
  - `thumbs/{pageId}.webp` — shelf/review sizes.
- **TexturePipeline** (data): original + EditParams → dewarp/crop/color (OpenCV) → downscale → mip chain → compress → KTX2. Runs on Dispatchers.Default pool at import and on edit; renderer only ever reads KTX2.

## 4. Driving the engine

```kotlin
interface NotebookRenderer {                     // implemented in :renderer
    fun attach(surfaceView: SurfaceView)          // Compose: AndroidView
    fun load(book: RenderBook)                    // engine-side model, mapped from domain
    fun onGesture(g: GestureEvent)                // down/move/up in view coords
    val state: StateFlow<BookState>               // Closed, Open(spreadIndex), Turning…
    fun setTextureProvider(p: TextureProvider)    // streaming callback, engine pulls
}
```

- Presentation maps `Notebook` → `RenderBook` (pure data: sheet count, profile physics params, texture ids). Engine pulls textures via `TextureProvider` — this keeps streaming policy in the engine (it knows camera + turn direction) while I/O stays in `data`.
- One `ViewModel` per screen; engine state observed as flow, never polled.

## 5. Threading model

| Thread | Work |
|---|---|
| Main | Compose UI, gesture capture (forwarded immediately) |
| Render thread (Choreographer-driven) | physics step(s) → mesh extrusion → vertex buffer upload → Filament render |
| IO pool | Room, file reads |
| Default pool | TexturePipeline (decode/dewarp/compress), KTX2 loads to staging |

Physics runs a **fixed 120 Hz timestep** with accumulator on the render thread (sim is µs-scale — no dedicated thread; avoids sync latency between sim and mesh). Texture uploads use Filament's async path; staged off-thread, committed on render thread.

## 6. DI

- Hilt in `app`, `data`, feature modules.
- Engine cluster: manual constructor injection; `app` builds the engine via a factory and binds it into Hilt. Rationale: `sample` and `benchmark` must run the engine without Hilt.

## 7. Error/degradation policy

- Texture not resident at draw time → prior mip level, never a gray page; log as a *performance defect*.
- Device below perf floor → degradation ladder: shadow resolution → mesh density (32×16 → 24×12) → 60 Hz cap. Physics feel never degrades.
- Corrupt derivative → regenerate from original; corrupt original → surfaced to user, never silently dropped.
