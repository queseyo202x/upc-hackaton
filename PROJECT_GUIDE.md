# MyApplication: resumen y guía técnica

## Resumen

Aplicación Android de demostración que recibe un mensaje, lo clasifica con Gemini y registra
el evento en Cloud Firestore. La respuesta exige JSON estructurado y usa streaming SSE para
medir la latencia de cada modelo. La interfaz muestra el resultado, la severidad, el modelo,
el tiempo en milisegundos, el código HTTP y los errores de cada intento.

Los únicos modelos configurados son:

- `gemini-2.5-flash-lite`
- `gemini-3.5-flash`
- `gemini-3.6-flash`

El orden prioriza `gemini-2.5-flash-lite`. Si un modelo no está habilitado o devuelve un
error HTTP, el intento se muestra en pantalla y se continúa con el siguiente. Si al menos uno
responde correctamente, se usa la primera clasificación válida.

## Arquitectura

```text
MainActivity
  -> GeminiClient
       -> Gemini streamGenerateContent (SSE)
       -> JSON estructurado
       -> ModelAttempt por modelo
       -> AnalysisResult
  -> FirestoreEventRepository
       -> Cloud Firestore /events/{autoId}
```

### Dashboard del padre o tutor

La rama `feature/tutor-dashboard` añade una vista separada para el adulto responsable:

- Desde la pantalla de análisis se entra con **Panel del tutor**.
- `DashboardActivity` consulta los últimos 100 documentos de `events`.
- Las tarjetas resumen eventos del día, eventos de severidad alta y contactos detectados.
- El reporte prioritario muestra el primer evento de alto riesgo.
- La línea de tiempo presenta categoría, severidad, contacto, fragmento y fecha de cada evento.
- **Volver al análisis** cierra la vista y conserva la pantalla de captura.

Esta primera versión es una vista operativa basada en los datos actuales de Firestore. Antes
de producción se debe añadir autenticación y reglas de seguridad para que cada tutor solo pueda
leer los eventos de los menores asociados a su cuenta. También conviene reemplazar los nombres
estáticos de la maqueta por datos del perfil autenticado.

#### Plan recomendado para continuar

1. **Cerrar el MVP visual:** probar la pantalla en un teléfono pequeño y grande, ajustar
   textos y colores, y validar los estados sin eventos, cargando y error de red.
2. **Agregar identidad:** incorporar Firebase Authentication, guardar `parentId` y `childId`
   en cada evento, y sustituir los nombres de ejemplo por el perfil real.
3. **Asegurar Firestore:** publicar reglas que permitan leer únicamente eventos del menor
   asociado al tutor; no confiar en filtros hechos solo desde Android.
4. **Completar estadísticas:** añadir filtro por periodo/contacto, gráfico de tendencias y
   detalle de reporte con la conversación o fragmento permitido.
5. **Cerrar el producto:** añadir pruebas de repositorio y UI, accesibilidad, estados offline,
   paginación y una revisión de privacidad/políticas de AccessibilityService antes de distribuir.

### Capas y archivos

- `MainActivity.kt`: pantalla, entrada del mensaje, cuadros de diagnóstico y confirmaciones.
- `GeminiClient.kt`: prompt, modelos permitidos, HTTP, streaming, parseo, latencia y errores.
- `AnalysisModels.kt`: `AnalysisResult`, categorías, severidades y `ModelAttempt`.
- `FirestoreEventRepository.kt`: escritura asíncrona de documentos en `events`.
- `DashboardActivity.kt`: consulta y presentación de estadísticas y reportes para el tutor.
- `activity_dashboard.xml`: layout oscuro inspirado en la referencia visual.
- `app/build.gradle.kts`: dependencias, Google Services y la API key de desarrollo.
- `app/google-services.json`: configuración del proyecto Firebase Android.
- `.idea/mcp.json`: configuración local del Firebase MCP para Windows con `npx.cmd`.

## Crear el proyecto desde cero

### 1. Requisitos

- Android Studio y JDK compatible con el Android Gradle Plugin del proyecto.
- Node.js y npm para Firebase MCP.
- Una cuenta de Firebase y una API key de Gemini.

### 2. Crear Android

1. En Android Studio crea un proyecto Empty Views Activity con Kotlin.
2. Usa el namespace `com.example.myapplication`.
3. Agrega permiso de Internet en `AndroidManifest.xml`.
4. Activa `buildFeatures.buildConfig`.
5. Agrega Firebase Firestore y el plugin Google Services.

Dependencias principales:

```kotlin
implementation(libs.firebase.firestore)
implementation(libs.androidx.activity.ktx)
implementation(libs.androidx.appcompat)
implementation(libs.material)
```

### 3. Conectar Firebase

1. Crea un proyecto en Firebase Console.
2. Registra una aplicación Android con el mismo `applicationId`.
3. Descarga `google-services.json` y colócalo en `app/google-services.json`.
4. Habilita **Build > Firestore Database**.
5. Selecciona una ubicación y crea la base de datos.
6. En desarrollo usa reglas adecuadas para pruebas; antes de producción exige autenticación
   y limita la escritura a usuarios autorizados.

### 4. Configurar Gemini

En `local.properties` agrega una clave solo para desarrollo:

```properties
geminiApiKey=TU_API_KEY
```

El Gradle la expone como `BuildConfig.GEMINI_API_KEY`. No subas `local.properties`, no
incluyas la clave en documentación y no compartas APKs de desarrollo con la clave embebida.

### 5. Implementar Gemini

`GeminiClient` envía `POST` a:

```text
https://generativelanguage.googleapis.com/v1beta/models/{modelo}:streamGenerateContent?alt=sse
```

El cuerpo solicita:

```json
{
  "generationConfig": {
    "temperature": 0,
    "responseMimeType": "application/json"
  }
}
```

El prompt exige exactamente `categoria` y `severidad`, usando únicamente las cuatro categorías
`grooming`, `acoso sexual`, `ciberbulying` y `coacción/intimidacion`, y las severidades
`bajo`, `medio` y `alto`. Cada modelo se mide con
`System.nanoTime()`. La respuesta SSE se concatena, se limpia de fences Markdown y se valida.
Los errores HTTP conservan código y cuerpo para mostrarlos en el cuadro del modelo.

### 6. Guardar en Firestore

`FirestoreEventRepository.saveEvent` crea un documento automático en la colección `events`.
Cada documento contiene:

```text
message    mensaje original
categoria  grooming, acoso sexual, ciberbulying o coacción/intimidacion
severidad  bajo, medio o alto
mensaje    mensaje original
contacto   nombre del contacto simulado
hora       timestamp del servidor
```

La app confirma el guardado mediante el estado de la pantalla y un Snackbar. Para verlo:
Firebase Console -> proyecto -> Build -> Firestore Database -> Data -> `events`.

## Firebase MCP en Windows

El error original ocurría porque el cliente MCP intentaba iniciar `npx` como ejecutable y
Windows no lo encontraba. `.idea/mcp.json` usa `npx.cmd`:

```json
{
  "mcpServers": {
    "firebase-mcp-server": {
      "command": "npx.cmd",
      "args": ["-y", "firebase-tools@latest", "mcp", "--dir",
        "C:\\Users\\Manue\\AndroidStudioProjects\\MyApplication"]
    }
  }
}
```

Comprueba antes que `node --version` y `npm --version` funcionen en la terminal que usa
Android Studio. El MCP usa las credenciales del Firebase CLI local; inicia sesión con
`firebase login` si el cliente lo solicita.

## Problemas resueltos

- **MCP no iniciaba en Windows:** se cambió `npx` por `npx.cmd`.
- **No se guardaban mensajes normales:** todos los análisis se guardan como eventos, incluidos
  `ok`.
- **No se veía por qué fallaba Gemini:** cada modelo muestra latencia, HTTP y cuerpo del error.
- **Respuesta no estructurada:** se exige `application/json` y se valida el esquema.
- **Streaming ausente:** se usa `streamGenerateContent` con SSE.
- **Modelos obsoletos:** la configuración actual está limitada a los tres IDs solicitados; si
  un ID no está habilitado por la cuenta, aparecerá explícitamente como error HTTP y se probará
  el siguiente.

## Compilar y probar

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
```

Instala el APK debug en un dispositivo o emulador con Internet, envía un mensaje y comprueba
que aparezcan los tres cuadros de modelos, la confirmación de Firestore y un documento nuevo
en `events`.
