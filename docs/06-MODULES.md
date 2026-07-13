# LEAF — Module Structure

## Graph

```
app ──────────────► bookshelf, editor, camera, search (features)
features ─────────► domain, designsystem, core, renderer(API)
data ─────────────► domain, core, storage-files, (OpenCV)
renderer ─────────► physics, filament, core
physics ──────────► (nothing; pure Kotlin)
filament ─────────► core (thin Filament wrapper + compiled materials)
benchmark ────────► renderer, physics, sample scenes
sample ───────────► renderer, physics, filament (NO hilt/room/domain)
```

## Modules

| Module | Type | Responsibility | Key API / notes |
|---|---|---|---|
| `:app` | android app | Hilt graph, navigation, engine factory binding | thin |
| `:core` | kotlin | Result, logging, dispatchers, math aliases | no Android deps beyond annotations |
| `:designsystem` | android lib | Compose theme, shelf-wood/paper design tokens, shared components | |
| `:domain` | kotlin | Notebook/Sheet/Page entities, use cases, repo interfaces | pure |
| `:data` | android lib | Room, file store, TexturePipeline (OpenCV), repo impls | owns originals/textures/thumbs dirs |
| `:physics` | kotlin | PBD strips, pivots, cover hinge, book physics | JVM unit-tested; see 05 |
| `:filament` | android lib | FilamentHost, material registry, KTX loaders, upload queue | only module importing Filament |
| `:renderer` | android lib | Scene graph, page lifecycle, extrusion, streaming policy, gestures→physics, `NotebookRenderer` API | see 03 |
| `:camera` | android lib | CameraX + OpenCV quad detect, capture flow UI + controller | feature module (UI included) |
| `:bookshelf` | android lib | Shelf screen, open-animation handoff to renderer | |
| `:editor` | android lib | Non-destructive edit UI → EditParams → pipeline regen | |
| `:search` | android lib | v1: stub screen. Future: Gemma Nano indexer | keep skeleton only |
| `:benchmark` | android test | Macrobenchmark (frame timing on turn gesture traces), physics microbench | gates every engine milestone |
| `:sample` | android app | Engine-only demo: synthetic book, tuning sliders, golden-gesture player | runs without Hilt/Room |

## Conventions

- Version catalog (`libs.versions.toml`); convention plugins in `build-logic/` for android-lib/kotlin-lib/compose-lib.
- Module dependency rules enforced (Gradle `checkDependencyRules` or Konsist tests): `physics` imports nothing Android; `renderer` never imports Compose/Hilt/Room; features never import `data`.
- Engine cluster publishes no Hilt modules; `:app` provides `NotebookRendererFactory` via `@Provides`.
