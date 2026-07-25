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

**Cambiar el esquema exige una migración, y `SchemaMigrationTest` lo obliga.**
Hay una instantánea del esquema en `androidUnitTest/resources/schema-snapshot.sql`
y el test falla si los `.sq` dejan de cuadrar con ella. Para un cambio
intencionado: añade `<versión>.sqm` junto a los `.sq` con el `ALTER TABLE`
correspondiente, borra la instantánea y vuelve a ejecutar los tests, que la
regeneran. Sin esto, la app instalada se rompería al actualizar, porque SQLDelight
solo migra si la versión del esquema sube (y sube con cada `.sqm`).

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

**Las dependencias de lifecycle y navegación tienen que ser las de
`org.jetbrains.androidx`, no las de `androidx` a secas.** Las de AndroidX no
publican targets de Apple todavía; las de JetBrains los añaden y delegan en
ellas. Cambiarlas a `androidx.*` rompe la resolución de iOS (probado). El precio
son los avisos de "same unique_name" del resolvedor de klibs, que por ahora son
esperables y no rompen nada.

## Estado

Están hechas todas las funcionalidades planificadas: inicio con racha y logros,
catálogo de ejercicios, rutinas con aviso de descompensación, mapa muscular y
ejercicios reordenables, generador automático de rutinas, registro de series con
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
