package br.com.thiaguinhosolucoes.smart24vision

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object MobileZoneStore {
    private const val PREFS = "smart24_mobile_zones"

    fun load(context: Context, storeId: String, cameraId: String): List<Zone> {
        val key = key(storeId, cameraId)
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, "[]").orEmpty()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        Zone(
                            zoneId = obj.getString("zoneId"),
                            storeId = storeId,
                            cameraId = cameraId,
                            left = obj.getDouble("left").toFloat(),
                            top = obj.getDouble("top").toFloat(),
                            right = obj.getDouble("right").toFloat(),
                            bottom = obj.getDouble("bottom").toFloat(),
                            productId = obj.optString("productId", ""),
                            productName = obj.optString("productName", ""),
                            sku = obj.optString("sku", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun upsert(context: Context, zone: Zone) {
        val zones = load(context, zone.storeId, zone.cameraId)
            .filterNot { it.zoneId == zone.zoneId }
            .toMutableList()
        zones += zone
        saveAll(context, zone.storeId, zone.cameraId, zones)
    }

    fun clear(context: Context, storeId: String, cameraId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(key(storeId, cameraId)).apply()
    }

    private fun saveAll(context: Context, storeId: String, cameraId: String, zones: List<Zone>) {
        val array = JSONArray()
        zones.forEach { zone ->
            array.put(
                JSONObject()
                    .put("zoneId", zone.zoneId)
                    .put("left", zone.left)
                    .put("top", zone.top)
                    .put("right", zone.right)
                    .put("bottom", zone.bottom)
                    .put("productId", zone.productId)
                    .put("productName", zone.productName)
                    .put("sku", zone.sku)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key(storeId, cameraId), array.toString()).apply()
    }

    private fun key(storeId: String, cameraId: String): String =
        "${storeId.trim().lowercase()}::${cameraId.trim().uppercase()}"
}
