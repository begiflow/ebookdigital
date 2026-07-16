package com.leaf.data.texture

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * KTX v1 container with uncompressed RGBA8 mip levels — the on-disk GPU
 * derivative format (docs/02 §3). v1 of the pipeline stores uncompressed
 * levels; the ASTC/BasisLZ encoder slots in behind the same container later
 * (encoding needs a native encoder this pipeline stage doesn't ship yet).
 * Writer + reader are pure Kotlin and roundtrip-tested; :filament uploads
 * the parsed levels directly.
 */
object Ktx1 {

    class Image(val width: Int, val height: Int, val levels: List<ByteArray>)

    private val IDENTIFIER = byteArrayOf(
        0xAB.toByte(), 0x4B, 0x54, 0x58, 0x20, 0x31, 0x31, 0xBB.toByte(),
        0x0D, 0x0A, 0x1A, 0x0A,
    )

    private const val ENDIANNESS = 0x04030201
    private const val GL_UNSIGNED_BYTE = 0x1401
    private const val GL_RGBA = 0x1908
    private const val GL_RGBA8 = 0x8058

    fun write(levels: List<MipChain.Level>): ByteArray {
        require(levels.isNotEmpty())
        val out = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        header.put(IDENTIFIER)
        header.putInt(ENDIANNESS)
        header.putInt(GL_UNSIGNED_BYTE) // glType
        header.putInt(1) // glTypeSize
        header.putInt(GL_RGBA) // glFormat
        header.putInt(GL_RGBA8) // glInternalFormat
        header.putInt(GL_RGBA) // glBaseInternalFormat
        header.putInt(levels[0].width)
        header.putInt(levels[0].height)
        header.putInt(0) // pixelDepth (2D)
        header.putInt(0) // numberOfArrayElements
        header.putInt(1) // numberOfFaces
        header.putInt(levels.size) // numberOfMipmapLevels
        header.putInt(0) // bytesOfKeyValueData
        out.write(header.array())

        val size4 = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        for (level in levels) {
            val bytes = MipChain.toRgbaBytes(level)
            size4.clear()
            size4.putInt(bytes.size)
            out.write(size4.array())
            out.write(bytes)
            // RGBA8 rows are 4-byte aligned already; no padding needed.
        }
        return out.toByteArray()
    }

    /** Parses a container written by [write]; null on anything unexpected. */
    fun read(bytes: ByteArray): Image? {
        if (bytes.size < 64 + 4) return null
        for (i in IDENTIFIER.indices) if (bytes[i] != IDENTIFIER[i]) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(12)
        if (buf.int != ENDIANNESS) return null
        val glType = buf.int
        buf.int // glTypeSize
        val glFormat = buf.int
        buf.int // glInternalFormat
        buf.int // glBaseInternalFormat
        val width = buf.int
        val height = buf.int
        buf.int // depth
        buf.int // array elements
        val faces = buf.int
        val mipCount = buf.int
        val kvBytes = buf.int
        if (glType != GL_UNSIGNED_BYTE || glFormat != GL_RGBA || faces != 1) return null
        if (width <= 0 || height <= 0 || mipCount <= 0 || kvBytes < 0) return null
        buf.position(buf.position() + kvBytes)

        val levels = ArrayList<ByteArray>(mipCount)
        var w = width
        var h = height
        repeat(mipCount) {
            if (buf.remaining() < 4) return null
            val size = buf.int
            if (size != w * h * 4 || buf.remaining() < size) return null
            val level = ByteArray(size)
            buf.get(level)
            levels.add(level)
            w = maxOf(1, w / 2)
            h = maxOf(1, h / 2)
        }
        return Image(width, height, levels)
    }
}
