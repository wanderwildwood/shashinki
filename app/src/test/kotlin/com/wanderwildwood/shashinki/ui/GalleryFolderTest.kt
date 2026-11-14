package com.wanderwildwood.shashinki.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GalleryFolderTest {

    @Test fun folderIsNamedByItsLastPart() {
        assertEquals("Screenshots", GalleryFolder.nameOf("primary:Pictures/Screenshots"))
        assertEquals("Camera", GalleryFolder.nameOf("primary:DCIM/Camera/"))
        assertEquals("Download", GalleryFolder.nameOf("1A2B-3C4D:Download"))
    }

    @Test fun aWholeStorageIsNamedByItself() {
        assertEquals("primary", GalleryFolder.nameOf("primary:"))
        assertEquals("", GalleryFolder.nameOf(null))
    }

    @Test fun newestPictureOrVideoWins() {
        val rows = listOf(
            Triple("a.jpg", "image/jpeg", 10L),
            Triple("b.mp4", "video/mp4", 30L),
            Triple("c.png", "image/png", 20L),
        )
        assertEquals("b.mp4" to "video/mp4", GalleryFolder.newestOf(rows))
    }

    @Test fun foldersAndOtherFilesAreNotPictures() {
        val rows = listOf(
            Triple("older.jpg", "image/jpeg", 5L),
            Triple("sub", "vnd.android.document/directory", 99L),
            Triple("notes.txt", "text/plain", 98L),
            Triple("unknown", null, 97L),
        )
        assertEquals("older.jpg" to "image/jpeg", GalleryFolder.newestOf(rows))
    }

    @Test fun aFolderWithNoPicturesGivesNothing() {
        assertNull(GalleryFolder.newestOf(emptyList()))
        assertNull(GalleryFolder.newestOf(listOf(Triple("notes.txt", "text/plain", 1L))))
    }
}
