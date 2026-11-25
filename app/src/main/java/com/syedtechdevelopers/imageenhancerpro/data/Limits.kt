package com.syedtechdevelopers.imageenhancerpro.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

/**
 * Free plan: 5 enhances per 24 hours (the 24 h start at the first enhance).
 * Watching 5 rewarded ads unlocks 1 hour of unlimited enhancing.
 *
 * Timers count down with the phone's uptime clock (elapsedRealtime), so changing the date or time
 * in Settings does not end them early. Only after a reboot is the wall clock used for the gap.
 */
class Limits(context: Context, private val clock: Clock = Clock.System(context)) {
    interface Clock {
        fun elapsed(): Long
        fun wall(): Long
        fun boot(): Int

        class System(private val context: Context) : Clock {
            override fun elapsed() = SystemClock.elapsedRealtime()
            override fun wall() = java.lang.System.currentTimeMillis()
            override fun boot() = runCatching {
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
            }.getOrDefault(-1)
        }
    }

    private val prefs = context.getSharedPreferences("limits", Context.MODE_PRIVATE)

    var freeUsed by mutableIntStateOf(prefs.getInt("freeUsed", 0)); private set
    /** Time left until the free enhances come back (0 = no 24 h window running). */
    var resetLeftMs by mutableLongStateOf(prefs.getLong("resetLeft", 0L)); private set
    var unlockLeftMs by mutableLongStateOf(prefs.getLong("unlockLeft", 0L)); private set
    var adsWatched by mutableIntStateOf(prefs.getInt("adsWatched", 0)); private set

    val freeLeft: Int get() = (FREE_PER_DAY - freeUsed).coerceAtLeast(0)
    val unlocked: Boolean get() = unlockLeftMs > 0
    val canEnhance: Boolean get() = unlocked || freeLeft > 0

    init {
        tick()
    }

    /** Called every second while the app is open (and at start). */
    fun tick() {
        val e = clock.elapsed()
        val w = clock.wall()
        val b = clock.boot()
        val lastE = prefs.getLong("lastElapsed", -1L)
        val lastW = prefs.getLong("lastWall", -1L)
        val lastB = prefs.getInt("lastBoot", Int.MIN_VALUE)
        val delta = when {
            lastE < 0 -> 0L
            b == lastB && e >= lastE -> e - lastE // same boot: tamper-proof uptime clock
            else -> (w - lastW).coerceAtLeast(0L) // after a reboot
        }
        if (delta > 0) {
            resetLeftMs = (resetLeftMs - delta).coerceAtLeast(0L)
            unlockLeftMs = (unlockLeftMs - delta).coerceAtLeast(0L)
        }
        if (resetLeftMs == 0L && freeUsed > 0) freeUsed = 0
        save(e, w, b)
    }

    /** Counts one enhance. Free enhances are used only while no unlock hour is running. */
    fun useEnhance() {
        tick()
        if (unlocked) return
        if (resetLeftMs == 0L) resetLeftMs = DAY_MS
        freeUsed = (freeUsed + 1).coerceAtMost(FREE_PER_DAY)
        save(clock.elapsed(), clock.wall(), clock.boot())
    }

    /** One rewarded ad watched in the unlock screen. Returns true when this one unlocked the hour. */
    fun adWatched(): Boolean {
        tick()
        adsWatched++
        val done = adsWatched >= ADS_TO_UNLOCK
        if (done) {
            adsWatched = 0
            unlockLeftMs = UNLOCK_MS
        }
        save(clock.elapsed(), clock.wall(), clock.boot())
        return done
    }

    private fun save(e: Long, w: Long, b: Int) {
        prefs.edit()
            .putInt("freeUsed", freeUsed)
            .putLong("resetLeft", resetLeftMs)
            .putLong("unlockLeft", unlockLeftMs)
            .putInt("adsWatched", adsWatched)
            .putLong("lastElapsed", e)
            .putLong("lastWall", w)
            .putInt("lastBoot", b)
            .apply()
    }

    companion object {
        const val FREE_PER_DAY = 5
        const val ADS_TO_UNLOCK = 5
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val UNLOCK_MS = 60 * 60 * 1000L

        fun format(ms: Long): String {
            val s = (ms + 999) / 1000
            return "%02d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
        }
    }
}
