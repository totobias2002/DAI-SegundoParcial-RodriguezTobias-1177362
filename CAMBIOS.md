# Cambios - Guardia de mantenimiento

Tobias Rodriguez - Legajo 1177362

## Antes de empezar: el proyecto no compilaba

Al abrirlo me tiró error en `ServicioLocal`. Usa `toMediaType()` y `toResponseBody()`, que son de OkHttp 4, pero Retrofit 2.11 trae OkHttp 3. Agregué `okhttp:4.12.0` en el `build.gradle.kts` de app y con eso compiló.

## 41 - Programa sin señal

El problema era que la app no tenía forma de saber si alguna vez había descargado el programa o no. Además, cuando fallaba la red, el mensaje de error reemplazaba toda la lista, así que te quedabas sin ver nada aunque los datos estuvieran guardados.

Lo que hice fue agregar una tabla `edicion` en Room. Tiene una sola fila, que se guarda únicamente cuando la descarga sale bien, aunque venga vacía. Con eso la pantalla distingue tres casos:

- **Hay datos guardados y falla la red:** sigue mostrando la lista, el detalle y las favoritas, y avisa que se están usando datos guardados. El aviso (con el botón Reintentar) se muestra solo en Programa, que es donde se actualiza; Mi agenda y el detalle muestran los datos guardados sin aviso.
- **Nunca se descargó nada y falla:** muestra el error con el botón de reintentar.
- **Llegó la v4 (vacía):** muestra "sin charlas programadas", y si después se abre sin señal sigue mostrando eso, porque es una edición válida.

Como la app ya está instalada y la gente tiene datos, subí la base a la versión 2 con una migración que solo crea la tabla nueva. No toca las charlas ni las marcas. Si el usuario ya tenía charlas guardadas, la migración registra que hay una edición previa.

## 42 - Agenda personal

Cada vez que se actualizaba, el `REPLACE` pisaba todas las charlas con `favorita = false`, así que se perdían las marcas. Además, las charlas que sacaban del programa nunca se borraban.

Lo cambié por una función `reemplazarPrograma` en el DAO, con `@Transaction`. Lee qué charlas están marcadas, borra el catálogo, inserta el nuevo manteniendo las marcas de las que siguen, y guarda la edición.

La clave es que las marcas se leen **adentro de la transacción**, en el momento de escribir, y no antes de ir a la red. Así, si la usuaria marca o desmarca algo mientras espera la respuesta, no se pierde.

Con esto:
- de v1 a v2, la 11 sigue marcada con el título y la sala nuevos (el id no cambia);
- en v3, la 11 desaparece del catálogo y de Mi agenda, y entra la 90;
- si una charla se va y después vuelve, vuelve sin marcar.

## 43 - Volver al punto de lectura

El tema del filtro estaba en un `remember` adentro del catálogo, así que se perdía al abrir una ficha o al rotar. El día estaba solo en el ViewModel, que no sobrevive si Android mata el proceso.

Pasé los dos filtros a `SavedStateHandle`. Así aguantan la rotación y la muerte del proceso, y una tarea nueva arranca sin filtros. La ficha abierta ya estaba en `rememberSaveable`, así que esa parte andaba.

## 44 - Control de descargas

Se recargaba de más: en cada `onStart` (rotar, volver de otra app) y cada vez que entrabas al catálogo, por el `LaunchedEffect`. Además, `Servicios` se creaba de nuevo con cada ViewModel, o sea varias bases y varios Retrofit.

Lo que cambié:
- `Servicios` ahora es una sola instancia por proceso.
- Hay una sola carga automática por proceso, desde el `init` del ViewModel. Saqué la del `onStart` y la del `LaunchedEffect`.
- Si ya hay una petición en curso, tocar Actualizar de nuevo no hace nada. Cuando termina, bien o mal, se puede volver a pedir.
- Los filtros siguen andando mientras carga.
- El extra `servicio_disponible` se aplica antes de crear el ViewModel, para que la primera petición ya lo tome en cuenta.

También cambié los `catch`: atrapo `IOException` (sin conexión) y `HttpException` (el servidor respondió con error), en vez de `Exception`, que se come también la cancelación de la corrutina.

## Cómo lo probé

Ojo: el estado de la pantalla Mesa (servicio disponible, edición v1-v4 y demora larga) se guarda en SharedPreferences (`mesa`, en `ServicioLocal`), así que sobrevive a matar el proceso. Para volver a empezar hay que hacer `pm clear` o abrir con `--ez servicio_disponible true`.

1. En v1 marqué la 11, pasé a v2 y actualicé: sigue marcada con "Compose en producción avanzada" y "Sala Puerto".
2. Pasé a v3 y actualicé: la 11 desapareció de todos lados y apareció la 90. Volví a v2: la 11 volvió sin marcar.
3. Con la demora larga, marqué una charla mientras esperaba: la marca quedó. Toqué Actualizar dos veces: el contador subió 1.
4. Apagué el servicio y actualicé: la lista, el detalle y las favoritas siguieron, con el aviso de datos guardados.
5. Puse la v4: "sin charlas programadas". Apagué el servicio, maté el proceso y volví a abrir: siguió igual.
6. Con `pm clear` y arrancando sin servicio: aparece el error con reintentar.
7. Día 2 + Datos, abrí "Cachés para viajeros", volví y roté: quedan 2 resultados. Con la ficha abierta, Inicio, `am kill` y volví desde Recientes: recuperó la ficha y, al volver, los dos filtros.
8. Contador de peticiones: con un arranque nuevo marca 1. Rotar, abrir una ficha y cambiar de pestaña no lo mueven.
9. Marca quitada durante una petición: con la demora larga, quité la marca de una charla vigente mientras esperaba y, al llegar la respuesta, siguió sin marcar. En v3 también se conservaron las marcas de otras charlas que siguen en el programa.
10. Migración de la base (la que ya tienen los usuarios): compilé la versión original (base v1), marqué la 11 y la actualicé encima con `adb install -r`, sin desinstalar. Quedaron las 12 charlas y la marca, y apareció la tabla `edicion` con su fila. Al abrirla sin servicio mostró los datos guardados con el aviso, en vez del error.
