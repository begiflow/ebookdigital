package com.leaf.renderer.scene

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.opengl.Matrix
import com.google.android.filament.EntityManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import com.google.android.filament.View
import com.leaf.filament.FilamentHost
import com.leaf.filament.MeshData
import com.leaf.filament.StaticMesh
import com.leaf.filament.Textures
import com.leaf.physics.PageParams
import com.leaf.physics.PageStrip
import com.leaf.renderer.FeelEvent
import com.leaf.renderer.PaperTuning
import com.leaf.renderer.RenderBook
import com.leaf.renderer.RenderGrain
import com.leaf.renderer.geometry.BookGeometry
import com.leaf.renderer.material.PaperGrain
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The notebook as Filament entities (docs/03-RENDERER.md §1). M5 state:
 * hinged front cover; page block split into left/right stack wedges that
 * follow the current spread; curved resting pages with the paper material.
 * The left stack and left page are parented to the cover — turned pages ride
 * it. Spread changes are programmatic jumps until page physics lands in M6.
 */
class BookScene(
    private val host: FilamentHost,
    private val book: RenderBook,
    assets: AssetManager,
) {
    var spread: Int = 0
        private set

    val spreadCount: Int get() = book.sheetCount
    val pageHeightMeters: Float get() = pageH

    private val engine = host.engine
    private val transformManager = engine.transformManager
    private val entityManager = EntityManager.get()

    private val staticMeshes = ArrayList<StaticMesh>(8)
    private val coverRoot: Int
    private val coverParentInstance: Int
    private val hingeX = -book.widthMeters / 2f
    private val hingeZ: Float
    private val transform = FloatArray(16)

    // Spread-dependent parts, rebuilt/reshaped by applySpread().
    private var leftWedge: StaticMesh? = null
    private var rightWedge: StaticMesh? = null
    private val leftPage: RestingPage
    private val rightPage: RestingPage
    private var leftPageVisible = false
    private var rightPageVisible = false

    private val leftPaperInstance: MaterialInstance
    private val rightPaperInstance: MaterialInstance
    private val blockInstance: MaterialInstance
    private val paperMaterial: com.google.android.filament.Material
    private val grainTexture: Texture

    private val pageTextures = HashMap<Int, Texture>()
    private val blankPage: Bitmap by lazy { blankPaper() }

    // ---- Flight pool (M6 single page -> M8 pool, docs/03-RENDERER.md §3) ----
    private class TurnFlight(
        val sheet: Int,
        val forward: Boolean,
        val strip: PageStrip,
        val page: FlightPage,
        val front: MaterialInstance,
        val back: MaterialInstance,
        /** Rest direction of this turn's source side (M7 corner skew). */
        val restAngle: Float,
        var grabV: Float,
        var skewStrength: Float = 0f,
        var flicked: Boolean = false,
    )

    private val flights = ArrayList<TurnFlight>(MAX_FLIGHTS)

    /** The flight under the finger; drags apply to it only. */
    private var active: TurnFlight? = null
    private var ledger = TurnLedger(book.sheetCount, 0)

    /** Physical feedback hook (M8): host maps events to sound + haptics. */
    var onFeel: ((FeelEvent) -> Unit)? = null

    val isTurning: Boolean get() = flights.isNotEmpty()
    val pagesInFlight: Int get() = flights.size
    val turnDirectionForward: Boolean
        get() = active?.forward ?: (flights.lastOrNull()?.forward ?: true)

    /** Live paper feel; physics params apply from the next grab. */
    var paperTuning: PaperTuning = PaperTuning.fromBook(book)
        private set

    // Geometry constants derived from the book.
    private val w = book.widthMeters
    private val h = book.heightMeters
    private val coverT = book.coverThicknessMeters
    private val halfT = book.totalThicknessMeters / 2f
    private val pageW = w * (1f - BLOCK_INSET) - GUTTER
    private val pageH = h * (1f - BLOCK_INSET)
    private val backInnerZ = -halfT + coverT
    private val spineX = -w / 2f

    /**
     * Cover rest pose: past PI so the opened board slopes down and its free
     * edge lands on the desk — a fixed hinge at the spine top means the board
     * must descend 2*halfT over its width (docs/03-RENDERER.md §5).
     */
    val coverOpenRestAngle: Float =
        (PI + atan(((halfT - coverT / 2f) + halfT + DESK_GAP) / w)).toFloat()

    private val leftSlope = -tan(coverOpenRestAngle - PI.toFloat())

    init {
        val coverMaterial = host.loadMaterial(
            assets.open("materials/cover.filamat").use { it.readBytes() },
        )
        val plainMaterial = host.loadMaterial(
            assets.open("materials/ribbon.filamat").use { it.readBytes() },
        )
        paperMaterial = host.loadMaterial(
            assets.open("materials/paper.filamat").use { it.readBytes() },
        )

        // ---- Materials ----
        val frontArt = book.frontCover ?: flatBoard()
        val frontInstance = coverMaterial.createInstance().apply {
            setParameter("roughness", 0.62f)
        }
        Textures.bind(frontInstance, "baseColorMap", Textures.fromBitmap(engine, frontArt))

        val backInstance = if (book.backCover != null) {
            coverMaterial.createInstance().apply {
                setParameter("roughness", 0.62f)
                Textures.bind(this, "baseColorMap", Textures.fromBitmap(engine, book.backCover!!))
            }
        } else {
            frontInstance
        }

        blockInstance = plainMaterial.createInstance().apply {
            setParameter("baseColor", 0.91f, 0.88f, 0.81f)
            setParameter("roughness", 0.92f)
        }
        val endpaperInstance = plainMaterial.createInstance().apply {
            setParameter("baseColor", 0.90f, 0.86f, 0.77f)
            setParameter("roughness", 0.95f)
        }
        val deskInstance = plainMaterial.createInstance().apply {
            setParameter("baseColor", 0.72f, 0.66f, 0.57f)
            setParameter("roughness", 0.96f)
        }

        grainTexture = Textures.fromBitmap(
            engine,
            when (book.grain) {
                RenderGrain.LAID -> PaperGrain.laidNormalMap()
                RenderGrain.WOVEN -> PaperGrain.wovenNormalMap()
                RenderGrain.GLOSS -> PaperGrain.glossNormalMap()
            },
            srgb = false,
        )
        leftPaperInstance = newPaperInstance(paperMaterial, grainTexture)
        rightPaperInstance = newPaperInstance(paperMaterial, grainTexture)

        hingeZ = halfT - coverT / 2f

        // ---- Static parts ----
        addStatic(BookGeometry.board(w, h, coverT, 0f, 0f, -halfT + coverT / 2f), backInstance)
        // Back endpaper: revealed at the last spread.
        addStatic(
            BookGeometry.panel(w * 0.995f, h * 0.995f, 0f, 0f, backInnerZ + Z_EPS, facingPositiveZ = true),
            endpaperInstance,
        )
        addStatic(BookGeometry.sewnSpine(h, halfT, -w / 2f), frontInstance)
        addStatic(
            BookGeometry.board(DESK_W, DESK_H, 0.002f, 0.05f, 0f, -halfT - 0.0045f),
            deskInstance,
            castShadows = false,
        )

        // ---- Hinged front cover (local space: hinge at x=0, z=0) ----
        coverRoot = entityManager.create()
        transformManager.create(coverRoot)
        coverParentInstance = transformManager.getInstance(coverRoot)

        val outer = StaticMesh(
            engine,
            BookGeometry.board(w, h, coverT, w / 2f, 0f, 0f, includeBackFace = false),
            frontInstance,
        )
        val inner = StaticMesh(
            engine,
            BookGeometry.panel(w * 0.995f, h * 0.995f, w / 2f, 0f, -coverT / 2f - Z_EPS, facingPositiveZ = false),
            endpaperInstance,
        )
        for (mesh in listOf(outer, inner)) {
            staticMeshes.add(mesh)
            host.scene.addEntity(mesh.entity)
            transformManager.create(mesh.entity, coverParentInstance, IDENTITY)
        }

        // ---- Resting pages (persistent; reshaped per spread) ----
        rightPage = RestingPage(host, rightPaperInstance, facingPositiveZ = true)
        leftPage = RestingPage(host, leftPaperInstance, facingPositiveZ = false)
        transformManager.create(leftPage.entity, coverParentInstance, IDENTITY)

        setSpread(0)
        setCoverAngle(0f)

        // ---- Light rig ----
        host.addDirectionalLight(1f, 0.98f, 0.94f, 90_000f, 0.45f, -0.75f, -0.5f, castShadows = true)
        host.setAmbientLight(
            22_000f,
            floatArrayOf(
                0.85f, 0.82f, 0.76f,
                0.22f, 0.21f, 0.19f,
                0.05f, 0.05f, 0.05f, 0.03f, 0.03f, 0.03f,
                0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            ),
        )
        host.view.ambientOcclusionOptions = View.AmbientOcclusionOptions().apply {
            enabled = true
            radius = 0.05f
            intensity = 1.2f
        }
        host.camera.setExposure(16f, 1f / 125f, 100f)
        host.setClearColor(0.94f, 0.92f, 0.87f)
    }

    /** Jumps to sheet boundary [k]: k sheets on the left, rest on the right. */
    fun setSpread(k: Int) {
        abortAllTurns()
        spread = k.coerceIn(0, spreadCount)
        ledger = TurnLedger(spreadCount, spread)
        refreshRest()
    }

    // ================= Turn lifecycle (M6, docs/03-RENDERER.md §3) =========

    /** Sim-space (spread-space) helpers: origin at (spineX, backInnerZ). */
    fun worldToSimX(worldX: Float) = worldX - spineX
    fun worldToSimZ(worldZ: Float) = worldZ - backInnerZ

    /** World z of the right page's pick plane. */
    fun rightPageWorldZ() = backInnerZ + ledger.rightCount * book.sheetThicknessMeters

    /** World z of the left page's pick plane (at the spine; plane slopes down). */
    fun leftPageWorldZ() = backInnerZ + leftSpineTopSim(ledger.leftCount)

    /** The stack's fore-edge strip just beyond the right page — the riffle zone. */
    fun riffleZoneContains(worldX: Float, worldY: Float): Boolean {
        if (kotlin.math.abs(worldY) > pageH / 2f) return false
        val edge = spineX + GUTTER + pageW
        return worldX in edge..(edge + pageW * RIFFLE_ZONE_FRACTION)
    }

    fun pageRectContains(worldX: Float, worldY: Float, rightSide: Boolean): Boolean {
        if (kotlin.math.abs(worldY) > pageH / 2f) return false
        return if (rightSide) {
            worldX in (spineX + GUTTER)..(spineX + GUTTER + pageW)
        } else {
            worldX in (spineX - pageW)..(spineX - GUTTER)
        }
    }

    /**
     * Starts turning the top sheet of a side. [u] = grab fraction along the
     * width from the spine, [v] = grab fraction along the height (0 = bottom
     * edge; drives the corner skew). Returns false at the ends of the book or
     * with [MAX_FLIGHTS] pages already airborne.
     */
    fun beginTurn(forward: Boolean, u: Float, v: Float = 0.5f, quiet: Boolean = false): Boolean {
        if (flights.size >= MAX_FLIGHTS) return false
        val sheet = (if (forward) ledger.grabForward() else ledger.grabBackward()) ?: return false
        refreshRest()

        val s = PageStrip(
            PageParams(
                widthMeters = pageW,
                stiffness = paperTuning.stiffness,
                damping = paperTuning.damping,
                airDrag = paperTuning.airDrag,
            ),
        )
        // Floors for the whole pool refresh below; resetFlat samples the
        // surface, so seed this strip's floors first.
        applySurfaces(s, sheet)
        s.resetFlat(onRight = forward, bowHeight = BOW_HEIGHT)
        s.grab(u)

        // Flight faces: sheet front / back, mapped per the domain sheet model.
        // Each face also carries the *other* side's texture for show-through.
        val frontInstance = newPaperInstance(paperMaterial, grainTexture)
        val backInstance = newPaperInstance(paperMaterial, grainTexture)
        Textures.bind(frontInstance, "baseColorMap", pageTexture(2 * sheet))
        Textures.bind(frontInstance, "backColorMap", pageTexture(2 * sheet + 1))
        Textures.bind(backInstance, "baseColorMap", pageTexture(2 * sheet + 1))
        Textures.bind(backInstance, "backColorMap", pageTexture(2 * sheet))

        val page = FlightPage(host, frontInstance, backInstance).also {
            host.scene.addEntity(it.frontEntity)
            host.scene.addEntity(it.backEntity)
            it.update(s, spineX, backInnerZ, pageH)
        }
        val f = TurnFlight(
            sheet = sheet,
            forward = forward,
            strip = s,
            page = page,
            front = frontInstance,
            back = backInstance,
            // Rest direction of the source side: flat right, or down the
            // opened cover's slope for backward turns.
            restAngle = if (forward) 0f else atan2(leftSlope, -1f),
            grabV = v.coerceIn(0f, 1f),
        )
        flights.add(f)
        active = f
        refreshFlightSurfaces()
        if (!quiet) onFeel?.invoke(FeelEvent.PAGE_GRAB)
        return true
    }

    fun dragTurn(simX: Float, simZ: Float) {
        active?.strip?.drag(simX, simZ)
    }

    fun releaseTurn(directionHint: Int) {
        val f = active ?: return
        f.strip.release(directionHint)
        f.flicked = directionHint != 0
        if (f.flicked) onFeel?.invoke(FeelEvent.PAGE_FLICK)
        active = null
    }

    /** Re-grabs the nearest in-flight page if its paper passes within [maxDist]. */
    fun tryRegrab(simX: Float, simZ: Float, maxDist: Float): Boolean {
        var bestFlight: TurnFlight? = null
        var bestIndex = 0
        var bestSq = maxDist * maxDist
        for (f in flights) {
            val s = f.strip
            for (i in MIN_REGRAB_INDEX until s.n) {
                val dx = s.px[i] - simX
                val dz = s.pz[i] - simZ
                val d = dx * dx + dz * dz
                if (d < bestSq) {
                    bestSq = d
                    bestFlight = f
                    bestIndex = i
                }
            }
        }
        val f = bestFlight ?: return false
        f.strip.grab(bestIndex / (f.strip.n - 1f))
        f.flicked = false
        active = f
        onFeel?.invoke(FeelEvent.PAGE_GRAB)
        return true
    }

    /** One riffled page: peeled at the fore-edge and flicked left, unheld. */
    fun riffleTurn(): Boolean {
        if (!beginTurn(forward = true, u = 0.92f, v = 0.5f, quiet = true)) return false
        val f = checkNotNull(active)
        f.strip.fling(speedX = -RIFFLE_KICK_X, lift = RIFFLE_KICK_LIFT)
        f.strip.release(-1)
        f.flicked = true
        active = null
        onFeel?.invoke(FeelEvent.RIFFLE_TICK)
        return true
    }

    fun stepTurn(dt: Float) {
        if (flights.isEmpty()) return
        for (f in flights) {
            f.strip.step(dt)
            // Skew ramps in while the finger holds the page and evens out
            // after release — a page must be skew-free when it rejoins a stack.
            f.skewStrength = if (f.strip.isGrabbed) {
                (f.skewStrength + dt * SKEW_RAMP).coerceAtMost(1f)
            } else {
                (f.skewStrength - dt * SKEW_DECAY).coerceAtLeast(0f)
            }
        }
        retireSettled()
    }

    /** Extrudes every strip's current state into its flight meshes (once/frame). */
    fun updateFlightMesh() {
        // λ(stiffness): how much of the curl the far edge keeps. Card stock
        // (stiffness 1) lifts as a plate — zero skew.
        val lambda = SKEW_LAMBDA_FLOOR + (1f - SKEW_LAMBDA_FLOOR) * paperTuning.stiffness
        for (f in flights) {
            f.page.update(
                f.strip, spineX, backInnerZ, pageH,
                grabV = f.grabV,
                skew = f.skewStrength * (1f - lambda),
                restAngle = f.restAngle,
            )
        }
    }

    /** Applies live paper-feel tuning; physics params take effect on the next grab. */
    fun setPaperTuning(tuning: PaperTuning) {
        paperTuning = tuning
        val show = tuning.translucency
        leftPaperInstance.setParameter("showThrough", show)
        rightPaperInstance.setParameter("showThrough", show)
        for (f in flights) {
            f.front.setParameter("showThrough", show)
            f.back.setParameter("showThrough", show)
        }
    }

    /** Settled, unheld flights land; landing may force blockers down too. */
    private fun retireSettled() {
        var landedAny = false
        var scan = true
        while (scan) {
            scan = false
            for (f in flights) {
                if (f === active || f.strip.settle == PageStrip.Settle.IN_FLIGHT) continue
                landFlight(f)
                landedAny = true
                scan = true
                break
            }
        }
        if (landedAny) {
            spread = ledger.restingSpread
            refreshRest()
            refreshFlightSurfaces()
        }
    }

    private fun landFlight(f: TurnFlight) {
        val landings = ledger.land(f.sheet, left = f.strip.settle == PageStrip.Settle.SETTLED_LEFT)
        for ((sheet, _) in landings) {
            val g = flights.first { it.sheet == sheet }
            onFeel?.invoke(
                if (g === f && f.flicked) FeelEvent.PAGE_LAND_FLICK else FeelEvent.PAGE_LAND_SOFT,
            )
            retire(g)
        }
    }

    private fun retire(f: TurnFlight) {
        host.scene.removeEntity(f.page.frontEntity)
        host.scene.removeEntity(f.page.backEntity)
        f.page.destroy()
        engine.destroyMaterialInstance(f.front)
        engine.destroyMaterialInstance(f.back)
        flights.remove(f)
        if (active === f) active = null
    }

    private fun abortAllTurns() {
        while (flights.isNotEmpty()) retire(flights[flights.size - 1])
        active = null
    }

    private fun leftSpineTopSim(leftSheets: Int): Float =
        // Opened cover's inner face passes the spine at world halfT; stack on top.
        (halfT - backInnerZ) + leftSheets * book.sheetThicknessMeters

    /**
     * A strip's collision floors: resting sheets plus any airborne sheets
     * that will pile beneath it on that side (keeps concurrent flights
     * stacking in the right order before they land).
     */
    private fun applySurfaces(strip: PageStrip, sheet: Int) {
        strip.setSurfaces(
            left = leftSpineTopSim(ledger.leftCount + ledger.airborneBelowOnLeft(sheet)),
            right = (ledger.rightCount + ledger.airborneAboveOnRight(sheet)) * book.sheetThicknessMeters,
            slopeLeft = leftSlope,
        )
    }

    private fun refreshFlightSurfaces() {
        for (f in flights) applySurfaces(f.strip, f.sheet)
    }

    /** Wedges + resting pages from the ledger's current resting counts. */
    private fun refreshRest() {
        val left = ledger.leftCount
        val right = ledger.rightCount
        rebuildWedgesAndPages(
            leftSheets = left,
            rightSheets = right,
            leftPageIndex = if (left > 0) 2 * left - 1 else null,
            rightPageIndex = if (right > 0) 2 * (spreadCount - right) else null,
        )
    }

    fun setCoverAngle(angleRad: Float) {
        val ti = transformManager.getInstance(coverRoot)
        Matrix.setIdentityM(transform, 0)
        Matrix.translateM(transform, 0, hingeX, 0f, hingeZ)
        Matrix.rotateM(transform, 0, -Math.toDegrees(angleRad.toDouble()).toFloat(), 0f, 1f, 0f)
        transformManager.setTransform(ti, transform)
    }

    fun destroy() {
        abortAllTurns()
        leftWedge?.destroy(); rightWedge?.destroy()
        leftPage.destroy(); rightPage.destroy()
        staticMeshes.forEach { it.destroy() }
        staticMeshes.clear()
        pageTextures.values.forEach { engine.destroyTexture(it) }
        pageTextures.clear()
        engine.destroyEntity(coverRoot)
        entityManager.destroy(coverRoot)
    }

    private fun rebuildWedgesAndPages(
        leftSheets: Int,
        rightSheets: Int,
        leftPageIndex: Int?,
        rightPageIndex: Int?,
    ) {
        val sheetT = book.sheetThicknessMeters
        val leftT = leftSheets * sheetT
        val rightT = rightSheets * sheetT

        // ---- Wedges (rebuilt: cheap 24-vert boxes, changes only on jumps/turns) ----
        leftWedge?.destroy()
        leftWedge = if (leftSheets > 0) {
            StaticMesh(
                engine,
                // Cover-local: inner face is -z; the stack grows downward from it.
                BookGeometry.board(pageW, pageH, leftT, GUTTER + pageW / 2f, 0f, -coverT / 2f - leftT / 2f),
                blockInstance,
            ).also {
                host.scene.addEntity(it.entity)
                transformManager.create(it.entity, coverParentInstance, IDENTITY)
            }
        } else {
            null
        }

        rightWedge?.destroy()
        rightWedge = if (rightSheets > 0) {
            StaticMesh(
                engine,
                BookGeometry.board(
                    pageW, pageH, rightT,
                    spineX + GUTTER + pageW / 2f, 0f, backInnerZ + rightT / 2f,
                ),
                blockInstance,
            ).also { host.scene.addEntity(it.entity) }
        } else {
            null
        }

        // ---- Resting pages + textures ----
        val showLeft = leftPageIndex != null
        if (showLeft) {
            leftPage.setShape(
                originX = GUTTER,
                width = pageW,
                height = pageH,
                baseZ = -coverT / 2f - leftT - Z_EPS,
                bowHeight = BOW_HEIGHT,
                spineU = 1f,
            )
            Textures.bind(leftPaperInstance, "baseColorMap", pageTexture(leftPageIndex!!))
            // Other side of the same sheet, for show-through (docs/04 §2.1).
            Textures.bind(leftPaperInstance, "backColorMap", pageTexture(leftPageIndex - 1))
        }
        if (showLeft != leftPageVisible) {
            if (showLeft) host.scene.addEntity(leftPage.entity) else host.scene.removeEntity(leftPage.entity)
            leftPageVisible = showLeft
        }

        val showRight = rightPageIndex != null
        if (showRight) {
            rightPage.setShape(
                originX = spineX + GUTTER,
                width = pageW,
                height = pageH,
                baseZ = backInnerZ + rightT + Z_EPS,
                bowHeight = BOW_HEIGHT,
                spineU = 0f,
            )
            Textures.bind(rightPaperInstance, "baseColorMap", pageTexture(rightPageIndex!!))
            Textures.bind(rightPaperInstance, "backColorMap", pageTexture(rightPageIndex + 1))
        }
        if (showRight != rightPageVisible) {
            if (showRight) host.scene.addEntity(rightPage.entity) else host.scene.removeEntity(rightPage.entity)
            rightPageVisible = showRight
        }
    }

    private fun pageTexture(pageIndex: Int): Texture = pageTextures.getOrPut(pageIndex) {
        val bitmap = if (pageIndex == BLANK_PAGE_INDEX) {
            blankPage
        } else {
            book.pageBitmapProvider?.invoke(pageIndex) ?: blankPage
        }
        Textures.fromBitmap(engine, bitmap)
    }

    private fun newPaperInstance(material: com.google.android.filament.Material, grain: Texture) =
        material.createInstance().apply {
            when (book.grain) {
                RenderGrain.LAID -> {
                    setParameter("roughness", 0.88f)
                    setParameter("grainStrength", 0.45f)
                }
                RenderGrain.WOVEN -> {
                    setParameter("roughness", 0.84f)
                    setParameter("grainStrength", 0.38f)
                }
                RenderGrain.GLOSS -> {
                    setParameter("roughness", 0.62f)
                    setParameter("grainStrength", 0.18f)
                }
            }
            setParameter("grainTiling", 5f, 7f)
            setParameter("showThrough", paperTuning.translucency)
            setParameter("keyLightDir", KEY_LIGHT_X, KEY_LIGHT_Y, KEY_LIGHT_Z)
            Textures.bind(this, "grainNormal", grain)
            // All samplers must be bound; real back textures rebind per page.
            Textures.bind(this, "backColorMap", pageTexture(BLANK_PAGE_INDEX))
        }

    private fun addStatic(
        data: MeshData,
        instance: MaterialInstance,
        castShadows: Boolean = true,
    ) {
        val mesh = StaticMesh(engine, data, instance, castShadows = castShadows)
        staticMeshes.add(mesh)
        host.scene.addEntity(mesh.entity)
    }

    private fun flatBoard(): Bitmap =
        Bitmap.createBitmap(intArrayOf(BOARD_COLOR, BOARD_COLOR, BOARD_COLOR, BOARD_COLOR), 2, 2, Bitmap.Config.ARGB_8888)

    private fun blankPaper(): Bitmap {
        val c = 0xFFF2ECDC.toInt()
        return Bitmap.createBitmap(intArrayOf(c, c, c, c), 2, 2, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        const val BLOCK_INSET = 0.02f
        const val GUTTER = 0.0015f
        const val BOW_HEIGHT = 0.0035f
        const val BOARD_COLOR = 0xFF6D4A2F.toInt()
        const val Z_EPS = 0.00015f
        const val DESK_GAP = 0.0045f
        const val DESK_W = 0.85f
        const val DESK_H = 1.2f
        val IDENTITY = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

        // Corner skew (M7): ramp-in/fade-out rates (1/s) and the floppiest
        // paper's far-edge weight floor.
        const val SKEW_RAMP = 9f
        const val SKEW_DECAY = 4.5f
        const val SKEW_LAMBDA_FLOOR = 0.35f

        // Flight pool + riffle (M8).
        const val MAX_FLIGHTS = 3
        const val MIN_REGRAB_INDEX = 2
        const val RIFFLE_KICK_X = 1.4f
        const val RIFFLE_KICK_LIFT = 0.55f
        const val RIFFLE_ZONE_FRACTION = 0.16f

        // Must match the addDirectionalLight() rig below (normalized).
        val KEY_LIGHT_LEN = sqrt(0.45f * 0.45f + 0.75f * 0.75f + 0.5f * 0.5f)
        val KEY_LIGHT_X = 0.45f / KEY_LIGHT_LEN
        val KEY_LIGHT_Y = -0.75f / KEY_LIGHT_LEN
        val KEY_LIGHT_Z = -0.5f / KEY_LIGHT_LEN

        /** pageTexture key for the blank fallback (never hits the provider). */
        const val BLANK_PAGE_INDEX = -1
    }
}
