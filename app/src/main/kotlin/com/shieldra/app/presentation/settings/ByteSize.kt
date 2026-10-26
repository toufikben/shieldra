package com.shieldra.app.presentation.settings

/** Human-readable sizes for the Storage screen: exact bytes below 1 KB, one decimal above. */
object ByteSize {
    private const val KB = 1024.0

    fun format(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes / KB
        var unit = 0
        while (value >= KB && unit < units.lastIndex) {
            value /= KB
            unit++
        }
        // Whole numbers read cleaner than "2.0 MB"; anything else keeps one decimal.
        val rounded = Math.round(value * 10.0) / 10.0
        val text = if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
        return "$text ${units[unit]}"
    }
}
