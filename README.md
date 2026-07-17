# LEAF — Notebook Engine

Carry your **real physical notebooks** inside your phone. Not scans-as-files,
not PDFs — the notebook itself: its cover, its thickness, its paper, the way
its pages turn. See `docs/01-PRD.md` for the mission and the locked product
decisions.

## Status

All 16 roadmap milestones implemented (`docs/07-ROADMAP.md`); v1.0.0-rc1.
Remaining gates are device work: the M11 hallway test (PRD §7), the M12
<3-minute booklet scan, side-by-side color validation (M9), and the
degradation ladder across the device matrix (M16).

## Architecture

Clean Architecture around a reusable **engine cluster** — see
`docs/02-ARCHITECTURE.md` and `docs/06-MODULES.md`:

- `:physics` — pure-Kotlin XPBD paper simulation (inextensible 1D strips,
  binding pivots, cover hinge). Deterministic; golden-gesture tested.
- `:filament` — thin Filament wrapper (engine lifecycle, dynamic meshes,
  compiled materials, mip-level uploads).
- `:renderer` — book scene graph, page-turn lifecycle, corner-skew
  extrusion, texture streaming policy, sound + haptics, degradation ladder;
  drives everything through the narrow `NotebookRenderer` API.
- `:camera` — CameraX + OpenCV capture: quad detection, stability
  auto-capture, reading-order scan session, dewarp, review.
- `:data` — Room schema, write-once original store, texture pipeline
  (edits → mips → KTX derivative).
- `:domain` — entities + repository interface (pure Kotlin).
- `:bookshelf` / `:editor` — feature UIs (Compose).
- `:app` — composition root (Hilt), shelf → engine handoff, import flow.
- `:sample` / `:benchmark` — engine demo with tuning harness / frame gates.

## Building

```
./gradlew build          # assembles everything, runs JVM tests + Konsist rules
./gradlew :sample:installDebug   # engine-only demo (no Hilt/Room)
```

Materials are compiled offline with `matc` (Filament 1.72.0) into
`filament/src/main/assets/materials/`; sources live in
`filament/src/main/materials/`.

## Tests

~120 JVM tests cover the physics invariants (inextensibility, determinism,
settle truth table, golden gestures), extrusion math, streaming policy,
capture session, storage integrity (originals byte-identical after edits),
and the architecture rules (Konsist). Macrobenchmarks gate frame timing on
the shadowed page-turn workload.
