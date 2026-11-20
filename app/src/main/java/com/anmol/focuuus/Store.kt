package com.anmol.focuuus

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Routines and limits, saved as JSON in shared preferences. Shared by the UI and [FocusService]. */
object Store {
    private lateinit var prefs: SharedPreferences
    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    private val _limits = MutableStateFlow<List<AppLimit>>(emptyList())
    private val passes = HashMap<String, Pass>()
    val routines: StateFlow<List<Routine>> = _routines
    val limits: StateFlow<List<AppLimit>> = _limits

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("focuuus", Context.MODE_PRIVATE)
        _routines.value = readArray("routines").map(::routineFrom)
        _limits.value = readArray("limits").map(::limitFrom)
        readArray("passes").forEach { passes[it.getString("pkg")] = Pass(it.getInt("minutes"), it.optLong("startedAt")) }
    }

    // Passes are saved so a restart of the service mid-visit keeps the chosen length and start.
    fun pass(pkg: String) = passes[pkg]
    fun grantPass(pkg: String, minutes: Int) { passes[pkg] = Pass(minutes); savePasses() }
    fun startPass(pkg: String, at: Long) { passes[pkg]?.let { passes[pkg] = it.copy(startedAt = at); savePasses() } }
    fun removePass(pkg: String) { if (passes.remove(pkg) != null) savePasses() }
    /** Drop every pass except [pkg]'s: the user moved on from those apps. */
    fun keepOnlyPass(pkg: String?) { if (passes.keys.retainAll { it == pkg }) savePasses() }

    private fun savePasses() {
        val a = JSONArray(passes.map { (pkg, p) -> JSONObject().put("pkg", pkg).put("minutes", p.minutes).put("startedAt", p.startedAt) })
        prefs.edit().putString("passes", a.toString()).apply()
    }

    fun saveRoutine(r: Routine) {
        val list = _routines.value
        _routines.value = if (list.any { it.id == r.id }) list.map { if (it.id == r.id) r else it } else list + r
        persist()
    }

    fun deleteRoutine(id: String) {
        _routines.value = _routines.value.filterNot { it.id == id }
        persist()
    }

    fun saveLimit(l: AppLimit) {
        val list = _limits.value
        _limits.value = if (list.any { it.pkg == l.pkg }) list.map { if (it.pkg == l.pkg) l else it } else list + l
        persist()
    }

    fun deleteLimit(pkg: String) {
        _limits.value = _limits.value.filterNot { it.pkg == pkg }
        persist()
    }

    fun limitFor(pkg: String) = _limits.value.firstOrNull { it.pkg == pkg }
    fun blockingRoutine(pkg: String) = _routines.value.firstOrNull { it.blocks(pkg) }

    var bubbleX: Int
        get() = prefs.getInt("bubbleX", 24)
        set(v) = prefs.edit().putInt("bubbleX", v).apply()
    var bubbleY: Int
        get() = prefs.getInt("bubbleY", 240)
        set(v) = prefs.edit().putInt("bubbleY", v).apply()

    private fun persist() {
        prefs.edit()
            .putString("routines", JSONArray(_routines.value.map(::toJson)).toString())
            .putString("limits", JSONArray(_limits.value.map(::toJson)).toString())
            .apply()
    }

    private fun readArray(key: String): List<JSONObject> {
        val a = JSONArray(prefs.getString(key, "[]"))
        return List(a.length()) { a.getJSONObject(it) }
    }

    private fun toJson(r: Routine) = JSONObject()
        .put("id", r.id).put("name", r.name).put("start", r.start).put("end", r.end)
        .put("days", JSONArray(r.days.sorted())).put("apps", JSONArray(r.apps.sorted())).put("enabled", r.enabled)

    private fun routineFrom(o: JSONObject) = Routine(
        id = o.getString("id"),
        name = o.optString("name"),
        start = o.getInt("start"),
        end = o.getInt("end"),
        days = o.getJSONArray("days").let { a -> List(a.length()) { a.getInt(it) }.toSet() },
        apps = o.getJSONArray("apps").let { a -> List(a.length()) { a.getString(it) }.toSet() },
        enabled = o.optBoolean("enabled", true),
    )

    private fun toJson(l: AppLimit) = JSONObject()
        .put("pkg", l.pkg).put("daily", l.dailyMinutes).put("session", l.sessionMinutes)
        .put("delay", l.delaySeconds).put("ask", l.askDuration).put("bubble", l.bubble).put("autoClose", l.autoClose)

    private fun limitFrom(o: JSONObject) = AppLimit(
        pkg = o.getString("pkg"),
        dailyMinutes = o.optInt("daily"),
        sessionMinutes = o.optInt("session"),
        delaySeconds = o.optInt("delay"),
        askDuration = o.optBoolean("ask", false),
        bubble = o.optBoolean("bubble", true),
        autoClose = o.optBoolean("autoClose", true),
    )
}
