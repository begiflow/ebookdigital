package com.leaf.data.files

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileStoreTest {

    private val root = File.createTempFile("leaf-store", "").let {
        it.delete()
        it.mkdirs()
        it
    }
    private val store = FileStore(root)

    @AfterTest
    fun cleanup() {
        root.deleteRecursively()
    }

    @Test
    fun `original writes once and re-import is idempotent`() {
        val bytes = "page-bytes".toByteArray()
        val hash = store.writeOriginal("p1", bytes)
        assertEquals(hash, store.writeOriginal("p1", bytes)) // same bytes OK
        assertTrue(store.verifyOriginal("p1", hash))
    }

    @Test
    fun `overwriting an original with different bytes is refused`() {
        store.writeOriginal("p1", "first".toByteArray())
        assertFailsWith<IllegalStateException> {
            store.writeOriginal("p1", "second".toByteArray())
        }
        assertContentEquals("first".toByteArray(), store.originalFile("p1").readBytes())
    }

    @Test
    fun `derivative regeneration leaves the original byte-identical`() {
        // The M13 exit criterion: originals byte-identical after edits.
        val original = ByteArray(4096) { (it * 31).toByte() }
        val hash = store.writeOriginal("p1", original)

        store.writeTexture("p1", "texture-v1".toByteArray())
        store.writeThumb("p1", "thumb-v1".toByteArray())
        store.writeTexture("p1", "texture-v2-after-edit".toByteArray())
        store.writeThumb("p1", "thumb-v2-after-edit".toByteArray())

        assertTrue(store.verifyOriginal("p1", hash))
        assertContentEquals(original, store.originalFile("p1").readBytes())
    }

    @Test
    fun `deleting artifacts can spare the original`() {
        store.writeOriginal("p1", "orig".toByteArray())
        store.writeTexture("p1", "tex".toByteArray())
        store.deletePageArtifacts("p1", includeOriginal = false)
        assertTrue(store.originalFile("p1").exists())
        assertTrue(!store.textureFile("p1").exists())
    }
}
