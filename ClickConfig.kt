package com.polas.autoclicker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class ModeType {
    SINGLE_TARGET,
    MULTI_TARGET,
    AUTO_SCROLL
}

enum class StopCondition {
    INFINITY,
    TIMER_SECONDS,
    CYCLE_COUNT
}

enum class ScrollDirection {
    DOWN_TO_UP,   // রিলস/শর্টস বা পেজ নিচে নামানোর জন্য (নিচ থেকে উপরে সোয়াইপ)
    UP_TO_DOWN,   // পেজ উপরে তোলার জন্য (উপর থেকে নিচে সোয়াইপ)
    RIGHT_TO_LEFT,
    LEFT_TO_RIGHT
}

data class TargetPoint(
    var x: Int,
    var y: Int
)

data class ClickConfig(
    var name: String = "Config 1",
    var mode: ModeType = ModeType.SINGLE_TARGET,
    var intervalMs: Long = 1000L,
    var swipeDurationMs: Long = 350L,
    var scrollDirection: ScrollDirection = ScrollDirection.DOWN_TO_UP,
    var stopCondition: StopCondition = StopCondition.CYCLE_COUNT,
    var timerSeconds: Int = 300, // 00:05:00
    var maxCycles: Int = 10,
    var targets: MutableList<TargetPoint> = mutableListOf(TargetPoint(540, 1100))
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("name", name)
        obj.put("mode", mode.name)
        obj.put("intervalMs", intervalMs)
        obj.put("swipeDurationMs", swipeDurationMs)
        obj.put("scrollDirection", scrollDirection.name)
        obj.put("stopCondition", stopCondition.name)
        obj.put("timerSeconds", timerSeconds)
        obj.put("maxCycles", maxCycles)
        val arr = JSONArray()
        for (t in targets) {
            val pt = JSONObject()
            pt.put("x", t.x)
            pt.put("y", t.y)
            arr.put(pt)
        }
        obj.put("targets", arr)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): ClickConfig {
            val pts = mutableListOf<TargetPoint>()
            val arr = obj.optJSONArray("targets")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    pts.add(TargetPoint(item.optInt("x", 540), item.optInt("y", 1100)))
                }
            }
            if (pts.isEmpty()) pts.add(TargetPoint(540, 1100))

            return ClickConfig(
                name = obj.optString("name", "Config 1"),
                mode = ModeType.valueOf(obj.optString("mode", ModeType.SINGLE_TARGET.name)),
                intervalMs = obj.optLong("intervalMs", 1000L).coerceAtLeast(40L),
                swipeDurationMs = obj.optLong("swipeDurationMs", 350L).coerceIn(100L, 3000L),
                scrollDirection = ScrollDirection.valueOf(
                    obj.optString("scrollDirection", ScrollDirection.DOWN_TO_UP.name)
                ),
                stopCondition = StopCondition.valueOf(
                    obj.optString("stopCondition", StopCondition.CYCLE_COUNT.name)
                ),
                timerSeconds = obj.optInt("timerSeconds", 300),
                maxCycles = obj.optInt("maxCycles", 10),
                targets = pts
            )
        }
    }
}

object ConfigStorage {
    private const val PREF_NAME = "autoclicker_prefs"
    private const val KEY_ACTIVE = "active_config"
    private const val KEY_SAVED_LIST = "saved_configs_list"

    fun saveActiveConfig(context: Context, config: ClickConfig) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE, config.toJson().toString())
            .apply()
    }

    fun loadActiveConfig(context: Context): ClickConfig {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE, null) ?: return ClickConfig()
        return try {
            ClickConfig.fromJson(JSONObject(raw))
        } catch (e: Exception) {
            ClickConfig()
        }
    }

    fun getSavedConfigs(context: Context): MutableList<ClickConfig> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SAVED_LIST, null)
        val list = mutableListOf<ClickConfig>()
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    list.add(ClickConfig.fromJson(arr.getJSONObject(i)))
                }
            } catch (_: Exception) {
            }
        }
        if (list.isEmpty()) {
            // ডিফল্ট কনফিগ প্রিসেট (ভিডিওর Manage Configuration অনুযায়ী)
            list.add(ClickConfig(name = "config 1 (Fast Tap 100ms)", intervalMs = 100L))
            list.add(ClickConfig(name = "config 2 (Standard 1000ms)", intervalMs = 1000L))
            list.add(
                ClickConfig(
                    name = "config 3 (Reels/Shorts Auto-Scroll)",
                    mode = ModeType.AUTO_SCROLL,
                    intervalMs = 5000L,
                    stopCondition = StopCondition.INFINITY
                )
            )
            saveConfigList(context, list)
        }
        return list
    }

    fun saveConfigList(context: Context, list: List<ClickConfig>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVED_LIST, arr.toString())
            .apply()
    }
}
