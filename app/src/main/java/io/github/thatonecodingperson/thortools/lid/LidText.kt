package io.github.thatonecodingperson.thortools.lid

import android.content.Context
import android.text.format.DateUtils
import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R
import java.text.DateFormat

/** The words for what the lid did. */
object LidText {
    @StringRes
    fun label(item: LidItem): Int = when (item) {
        LidItem.WIFI -> R.string.lidItemWifi
        LidItem.BLUETOOTH -> R.string.lidItemBluetooth
    }

    fun items(context: Context, items: List<LidItem>): String = items.joinToString(", ") { context.getString(label(it)) }

    fun notBack(context: Context, items: List<LidItem>): String = context.getString(R.string.lidNotRestored, items(context, items))

    fun result(context: Context, result: LidResult?, now: Long = System.currentTimeMillis()): String {
        if (result == null) return context.getString(R.string.lidLastNone)
        val closed = time(result.closedAt, now)
        val opened = time(result.openedAt, now)
        val text = if (result.notBack.isEmpty()) {
            context.getString(R.string.lidLastOk, closed, opened)
        } else {
            context.getString(R.string.lidLastMissing, closed, opened, items(context, result.notBack))
        }
        if (result.sentBack == 0) return text
        return text + " " + context.resources.getQuantityString(R.plurals.lidSentBack, result.sentBack, result.sentBack)
    }

    private fun time(at: Long, now: Long): String = DateUtils.formatSameDayTime(at, now, DateFormat.SHORT, DateFormat.SHORT).toString()
}
