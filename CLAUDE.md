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

**Al añadir una columna con `ALTER TABLE`, en el `.sq` va donde la deja SQLite**:
detrás de la última columna y **antes** de las restricciones de tabla (`CHECK`,
`PRIMARY KEY` compuesta…). `SchemaMigrationTest` compara el texto de
`sqlite_master`, así que ponerla en otro sitio hace que el esquema migrado no
cuadre con el de una instalación nueva aunque las dos tengan las mismas columnas.
Lo mismo pasó con `Exercise.searchName` y con `DietPlanMeal.dayOfWeek`.

**El backup tiene su propia versión** (`BackupData.CURRENT_VERSION`), aparte de la
del esquema. Si añades una entidad nueva del usuario, acuérdate de exportarla:
`deleteAllUserData` la borrará al restaurar aunque el JSON no la traiga. Pasó con
los planes de dieta. Los campos nuevos van con valor por defecto y **el defecto
tiene que decir "esto no venía"**, no un valor plausible: `dayOfWeek` es nulable
para que una copia de la versión 2 (plan de un día) se reparta por la semana al
restaurar, en vez de amontonarse el lunes.

Desde la versión 5 la copia lleva también los **alimentos de Open Food Facts que
el diario, las recetas o los planes referencian** (`cachedFoods`): en una
instalación limpia la caché está vacía y sin ellos esas entradas se descartaban
en silencio. Las **sesiones se reenganchan a su rutina y día por nombre** al
importar (antes quedaban todas como "Entrenamiento libre" y el nombre del día se
colaba en las notas), y lo creado a mano viaja con una **`ref` sintética** ("e0",
"f1", "r2") en vez del nombre, para que dos "Ensalada" no colapsen en una. El
`ImportSummary` cuenta inserciones reales y lo omitido (`skippedFoodItems`), no
lo que traía el JSON. La exportación corre dentro de una transacción.

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

  Se piden tres cosas: `/language/` (para resolver los ids de idioma), `/equipment/`
  y `/exerciseinfo/` paginado de 100 en 100. De cada ejercicio se guardan id,
  nombre y descripción de la traducción elegida, categoría, músculos primarios y
  secundarios, equipamiento, imágenes, el primer vídeo y los **alias**, que se
  concatenan en `searchName` porque son lo que la gente teclea de verdad. Los
  alias solo llegan al volver a descargar el catálogo: `fillMissingSearchNames`
  no los repone porque solo actúa sobre los `searchName` a NULL.

  Lo que llega y se ignora: `variation_group` (un UUID que agrupa las variantes
  del mismo movimiento —"Lunges" y "Barbell Lunges Walking" lo comparten—, que
  sería mejor fuente de alternativas que nuestro cálculo por grupo muscular),
  `notes`, `uuid` y la licencia con su autor. **wger es CC-BY-SA**: si la app
  llega a publicarse, hay que llevar la atribución de textos e imágenes.
- **Open Food Facts**: alimentos. `status` 1/0 indica si el producto existe, y
  `serving_quantity` llega unas veces como número y otras como cadena.

**La búsqueda de alimentos necesita dos peticiones, y no es por gusto.**
`search.openfoodfacts.org` busca por texto bien, pero **no devuelve `nutriments`
ni pidiéndolo en `fields`**: de ahí solo salen los códigos de barras. Los datos
completos los da la búsqueda de la API v2
(`world.openfoodfacts.org/api/v2/search?code=c1,c2,...`), que acepta los códigos
separados por comas y resuelve la lista entera en una sola llamada. El orden de
relevancia lo pone el primer paso. El `cgi/search.pl` antiguo sí trae todo junto,
pero se corta a las pocas peticiones.

**`brands` llega como lista en el buscador nuevo y como cadena en la v2.** Ese
desajuste reventaba la deserialización y la app enseñaba "comprueba tu conexión a
internet" con la conexión perfecta. Lo resuelve `BrandsSerializer`, que acepta las
dos formas. Por lo mismo, `NutritionRepository` distingue ahora el fallo de
formato del de red: si vuelve a cambiar un campo, el mensaje lo dice en vez de
culpar al usuario.

**Open Food Facts limita por IP a los usuarios anónimos** (unas 10 búsquedas por
minuto) y cuando corta devuelve **503** con una página HTML de aviso. Por eso el
503 se trata como el 429 y no como una caída: ambos ponen
`OpenFoodFactsException.retryAfterSeconds` (del `Retry-After` si viene, si no 60),
y el repositorio apunta hasta cuándo dura el corte en `rateLimitedUntil`. Mientras
dure, `searchOnline` y `findByBarcode` **fallan sin salir a la red**, porque
insistir es lo que mantiene el corte abierto; la UI bloquea el botón, enseña la
cuenta atrás y sugiere crear el alimento a mano. El 502 y el 504 sí son caídas de
verdad y no bloquean nada.

El corte vive en el repositorio, que es único, porque el límite es por IP: da
igual desde qué pantalla se pregunte. La búsqueda online se lanza con un botón, no
al teclear, justo para no gastar el cupo. El escaneo sigue resolviendo lo que ya
esté en la caché local aunque haya corte.

**Un alimento sabe si se mide en gramos o en mililitros** (`Food.unit`). Los
valores siguen siendo por 100 de esa unidad, así que el cálculo de macros no
cambia: cambia la etiqueta, y una leche que decía "100 g" estaba mintiendo. La
unidad la deduce `OffProduct.isLiquid` mirando en orden `serving_quantity_unit`,
`product_quantity_unit` y, como último recurso, el texto de `quantity` ("1 L").
Ninguno de los tres viene siempre.

Lo que se cacheó antes de que existiera la columna se quedó en gramos —desde SQL
no hay forma de saber que algo era líquido— y se arregla de dos maneras: volviendo
a bajar el producto (el upsert trae la unidad) o a mano, con los chips de la ficha
del catálogo. `Food.servingSizeG` conserva el nombre de columna aunque pueda
guardar mililitros; renombrarla costaba una migración que no aporta nada.

**Los errores de OFF se traducen a español en `OpenFoodFactsApi.ensureSuccess()`**
y el repositorio envuelve los fallos de red en `OpenFoodFactsException`, para que
la UI nunca enseñe una excepción de Ktor.

Ambas piden un `User-Agent` identificable, que pone `HttpClientFactory`.

**El escáner de códigos usa el de Play Services** (`GmsBarcodeScanning`), que trae
su propia interfaz a pantalla completa. Por eso la app no pide permiso de cámara
ni integra CameraX. A cambio, no funciona en dispositivos sin Play Services, y
`BarcodeScanner.isAvailable()` decide si se enseña el botón. Se escanea desde tres
sitios (catálogo, añadir a una comida y añadir ingrediente a una receta) y los
tres resuelven el código igual, con `scanAndResolveFood`: cada uno solo decide qué
hacer con el alimento que sale. Si se toca el flujo, se toca ahí.

**El catálogo de alimentos y el buscador comparten `FoodSearchViewModel`**, porque
buscar en local, bajar de Open Food Facts, escanear y crear a mano es lo mismo
tanto si vas a apuntar la comida como si solo estás llenando la despensa.
`FoodCatalogScreen` usa ese ViewModel sin la parte de registrar. La lista va
topada a `searchLimit` (100) y la pantalla lo dice cuando se alcanza: con cientos
de productos cacheados, listarlos todos no ayuda a nadie.

**Las rutinas se exportan e importan en Markdown, no en JSON** (`RoutineMarkdown`).
El caso de uso manda: el usuario exporta la rutina, se la pega a un agente para que
se la revise y vuelve a importar lo que le devuelvan. Por eso la escritura es
canónica (`#` rutina, `##` día, una línea numerada por ejercicio) pero la lectura
es tolerante: acepta viñetas o numeración, `4x8-10` y `4 series × 8-10 reps`, los
guiones y dos puntos que sean, el descanso en minutos, y un ejercicio sin números
se queda con la prescripción del objetivo.

Tres detalles que costaron un test cada uno: `text/*` dentro de un KDoc abre un
comentario anidado y rompe el fichero entero; una regla `---` cierra el día para
que el pie del documento no acabe siendo un ejercicio; y `Nota:` dentro de un día
es del ejercicio, pero fuera es la descripción de la rutina, así que el orden de
los patrones importa.

Los ejercicios se reconocen por nombre normalizado contra el catálogo local (en
español o inglés, con una pasada por "contiene" para los nombres aproximados). Lo
que no se reconoce se salta y se enumera al usuario. Importar **nunca** pisa una
rutina existente: si el nombre choca, se desambigua con "(2)".

**El backup referencia los ejercicios por `wgerId`, no por id local**, porque los
ids de la base de datos no coinciden entre instalaciones. Lo mismo con los
alimentos y su código de barras. Si un ejercicio del backup no está en el catálogo
local, se omite y se cuenta en `ImportSummary.skippedExercises`.

**Las rachas se cuentan por semanas, no por días seguidos.** En una rutina los días
de descanso son parte del plan, así que encadenar días penalizaría a quien entrena
bien. Una semana cuenta si se alcanzan tantas sesiones como días tenga la rutina
activa (`StreakCalculator`).

**wger solo distingue el cardio, así que el formato del ejercicio se deduce**
(`ExerciseFormatClassifier`). Los isométricos llegaban como ejercicios de
repeticiones y la plancha pedía kilos y reps; ahora el nombre decide si va por
tiempo. El catálogo ya descargado no se arregla solo, así que `DatabaseSeeder`
lo repasa al arrancar (`fixTimeBasedExerciseTypes`), igual que con `searchName`,
y solo toca los de wger: el tipo de un ejercicio propio lo eligió el usuario.

Del mismo sitio sale si un ejercicio lleva peso. **Sin equipamiento no significa
peso corporal**: medio catálogo de wger viene sin material, press de banca
incluido, y darlo por hecho escondía el campo de kilos donde hacía falta. Solo
cuenta el nombre ("crunch", "plancha", "dominada") o un material que no carga
(esterilla, fitball, barra de dominadas). Aun así el campo se puede sacar de un
toque, porque unos fondos se lastran.

**Las series que se miden en tiempo se pueden cronometrar** desde la pantalla del
entreno: el cronómetro cuenta hacia arriba y al parar deja los segundos en el
campo. El instante de arranque vive en el ViewModel (`stopwatchStarts`), como el
descanso, porque al cambiar de app hay dispositivos que recrean la actividad y
el estado de la composición no basta. Lo que se guarda es el **instante, no la
cuenta**, y tanto este cronómetro como la cuenta atrás del descanso se recalculan
contra el reloj en cada tick: contando ticks, bloquear el móvil o irse a otra
app congelaba la cuenta, porque en segundo plano el proceso se congela y los
ticks se pierden. El pintado del cronómetro va además enganchado al reloj de
frames (`withFrameMillis`), que garantiza un recálculo en el primer frame al
volver al primer plano.

**El prerrelleno de la serie hereda primero lo apuntado hoy**: la última serie
efectiva de la sesión manda sobre la equivalente de la sesión anterior y esta
sobre el objetivo de la rutina, porque entre series casi siempre se repite peso.
Los calentamientos no sirven de referencia ni desplazan el índice contra la
sesión anterior (se cuenta con `effectiveSets`). Al registrar, el botón limpia
el foco y esconde el teclado, que abierto tapa media lista.

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

`GimnasioLogo` repite ese mismo dibujo en código común para poder enseñarlo
dentro de la app (hoy, en la cabecera de Inicio): los recursos de Android no se
pueden leer desde `commonMain`, así que las rutas están copiadas del `pathData`
del vector. **Si se retoca el icono, hay que retocar las dos.** Ahí la mancuerna
va un 18 % más grande, porque dentro de la app no hay zona segura que respetar.
Los colores van fijos y no salen del tema: es una marca y debe verse igual en
claro y en oscuro.

**Las rutas SVG las lee `svgPath`**, compartido por el mapa muscular y el
logotipo. Entiende `M`, `L`, `C`, `Q` y `Z` **absolutos**; lo demás lo descarta
junto con sus números. Ese descarte importa: cuando se comía solo la letra, los
números del comando ignorado se colaban en el anterior y acababan tumbando la
pantalla con un `NumberFormatException`.

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

**Del catálogo de wger no todo sirve para una rutina de sala.** Nadar sale con el
dorsal como músculo primario y correr con el cuádriceps, así que el generador los
daba por buenos. `GymExerciseFilter` los descarta por categoría (la 15 de wger es
cardio) y por nombre, porque hay actividades catalogadas fuera de cardio. Los
ejercicios propios del usuario no pasan por el filtro: si se crea "Nadar", lo
quiere.

**A igualdad de todo lo demás se prefiere máquina y polea, luego mancuernas y
banco, y la barra al final** (`ExerciseStyleClassifier`). wger **no tiene
equipamiento para máquina ni para polea**: esos ejercicios llegan sin material, y
lo único que los delata es el nombre ("jalón", "prensa", "en polea"). Por eso el
clasificador mira primero el nombre y solo después el equipamiento, al revés que
`ExerciseComplexityClassifier`.

**El filtro de material del generador entiende de máquinas.** Como wger no las
tiene como equipamiento, la pantalla ofrece dos chips sintéticos ("Máquinas" y
"Poleas", ids `RoutineGenerator.MACHINE_EQUIPMENT_ID`/`PULLEY_EQUIPMENT_ID`
negativos) y el generador excluye por estilo lo que no esté marcado: sin esto, a
quien entrenaba en casa con mancuernas se le proponían prensas y jalones. La
lista de material de un ejercicio se trata como **alternativas** (`any`), no como
conjunto: un press con {barra, banco} vale con cualquiera de los dos.

**Las rutinas generadas nacen con día de la semana propuesto** (L/X/V para 3
días, etc.): sin él ni los recordatorios ni la rama `Scheduled` de
`NextWorkoutPicker` tenían nada que hacer. En rutinas mixtas (unos días con
`weekday` y otros sin), los sueltos rotan cuando hoy no toca ninguno fijo; antes
desaparecían de la rotación para siempre.

**La propuesta se reequilibra antes de enseñarla, para que no nazca con avisos.**
Las plantillas reparten bien enteras, pero el tiempo disponible las recorta y ahí
aparecían los huecos ("no entrenas gemelos" ya escrito en la propuesta).
`RoutineGenerator.rebalance` cambia ejercicios que sobran por otros que faltan
midiendo con `MuscleBalanceAnalyzer`, el mismo que escribe el aviso: así lo que se
optimiza es exactamente lo que se ve. El coste pesa cada aviso por 100 y le suma
los sets que falten, que es la pendiente por la que baja cuando ningún cambio
suelto quita un aviso entero. El primer ejercicio de cada día no se toca: es el
compuesto sobre el que se monta la sesión.

Los candidatos a entrar incluyen los grupos del lado que se queda corto cuando hay
aviso de proporción (empuje/tirón o torso/pierna); sin eso no había nada que
probar y el aviso se quedaba puesto aunque ningún grupo estuviera bajo mínimos.

**Con menos de tres días o sesiones de 30 min los avisos son inevitables** y son
ciertos: no caben diez grupos a seis sets en cuatro ejercicios. La pantalla del
generador lo dice y sugiere subir días o minutos, en vez de callarlo.

**Cada ejercicio de la rutina se puede cambiar por otro del mismo grupo**
(`RoutineDetailViewModel.loadAlternatives` + `replaceRoutineExercise`). Las
alternativas se ordenan por parecido: primero las del mismo carácter (compuesto
por compuesto), luego por material preferido y por complejidad. El cambio conserva
series, descanso y posición; solo rehace las repeticiones, porque el ejercicio
nuevo puede ser de tiempo y el viejo de repeticiones.

**El generador nunca repite ejercicio en la semana, ni siquiera para tapar un
hueco.** Las plantillas de día traen más huecos de los que se usan, ordenados de
más a menos importante, y el tiempo disponible decide dónde se corta
(`SessionTimeEstimator`). Si un grupo agota sus ejercicios a mitad de semana el
hueco se queda sin rellenar, porque ese grupo ya está entrenado; solo se avisa en
`uncoveredGroups` cuando no hay **ningún** ejercicio utilizable para el grupo, que
es lo que sí indica un problema de material o de nivel.

**Las series se eligen a mano en el generador y en la rutina.** En el generador
son parte de la prescripción (`setsPerExercise`), así que subirlas baja el número
de ejercicios que caben en la misma sesión: es el mismo tiempo repartido. En una
rutina ya creada se cambian desde el diálogo de editar y se aplican a todos los
ejercicios de golpe; el valor que se enseña es la cifra más repetida, y si no se
toca no se escribe nada, para no pisar los ajustes hechos ejercicio a ejercicio.

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
se ignoran. `searchTerms` quita además `%` y `_`, que son comodines de `LIKE` y
venían del teclado tal cual.

**Los alimentos tienen su propio `Food.searchName`** (nombre y marca,
normalizados) desde la versión 4 del esquema, por el mismo motivo: "cafe" no
encontraba "Café". Lo rellenan los tres caminos de escritura (upsert de OFF,
alta manual y restauración) y `DatabaseSeeder.fillMissingFoodSearchNames` lo
completa en las bases anteriores a la columna.

**Las palabras clave de los clasificadores casan en límite de palabra**
(`containsWordStarting` en `util/TextSearch.kt`), no como subcadena: "pino"
vivía dentro de "supino" y vetaba a principiantes cualquier curl con ese agarre,
y "andar" dentro de "estándar" descartaba ejercicios de sala. Basta el límite
inicial, así los prefijos tipo "explosiv" siguen cazando "explosivo/explosiva".
Si añades una palabra clave a un clasificador, piensa en qué otras palabras la
contienen.

**Las sugerencias son el aviso de balance ya resuelto.** `RoutineAdvisor` toma el
`BalanceReport`, los ejercicios de la rutina y el catálogo, y devuelve altas
("añade X al día Y") y cambios ("cambia X por Y"). Nunca propone un ejercicio que
ya esté en la rutina, nunca sacrifica un compuesto al sugerir un cambio y no da
dos consejos del mismo grupo. Se recalculan en cada `reload()`, así que lo que se
descarte con "Ahora no" vuelve al reabrir la pantalla.

**El plan de dieta es semanal: cada comida cuelga de un día** (`DietPlanMeal.dayOfWeek`,
ISO con lunes = 1). "Aplicar a hoy" vuelca solo el menú del día de la semana que
toca, no el plan entero, y los objetivos de calorías y macros del plan son
diarios, así que la pantalla compara contra el día que se esté viendo
(`plannedMacros(dayOfWeek)`), nunca contra el total de la semana. Rellenar siete
días a mano era el precio de esto, y lo paga `copyDietPlanDay`, que reemplaza el
día destino en vez de sumarse a él.

**Al copiar un día a otra rutina el día de la semana solo viaja si está libre**
en el destino (`copyDayToRoutine`): dos días el mismo martes harían que el
recordatorio avisara dos veces. El nombre solo se desambigua si choca.

**Una receta apuntada en el diario no se puede borrar.** `FoodLogEntry.recipeId` y
`DietPlanMeal.recipeId` referencian la receta **sin `ON DELETE`**, así que borrarla
lanzaba un fallo de clave foránea que en el móvil es un cierre de la app.
`deleteRecipe` devuelve ahora `false` si está en el diario —que es el registro de
lo que se comió y no se reescribe— y sí la quita de los planes de dieta, que son
plantillas. `deleteFood` y `deleteCustomExercise` siguen el mismo patrón: `false`
si el alimento está en el diario o en una receta, o el ejercicio en una rutina o
en el historial; la UI se lo explica al usuario en vez de reventar.

**Los ejercicios y alimentos propios se crean y se editan desde la app**
(`CustomExerciseDialog` en el catálogo y su ficha; `CustomFoodDialog` con
`initial` para editar desde la ficha del catálogo de alimentos). Los grupos
musculares del alta se materializan como el primer músculo de wger de cada grupo:
el balance agrega por grupo y con uno basta. Los de wger no se editan: la
sincronización los pisaría.

**Lo que se borra de un toque pregunta antes** (`ConfirmDeleteDialog`): recetas e
ingredientes, que son los que tienen la papelera pegada a la lista.

**Nutrición tiene portada, como Entrenamiento.** `NutritionScreen` es la pestaña y
reparte a diario, planes, recetas y catálogo; el diario vive en `FoodDiaryRoute`.
Antes todo colgaba del diario y había que descubrir que los iconos de la barra
llevaban a otra parte. La portada lleva además el gráfico de calorías de las dos
últimas semanas, con los días sin registro a cero: saltárselos dibujaría una línea
que engaña.

**El calendario también marca lo comido.** Los puntos de entrenamiento son del
color primario y el de la comida del terciario, y al elegir un día se ven sus
calorías. `dailyCalories` recorre solo los días con registro (`selectFoodLogDates`)
y llama a `dayNutrition`: hacerlo en SQL obligaría a replicar en la consulta el
escalado de recetas por raciones.

**Lo de la sesión anterior se queda a la vista mientras entrenas**, con la serie
que toca en negrita, en vez de desaparecer al apuntar la primera. Es contra lo que
se compite serie a serie, y la fecha (`previousDate`) sitúa de cuándo son. Las
series y la fecha salen de **la misma consulta** (`selectPreviousSetsForExercise`,
elegida por fecha y no por id): antes venían de dos consultas con criterios
distintos y una sesión retrodatada enseñaba los números de una con la fecha de
otra.

**Las series de aproximación se marcan con el chip "Calentamiento"** al
registrarlas: no cuentan para el objetivo, el volumen, las kcal ni la progresión,
y no disparan el descanso completo. `updateSet` conserva `isWarmup` y `rpe` si no
se pasan: corregir un peso no debe convertir un calentamiento en serie efectiva.
El RPE se apunta al corregir la serie desde el resumen y alimenta el consejo de
deload.

**`ProgressionAdvisor` también sabe bajar.** Tres sesiones con el mismo peso sin
llegar al tope proponen un deload (~10 %, redondeado a múltiplos de 2,5) con
`isDeload = true`; la subida sigue siendo la doble progresión de siempre. El peso
de referencia es la mejor serie de la sesión, no la primera, que puede ser una
aproximación. Los ejercicios por tiempo tienen su regla equivalente
(`suggestTime`): dos sesiones aguantando el objetivo en todas las series proponen
subirlo 15 s.

**Los récords se celebran en el resumen** (`newRecordsInSession`): peso, 1RM
estimado y tiempo de la sesión contra todo lo anterior a ella. Solo con
historial previo: en la primera sesión de un ejercicio todo sería récord y no
diría nada.

**Borrar una serie o una entrada del diario no pide confirmación: ofrece
deshacer** (snackbar que reinserta con `restoreSet`/`restoreLogEntry`). La
confirmación con diálogo queda para lo grande (sesiones, días de rutina, planes).
Por eso `SetLogItem` lleva `rpe` y `FoodLogItem` lleva `foodId`/`recipeId`: sin
las referencias originales el deshacer no podría reinsertar lo mismo.

**"Repetir ayer" copia una comida del día anterior**
(`repeatMealFromPreviousDay`), idempotente como "Aplicar a hoy": lo ya apuntado
idéntico no se duplica, y devuelve cuántas copió para poder decir "ayer no
apuntaste nada".

**El gesto de atrás pasa por `AppBackHandler`** (`platform/BackHandler.kt`,
expect/actual: Android envuelve activity-compose y en iOS es no-op). En el
entreno activo descarta la sesión vacía igual que la X; sin esto quedaban
sesiones huérfanas reapareciendo en Inicio.

**El balance calórico no suma el entreno al TDEE**: el factor de actividad ya lo
incluye por definición ("Moderado (3-5 días/semana)") y sumarlo encima lo contaba
dos veces. `CalorieBalance.exerciseKcal` queda solo como detalle informativo. La
tendencia de peso de Inicio es una media móvil de 7 días con ritmo semanal
(`WeightTrendCalculator`), no la resta de las dos últimas mediciones. Con el
peso objetivo del perfil (`UserProfile.targetWeightKg`, migración 5),
`projectedDate` estima cuándo se llega; se calla si el ritmo es ruido (<0,05
kg/sem), aleja del objetivo o la fecha cae a más de un año.

**La apariencia vive en `ThemeSettings`** (singleton de Koin, persistido en
`Settings`): claro/oscuro/sistema y Material You. Lo lee `App()` por encima de
la navegación y lo escribe Ajustes directamente con `koinInject`, sin pasar por
un ViewModel. El color dinámico es un `expect/actual`
(`platformDynamicColorScheme`): Android 12+ lo da y en iOS devuelve null, con la
paleta lima de siempre como respaldo. `KeepScreenOn` sigue el mismo patrón
(vista de Android / `idleTimerDisabled` en iOS) y mantiene la pantalla encendida
solo durante el entreno.

**Los ejercicios de la rutina se reordenan arrastrando el tirador**
(`DragHandle` con `detectDragGesturesAfterLongPress`): el orden se mueve en un
estado local mientras dura el gesto y se escribe de una vez al soltar
(`reorderRoutineExercises`, transaccional y tolerante a ids borrados a mitad).
Escribir en la base en cada cruce de fila provocaba recargas a mitad de gesto.
`moveRoutineExercise` (±1) sigue existiendo y probado, pero la UI ya no lo usa.

**En el entreno las tarjetas se pliegan solas al completar el objetivo** (y se
reabren tocando la cabecera): siete ejercicios con historial era un scroll
kilométrico. Las animaciones se limitan a los momentos de premio: el check con
rebote, el color de la tarjeta y la barra de descanso vaciándose en continuo.

**Los umbrales del balance muscular dependen del nivel**
(`MuscleBalanceAnalyzer.analyze(entries, level)`): 4-15 sets para principiante,
6-20 genérico, 8-25 avanzado. El generador pasa su nivel para que el aviso y el
reequilibrado midan lo mismo; la pantalla de rutina usa el genérico porque la
rutina no guarda nivel.

**El entrenamiento en curso se refresca en dos niveles.** `reload()` recarga todo
(plan, prerrelleno e historial de progresión) y solo se llama al abrir la
pantalla; al apuntar o borrar una serie se usa `refreshLoggedSets()`, que solo
relee las series de la sesión. Recargar entero en cada serie disparaba una
veintena de consultas para recalcular cosas que dependen de sesiones anteriores y
no cambian mientras entrenas.

## Estado

Están hechas todas las funcionalidades planificadas: inicio con racha, logros y
tendencia de peso, catálogo de ejercicios (con altas y edición de ejercicios
propios), rutinas con aviso de descompensación, mapa muscular y ejercicios
reordenables, generador automático de rutinas por tiempo, nivel y material (con
días de la semana propuestos), registro de series con calentamientos, cronómetro
de descanso, sugerencia de subida de peso y deload, récords personales con 1RM
estimado (Epley), calendario, métricas corporales, gasto calórico por METs,
diario de nutrición editable con balance calórico, recetas (renombrables, que se
pueden apuntar en el diario y meter en el plan), planes de dieta semanales
editables, catálogo de alimentos propio con edición y borrado, escáner de código
de barras, recordatorios, copia de seguridad con exportación a CSV e icono
propio. Además: repetir la comida de ayer en el diario, récords celebrados en el
resumen, pantalla encendida durante el entreno con tarjetas plegables y
progresión también por tiempo, deshacer en los borrados pequeños, reordenado por
arrastre, peso objetivo con proyección de llegada, selector de tema con Material
You opcional, transiciones de navegación y gráficas consultables al toque.

Ideas que quedaron fuera: fotos de progreso, contador de hidratación, Health
Connect, widget de pantalla de inicio y usar `variation_group` de wger como
fuente de alternativas.

Probado en dispositivo por el usuario. En el equipo de desarrollo no hay emulador
ni suele haber un móvil conectado, así que los cambios de UI no se pueden
verificar visualmente desde aquí.
