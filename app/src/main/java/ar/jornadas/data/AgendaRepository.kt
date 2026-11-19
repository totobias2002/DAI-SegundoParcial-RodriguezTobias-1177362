package ar.jornadas.data

class AgendaRepository(private val api: ProgramaApi, private val dao: CarteleraDao) {
    val programa = dao.observar()
    val edicion = dao.observarEdicion()

    /** Pide la instantánea completa y la guarda en una sola transacción. */
    suspend fun sincronizar() {
        val fichas = api.consultar().map {
            Charla(it.id, it.title, it.track, it.day, it.time, it.room, it.speaker, it.summary)
        }
        dao.reemplazarPrograma(fichas, System.currentTimeMillis())
    }

    suspend fun cambiarFavorita(id: String) = dao.alternar(id)
}
