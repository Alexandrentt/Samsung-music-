# Changelog — Música

Registro de cambios de la app Android "Música". Cada versión se publica
automáticamente en GitHub Releases (CI "Build Android APK") y como
`samsung-music.apk` en la raíz del repositorio.

## 2.3 (versionCode 14) — 2026-09-25

### Duplicados con código al final (raíz corregida)
- Al escanear, los nombres con sufijo basura ("Sunsetz 5-rbSNzU",
  "fanshop supernova mZyXw1") se limpian ANTES de crear la fila y, si el
  sufijo es un ID de YouTube real, se deriva el ID canónico: la fila del
  escaneo nace con la MISMA clave que la canónica → REPLACE, no duplicado.
- `parseMediaFileName` centraliza el parsing (con pruebas unitarias
  nuevas `SongMatchingTest`): ID canónico, ID truncado y sufijos basura.
  Los títulos reales no se tocan ("Verano 2024", "Song 2").

### Reescaneo
- Ahora también escanea la carpeta SAF elegida por el usuario, no solo
  /Music/SamsungMusic y la interna.
- Y purga las PORTADAS huérfanas (covers/cover_*.jpg que ya no usa
  ninguna fila): respondía el usuario que "siguen guardadas en algún
  lado". El resumen del reescaneo informa cuántas se borraron.

### Arranque
- Secuencial y estable: recuperar → fusionar duplicados → migrar
  metadatos → purgar portadas → enriquecer con MusicBrainz. Antes las
  correcciones corrían en paralelo y se deshacían entre sí.
- El dedupe agrupa por videoId / filePath / título SIN sufijo basura
  (`SongMatching.titleKey`), con fusión débil↔fuerte por prefijo o typo
  (ratio ≥82). Nunca fusiona filas con videoIds reales distintos.

### Playbar
- Fuera el hueco izquierdo fijo con la nota musical (el "espacio muerto"):
  miniatura de portada solo si existe; sin portada el texto arranca donde
  empieza la barra y los tres botones conservan su tamaño completo.

## 2.0 (versionCode 11) — 2026-09-25

### Rendimiento
- `MediaPlayer.prepare()` ya no corre en el hilo principal: la UI no se
  congela al cambiar de canción.
- El escaneo de recuperación omite el probe (MediaPlayer) de archivos cuyo
  tamaño ya figura en la base de datos.
- Notificación de reproducción cada 5 s en vez de cada 2 s (cada tick
  re-renderizaba RemoteViews + widgets).

### Correcciones
- Race de cola: el audio ya no se queda en la canción anterior al saltar;
  se detiene el reproductor viejo antes de preparar el nuevo y se descartan
  preparaciones obsoletas.
- Enriquecimiento MusicBrainz con umbral 75 (antes 40): "Si estuviera
  contigo" ya no toma el autor de otra obra homónima.
- Frases "YouTube Music"/"topic" eliminadas de títulos y migración de BD
  `YouTube Music` → `Descargas`.

### Nuevas funciones
- Editor de metadatos por canción (título/artista/álbum) en el menú ⋮.
- Subtítulo "Sin letra" cuando la canción no tiene lyrics.
- Carpeta de música (SAF) estilo Samsung Music: escanea la carpeta elegida
  y sus subcarpetas (2 niveles), persistente y quitable.
- Tarjeta de progreso de descargas rediseñada (% grande, bytes, "canción
  i de n") y cola con barras animadas en la fila que suena.

## 1.9 (versionCode 10) — 2026-09-25

- Notificación del reproductor rediseñada: portada cuadrada a la izquierda
  (76/104 dp) sobre tarjeta oscura; fuera el arte full-bleed con controles
  encima y el pill "Este teléfono".
- Playbar: progreso redondeado, tiempo "1:23 / 3:45" en el subtítulo,
  portada limpia sin badge.

## 1.8 / 1.7 (versionCode 9 / 8) — 2026-09-25

- Dedupe reforzado: fusión difusa (Levenshtein ≥80, umbral corregido de 85
  tras calcular "perdn"/"perdon" = 83) y fusión de filas débiles con el
  mismo título y audio idéntico (duración ±1,5 s, tamaño ±2 %).
- Toolbar de canciones: el conteo ya no colapsa en vertical en pantallas
  estrechas.

## 1.6 (versionCode 7) — 2026-09-25

- Crash al reproducir arreglado (stack trace del usuario vía CrashHandler):
  `<View>` genérico no permitido en RemoteViews de la notificación;
  `safeRemoteViews()` valida cada layout antes de publicarlo.

## 1.5 (versionCode 6) — 2026-09-25

- Menú ⋮ → "Registro de errores": ver/compartir/borrar `crash_log.txt`
  desde la app (sin acceso a Android/data).
- Menú ⋮ → "Reescanear canciones" con resumen en Toast.
- Dedupe por título cruzando débil/fuerte y migración de artista
  "Samsung Music" heredado.

## 1.4 (versionCode 5) — 2026-09-25

- `CrashHandler`: stack traces en `logs/crash_log.txt` (recorte a 512 KB).
- Blindaje de reproducción y dedupe inicial (ID canónico de 11 chars).
