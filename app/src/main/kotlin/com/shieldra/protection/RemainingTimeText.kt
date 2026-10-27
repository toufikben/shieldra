package com.shieldra.protection

import android.content.Context
import com.shieldra.app.R

/** The owner-facing wording of a time left, in the app's current language. */
object RemainingTimeText {
    fun of(context: Context, remainingMs: Long): String = RemainingTime.format(
        remainingMs,
        day = context.getString(R.string.unit_day),
        hour = context.getString(R.string.unit_hour),
        minute = context.getString(R.string.unit_minute),
        lessThanMinute = context.getString(R.string.unit_less_than_minute),
    )
}
