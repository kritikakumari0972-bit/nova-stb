package com.nova.stb.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferProgressStateTest {
    @Test
    fun computesFractionForKnownSize() {
        val progress = TransferProgressState("Uploading file", 25L, 100L)
        assertEquals(0.25f, progress.fraction!!, 0.0001f)
    }

    @Test
    fun clampsFractionWhenProgressExceedsReportedSize() {
        val progress = TransferProgressState("Downloading file", 120L, 100L)
        assertEquals(1.0f, progress.fraction!!, 0.0001f)
    }

    @Test
    fun unknownOrZeroTotalUsesIndeterminateProgress() {
        assertNull(TransferProgressState("Uploading", 10L, null).fraction)
        assertNull(TransferProgressState("Uploading", 0L, 0L).fraction)
    }

    @Test
    fun negativeProgressIsClampedToZero() {
        val progress = TransferProgressState("Downloading", -5L, 100L)
        assertEquals(0.0f, progress.fraction!!, 0.0001f)
    }
}
