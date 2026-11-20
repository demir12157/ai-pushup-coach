package com.sinavkoc.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sinavkoc.app.model.FormIssue
import com.sinavkoc.app.model.WorkoutSession
import java.text.SimpleDateFormat
import java.util.*

data class DayStat(val label: String, val reps: Int)

class WorkoutRepository(context: Context) {
    private val prefs = context.getSharedPreferences("sinavkoc_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun loadSessions(): List<WorkoutSession> {
        val raw = prefs.getString("sessions", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<WorkoutSession>>() {}.type
            gson.fromJson(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveSession(session: WorkoutSession): List<WorkoutSession> {
        val updated = listOf(session) + loadSessions()
        prefs.edit().putString("sessions", gson.toJson(updated)).apply()
        return updated
    }

    fun deleteSession(id: String): List<WorkoutSession> {
        val updated = loadSessions().filterNot { it.id == id }
        prefs.edit().putString("sessions", gson.toJson(updated)).apply()
        return updated
    }

    fun loadDailyGoal(): Int = prefs.getInt("daily_goal", 30)
    fun saveDailyGoal(goal: Int) = prefs.edit().putInt("daily_goal", goal).apply()

    fun loadVoiceEnabled(): Boolean = prefs.getBoolean("voice_enabled", true)
    fun saveVoiceEnabled(v: Boolean) = prefs.edit().putBoolean("voice_enabled", v).apply()

    fun loadShowSkeleton(): Boolean = prefs.getBoolean("show_skeleton", true)
    fun saveShowSkeleton(v: Boolean) = prefs.edit().putBoolean("show_skeleton", v).apply()

    fun todayReps(sessions: List<WorkoutSession>): Int {
        val todayKey = dayFmt.format(Date())
        return sessions.filter { dayFmt.format(Date(it.timestampMs)) == todayKey }.sumOf { it.reps }
    }

    fun calculateStreak(sessions: List<WorkoutSession>): Int {
        if (sessions.isEmpty()) return 0
        val days = sessions.map { dayFmt.format(Date(it.timestampMs)) }.toSet()
        val cal = Calendar.getInstance()
        val todayKey = dayFmt.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayKey = dayFmt.format(cal.time)

        if (todayKey !in days && yesterdayKey !in days) return 0

        val cursor = Calendar.getInstance()
        if (todayKey !in days) cursor.add(Calendar.DAY_OF_YEAR, -1)

        var streak = 0
        while (dayFmt.format(cursor.time) in days) {
            streak += 1
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    fun last7Days(sessions: List<WorkoutSession>): List<DayStat> {
        val trDays = arrayOf("Paz", "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt")
        val result = mutableListOf<DayStat>()
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val key = dayFmt.format(cal.time)
            val reps = sessions.filter { dayFmt.format(Date(it.timestampMs)) == key }.sumOf { it.reps }
            val label = trDays[cal.get(Calendar.DAY_OF_WEEK) - 1]
            result.add(DayStat(label, reps))
        }
        return result
    }

    fun topFormIssues(sessions: List<WorkoutSession>): List<Pair<FormIssue, Int>> {
        val map = mutableMapOf<FormIssue, Int>()
        for (s in sessions) {
            for ((k, v) in s.issueCounts) {
                map[k] = (map[k] ?: 0) + v
            }
        }
        return map.entries.filter { it.value > 0 }.sortedByDescending { it.value }.map { it.key to it.value }
    }
}
