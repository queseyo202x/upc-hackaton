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

### Capas y archivos

- `MainActivity.kt`: pantalla, entrada del mensaje, cuadros de diagnóstico y confirmaciones.
- `GeminiClient.kt`: prompt, modelos permitidos, HTTP, streaming, parseo, latencia y errores.
- `AnalysisModels.kt`: `AnalysisResult`, categorías, severidades y `ModelAttempt`.
- `FirestoreEventRepository.kt`: escritura asíncrona de documentos en `events`.
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

El prompt exige exactamente `categoria`, `severidad` y `fragmento`. Cada modelo se mide con
`System.nanoTime()`. La respuesta SSE se concatena, se limpia de fences Markdown y se valida.
Los errores HTTP conservan código y cuerpo para mostrarlos en el cuadro del modelo.

### 6. Guardar en Firestore

`FirestoreEventRepository.saveEvent` crea un documento automático en la colección `events`.
Cada documento contiene:

```text
message    mensaje original
fragment   fragmento devuelto por Gemini
category   ok, grooming o contenido_sexual
severity   ninguna, baja, media o alta
contact    nombre del contacto simulado
model      modelo que produjo la clasificación usada
latencyMs  tiempo de respuesta en milisegundos
streamed   true cuando se procesó SSE
createdAt  timestamp del servidor
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

## Prueba de WhatsApp mediante AccessibilityService

La rama `feature/whatsapp-accessibility-monitor` añade
`WhatsappAccessibilityService`. Android lo ejecuta solo después de que el usuario lo active
manualmente en **Ajustes -> Accesibilidad -> Aplicaciones instaladas -> Analizador de mensajes
de WhatsApp**.

El servicio:

- Rechaza eventos que no procedan del paquete `com.whatsapp`.
- Obtiene el texto del evento y, si es necesario, del contenido visible de la ventana.
- Ignora texto vacío y mensajes mayores de 1.000 caracteres.
- Ignora duplicados usando SHA-256.
- Permite una sola solicitud Gemini activa.
- Espera al menos 8 segundos entre solicitudes.
- Escribe el análisis válido en `events` con `contact = "WhatsApp"`.
- Registra en Logcat el modelo, código HTTP y latencia de cada intento.
- Publica en la actividad el reconocimiento, cada intento de modelo y el resultado real de
  Firestore; la interfaz muestra si el mensaje fue reconocido, analizado y guardado.

Para probarlo:

1. Instala el APK debug en un teléfono de pruebas.
2. Abre la pantalla de Accesibilidad y activa el servicio.
3. Concede la confirmación del sistema.
4. Abre una conversación de WhatsApp y recibe un mensaje.
5. Usa `adb logcat -s WhatsappAccessibility` para ver los intentos.
6. Revisa el documento creado en Firestore en la colección `events`.

La pantalla principal debe estar abierta para ver el flujo en vivo. Si estaba en segundo plano,
el servicio sigue procesando y guardando el evento, pero la pantalla solo podrá mostrar nuevos
estados cuando vuelva a abrirse.

El servicio no envía mensajes ni responde en WhatsApp. Solo lee el texto que Android expone al
servicio y lo envía al flujo existente de análisis. AccessibilityService requiere consentimiento
explícito del usuario y es una solución de prueba: su comportamiento depende de la versión de
Android y de la interfaz de WhatsApp. Google Play y las políticas de WhatsApp restringen
fuertemente este permiso; esta implementación está orientada a APK de prueba, hackatón o uso
académico, no a publicar sin una revisión de políticas y privacidad.
