# Samsung Music - Android App

Una recreación moderna y minimalista de la experiencia de **Samsung Music (One UI)** construida en **Kotlin** y **Jetpack Compose** para Android. Incluye soporte para reproducción en segundo plano, descarga de música de YouTube (canciones individuales y listas enteras), letras sincronizadas en tiempo real (`.lrc`), ecualizador SoundAlive y gestión de listas de reproducción personalizadas con base de datos local **Room**.

---

## ✨ Características Principales

- 📱 **Diseño Fiel One UI (Samsung Music)**:
  - Navegación fluida por pestañas: **Canciones**, **Listas**, **Álbumes**, **Artistas**, **Favoritos** y **Descargas YT**.
  - Mini-reproductor persistente inferior y pantalla de reproducción completa deslizable con carátula expandida.
  - Paleta de color One UI Blue (#2176FF / #0056D2) con soporte para modo oscuro y bordes redondeados ergonómicos.

- 🎧 **Reproducción en Segundo Plano**:
  - Servicio en primer plano (*Foreground Service*) y compatibilidad con `MediaSession`.
  - Controles de reproducción en barra de notificaciones del sistema y pantalla de bloqueo.
  - Control de volumen independiente, modos de repetición (Todo / Una pista) y reproducción aleatoria.

- 🎤 **Letras Sincronizadas (.lrc)**:
  - Visualización de letras en tiempo real sincronizadas con la marca de tiempo de la pista.
  - Salto táctil interactivo: toca cualquier estrofa para saltar a ese punto exacto de la canción.

- 🎛️ **Ecualizador SoundAlive**:
  - Curva de ecualización gráfica de 7 bandas (60 Hz a 14 kHz).
  - Presets preconfigurados estilo One UI: *Normal*, *Pop*, *Rock*, *Jazz*, *Clásica*, *Vocal*, *Club*, *Refuerzo de graves* y *Personalizado*.
  - Efectos de *Bass Boost* y *Virtualizador 3D*.

- 📂 **Gestión de Listas de Reproducción con Room Database**:
  - Almacenamiento local seguro de listas creadas por el usuario usando **Room (SQLite)**.
  - Operaciones CRUD completas: crear listas, renombrar, actualizar descripción, asociar canciones, reordenar y eliminar listas con borrado en cascada.
  - Menú contextual de 3 puntos en cada pista: "Añadir a lista...".

- 📥 **Gestor de Descargas de YouTube**:
  - Descarga pistas individuales o listas de reproducción completas a partir de enlaces de YouTube o YouTube Music.
  - Detección automática y extracción de metadatos (título, artista, duración y carátula).
  - Enriquecimiento automático de metadatos vía **MusicBrainz** y **Cover Art Archive**.

---

## 🏗️ Arquitectura y Tecnologías

- **Lenguaje:** Kotlin 2.0+
- **Interfaz:** Jetpack Compose con Material Design 3
- **Patrón de Diseño:** MVVM (Model-View-ViewModel) con `StateFlow` reactivo
- **Base de Datos:** Room Database con KSP (Kotlin Symbol Processing)
- **Carga de Imágenes:** Coil (AsyncImage)
- **Motor de Audio:** Android MediaPlayer & MediaSession
- **Concurrencia:** Kotlin Coroutines & Asynchronous Flows

---

## 🚀 Cómo Subir a GitHub y Publicar Releases

### 1. Vincular el Proyecto a GitHub desde AI Studio
En la barra superior de **Google AI Studio**, haz clic en el botón de **GitHub** / **Export**:
- Selecciona **"Push to GitHub"** o **"Export to GitHub repository"**.
- Elige tu cuenta y crea un nuevo repositorio (por ejemplo, `samsung-music-android`).

### 2. Generar un Release con el APK Automáticamente
El proyecto incluye un flujo de trabajo configurado en `.github/workflows/release.yml`. Para generar un nuevo Release con el APK adjunto:

1. Ve a tu repositorio en GitHub y abre la pestaña **Releases**.
2. Haz clic en **"Draft a new release"**.
3. En la casilla **Tag version**, ingresa una etiqueta como `v1.0.0` y haz clic en **"Create new tag: v1.0.0"**.
4. Escribe un título y descripción para tu versión.
5. Haz clic en **"Publish release"**.
6. GitHub Actions compilará automáticamente el proyecto y adjuntará el archivo `app-debug.apk` directamente en la sección de descargas del Release.

### 3. Compilación Local desde Terminal
Si clonas el repositorio en tu computadora:

```bash
# Compilar el APK en modo debug
./gradlew :app:assembleDebug

# El archivo APK generado se ubicará en:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📦 Estructura del Proyecto

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/example/
│   │   │   ├── data/              # Base de datos Room (Entities, DAOs, Database, Models)
│   │   │   │   ├── AppDatabase.kt
│   │   │   │   ├── Playlist.kt
│   │   │   │   ├── PlaylistDao.kt
│   │   │   │   ├── Song.kt
│   │   │   │   ├── SongDao.kt
│   │   │   │   └── MusicRepository.kt
│   │   │   ├── player/            # Motor de reproducción y sincronización de letras
│   │   │   │   ├── AudioPlayerManager.kt
│   │   │   │   └── MusicPlaybackService.kt
│   │   │   ├── downloader/        # Gestor de descargas de audio y enriquecimiento de metadatos
│   │   │   │   ├── YouTubeAudioDownloader.kt
│   │   │   │   └── MetadataEnricher.kt
│   │   │   ├── ui/                # Componentes y pantallas en Jetpack Compose
│   │   │   │   ├── SamsungMusicApp.kt
│   │   │   │   ├── SamsungMusicViewModel.kt
│   │   │   │   ├── components/
│   │   │   │   │   ├── PlaylistsTabContent.kt
│   │   │   │   │   ├── NowPlayingSheet.kt
│   │   │   │   │   ├── MiniPlayerBar.kt
│   │   │   │   │   ├── SongItemRow.kt
│   │   │   │   │   ├── SoundAliveDialog.kt
│   │   │   │   │   └── DownloadTabContent.kt
│   │   │   │   └── theme/         # Tipografía, colores One UI y tema M3
│   │   │   └── AndroidManifest.xml
│   │   └── res/                   # Recursos de imagen, iconos adaptativos y strings
│   └── test/                      # Pruebas unitarias locales (Robolectric y Room in-memory)
│       └── java/com/example/
│           └── PlaylistDatabaseTest.kt
├── .github/
│   └── workflows/
│       └── release.yml            # Automatización de GitHub Releases y compilación de APK
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 📄 Licencia

Este proyecto fue desarrollado como demostración técnica de Jetpack Compose y la experiencia One UI de Samsung Music. Distribuido bajo la licencia MIT.
