package com.wanderwildwood.shashinki.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore

/**
 * What the gallery button opens: the last picture taken, or the newest picture or video in a
 * folder chosen in settings (Screenshots, another app's pictures).
 *
 * The folder is chosen on the phone's own "choose a folder" screen, which gives this app that
 * one folder to read and nothing else, so the app still asks for no storage permission. Only
 * the folder's address is kept. Folders inside it are not looked into.
 */
object GalleryFolder {

    private const val KEY = "shashinki_gallery_folder"

    /**
     * One picture or video: where it is, what kind it is, and whether the gallery must be lent
     * the right to read it (true for an address inside the chosen folder, which only this app
     * may read; false for one in the phone's media collection, which a gallery reads by itself
     * and this app could not lend).
     */
    class Found(@JvmField val uri: Uri, @JvmField val type: String, @JvmField val lend: Boolean)

    /** The chosen folder, or null when the button opens the last picture. */
    @JvmStatic
    fun tree(context: Context): Uri? =
        Defaults.prefs(context).getString(KEY, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    /** Keep [tree] as the folder (and the right to read it across restarts); null goes back to the last picture. */
    fun choose(context: Context, tree: Uri?) {
        val resolver = context.contentResolver
        val old = tree(context)
        if (old != null && old != tree) {
            runCatching { resolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        if (tree != null) {
            runCatching { resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        Defaults.prefs(context).edit().apply { if (tree == null) remove(KEY) else putString(KEY, tree.toString()) }.apply()
    }

    /** The folder's name as the phone shows it: "Screenshots" for primary:Pictures/Screenshots. */
    fun name(tree: Uri): String = nameOf(runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull())

    internal fun nameOf(documentId: String?): String {
        val path = documentId.orEmpty().substringAfter(':', "")
        return path.trimEnd('/').substringAfterLast('/').ifEmpty { documentId.orEmpty().substringBefore(':') }
    }

    /** Of (id, type, modified) rows, the newest picture or video; null when there is none. */
    internal fun newestOf(rows: List<Triple<String, String?, Long>>): Pair<String, String>? =
        rows.filter { (_, type, _) -> type != null && (type.startsWith("image/") || type.startsWith("video/")) }
            .maxByOrNull { it.third }
            ?.let { it.first to it.second!! }

    /**
     * The newest picture or video directly inside [tree]; null when the folder holds none or can
     * no longer be read. Reads the folder's list, so not for the main thread.
     *
     * Answered with the picture's address in the phone's media collection when the phone gives
     * one, since that is what a gallery opens inside its folder, with the neighbours a swipe
     * away; otherwise with its address in the chosen folder, which the gallery is lent for the
     * one viewing.
     */
    @JvmStatic
    fun newest(context: Context, tree: Uri): Found? {
        val rows = ArrayList<Triple<String, String?, Long>>()
        try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) rows += Triple(c.getString(0), c.getString(1), if (c.isNull(2)) 0L else c.getLong(2))
            }
        } catch (e: RuntimeException) {
            return null // the folder is gone, or the right to read it was taken back
        }
        val (id, type) = newestOf(rows) ?: return null
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        val media = try {
            MediaStore.getMediaUri(context, document)
        } catch (e: RuntimeException) {
            null
        }
        return Found(media ?: document, type, media == null)
    }
}
