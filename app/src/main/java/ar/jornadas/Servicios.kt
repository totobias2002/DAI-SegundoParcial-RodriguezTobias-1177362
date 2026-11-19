package ar.jornadas

import android.content.Context
import androidx.room.Room
import ar.jornadas.data.*
import ar.jornadas.lab.ServicioLocal
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Una sola instancia por proceso: una base, un cliente de red y un contador de la mesa. */
class Servicios private constructor(context: Context) {
    val mesa = ServicioLocal(context)
    private val archivo = Room.databaseBuilder(context, Archivo::class.java, "cartelera.db")
        .addMigrations(MIGRACION_1_2)
        .build()
    private val api = Retrofit.Builder().baseUrl("https://programa.invalid/")
        .client(OkHttpClient.Builder().addInterceptor(mesa).build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(ProgramaApi::class.java)
    val agenda = AgendaRepository(api, archivo.cartelera())

    companion object {
        @Volatile private var instancia: Servicios? = null
        private var cargaAutomaticaHecha = false

        fun de(context: Context): Servicios = instancia ?: synchronized(this) {
            instancia ?: Servicios(context.applicationContext).also { instancia = it }
        }

        /** Devuelve true una sola vez por proceso (sesión). */
        @Synchronized fun tomarCargaAutomatica(): Boolean {
            if (cargaAutomaticaHecha) return false
            cargaAutomaticaHecha = true
            return true
        }
    }
}
