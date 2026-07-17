package com.leaf.data.files

import java.io.File
import java.security.MessageDigest

/**
 * App-private image store (docs/02-ARCHITECTURE.md §3):
 *
 * - `originals/{pageId}.jpg` — write-once, never recompressed, never touched
 *   by edits. Storage integrity is a PRD non-functional requirement: the
 *   original is the user's real notebook page.
 * - `textures/{pageId}.ktx` — GPU derivative (mipmapped), regenerated when
 *   edits change.
 * - `thumbs/{pageId}.jpg` — shelf/review sizes.
 *
 * Pure java.io — JVM-tested, no Android types.
 */
class FileStore(root: File) {

    val originals = File(root, "originals")
    val textures = File(root, "textures")
    val thumbs = File(root, "thumbs")

    init {
        originals.mkdirs()
        textures.mkdirs()
        thumbs.mkdirs()
    }

    /**
     * Stores an original exactly once. A second write for the same page is
     * an error unless the bytes are identical (idempotent re-import).
     * Returns the content hash recorded in the DB alongside the path.
     */
    fun writeOriginal(pageId: String, bytes: ByteArray): String {
        val file = originalFile(pageId)
        val hash = sha256(bytes)
        if (file.exists()) {
            check(sha256(file.readBytes()) == hash) {
                "original for $pageId already exists with different content — originals are write-once"
            }
            return hash
        }
        val tmp = File(originals, "$pageId.tmp")
        tmp.writeBytes(bytes)
        check(tmp.renameTo(file)) { "atomic rename failed for $pageId" }
        return hash
    }

    fun originalFile(pageId: String): File = File(originals, "$pageId.jpg")

    fun textureFile(pageId: String): File = File(textures, "$pageId.ktx")

    fun thumbFile(pageId: String): File = File(thumbs, "$pageId.jpg")

    /** Derivatives may be rewritten freely — they regenerate from originals. */
    fun writeTexture(pageId: String, bytes: ByteArray) {
        val tmp = File(textures, "$pageId.tmp")
        tmp.writeBytes(bytes)
        check(tmp.renameTo(textureFile(pageId))) { "atomic rename failed for $pageId texture" }
    }

    fun writeThumb(pageId: String, bytes: ByteArray) {
        thumbFile(pageId).writeBytes(bytes)
    }

    /** Verifies an original against its recorded hash (corruption check). */
    fun verifyOriginal(pageId: String, expectedSha256: String): Boolean {
        val file = originalFile(pageId)
        return file.exists() && sha256(file.readBytes()) == expectedSha256
    }

    fun deletePageArtifacts(pageId: String, includeOriginal: Boolean) {
        textureFile(pageId).delete()
        thumbFile(pageId).delete()
        if (includeOriginal) originalFile(pageId).delete()
    }

    companion object {
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}
