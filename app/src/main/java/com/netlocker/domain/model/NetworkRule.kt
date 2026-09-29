package com.netlocker.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The user-configured network access rule for one app, identified by [packageName].
 *
 * A rule exists only for apps the user explicitly configured (a row in the database);
 * an app with no row is simply "default" — fully allowed. NetLocker never silently
 * restricts anything the user didn't turn off.
 *
 * [isEnabled] lets the user pause a rule without losing it: a disabled rule keeps its
 * saved [wifiAllowed]/[mobileDataAllowed] values but is **not enforced** — the app
 * behaves exactly as if it had no rule. Every enforcement decision must therefore go
 * through the `effective*` properties below, never the raw flags.
 */
data class NetworkRule(
    val packageName: String,
    val wifiAllowed: Boolean = true,
    val mobileDataAllowed: Boolean = true,
    val isEnabled: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** Optional "block during this time window" (spec: Settings §Schedule). Only has any
     *  effect when the Settings-level Schedule master switch is also on — see
     *  [com.netlocker.util.PreferencesManager.scheduleMasterEnabled]. Persists even while
     *  the master is off, exactly like a paused rule keeps its raw values. */
    val scheduleEnabled: Boolean = false,
    /** Minutes since local midnight, 0..1439. */
    val scheduleStartMinute: Int = 0,
    val scheduleEndMinute: Int = 0,
    /** Bitmask of which days the schedule applies to, bit 0 = Sunday .. bit 6 = Saturday
     *  (see [dayBit]). Defaults to every day, matching the schedule's behaviour before
     *  per-day selection existed. */
    val scheduleDays: Int = ALL_DAYS_MASK,
) {
    /** What is actually enforced for Wi-Fi: a disabled rule allows everything. */
    val effectiveWifiAllowed: Boolean get() = !isEnabled || wifiAllowed

    /** What is actually enforced for Mobile Data: a disabled rule allows everything. */
    val effectiveMobileDataAllowed: Boolean get() = !isEnabled || mobileDataAllowed

    /** True when nothing is restricted — the app never needs to enter the VPN tunnel. */
    val isEffectivelyOpen: Boolean get() = effectiveWifiAllowed && effectiveMobileDataAllowed

    /** Access as actually enforced (a disabled rule reads as [NetworkAccessState.ALLOWED]). */
    val accessState: NetworkAccessState
        get() = when {
            effectiveWifiAllowed && effectiveMobileDataAllowed -> NetworkAccessState.ALLOWED
            effectiveWifiAllowed && !effectiveMobileDataAllowed -> NetworkAccessState.WIFI_ONLY
            !effectiveWifiAllowed && effectiveMobileDataAllowed -> NetworkAccessState.MOBILE_ONLY
            else -> NetworkAccessState.BLOCKED
        }

    /** Status shown on the Rules tab: like [accessState], but distinguishes a paused rule. */
    val status: RuleStatus
        get() = if (!isEnabled) {
            RuleStatus.DISABLED
        } else {
            when (accessState) {
                NetworkAccessState.ALLOWED -> RuleStatus.ALLOWED
                NetworkAccessState.WIFI_ONLY -> RuleStatus.WIFI_ONLY
                NetworkAccessState.MOBILE_ONLY -> RuleStatus.MOBILE_ONLY
                NetworkAccessState.BLOCKED -> RuleStatus.BLOCKED
            }
        }

    /**
     * True when [nowMinuteOfDay] (0..1439, local time) on [dayOfWeek] (0=Sunday..6=Saturday,
     * see [dayBit]) falls inside the schedule window — regardless of the Settings master
     * switch, [isEnabled], or anything else; callers combine this with those separately.
     *
     * A window that crosses midnight (e.g. Friday 22:00 - Saturday 06:00) is treated as
     * belonging to the day it *starts* on: with only Friday checked, it still blocks in
     * the small hours of Saturday morning, which is what "block Friday night" means in
     * practice — so the evening half is gated on today's day bit and the morning half is
     * gated on *yesterday's*.
     */
    fun isWithinSchedule(nowMinuteOfDay: Int, dayOfWeek: Int): Boolean {
        if (!scheduleEnabled) return false
        val yesterday = (dayOfWeek + 6) % 7
        return if (scheduleStartMinute <= scheduleEndMinute) {
            hasDay(dayOfWeek) && nowMinuteOfDay in scheduleStartMinute until scheduleEndMinute
        } else {
            (hasDay(dayOfWeek) && nowMinuteOfDay >= scheduleStartMinute) ||
                (hasDay(yesterday) && nowMinuteOfDay < scheduleEndMinute)
        }
    }

    /** Whether [dayOfWeek] (0=Sunday..6=Saturday) is one of the schedule's selected days. */
    fun hasDay(dayOfWeek: Int): Boolean = (scheduleDays and dayBit(dayOfWeek)) != 0

    companion object {
        fun default(packageName: String) = NetworkRule(packageName)
    }
}

/** Bit for [NetworkRule.scheduleDays]: 0=Sunday, 1=Monday, … 6=Saturday. */
fun dayBit(dayOfWeek: Int): Int = 1 shl dayOfWeek

/** All seven [dayBit]s set — the schedule's "Everyday" default. */
const val ALL_DAYS_MASK: Int = 0b111_1111

/** 0=Sunday..6=Saturday for [date] (default zone) — the indexing [NetworkRule.scheduleDays]
 *  and the day-of-week UI use, since it matches the common "Su Mo Tu We Th Fr Sa" order
 *  rather than [DayOfWeek]'s own Monday-first numbering. */
fun dayOfWeekIndex(date: LocalDate): Int = date.dayOfWeek.value % 7

fun nowDayOfWeekIndex(zone: ZoneId = ZoneId.systemDefault()): Int = dayOfWeekIndex(LocalDate.now(zone))

/** Human-facing summary of what is enforced for an app, used for badges/status text. */
enum class NetworkAccessState {
    ALLOWED,
    WIFI_ONLY,
    MOBILE_ONLY,
    BLOCKED,
}

/** Rule status on the Rules tab. Order = display order (most restrictive first). */
enum class RuleStatus {
    BLOCKED,
    WIFI_ONLY,
    MOBILE_ONLY,
    ALLOWED,
    DISABLED,
}

/** Minutes since local midnight, 0..1439 — what [NetworkRule.isWithinSchedule] expects
 *  and what the schedule time pickers store. */
fun nowMinuteOfDay(zone: ZoneId = ZoneId.systemDefault()): Int =
    LocalTime.now(zone).let { it.hour * 60 + it.minute }

/** An [InstalledApp] paired with its current [NetworkRule] — what the Apps tab renders. */
data class AppWithRule(
    val app: InstalledApp,
    val rule: NetworkRule,
    /** True when the user has a saved rule for this app (it appears in the Rules tab);
     *  false when [rule] is just the fully-allowed default. */
    val hasRule: Boolean = false,
)
