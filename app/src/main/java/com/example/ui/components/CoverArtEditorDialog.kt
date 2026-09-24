package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CoverArtEditorDialog(
    song: Song,
    onSaveCover: (songId: String, localCoverPath: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Web, 1: Galería, 2: URL
    var searchQuery by remember {
        mutableStateOf("${song.title} ${song.artist} portada")
    }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var directUrlInput by remember { mutableStateOf("") }
    var webProgress by remember { mutableFloatStateOf(0f) }
    var isWebLoading by remember { mutableStateOf(true) }

    // Launcher para seleccionar imagen de la galería del dispositivo
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isDownloading = true
                val savedPath = saveImageFromUri(context, song.id, uri)
                isDownloading = false
                if (savedPath != null) {
                    onSaveCover(song.id, savedPath)
                    Toast.makeText(context, "Portada actualizada con foto de galería", Toast.LENGTH_SHORT).show()
                    onDismiss()
                } else {
                    Toast.makeText(context, "No se pudo leer la imagen de la galería", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Barra superior
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "Editar portada",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${song.title} • ${song.artist}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Miniatura portada actual
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = song.coverArtUrl,
                            contentDescription = "Portada actual",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // Pestañas: Navegador Web | Galería | Enlace URL
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Buscador Web", fontSize = 13.sp) },
                        icon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Galería", fontSize = 13.sp) },
                        icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("URL Directa", fontSize = 13.sp) },
                        icon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                // Contenido según pestaña
                when (selectedTab) {
                    0 -> {
                        // Navegador Web interactivo de búsqueda de portadas
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Barra de búsqueda con plantilla predefinida
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                    placeholder = { Text("Buscar portada...", fontSize = 13.sp) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(
                                        onSearch = {
                                            val encoded = URLEncoder.encode(searchQuery, "UTF-8")
                                            webViewInstance?.loadUrl("https://www.google.com/search?tbm=isch&q=$encoded")
                                        }
                                    )
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = {
                                        val encoded = URLEncoder.encode(searchQuery, "UTF-8")
                                        webViewInstance?.loadUrl("https://www.google.com/search?tbm=isch&q=$encoded")
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Buscar",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = { webViewInstance?.reload() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Recargar",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Barra de progreso web
                            if (isWebLoading) {
                                LinearProgressIndicator(
                                    progress = { webProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.5.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Banner instructivo
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "💡 Toca o mantén presionada cualquier foto en el navegador para seleccionarla como portada",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.loadWithOverviewMode = true
                                            settings.useWideViewPort = true
                                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                            settings.userAgentString =
                                                "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                                            addJavascriptInterface(
                                                WebAppInterface { url ->
                                                    if (!url.isNullOrBlank() && !url.startsWith("data:image/svg")) {
                                                        selectedImageUrl = url
                                                    }
                                                },
                                                "AndroidCoverBridge"
                                            )

                                            webViewClient = object : WebViewClient() {
                                                override fun shouldOverrideUrlLoading(
                                                    view: WebView?,
                                                    request: WebResourceRequest?
                                                ): Boolean {
                                                    return false
                                                }

                                                override fun onPageFinished(view: WebView?, url: String?) {
                                                    isWebLoading = false
                                                    // Inyectar detector de clics en imágenes
                                                    val js = """
                                                        (function() {
                                                            document.addEventListener('click', function(e) {
                                                                var el = e.target;
                                                                if (el && el.tagName === 'IMG') {
                                                                    var src = el.currentSrc || el.src || el.getAttribute('data-src') || el.getAttribute('src');
                                                                    if (src && src.length > 10) {
                                                                        window.AndroidCoverBridge.onImageSelected(src);
                                                                    }
                                                                }
                                                            }, true);
                                                        })();
                                                    """.trimIndent()
                                                    view?.evaluateJavascript(js, null)
                                                }
                                            }

                                            webChromeClient = object : WebChromeClient() {
                                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                    webProgress = newProgress / 100f
                                                    isWebLoading = newProgress < 100
                                                }
                                            }

                                            // Detectar clics mediante HitTestResult nativo
                                            setOnTouchListener { v, event ->
                                                if (event.action == android.view.MotionEvent.ACTION_UP) {
                                                    val hit = hitTestResult
                                                    if (hit.type == WebView.HitTestResult.IMAGE_TYPE ||
                                                        hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                                                    ) {
                                                        hit.extra?.let { imgUrl ->
                                                            if (imgUrl.isNotBlank()) {
                                                                selectedImageUrl = imgUrl
                                                            }
                                                        }
                                                    }
                                                }
                                                false
                                            }

                                            val encoded = URLEncoder.encode(searchQuery, "UTF-8")
                                            loadUrl("https://www.google.com/search?tbm=isch&q=$encoded")
                                            webViewInstance = this
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Barra inferior flotante cuando se ha detectado / seleccionado una imagen
                                if (selectedImageUrl != null) {
                                    Card(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Miniatura de la imagen seleccionada
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AsyncImage(
                                                    model = selectedImageUrl,
                                                    contentDescription = "Foto elegida",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "¡Foto seleccionada!",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "Toca el botón para descargarla y usarla como carátula",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            IconButton(
                                                onClick = { selectedImageUrl = null },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Cancelar selección",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            Button(
                                                onClick = {
                                                    val urlToDownload = selectedImageUrl ?: return@Button
                                                    scope.launch {
                                                        isDownloading = true
                                                        val savedPath = downloadAndSaveImage(context, song.id, urlToDownload)
                                                        isDownloading = false
                                                        if (savedPath != null) {
                                                            onSaveCover(song.id, savedPath)
                                                            Toast.makeText(context, "¡Portada actualizada correctamente!", Toast.LENGTH_SHORT).show()
                                                            onDismiss()
                                                        } else {
                                                            Toast.makeText(context, "Error al descargar la imagen", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                enabled = !isDownloading,
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                if (isDownloading) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        strokeWidth = 2.dp
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Aplicar", fontSize = 13.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // Selección de foto desde la Galería del teléfono
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Elige una foto de tu Galería",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Puedes seleccionar cualquier imagen guardada en tu teléfono para asignarla como portada de \"${song.title}\".",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Abrir selector de fotos", fontSize = 14.sp)
                            }
                        }
                    }

                    2 -> {
                        // Enlace directo de imagen
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Pega el enlace directo de la imagen",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Introduce una URL de imagen (JPG, PNG o WebP)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = directUrlInput,
                                onValueChange = { directUrlInput = it },
                                label = { Text("URL de la imagen") },
                                placeholder = { Text("https://ejemplo.com/caratula.jpg") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            if (directUrlInput.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(160.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = directUrlInput,
                                        contentDescription = "Vista previa",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        isDownloading = true
                                        val savedPath = downloadAndSaveImage(context, song.id, directUrlInput.trim())
                                        isDownloading = false
                                        if (savedPath != null) {
                                            onSaveCover(song.id, savedPath)
                                            Toast.makeText(context, "Portada descargada y aplicada", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        } else {
                                            Toast.makeText(context, "No se pudo descargar la imagen desde el enlace proporcionado", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = directUrlInput.isNotBlank() && !isDownloading,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                if (isDownloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Descargar y aplicar como portada")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Interfaz JavaScript para capturar eventos de clic en imágenes dentro del WebView */
private class WebAppInterface(private val onImageSelectedCallback: (String) -> Unit) {
    @JavascriptInterface
    fun onImageSelected(src: String) {
        onImageSelectedCallback(src)
    }
}

/** Descarga una imagen remota o decodifica base64 y la almacena localmente en la app */
private suspend fun downloadAndSaveImage(context: Context, songId: String, imageUrl: String): String? =
    withContext(Dispatchers.IO) {
        try {
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val outputFile = File(coversDir, "cover_${songId}_${System.currentTimeMillis()}.jpg")

            if (imageUrl.startsWith("data:image")) {
                // Formato Base64 Data URL (común en miniaturas de Google Images)
                val base64Data = imageUrl.substringAfter("base64,")
                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                FileOutputStream(outputFile).use { it.write(decodedBytes) }
                return@withContext outputFile.absolutePath
            }

            // Descarga HTTP / HTTPS estándar
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()

            val request = Request.Builder()
                .url(imageUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null

                // Validar decodificación Bitmap y comprimir como JPEG de alta calidad
                val bytes = body.bytes()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    FileOutputStream(outputFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    outputFile.absolutePath
                } else {
                    FileOutputStream(outputFile).use { it.write(bytes) }
                    outputFile.absolutePath
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

/** Guarda una imagen seleccionada desde el Selector de Fotos de Android hacia el directorio interno */
private suspend fun saveImageFromUri(context: Context, songId: String, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val outputFile = File(coversDir, "cover_${songId}_${System.currentTimeMillis()}.jpg")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (outputFile.exists() && outputFile.length() > 0) {
                outputFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
