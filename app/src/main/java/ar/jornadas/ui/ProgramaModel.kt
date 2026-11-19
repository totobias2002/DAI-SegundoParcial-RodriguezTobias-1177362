package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.Servicios
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class ProgramaModel(app: Application, private val estado: SavedStateHandle) : AndroidViewModel(app) {
    private val servicios = Servicios.de(app)
    val mesa = servicios.mesa
    private val repo = servicios.agenda

    val charlas = repo.programa.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** null mientras Room no respondió; false si nunca llegó una edición exitosa. */
    val hayEdicion: StateFlow<Boolean?> =
        repo.edicion.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Filtros en SavedStateHandle: sobreviven al giro y a la muerte del proceso; una tarea nueva arranca sin filtros.
    val dia: StateFlow<Int> = estado.getStateFlow(CLAVE_DIA, 0)
    val tema: StateFlow<String> = estado.getStateFlow(CLAVE_TEMA, TODOS)

    private val _cargando = MutableStateFlow(false)
    val cargando = _cargando.asStateFlow()
    private val _falla = MutableStateFlow(false)
    val falla = _falla.asStateFlow()

    private var peticion: Job? = null

    init {
        // Una sola carga automática por proceso: rotar o volver a la pantalla no vuelve a pedir.
        if (Servicios.tomarCargaAutomatica()) actualizar()
    }

    fun elegirDia(valor: Int) { estado[CLAVE_DIA] = valor }
    fun elegirTema(valor: String) { estado[CLAVE_TEMA] = valor }

    /** Actualización explícita. Si ya hay una petición en curso, no se lanza otra. */
    fun actualizar() {
        if (peticion?.isActive == true) return
        peticion = viewModelScope.launch {
            _cargando.value = true
            _falla.value = false
            try {
                repo.sincronizar()
            } catch (e: IOException) {          // sin conexión o servicio apagado
                _falla.value = true
            } catch (e: HttpException) {        // el servidor respondió con error (404, 500)
                _falla.value = true
            } finally {
                _cargando.value = false
            }
        }
    }

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }

    companion object {
        const val TODOS = "Todos"
        private const val CLAVE_DIA = "dia"
        private const val CLAVE_TEMA = "tema"
    }
}
