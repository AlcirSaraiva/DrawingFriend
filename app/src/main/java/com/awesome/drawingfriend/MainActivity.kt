package com.awesome.drawingfriend

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.awesome.drawingfriend.ui.theme.DrawingFriendTheme
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DrawingFriendTheme {
                DrawingApp()
            }
        }
    }
}

@Composable
fun DrawingApp() {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        MainScreen()
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Camera permission is required")
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var importedImageUri by remember { mutableStateOf<Uri?>(null) }
    var opacity by remember { mutableFloatStateOf(0.5f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showControls by remember { mutableStateOf(true) }
    var isLoaded by remember { mutableStateOf(false) }

    // Preference Keys
    val imageUriKey = stringPreferencesKey("image_uri")
    val opacityKey = floatPreferencesKey("opacity")
    val scaleKey = floatPreferencesKey("scale")
    val rotationKey = floatPreferencesKey("rotation")
    val offsetXKey = floatPreferencesKey("offset_x")
    val offsetYKey = floatPreferencesKey("offset_y")

    // Load State
    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        prefs[imageUriKey]?.let { uriString ->
            val uri = Uri.parse(uriString)
            try {
                // Check if we still have access
                context.contentResolver.openInputStream(uri)?.close()
                importedImageUri = uri
            } catch (e: Exception) {
                // File not found or no permission
                e.printStackTrace()
            }
        }
        opacity = prefs[opacityKey] ?: 0.5f
        scale = prefs[scaleKey] ?: 1f
        rotation = prefs[rotationKey] ?: 0f
        offset = Offset(
            prefs[offsetXKey] ?: 0f,
            prefs[offsetYKey] ?: 0f
        )
        isLoaded = true
    }

    // Save State
    LaunchedEffect(importedImageUri, opacity, scale, rotation, offset) {
        if (!isLoaded) return@LaunchedEffect
        context.dataStore.edit { prefs ->
            prefs[imageUriKey] = importedImageUri?.toString() ?: ""
            prefs[opacityKey] = opacity
            prefs[scaleKey] = scale
            prefs[rotationKey] = rotation
            prefs[offsetXKey] = offset.x
            prefs[offsetYKey] = offset.y
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                importedImageUri = it
                scale = 1f
                rotation = 0f
                offset = Offset.Zero
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var isInvalid = false
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.size > 1 || event.changes.any { it.isConsumed }) {
                                isInvalid = true
                            }
                            if (event.changes.all { !it.pressed }) {
                                if (!isInvalid) {
                                    val up = event.changes.first()
                                    val distance = (up.position - down.position).getDistance()
                                    if (distance < viewConfiguration.touchSlop) {
                                        showControls = !showControls
                                    }
                                }
                                break
                            }
                        }
                    }
                }
        ) {
            // Layer 1: Camera Preview
            CameraPreview(modifier = Modifier.fillMaxSize())

            // Layer 2: Imported Image
            importedImageUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = "Imported image",
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(opacity)
                        .pointerInput(showControls) {
                            if (showControls) {
                                detectTransformGestures { _, pan, zoom, rotationChange ->
                                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                                    rotation += rotationChange
                                    offset += pan
                                }
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            rotationZ = rotation,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit,
                    onError = {
                        // Handle error if image fails to load (e.g. file deleted)
                        importedImageUri = null
                    }
                )
            }

            // Layer 3: Controls
            if (showControls) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(innerPadding)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                    )
                    Button(onClick = { imagePickerLauncher.launch(arrayOf("image/*")) }) {
                        Text("Import Image")
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, executor)
            previewView
        },
        modifier = modifier
    )
}
