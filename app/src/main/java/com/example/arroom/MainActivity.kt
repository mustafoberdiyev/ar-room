package com.example.arroom

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var granted by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                    )
                }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted = it }

                if (granted) {
                    ArScreen(activity = this)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Для работы AR нужен доступ к камере", textAlign = TextAlign.Center)
                            Text(
                                "Дать доступ",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .clickable { launcher.launch(Manifest.permission.CAMERA) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArScreen(activity: ComponentActivity) {
    val context = LocalContext.current

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberARCameraNode(engine)
    val childNodes = rememberNodes()
    val view = rememberView(engine)
    val collisionSystem = rememberCollisionSystem(view)

    var frame by remember { mutableStateOf<Frame?>(null) }
    var trackingFailure by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var planeRenderer by remember { mutableStateOf(true) }
    var selectedItem by remember { mutableStateOf(CATALOG.first()) }
    var selectedAnchor by remember { mutableStateOf<AnchorNode?>(null) }
    var hideUi by remember { mutableStateOf(false) }

    fun modelOf(anchor: AnchorNode?): ModelNode? =
        anchor?.childNodes?.firstOrNull { it is ModelNode } as? ModelNode

    fun placeAt(x: Float, y: Float) {
        val hit = frame?.hitTest(x, y)?.firstOrNull {
            val t = it.trackable
            t is Plane &&
                t.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                t.isPoseInPolygon(it.hitPose)
        } ?: return
        val anchor = hit.createAnchor()
        val anchorNode = AnchorNode(engine = engine, anchor = anchor)
        val model = ModelNode(
            modelInstance = modelLoader.createModelInstance(selectedItem.modelPath),
            scaleToUnits = selectedItem.size,
            // низ модели ставим на поверхность
            centerOrigin = Float3(0f, -1f, 0f),
        ).apply { isEditable = true } // перемещение, поворот и масштаб жестами
        anchorNode.addChildNode(model)
        childNodes += anchorNode
        selectedAnchor = anchorNode
        planeRenderer = false
    }

    fun scaleSelected(factor: Float) {
        val m = modelOf(selectedAnchor) ?: return
        val s = m.scale
        m.scale = Float3(s.x * factor, s.y * factor, s.z * factor)
    }

    fun rotateSelected(deg: Float) {
        val m = modelOf(selectedAnchor) ?: return
        val r = m.rotation
        m.rotation = Float3(r.x, r.y + deg, r.z)
    }

    fun deleteSelected() {
        val a = selectedAnchor ?: return
        childNodes -= a
        a.anchor.detach()
        a.destroy()
        selectedAnchor = null
        if (childNodes.isEmpty()) planeRenderer = true
    }

    Box(Modifier.fillMaxSize()) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            childNodes = childNodes,
            engine = engine,
            view = view,
            modelLoader = modelLoader,
            collisionSystem = collisionSystem,
            cameraNode = cameraNode,
            planeRenderer = planeRenderer,
            sessionConfiguration = { _, config ->
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            },
            onTrackingFailureChanged = { trackingFailure = it },
            onSessionUpdated = { _, updated -> frame = updated },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { event, node ->
                    if (node == null) {
                        placeAt(event.x, event.y)
                    } else {
                        // тап по объекту — выбираем его
                        val anchor = (node as? AnchorNode) ?: (node.parent as? AnchorNode)
                        if (anchor != null) selectedAnchor = anchor
                    }
                }
            ),
        )

        if (!hideUi) {
            // Подсказка сверху
            Text(
                text = when {
                    trackingFailure != null -> "Медленно двигайте телефон, ищем поверхность…"
                    childNodes.isEmpty() -> "Наведите на пол или стол и коснитесь поверхности"
                    else -> "Тап по объекту — выбрать, жесты — двигать/вращать/масштабировать"
                },
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(12.dp)
                    .align(Alignment.TopCenter)
                    .background(Color(0x99000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Панель управления выбранным объектом
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC000000)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ControlButton(Icons.Default.Remove, "Меньше") { scaleSelected(0.85f) }
                        ControlButton(Icons.Default.Add, "Больше") { scaleSelected(1.18f) }
                        ControlButton(Icons.Default.RotateRight, "Повернуть") { rotateSelected(15f) }
                        ControlButton(Icons.Default.Delete, "Удалить") { deleteSelected() }
                        ControlButton(Icons.Default.CameraAlt, "Снимок") {
                            hideUi = true
                            Handler(Looper.getMainLooper()).postDelayed({
                                captureScreenshot(activity) { hideUi = false }
                            }, 250)
                        }
                    }
                }

                // Каталог моделей
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xCCFFFFFF)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(CATALOG) { item ->
                            CatalogCell(
                                item = item,
                                selected = item.id == selectedItem.id,
                                onClick = { selectedItem = item },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}

@Composable
private fun CatalogCell(item: CatalogItem, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb = remember(item.id) {
        runCatching {
            context.assets.open(item.thumbPath).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Card(
            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
            shape = RoundedCornerShape(14.dp),
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp),
                )
            } else {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Text(item.title.take(1), fontSize = 24.sp)
                }
            }
        }
        Text(item.title, fontSize = 12.sp, color = Color.Black)
    }
}

/** Снимок всего окна (камера + 3D) в галерею: Pictures/ARRoom. */
private fun captureScreenshot(activity: ComponentActivity, done: () -> Unit) {
    val window = activity.window
    val decor = window.decorView
    val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
    PixelCopy.request(window, bitmap, { result ->
        if (result == PixelCopy.SUCCESS) {
            saveToGallery(activity, bitmap)
            Toast.makeText(activity, "Снимок сохранён в Pictures/ARRoom", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(activity, "Не удалось сделать снимок", Toast.LENGTH_SHORT).show()
        }
        done()
    }, Handler(Looper.getMainLooper()))
}

private fun saveToGallery(activity: ComponentActivity, bitmap: Bitmap) {
    val name = "ARRoom_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ARRoom")
        }
    }
    val uri = activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return
    activity.contentResolver.openOutputStream(uri)?.use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
}
