package com.shieldra.storage

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileBackedKeyLifecycleSequenceTest {

    // nextKeyVersion is a pure function of the version string; no record file is touched.
    private val lifecycle = FileBackedKeyLifecycle(File("unused-lifecycle-record")) { 0L }

    @Test
    fun `rotation always has an approved successor`() {
        assertEquals("v2", lifecycle.nextKeyVersion("v1"))
        assertEquals("v3", lifecycle.nextKeyVersion("v2"))
        assertEquals("v4", lifecycle.nextKeyVersion("v3"))
        assertEquals("v10", lifecycle.nextKeyVersion("v9"))
    }

    @Test
    fun `unsupported current versions still fail closed`() {
        assertNull(lifecycle.nextKeyVersion("v0"))
        assertNull(lifecycle.nextKeyVersion("v01"))
        assertNull(lifecycle.nextKeyVersion("nope"))
        assertNull(lifecycle.nextKeyVersion(""))
    }
}
