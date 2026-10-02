package br.com.thiaguinhosolucoes.smart24vision

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Durable event queue. A retry PUT uses the same key, preventing duplicate events. */
class MobileEventOutbox(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("smart24_mobile_events", Context.MODE_PRIVATE)
    @Synchronized fun add(values: Map<String, Any?>, ownerUid: String): String {
        val rows = rows()
        check(rows.size < 1000) { "A fila local atingiu 1000 eventos. Sincronize antes de continuar." }
        val id = "MOBILE-${UUID.randomUUID()}"
        rows += JSONObject().put("id", id).put("ownerUid", ownerUid).put("payload", JSONObject(values))
        save(rows)
        return id
    }
    @Synchronized fun pending(ownerUid: String): List<JSONObject> = rows().filter { ownerUid.isNotBlank() && it.optString("ownerUid") == ownerUid }
    @Synchronized fun uploaded(id: String) { save(rows().filterNot { it.optString("id") == id }) }
    @Synchronized fun count(): Int = rows().size
    @Synchronized fun localOnlyCount(): Int = rows().count { it.optString("ownerUid").isBlank() }
    private fun rows(): MutableList<JSONObject> {
        val raw = prefs.getString("queue", "[]") ?: "[]"
        val json = JSONArray(raw)
        return (0 until json.length()).map { json.getJSONObject(it) }.toMutableList()
    }
    private fun save(rows: List<JSONObject>) {
        val json = JSONArray()
        rows.forEach(json::put)
        check(prefs.edit().putString("queue", json.toString()).commit()) { "Não foi possível salvar o evento local." }
    }
}
