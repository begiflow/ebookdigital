# LEAF — Rendering Engine Design (`:renderer`)

## 1. Scene composition

A loaded notebook is a small set of Filament entities:

```
BookRoot (transform: shelf→table→hand framing)
├─ CoverFront / CoverBack        rigid meshes, slight bow, thickness, rounded corners
├─ InsideCoverL / InsideCoverR   textured inner faces
├─ Spine                         binding-specific mesh (see §4)
├─ StackL / StackR               static "page block" wedges (see §2)
├─ RestingPageL / RestingPageR   the open spread: 2 lightly-curved static meshes
└─ FlightPage[0..2]              deforming meshes for in-flight pages (pooled)
```

Camera: perspective, ~35° vfov, orbit constrained to reading angles; framing states `Shelf → Approach → Reading → Zoom(page, focal point)` with critically-damped transitions. Zoom is camera movement toward the page plane (page stays 3D — edges/curl remain visible at the frame border, preserving object-ness).

## 2. The 100-page problem

Never 100 meshes, never 100 resident textures.

- **Geometry:** StackL/StackR are wedge blocks whose thickness = sheetsOnSide × sheetThickness(profile). Wedge side faces get a procedural page-edge material (fine horizontal striations — reads as stacked paper edges). Turning a page animates both wedge heights by one sheet — this is how progress through the book *reads physically*.
- **Resting spread:** the visible left/right pages are single meshes with a profile-dependent rest curve (near-flat for SPIRAL, bowed near spine for GLUED/SEWN).
- **Flight pages:** ≤3 pooled deforming meshes (32×16 grid, double-buffered vertex buffers). A page "becomes" a flight page on grab and returns to stack/resting on settle.
- **Textures:** priority-based residency (see 04-GRAPHICS §4).

## 3. Page turn lifecycle

```
Resting → Grabbed(touch ray hit → grab point on page)
        → Dragging(PBD strip follows attachment constraint)
        → Released(momentum) → Settling(falls to nearest side)
        → Resting (mesh returns to pool; stacks adjust; textures rebind)
```

- Grab from right edge/corner turns forward; left turns back; grab point is preserved under the finger (critical to the "touching paper" feel — the paper point under your finger never slides).
- Flick velocity threshold overrides position for settle direction.
- New grab while ≤2 pages in flight starts another flight page (real fast-flipping).
- Fast-flip mode: drag along the stack edge riffles pages (velocity-driven, lower-fidelity curl, thumb-riffle sound).

## 4. Binding strategies (locked: all four in v1)

`BindingStrategy` supplies: spine mesh, page pivot constraint params for physics, rest pose curve, opening-flat limit, cover hinge behavior.

| | SEWN (reference) | STAPLED | GLUED | SPIRAL |
|---|---|---|---|---|
| Spine mesh | rounded, cloth/board | flat, visible staples | flat squared, crack lines | wire coil, punched holes |
| Pivot | hinge line at fold, soft | same, tighter | hinge + strong root bend resistance | pivot around wire axis, ~2mm free play |
| Opens flat? | mostly | yes | no — V shape | fully, 360° allowed |
| Rest curve | mild bow | near flat | strong bow near spine | flat |
| Delivery order | 1 | 2 (variant of SEWN) | 3 | 4 (new geometry + constraint) |

SPIRAL notes: wire is static geometry (instanced torus segments); page mesh has a hole-punched left margin (texture alpha, not geometry); pivot constraint allows slight radial travel along the wire — this slop is what makes spiral pages feel loose.

## 5. Covers

- Rigid bodies with a single hinge DOF at the spine + slight panel flex (vertex-shader bow, not simulated).
- Cover open/close is interactive (drag) with a detent: resistance rises near closed, then a soft snap — the "cover feel."
- Cover thickness real geometry (rounded edge loop); front/back/inside faces separately textured from CoverSet.

## 6. Interaction mapping

- Touch → ray from camera → intersect current page mesh (post-deform, CPU-side, coarse grid) → grab (u,v) on page → physics attachment target updated per move event, with event timestamps used for velocity estimation.
- Pinch anywhere → Zoom state (gesture arbitration: page drag owns single-finger, zoom owns two-finger; a started drag is not stolen).
- Two-finger twist while closed → rotate book to inspect spine/back.

## 7. Engine state machine

`Closed → CoverOpening → Open(spread k) ⇄ Turning(k, dir, pagesInFlight) → Zoom(page) → …`
Exposed as `StateFlow<BookState>` for the app (e.g., editor entry allowed in Open/Zoom only).

## 8. Performance budget (per frame @120Hz = 8.3ms)

| | budget |
|---|---|
| Physics (≤3 strips × substeps) | 0.15 ms |
| Mesh extrusion + normals (≤3 × 32×16) | 0.35 ms |
| VB upload (double-buffered) | 0.3 ms |
| Filament render (shadows on) | ≤5 ms |
| Headroom | ~2.5 ms |
