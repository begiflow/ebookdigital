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
import com.leaf.renderer.RenderBook
import com.leaf.renderer.geometry.BookGeometry
import com.leaf.renderer.material.PaperGrain
import kotlin.math.PI
import kotlin.math.atan
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

    // ---- Turn-in-flight state (M6) ----
    private var flight: FlightPage? = null
    private var strip: PageStrip? = null
    private var flightInstances = ArrayList<MaterialInstance>(2)
    private var turnForward = true
    val isTurning: Boolean get() = flight != null
    val turnDirectionForward: Boolean get() = turnForward

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

        grainTexture = Textures.fromBitmap(engine, PaperGrain.laidNormalMap(), srgb = false)
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
        abortTurn()
        spread = k.coerceIn(0, spreadCount)
        applySpread()
    }

    // ================= Turn lifecycle (M6, docs/03-RENDERER.md §3) =========

    /** Sim-space (spread-space) helpers: origin at (spineX, backInnerZ). */
    fun worldToSimX(worldX: Float) = worldX - spineX
    fun worldToSimZ(worldZ: Float) = worldZ - backInnerZ

    /** World z of the right page's pick plane. */
    fun rightPageWorldZ() = backInnerZ + (spreadCount - spread) * book.sheetThicknessMeters

    /** World z of the left page's pick plane (at the spine; plane slopes down). */
    fun leftPageWorldZ() = backInnerZ + leftSpineTopSim(spread)

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
     * width from the spine. Returns false at the ends of the book.
     */
    fun beginTurn(forward: Boolean, u: Float): Boolean {
        if (isTurning) return false
        if (forward && spread >= spreadCount) return false
        if (!forward && spread <= 0) return false
        turnForward = forward

        val sheet = if (forward) spread else spread - 1
        val sheetT = book.sheetThicknessMeters
        val rightUnderSheets = if (forward) spreadCount - spread - 1 else spreadCount - spread

        applyTurnVisuals(
            leftSheets = if (forward) spread else spread - 1,
            rightSheets = rightUnderSheets,
        )

        val s = PageStrip(
            PageParams(widthMeters = pageW, stiffness = book.paperStiffness),
        )
        s.setSurfaces(
            left = leftSpineTopSim(if (forward) spread else spread - 1),
            right = rightUnderSheets * sheetT,
            slopeLeft = leftSlope,
        )
        s.resetFlat(onRight = forward, bowHeight = BOW_HEIGHT)
        s.grab(u)
        strip = s

        // Flight faces: sheet front / back, mapped per the domain sheet model.
        val frontInstance = newPaperInstance(paperMaterial, grainTexture)
        val backInstance = newPaperInstance(paperMaterial, grainTexture)
        Textures.bind(frontInstance, "baseColorMap", pageTexture(2 * sheet))
        Textures.bind(backInstance, "baseColorMap", pageTexture(2 * sheet + 1))
        flightInstances.add(frontInstance)
        flightInstances.add(backInstance)

        flight = FlightPage(host, frontInstance, backInstance).also {
            host.scene.addEntity(it.frontEntity)
            host.scene.addEntity(it.backEntity)
            it.update(s, spineX, backInnerZ, pageH)
        }
        return true
    }

    fun dragTurn(simX: Float, simZ: Float) = strip?.drag(simX, simZ)

    fun releaseTurn(directionHint: Int) = strip?.release(directionHint)

    /** Re-grab an in-flight page at the particle nearest the touch point. */
    fun regrabTurn(simX: Float, simZ: Float) {
        val s = strip ?: return
        var best = 2
        var bestSq = Float.MAX_VALUE
        for (i in 2 until s.n) {
            val dx = s.px[i] - simX
            val dz = s.pz[i] - simZ
            val d = dx * dx + dz * dz
            if (d < bestSq) { bestSq = d; best = i }
        }
        s.grab(best / (s.n - 1f))
    }

    fun stepTurn(dt: Float) = strip?.step(dt)

    fun turnSettle(): PageStrip.Settle = strip?.settle ?: PageStrip.Settle.IN_FLIGHT

    /** Extrudes the current strip state into the flight meshes (once/frame). */
    fun updateFlightMesh() {
        val s = strip ?: return
        flight?.update(s, spineX, backInnerZ, pageH)
    }

    /** Ends the turn: advances the spread by where the sheet landed. */
    fun completeTurn() {
        val s = strip ?: return
        val landedLeft = s.settle == PageStrip.Settle.SETTLED_LEFT
        val next = when {
            turnForward && landedLeft -> spread + 1
            !turnForward && !landedLeft -> spread - 1
            else -> spread
        }
        setSpread(next) // also destroys flight via abortTurn()
    }

    private fun abortTurn() {
        flight?.let {
            host.scene.removeEntity(it.frontEntity)
            host.scene.removeEntity(it.backEntity)
            it.destroy()
        }
        flight = null
        strip = null
        flightInstances.forEach { engine.destroyMaterialInstance(it) }
        flightInstances.clear()
    }

    private fun leftSpineTopSim(leftSheets: Int): Float =
        // Opened cover's inner face passes the spine at world halfT; stack on top.
        (halfT - backInnerZ) + leftSheets * book.sheetThicknessMeters

    /** Wedges/resting pages while a sheet is airborne. */
    private fun applyTurnVisuals(leftSheets: Int, rightSheets: Int) {
        rebuildWedgesAndPages(
            leftSheets = leftSheets,
            rightSheets = rightSheets,
            leftPageIndex = if (leftSheets > 0) 2 * leftSheets - 1 else null,
            rightPageIndex = if (rightSheets > 0) 2 * (spreadCount - rightSheets) else null,
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
        abortTurn()
        leftWedge?.destroy(); rightWedge?.destroy()
        leftPage.destroy(); rightPage.destroy()
        staticMeshes.forEach { it.destroy() }
        staticMeshes.clear()
        pageTextures.values.forEach { engine.destroyTexture(it) }
        pageTextures.clear()
        engine.destroyEntity(coverRoot)
        entityManager.destroy(coverRoot)
    }

    private fun applySpread() {
        rebuildWedgesAndPages(
            leftSheets = spread,
            rightSheets = spreadCount - spread,
            leftPageIndex = if (spread > 0) 2 * spread - 1 else null,
            rightPageIndex = if (spread < spreadCount) 2 * spread else null,
        )
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
        }
        if (showRight != rightPageVisible) {
            if (showRight) host.scene.addEntity(rightPage.entity) else host.scene.removeEntity(rightPage.entity)
            rightPageVisible = showRight
        }
    }

    private fun pageTexture(pageIndex: Int): Texture = pageTextures.getOrPut(pageIndex) {
        val bitmap = book.pageBitmapProvider?.invoke(pageIndex) ?: blankPage
        Textures.fromBitmap(engine, bitmap)
    }

    private fun newPaperInstance(material: com.google.android.filament.Material, grain: Texture) =
        material.createInstance().apply {
            setParameter("roughness", 0.88f)
            setParameter("grainStrength", 0.45f)
            setParameter("grainTiling", 5f, 7f)
            Textures.bind(this, "grainNormal", grain)
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
    }
}
