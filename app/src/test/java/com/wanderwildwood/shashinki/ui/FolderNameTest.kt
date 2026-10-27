package com.wanderwildwood.shashinki.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderNameTest {
    @Test fun plainNamesPass() {
        assertTrue(folderNameOk("Camera"))
        assertTrue(folderNameOk("Trip 2026"))
    }

    @Test fun emptyFails() = assertFalse(folderNameOk(""))

    @Test fun slashesFail() {
        assertFalse(folderNameOk("a/b"))
        assertFalse(folderNameOk("a\\b"))
    }

    @Test fun dotsFail() {
        assertFalse(folderNameOk("."))
        assertFalse(folderNameOk(".."))
    }
}
