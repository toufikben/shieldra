package com.shieldra.app.presentation.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteSizeTest {

    @Test
    fun `small sizes are exact bytes`() {
        assertEquals("0 B", ByteSize.format(0))
        assertEquals("1023 B", ByteSize.format(1023))
    }

    @Test
    fun `kilobytes keep one decimal only when needed`() {
        assertEquals("1 KB", ByteSize.format(1024))
        assertEquals("1.5 KB", ByteSize.format(1536))
    }

    @Test
    fun `megabytes and gigabytes scale`() {
        assertEquals("2 MB", ByteSize.format(2L * 1024 * 1024))
        assertEquals("1.3 GB", ByteSize.format((1.25 * 1024 * 1024 * 1024).toLong() + 1))
    }
}
