package ar.jornadas.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import retrofit2.http.GET
import kotlinx.coroutines.flow.Flow

data class FichaRemota(val id: String, val title: String, val track: String, val day: Int,
    val time: String, val room: String, val speaker: String, val summary: String)

interface ProgramaApi {
    @GET("programa") suspend fun consultar(): List<FichaRemota>
}

@Entity(tableName = "charlas")
data class Charla(@PrimaryKey val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String,
    val favorita: Boolean = false)

/** Una sola fila (id = 0): existe solo si alguna vez llegó una edición exitosa, aunque traiga cero charlas. */
@Entity(tableName = "edicion")
data class Edicion(@PrimaryKey val id: Int = 0, val recibida: Long)

@Dao
abstract class CarteleraDao {
    @Query("SELECT * FROM charlas ORDER BY dia, hora, sala")
    abstract fun observar(): Flow<List<Charla>>

    @Query("SELECT * FROM edicion WHERE id = 0")
    abstract fun observarEdicion(): Flow<Edicion?>

    @Query("UPDATE charlas SET favorita = NOT favorita WHERE id = :id")
    abstract suspend fun alternar(id: String)

    @Query("SELECT id FROM charlas WHERE favorita = 1")
    abstract suspend fun idsMarcados(): List<String>

    @Query("DELETE FROM charlas")
    abstract suspend fun borrarCatalogo()

    @Insert
    abstract suspend fun insertar(fichas: List<Charla>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun registrarEdicion(edicion: Edicion)

    /**
     * Reemplaza el catálogo por la instantánea recibida, todo o nada.
     * Las marcas se leen DENTRO de la transacción (no antes de la red): así se respeta
     * cualquier marca agregada o quitada mientras se esperaba la respuesta.
     * Una charla retirada se borra con su marca: si después regresa, regresa sin marcar.
     */
    @Transaction
    open suspend fun reemplazarPrograma(fichas: List<Charla>, recibida: Long) {
        val marcados = idsMarcados().toSet()
        borrarCatalogo()
        insertar(fichas.map { it.copy(favorita = it.id in marcados) })
        registrarEdicion(Edicion(recibida = recibida))
    }
}

@Database(entities = [Charla::class, Edicion::class], version = 2, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao
}

/**
 * v1 → v2: agrega la tabla edicion sin tocar las charlas ni las marcas de los usuarios.
 * Si la instalación vieja ya tenía charlas, se registra que hay una edición guardada.
 */
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `edicion` (`id` INTEGER NOT NULL, `recibida` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO edicion (id, recibida) SELECT 0, 0 WHERE EXISTS (SELECT 1 FROM charlas)")
    }
}
