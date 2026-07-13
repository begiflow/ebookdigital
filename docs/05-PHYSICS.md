# LEAF — Physics Engine Design (`:physics`, pure Kotlin)

Locked decision 2026-07-11: **1D PBD inextensible strip**, extruded to the render mesh. No 2D cloth.

## 1. Why (recorded rationale)

Paper is inextensible and bends as a near-developable surface. A mass-spring cloth fights this (stretch → jelly, or stiffness → instability). A position-based strip with hard distance constraints is physically honest for page turning, unconditionally stable, deterministic, and costs microseconds — enabling 120 Hz with 3 pages in flight. Trade-off accepted: no crumple/wrinkle (not wanted for preserved notebooks).

## 2. Model

Per in-flight page: `N = 16` particles across the page **width** (spine → free edge), unit-length normalized, mapped to page width `W` from profile.

```
State:  x[i], v[i]  (Vec3), invMass[i]  (particle 0..1 near spine: pivot-owned)
Params (from PaperSpec + BindingStrategy):
  stiffness   → bending compliance α_bend
  weightGsm   → mass, gravity response
  damping     → velocity damping + drag
  pivot       → binding-specific constraint on root particles
```

### Constraints (XPBD, solved in order, `iterations = 8`, `substeps = 2` @ fixed 120 Hz)
1. **Distance** (inextensibility): |x[i+1] − x[i]| = ℓ, compliance 0 (hard).
2. **Bending**: angle at each interior particle → rest angle (rest = slight profile curl), compliance α_bend from stiffness. This term IS the paper feel — per-profile tuning lives here.
3. **Pivot** (binding): SEWN/STAPLED/GLUED — root particle fixed at fold line, second particle constrained to a cone (opening limit; GLUED adds strong root bending resistance). SPIRAL — root constrained to wire circle with radial free play ε (the "loose spiral page" slop).
4. **Attachment** (during drag): grabbed particle(s) pulled toward finger target, compliance small but nonzero (finger can't tear paper through the stack); target from touch ray, velocity from event timestamps.
5. **Collision**: half-space per side (top of each stack wedge, height updates as stacks change) + cover planes when nearly closed. Position projection + friction-ish tangential damping.

### External forces
Gravity (scaled by weightGsm), air drag ∝ −v·|v| (gives the *flutter-settle* on release — important), release momentum inherited from attachment velocity.

## 3. Settle logic

On release: energy + lean direction decide target side; a weak "turn-completion" bias force (torque toward target) ensures pages never balance vertically forever. Settle completes when max particle speed < ε for k frames → page leaves flight pool, stacks adjust. Flick: attachment velocity at release ≥ threshold forces direction regardless of position.

## 4. Extrusion to render mesh (in `:renderer`, but specified here)

Strip (16 pts) → page mesh 32×16:
- Columns: Catmull-Rom through strip points, resampled to 32 (arc-length preserving — inextensibility must survive interpolation).
- Rows: ruled extrusion along page height; **corner grabs** add a linear skew: attachment row weight 1 → opposite edge weight λ(stiffness) — a stiff page lifts as a plate, floppy paper lets the corner lead. This is the one deliberate departure from pure developable surfaces and it's what makes corner-drags look right.
- Normals analytic from the curve tangent + skew, not averaged from triangles (stable lighting on the curl).

## 5. API (pure Kotlin, JVM-testable)

```kotlin
class PageStrip(params: PageParams, binding: PivotModel) {
    fun grab(u: Float, v: Float)
    fun drag(target: Vec3, velocity: Vec3)
    fun release()
    fun step(dt: Float)                      // fixed dt only
    val points: FloatArray                    // 16 × vec3, read by extruder
    val settled: SettleState                  // InFlight | SettledLeft | SettledRight
}
class BookPhysics(profile, binding) { /* flight-page pool, stack heights, cover hinge */ }
```

Cover hinge: single-DOF angular spring-damper with detent near closed (not a strip — covers are rigid).

## 6. Testing & tuning

- JVM unit tests: inextensibility invariant (Σ segment error < 1e-4 after 10k steps), determinism (bitwise across runs), settle-direction truth table (position × velocity grid), constraint convergence.
- Tuning harness in `:sample`: on-screen sliders for α_bend/damping/drag per profile → exports profile JSON. Paper feel is tuned by hand on device, committed as data.
- Golden-motion tests: recorded gesture traces replayed → point trajectories snapshot-compared (catches feel regressions in CI).
