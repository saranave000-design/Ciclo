package it.saranave.ciclo

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import it.saranave.ciclo.ui.CicloTheme
import java.io.File

/**
 * Fotocamera con sagoma: ovale per il viso e, se c'è, l'ultima foto in trasparenza
 * per rifare lo scatto sempre nella stessa posizione.
 * Extra: "out" = percorso del file da creare, "ghost" = percorso dell'ultima foto (opzionale).
 */
class CameraActivity : ComponentActivity() {
    private var granted by mutableStateOf(false)
    private val askPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) { setResult(Activity.RESULT_CANCELED); finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val out = File(intent.getStringExtra("out") ?: run { finish(); return })
        val ghost = intent.getStringExtra("ghost")?.let { File(it) }?.takeIf { it.exists() }
        granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!granted) askPerm.launch(Manifest.permission.CAMERA)
        setContent {
            CicloTheme {
                if (granted) CameraScreen(this, out, ghost,
                    onSaved = { setResult(Activity.RESULT_OK); finish() },
                    onClose = { setResult(Activity.RESULT_CANCELED); finish() })
                else Box(Modifier.fillMaxSize().background(Color.Black))
            }
        }
    }
}

@Composable
private fun CameraScreen(activity: ComponentActivity, out: File, ghost: File?, onSaved: () -> Unit, onClose: () -> Unit) {
    var front by remember { mutableStateOf(true) }
    var showGhost by remember { mutableStateOf(ghost != null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val previewView = remember { PreviewView(activity).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }

    DisposableEffect(front) {
        val future = ProcessCameraProvider.getInstance(activity)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()
                provider.bindToLifecycle(activity, selector, preview, capture)
            }.onFailure { err = "Fotocamera non disponibile" }
        }, ContextCompat.getMainExecutor(activity))
        onDispose { runCatching { future.get().unbindAll() } }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (showGhost && ghost != null) AsyncImage(
            model = ghost, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.32f),
        )
        // Ovale guida: fuori dall'ovale è scurito
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width * 0.68f; val h = w * 1.32f
            val oval = Rect(Offset((size.width - w) / 2, size.height * 0.44f - h / 2), Size(w, h))
            val p = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size)); addOval(oval)
            }
            drawPath(p, Color.Black.copy(alpha = 0.45f))
            drawOval(Color.White, oval.topLeft, oval.size, style = Stroke(width = 5f))
            val eyeY = oval.top + oval.height * 0.42f
            drawLine(Color.White.copy(alpha = 0.5f), Offset(oval.left + 20f, eyeY), Offset(oval.right - 20f, eyeY), strokeWidth = 2f)
            drawLine(Color.White.copy(alpha = 0.5f), Offset(oval.center.x, oval.top + 20f), Offset(oval.center.x, oval.bottom - 20f), strokeWidth = 2f)
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("✕ Chiudi", color = Color.White) }
                Text("", Modifier.weight(1f))
                if (ghost != null) TextButton(onClick = { showGhost = !showGhost }) {
                    Text(if (showGhost) "👻 Nascondi foto prima" else "👻 Mostra foto prima", color = Color.White)
                }
            }
            Text(
                "Viso dentro l'ovale, occhi sulla linea, luce di fronte (finestra), niente flash." +
                    if (ghost != null) "\nAllinea il viso alla foto precedente in trasparenza." else "",
                color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            err?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { front = !front }) { Text(if (front) "🔄 Posteriore" else "🔄 Frontale") }
            Box(
                Modifier.size(78.dp).border(5.dp, Color.White, CircleShape).padding(8.dp)
                    .background(if (busy) Color.Gray else Color.White, CircleShape)
                    .clickable(enabled = !busy) {
                        busy = true
                        val meta = ImageCapture.Metadata().apply { isReversedHorizontal = front }
                        val opts = ImageCapture.OutputFileOptions.Builder(out).setMetadata(meta).build()
                        capture.takePicture(opts, ContextCompat.getMainExecutor(activity), object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) = onSaved()
                            override fun onError(exception: ImageCaptureException) { busy = false; err = "Scatto non riuscito, riprova" }
                        })
                    },
            )
            Box(Modifier.size(100.dp, 1.dp))
        }
    }
}
