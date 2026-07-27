# Gimnasio

App personal de gimnasio y nutrición. **Kotlin Multiplatform + Compose Multiplatform**:
Android es el target funcional; los targets iOS están declarados y todo el código
común compila para ellos, pero aún no hay proyecto Xcode.

Todos los datos viven en el dispositivo (SQLite vía SQLDelight). No hay cuentas ni
servidor. La UI está en español.

## Comandos

```bash
./gradlew :composeApp:assembleDebug
```

```bash
./gradlew :composeApp:testDebugUnitTest
```

```bash
./gradlew :composeApp:installDebug
```

Windows no puede compilar Kotlin/Native para Apple, pero **sí verifica el código
de iOS con el compilador de metadatos**, que es lo que caza los errores de API y
los `actual` que falten:

```bash
./gradlew :composeApp:compileIosMainKotlinMetadata
```

El informe de tests queda en `composeApp/build/reports/tests/testDebugUnitTest/index.html`.

## Estructura

Módulo único `composeApp`, con la lógica y la UI en `commonMain`:

- `data/db/` — semilla de catálogos y mapeos de wger a nuestros grupos musculares
- `data/remote/` — clientes de wger y Open Food Facts, DTOs y mapeadores
- `data/repository/` — repositorios; toda la lógica de acceso a datos pasa por aquí
- `domain/model/` — modelos de dominio y enums
- `domain/calc/` — fórmulas y algoritmos puros (lo más testeado del proyecto)
- `ui/` — pantallas por área (`home`, `workout`, `calendar`, `nutrition`, `profile`)
- `platform/` — `expect` de lo específico de plataforma (driver de DB, notificador del cronómetro)
- `commonMain/sqldelight/` — esquema `.sq`, del que SQLDelight genera las queries tipadas

## Cosas que conviene saber antes de tocar nada

**Las subconsultas escalares en SQLDelight se infieren como no-nulas.** Traer la
imagen principal de un ejercicio con `(SELECT url FROM ExerciseImage ...)` genera
un `getString(n)!!` que revienta con cualquier ejercicio sin foto. Por eso
`searchExercises` y `selectExercisesForDay` la traen con `LEFT JOIN`.

**SQLite ignora las claves foráneas salvo que se activen explícitamente.** El
esquema depende de `ON DELETE CASCADE`, así que el pragma se activa en el driver
de Android (`AndroidSqliteDriver.Callback.onOpen`), en el de iOS
(`extendedConfig.foreignKeyConstraints`) y en `createTestDatabase()`.

**Los tests de repositorio corren contra SQLite de verdad** (`androidUnitTest`,
driver JDBC en memoria), no contra mocks. Es lo que ha destapado los dos fallos de
arriba. Si tocas el esquema o una query, esos tests son la red de seguridad.

**Los tests de interfaz corren en la JVM con Robolectric**, no en un emulador.
Tres cosas que hay que respetar al añadir uno:

- `@Config(application = android.app.Application::class)`, o Robolectric arranca
  `GimnasioApp`, que inicia Koin, y el segundo test peta con "already started".
- `ui-test-manifest` va en `debugImplementation`, no en el sourceSet de tests:
  aporta la `ComponentActivity` vacía y su manifiesto tiene que fusionarse.
- Usa `assertExists()` en vez de `assertIsDisplayed()` para lo que pueda caer por
  debajo del alto de la ventana de test; si no, fallan cosas que sí se renderizan.
- En pantallas con `LazyColumn`, lo que queda por debajo **ni se compone**, así
  que `assertExists()` tampoco lo encuentra: hay que desplazarse antes con
  `onNode(hasScrollAction()).performScrollToNode(...)`.

Para probar una pantalla con su ViewModel hay que llamar a
`Dispatchers.setMain(Dispatchers.Unconfined)`, porque `viewModelScope` despacha en
Main. `ActiveWorkoutScreenTest` es el ejemplo a copiar.

**Cambiar el esquema exige una migración, y `SchemaMigrationTest` lo obliga.**
Hay una instantánea del esquema en `androidUnitTest/resources/schema-snapshot.sql`
y el test falla si los `.sq` dejan de cuadrar con ella. Para un cambio
intencionado: añade `<versión>.sqm` junto a los `.sq` con el `ALTER TABLE`
correspondiente, borra la instantánea y vuelve a ejecutar los tests, que la
regeneran. Sin esto, la app instalada se rompería al actualizar, porque SQLDelight
solo migra si la versión del esquema sube (y sube con cada `.sqm`).

`schema-v1.sql` es otra cosa y **no se regenera nunca**: es el esquema de la
primera versión publicada, el punto de partida de la cadena de migraciones. Los
tests levantan esa base de datos, aplican todos los `.sqm` y comprueban que sale
el esquema de hoy con los datos intactos. La instantánea se mueve con cada
cambio; la base congelada no.

**El backup tiene su propia versión** (`BackupData.CURRENT_VERSION`), aparte de la
del esquema. Si añades una entidad nueva del usuario, acuérdate de exportarla:
`deleteAllUserData` la borrará al restaurar aunque el JSON no la traiga. Pasó con
los planes de dieta.

**`AppModuleTest` verifica el grafo de Koin.** Si añades un ViewModel con
parámetros, decláralo también ahí con `definition<T>(...)` o el test falla.

**kotlinx-datetime 0.8 cambió la API**: `date.month` devuelve `Month`, no `Int`.
Usa `LocalDate.monthNumber` de `util/CalendarMath.kt`.

**La memoria de la máquina es justa** (sin archivo de paginación). `gradle.properties`
limita el heap a 1536m y compila Kotlin dentro del daemon de Gradle; subirlo hace
que la JVM se caiga con "insufficient memory".

## APIs externas

- **wger** (`wger.de/api/v2`): catálogo de ejercicios, ~828, con traducción al
  español (idioma 4; inglés es el 2, pero se resuelven en runtime). Se descarga
  una vez desde Entrenamiento → Catálogo y se guarda en local.
- **Open Food Facts**: alimentos. `status` 1/0 indica si el producto existe, y
  `serving_quantity` llega unas veces como número y otras como cadena.

**La búsqueda por texto va contra `search.openfoodfacts.org`, no contra
`cgi/search.pl`.** El endpoint antiguo responde 503 de forma sistemática (probado
en `world.` y en `es.`). El nuevo devuelve los productos en `hits`, no en
`products`; el resto de campos son iguales. Los códigos de barras sí siguen en
`world.openfoodfacts.org/api/v2/product/`, que funciona bien.

**Los errores de OFF se traducen a español en `OpenFoodFactsApi.ensureSuccess()`**
y el repositorio envuelve los fallos de red en `OpenFoodFactsException`, para que
la UI nunca enseñe una excepción de Ktor.

Ambas piden un `User-Agent` identificable, que pone `HttpClientFactory`.

**El escáner de códigos usa el de Play Services** (`GmsBarcodeScanning`), que trae
su propia interfaz a pantalla completa. Por eso la app no pide permiso de cámara
ni integra CameraX. A cambio, no funciona en dispositivos sin Play Services, y
`BarcodeScanner.isAvailable()` decide si se enseña el botón.

**El backup referencia los ejercicios por `wgerId`, no por id local**, porque los
ids de la base de datos no coinciden entre instalaciones. Lo mismo con los
alimentos y su código de barras. Si un ejercicio del backup no está en el catálogo
local, se omite y se cuenta en `ImportSummary.skippedExercises`.

**Las rachas se cuentan por semanas, no por días seguidos.** En una rutina los días
de descanso son parte del plan, así que encadenar días penalizaría a quien entrena
bien. Una semana cuenta si se alcanzan tantas sesiones como días tenga la rutina
activa (`StreakCalculator`).

**El aviso de fin de descanso lo dispara AlarmManager, no una corrutina.** La
cuenta atrás que se ve en pantalla es cosmética; el aviso real lo programa el
sistema contra `RestTimerReceiver`, para que suene aunque Android mate el proceso
o el usuario salga de la pantalla. Por eso `ActiveWorkoutViewModel.onCleared()`
**no** cancela el descanso: solo se cancela al saltarlo o al terminar el entreno.
En Android 12+ el permiso de alarmas exactas hay que concederlo a mano; sin él el
aviso llega igual pero puede retrasarse, y la UI ofrece un botón para darlo.

**Los recordatorios usan WorkManager con trabajo periódico de 7 días**, calculando
el retardo inicial hasta el próximo día/hora que toque. `Calendar` numera los días
con domingo = 1 y el modelo usa ISO (lunes = 1), así que hay una conversión en
`ReminderScheduler.delayUntil()`.

**El icono es adaptativo y vectorial** (`mipmap-anydpi-v26`), sin PNG: con
minSdk 26 todos los dispositivos lo soportan. El dibujo cabe en el círculo seguro
de 72 del lienzo de 108.

**Las dependencias de Compose, lifecycle y navegación tienen que ser las de
JetBrains** (`compose.*` y `org.jetbrains.androidx.*`), no las de `androidx` a
secas. Las de AndroidX no publican targets de Apple todavía; las de JetBrains los
añaden y delegan en ellas. Cambiarlas rompe la resolución de iOS: probado tanto
con lifecycle/navegación como con `androidx.compose.runtime/foundation/ui/
material3`. El precio son los avisos de deprecación ("Specify dependency
directly") y los de "same unique_name" del resolvedor de klibs; ambos son
esperables y no rompen nada. Hay que esperar a que AndroidX publique Apple.

**wger no dice lo difícil que es un ejercicio, así que se deduce.**
`ExerciseComplexityClassifier` lo clasifica en básico/intermedio/avanzado por el
nombre (normalizado sin tildes) y el material: barra, barra Z, kettlebell y barra
de dominadas suben a intermedio, y hay una lista de palabras clave para lo
balístico, lo invertido y lo que carga la columna en flexión. Ante la duda
devuelve básico: el generador combina el filtro con la preferencia por lo
sencillo, así que un falso negativo suelto se diluye pero un catálogo vacío no.
Si el clasificador se afina, hay que mirar `RoutineGeneratorTest`, que da por
hecho que el catálogo de prueba ("Pecho compuesto", "Pecho aislamiento 1"…) sale
básico entero.

**El generador nunca repite ejercicio en la semana, ni siquiera para tapar un
hueco.** Las plantillas de día traen más huecos de los que se usan, ordenados de
más a menos importante, y el tiempo disponible decide dónde se corta
(`SessionTimeEstimator`). Si un grupo agota sus ejercicios a mitad de semana el
hueco se queda sin rellenar, porque ese grupo ya está entrenado; solo se avisa en
`uncoveredGroups` cuando no hay **ningún** ejercicio utilizable para el grupo, que
es lo que sí indica un problema de material o de nivel.

**La prescripción del generador no es la del objetivo a secas.**
`TrainingPrescription.forGoal(goal, level)` recorta series al principiante y se
las sube al avanzado, así que `saveGenerated` guarda la prescripción de la
propuesta y no vuelve a llamar a `forGoal(goal)`. Si se añade otro camino que
persista rutinas generadas, hay que pasarle la prescripción o el ajuste de nivel
se pierde al guardar.

**La silueta del mapa muscular son rutas SVG, no rectángulos.** `BodyShapes`
guarda el cuerpo y cada zona muscular como cadenas `d` de SVG sobre un lienzo de
100x200, y las lee con un intérprete propio que solo entiende `M`, `L`, `C` y `Z`
absolutos. Se hizo así porque transcribir veinticinco curvas a llamadas de `Path`
a mano era pedir una errata; si añades una forma, escríbela en SVG y respeta ese
subconjunto. `MuscleMap` pinta primero la silueta entera en gris (al 60 % del
color vacío) y encima las zonas, así que lo que no es ningún grupo —cabeza,
cuello, antebrazos, manos, rodillas y pies— hace de relleno y el cuerpo se
entiende aunque no se entrene nada.

Para verlo sin emulador: monta las mismas rutas en un `.html` con `<svg>` y
sácale una captura con `msedge.exe --headless --disable-gpu --screenshot`, que es
el único navegador instalado en el equipo. `BodyShapesTest` cubre lo que la vista
no puede: que las zonas caigan dentro del cuerpo, que estén ordenadas de arriba
abajo y que las pares queden centradas en el eje.

**La búsqueda del catálogo va contra `Exercise.searchName`, no contra `nameEs`.**
Esa columna guarda el nombre en español y en inglés en minúsculas y sin tildes,
porque el `LIKE` de SQLite solo ignora mayúsculas en ASCII y no sabe nada de
acentos. La calcula `normalizeForSearch` en Kotlin al guardar el ejercicio, así
que **todo camino que inserte ejercicios tiene que rellenarla**: la sincronización
con wger, los ejercicios propios y la restauración del backup. Las bases de datos
anteriores a la columna la traen a NULL y `DatabaseSeeder.fillMissingSearchNames`
la completa al arrancar; la migración no puede hacerlo porque quitar tildes en SQL
no es posible.

La consulta exige cada palabra por separado (`searchTerms` parte la consulta), de
modo que "press inclinado" encuentra "Press de banca inclinado". Son **cuatro
términos como máximo** porque cada uno ocupa un hueco fijo en el `.sq`; el resto
se ignoran.

**Las sugerencias son el aviso de balance ya resuelto.** `RoutineAdvisor` toma el
`BalanceReport`, los ejercicios de la rutina y el catálogo, y devuelve altas
("añade X al día Y") y cambios ("cambia X por Y"). Nunca propone un ejercicio que
ya esté en la rutina, nunca sacrifica un compuesto al sugerir un cambio y no da
dos consejos del mismo grupo. Se recalculan en cada `reload()`, así que lo que se
descarte con "Ahora no" vuelve al reabrir la pantalla.

**Al copiar un día a otra rutina el día de la semana solo viaja si está libre**
en el destino (`copyDayToRoutine`): dos días el mismo martes harían que el
recordatorio avisara dos veces. El nombre solo se desambigua si choca.

**El entrenamiento en curso se refresca en dos niveles.** `reload()` recarga todo
(plan, prerrelleno e historial de progresión) y solo se llama al abrir la
pantalla; al apuntar o borrar una serie se usa `refreshLoggedSets()`, que solo
relee las series de la sesión. Recargar entero en cada serie disparaba una
veintena de consultas para recalcular cosas que dependen de sesiones anteriores y
no cambian mientras entrenas.

## Estado

Están hechas todas las funcionalidades planificadas: inicio con racha y logros,
catálogo de ejercicios, rutinas con aviso de descompensación, mapa muscular y
ejercicios reordenables, generador automático de rutinas por tiempo y nivel,
registro de series con
cronómetro de descanso y sugerencia de subida de peso, calendario, métricas
corporales, gasto calórico por METs, diario de nutrición con balance calórico,
recetas, planes de dieta, escáner de código de barras, recordatorios, copia de
seguridad con exportación a CSV e icono propio.

Ideas que quedaron fuera: récords y 1RM estimado, fotos de progreso, contador de
hidratación, pantalla siempre encendida durante el entreno, Health Connect y
widget de pantalla de inicio.

Probado en dispositivo por el usuario. En el equipo de desarrollo no hay emulador
ni suele haber un móvil conectado, así que los cambios de UI no se pueden
verificar visualmente desde aquí.
