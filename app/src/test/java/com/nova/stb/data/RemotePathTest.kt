package com.nova.stb.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePathTest {
    @Test
    fun keepsSharedStorageRoot() {
        assertEquals("/sdcard", RemotePath.normalize("/sdcard/"))
    }

    @Test
    fun normalizesDotSegmentsWithoutEscapingRoot() {
        assertEquals("/sdcard/Download", RemotePath.normalize("/sdcard/Pictures/../Download"))
    }

    @Test
    fun createsSafeChildPath() {
        assertEquals("/sdcard/Download/movie.mp4", RemotePath.child("/sdcard/Download", "movie.mp4"))
    }

    @Test
    fun findsParentButNeverGoesAboveSharedStorage() {
        assertEquals("/sdcard", RemotePath.parent("/sdcard/Download"))
        assertEquals("/sdcard", RemotePath.parent("/sdcard"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPathsOutsideSharedStorage() {
        RemotePath.normalize("/data/data")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTraversalAboveSharedStorage() {
        RemotePath.normalize("/sdcard/../../data")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsChildPathSeparators() {
        RemotePath.child("/sdcard", "../escape")
    }

    @Test
    fun reportsInsidePaths() {
        assertTrue(RemotePath.isInsideSharedStorage("/sdcard/Download/file.apk"))
        assertFalse(RemotePath.isInsideSharedStorage("/system"))
    }
}
