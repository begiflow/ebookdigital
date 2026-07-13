# LEAF — Product Requirements Document

Codename: LEAF · Product: Notebook Engine · Status: Draft for approval · 2026-07-11

## 1. Mission

Let users carry their **real physical notebooks** inside their phone. Not scans-as-files, not OCR, not PDF — the notebook itself: its cover, its thickness, its paper, the way its pages turn.

**Success metric:** a first-time user, within 30 seconds of opening a notebook, says *"I feel like I'm holding my real notebook."* Every requirement below is subordinate to this.

## 2. Non-goals

- Document management / file explorer UX
- OCR-as-product (OCR exists later only to index for search)
- Cloud sync, accounts, login
- Whole-notebook export/sharing (individual pages only)
- Text editing, annotation layers (v1)

## 3. Personas & core stories

| Persona | Notebook | Why realism matters |
|---|---|---|
| Pet owner | Dog vaccination booklet | Hands phone to the vet; the vet "flips through the booklet" |
| Parent | Child medical record | Trust — the record must look like *the* record |
| Traveler | Passport | Instant recognition of the physical object |
| Cook | Handwritten recipe book | Emotional artifact; grandmother's handwriting on real paper |
| Student | Spiral notebook | Flipping to find things beats searching |
| Diarist | Personal diary | Intimacy of the object itself |

Primary story: *"I own a physical notebook. I scan it once. From then on, opening it in LEAF feels like taking it off my shelf."*

## 4. Locked product decisions (2026-07-11)

| Decision | Choice |
|---|---|
| Sheet model | **Every sheet has front + back.** Scanning is reading-order; sheets auto-pair (page 2k−1 / 2k). "Mark blank" one-tap for empty backs → generated blank paper matching profile. |
| Bindings in v1 | **All four:** SEWN, STAPLED, SPIRAL, GLUED — behind a binding strategy interface, delivered in that order. |
| Physics | **1D PBD inextensible strip** extruded to render mesh (no 2D cloth). |
| Capture | **CameraX + OpenCV** custom pipeline (no ML Kit scanner UI). |

## 5. Functional requirements

### 5.1 Bookshelf (home)
- Home screen is a shelf, not a list. Each notebook shows real cover texture, true relative thickness, spine, title.
- Tap → notebook lifts off shelf and opens (continuous animation into the renderer scene; no screen "cut").
- Long-press → manage (rename, delete, profile, reorder shelf).

### 5.2 Notebook (the renderer scene)
- Closed state shows front cover; back cover and spine visible when rotated/zoomed.
- Cover opens interactively (drag) and by tap; inside covers are real captured surfaces.
- Pages: interactive drag from any edge/corner, momentum on release, natural settle forward or back, 2–3 pages may be in flight simultaneously, stack thickness on each side reflects position in the book.
- Lighting responds to page curvature; turning pages cast moving shadows on the pages beneath; page backs show faint front-side show-through per paper weight.
- Zoom (pinch) into a page up to original-scan fidelity; pan while zoomed; zoom out returns to book.
- Landscape supported; sound (velocity-dependent paper samples) + haptic tick at page release. Both ship with physics milestone, not "polish."

### 5.3 Notebook profiles
Profile = paper weight, stiffness, size/aspect, binding, cover type, opening angle, shadow character.

| Profile | Binding | Paper | Stiffness |
|---|---|---|---|
| Vaccination | STAPLED | thin coated | medium |
| Passport | SEWN | polycarbonate/paper mix | high |
| Medical | STAPLED/SEWN | thin | medium |
| Diary | SEWN | cream, midweight | low-med |
| Recipe | SEWN/GLUED | midweight | medium |
| Student | SPIRAL | lined, light | low |
| Warranty | GLUED/STAPLED | thin gloss | medium |
| Custom | user-set | user-set | user-set |

### 5.4 Capture
Flow: **Create notebook → capture front cover (mandatory) → name → profile (binding auto-suggested, editable) → continuous reading-order scanning → review → done.**
- Live quad detection with edge overlay; auto-capture on stability; manual shutter always available.
- Continuous mode: capture → auto-advance → next page; running page counter; "Mark blank" button.
- Perspective correction (dewarp) on every capture; original frame always preserved.
- Review: retake, manual crop corners, rotate, insert, delete, reorder.
- Also capture: back cover, inside covers (prompted, skippable → generated material).

### 5.5 Editor (post-capture, any time)
Crop, rotate, brightness, contrast, replace page, insert, delete, reorder. All edits **non-destructive**: parameter sets stored in DB, applied to derivatives; originals never touched.

### 5.6 Sharing
Individual pages only. Export the *edited derivative* (what the user sees) as JPEG/PNG via system share sheet. No notebook-level export.

### 5.7 Search (future, out of v1 scope)
Offline only. Gemma Nano + OCR strictly for indexing; results always open the page image.

## 6. Non-functional requirements

- **Performance:** 60 fps floor, 120 fps target on 120Hz devices, during interactive page drag with shadows on. 100-page notebooks with no visible texture pop-in during normal-speed flipping.
- **Offline:** 100% functionality without network. No login. No analytics that leave the device (v1: none at all).
- **Storage integrity:** originals are write-once. App uninstall warning; export-originals escape hatch (per page) is the only copy path.
- **Devices:** minSdk 29, target Android 15+. Graceful degradation ladder (shadow quality → mesh density → refresh rate) on weak GPUs; the *feel* (physics) never degrades.
- **App size:** renderer assets (materials, IBL, paper normal maps, sounds) budgeted ≤ 15 MB.

## 7. Acceptance of the success metric

Milestone 11 gate: hallway test with 5 people, real vaccination booklet scanned in. Question asked verbatim: "What does this feel like?" Pass = majority unprompted references to holding/flipping a real notebook.
