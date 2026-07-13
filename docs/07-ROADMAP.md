# LEAF — Implementation Roadmap

Rules: every milestone compiles, runs, and is demo-able; engine milestones are validated in `:sample` + `:benchmark` before any app UI exists. Each lands with architecture notes, reasoning, trade-offs, and the next milestone's entry criteria.

| M | Deliverable | Exit criteria |
|---|---|---|
| **1** | Project setup: all modules, convention plugins, version catalog, CI (build + JVM tests + Konsist rules), empty `:sample` renders a Filament clear color | `./gradlew build` green; dependency rules enforced and tested |
| **2** | Filament integration **with dynamic mesh**: FilamentHost, SurfaceView/UiHelper, Choreographer loop, double-buffered VertexBuffer updating a waving strip @ device refresh | 120fps waving strip in `:sample` on a 120Hz device; frame timing in `:benchmark` (this de-risks the #1 integration risk) |
| **3** | Static closed notebook: covers, spine (SEWN), stack wedges, camera rig, IBL + key light, rotate-to-inspect | Closed book with test cover texture looks like an object, not a model |
| **4** | Cover open/close: hinge physics, detent feel, inside covers, contact shadow grounding | Draggable cover with the "cover feel"; state machine Closed⇄Open |
| **5** | Open spread: resting pages, rest-pose curves, paper material v1 (baseColor+grain normal+roughness), stack wedges adjust with spread index (programmatic jump) | Page textures render color-faithful; wedge thickness reads correctly |
| **6** | Interactive page drag: touch ray → grab, PBD strip (SEWN pivot), extrusion, grab-point-under-finger invariant | Drag a page anywhere, paper point stays under finger, 120fps |
| **7** | Deformation quality: corner-skew, analytic normals, show-through material, backside textures (sheets), profile stiffness via tuning harness | Corner drags look right; diary vs passport feel different; tuning sliders export profiles |
| **8** | Full physics: momentum/flick, settle logic, multi-page flight (≤3), riffle mode, **sound + haptics** | Golden-gesture tests green; flipping feels alive; hallway-testable |
| **9** | Lighting polish: key sway w/ tilt, edge tint, neutral color pipeline validation vs. physical notebook side-by-side | Scanned page on screen matches paper under a lamp |
| **10** | Shadows: directional map (turning-page cast shadow), self-shadow across curl extremes, bias tuning, degradation ladder | Hero shadow effect at 120fps; ladder verified on a minSdk-29 device |
| **11** | Thickness + bindings complete: STAPLED, GLUED, SPIRAL (wire, holes, loose pivot); binding-specific rest poses & opening limits. **Success-metric gate:** hallway test (PRD §7) | All 4 bindings demo-able; majority of testers say "real notebook" unprompted |
| **12** | Camera: CameraX + OpenCV quad detection, continuous reading-order scan, auto-capture, Mark-blank, dewarp, review (retake/crop/reorder), cover capture flow | Scan a real 20-page booklet start→finish in < 3 min |
| **13** | Storage: Room schema, file store, TexturePipeline (KTX2/ASTC derivatives), originals write-once, streaming hooked to real data | 100-page notebook flips with zero pop-in; originals byte-identical after edits |
| **14** | Bookshelf: shelf home, covers/thickness/spines, open-animation handoff into engine scene, manage actions | Shelf → open book with no visual cut |
| **15** | Editor: non-destructive crop/rotate/brightness/contrast, insert/delete/reorder/replace, page sharing | Edits regenerate derivatives only; share exports edited page |
| **16** | Hardening: degradation ladder across device matrix, memory budget enforcement, uninstall warning, polish backlog from M11 gate | Release candidate |
| *17 (future)* | Search: OCR indexing + Gemma Nano, offline | — |

Sequencing notes:
- Bindings: SEWN is the reference through M3–M10; M11 adds the other three (this is where the +2–3 weeks from the all-bindings decision lands).
- Sound/haptics deliberately in M8, not polish — they change how M11's gate test scores.
- Camera (M12) starts only after the success-metric gate passes; there is no point scanning notebooks into a renderer that doesn't feel real.

**STOP point: this roadmap + docs 01–06 await approval before Milestone 1 begins.**
