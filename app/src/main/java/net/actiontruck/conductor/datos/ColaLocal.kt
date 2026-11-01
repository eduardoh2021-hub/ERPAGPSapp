package net.actiontruck.conductor.datos

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/**
 * Lo que aún no ha llegado al ERP: posiciones GPS y eventos de las órdenes (con su firma y fotos). Sin cobertura se
 * acumula aquí y se envía al recuperarla. Cada elemento lleva el UUID que generó la app, así que reenviar no duplica.
 */
class ColaLocal(contexto: Context) : SQLiteOpenHelper(contexto, "cola.db", null, 1) {

    data class EventoPendiente(val id: String, val ordenId: String, val descripcion: String, val cuerpo: JSONObject, val error: String?, val creado: Long)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE posicion (id TEXT PRIMARY KEY, cuerpo TEXT NOT NULL, creado INTEGER NOT NULL)")
        db.execSQL("""CREATE TABLE evento (id TEXT PRIMARY KEY, orden_id TEXT NOT NULL, descripcion TEXT NOT NULL, cuerpo TEXT NOT NULL,
                      error TEXT, creado INTEGER NOT NULL)""")
    }

    override fun onUpgrade(db: SQLiteDatabase, anterior: Int, nueva: Int) {}

    // --- Posiciones ----------------------------------------------------------------------
    fun guardarPosicion(p: JSONObject) {
        writableDatabase.insertWithOnConflict("posicion", null, ContentValues().apply {
            put("id", p.getString("id")); put("cuerpo", p.toString()); put("creado", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun posiciones(limite: Int): List<JSONObject> =
        readableDatabase.rawQuery("SELECT cuerpo FROM posicion ORDER BY creado LIMIT $limite", null).use { c ->
            buildList { while (c.moveToNext()) add(JSONObject(c.getString(0))) }
        }

    fun borrarPosiciones(ids: List<String>) {
        if (ids.isEmpty()) return
        writableDatabase.delete("posicion", "id IN (${ids.joinToString(",") { "?" }})", ids.toTypedArray())
    }

    fun posicionesPendientes(): Int =
        readableDatabase.rawQuery("SELECT count(*) FROM posicion", null).use { it.moveToFirst(); it.getInt(0) }

    // --- Eventos ---------------------------------------------------------------------------
    fun guardarEvento(ordenId: String, descripcion: String, cuerpo: JSONObject) {
        writableDatabase.insertWithOnConflict("evento", null, ContentValues().apply {
            put("id", cuerpo.getString("id")); put("orden_id", ordenId); put("descripcion", descripcion)
            put("cuerpo", cuerpo.toString()); put("creado", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun eventos(): List<EventoPendiente> =
        readableDatabase.rawQuery("SELECT id, orden_id, descripcion, cuerpo, error, creado FROM evento ORDER BY creado", null).use { c ->
            buildList {
                while (c.moveToNext()) add(EventoPendiente(c.getString(0), c.getString(1), c.getString(2), JSONObject(c.getString(3)),
                    if (c.isNull(4)) null else c.getString(4), c.getLong(5)))
            }
        }

    fun marcarErrorEvento(id: String, error: String) {
        writableDatabase.update("evento", ContentValues().apply { put("error", error) }, "id = ?", arrayOf(id))
    }

    fun borrarEvento(id: String) {
        writableDatabase.delete("evento", "id = ?", arrayOf(id))
    }
}
