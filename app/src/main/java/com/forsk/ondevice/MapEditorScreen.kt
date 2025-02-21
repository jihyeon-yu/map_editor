package com.forsk.ondevice


import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp


@Composable
fun MapEditorScreen(imageBitmap: ImageBitmap) {
    val context = LocalContext.current

    val scale = remember { mutableStateOf(1f) }
    val offset = remember { mutableStateOf(Offset(0f, 0f)) }
    val rotation = remember { mutableStateOf(0f) }

    val walls = remember { mutableStateListOf<RectF>() } // 가상 벽 저장
    val noEntryZones = remember { mutableStateListOf<RectF>() } // 진입 금지 영역 저장
    var startWallPoint = remember { mutableStateOf<Offset?>(null) }
    var currentWallPreview = remember { mutableStateOf<RectF?>(null) }

    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var currentPoint by remember { mutableStateOf<Offset?>(null) }
    var finalRect by remember { mutableStateOf<RectF?>(null) }
    var finalLine by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    var selectedIndex by remember { mutableStateOf(-1) }
    val buttonLabels = listOf("공간 생성", "가상벽 추가", "금지 공간 추가", "90도 회전")
    LaunchedEffect(startPoint, currentPoint) {
        Log.w(">>>", "$startPoint $currentPoint")
    }
    var points by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Column() {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .weight(1f)
                .clipToBounds()
                .background(Color.Gray)
                .pointerInput(selectedIndex) {
                    when (selectedIndex) {
                        -1 -> {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale.value *= zoom
                                offset.value += pan
                            }
                        }

                        0 -> {
                            detectTapGestures { offset ->
                                points = points + offset
                            }
                        }

                        1 -> {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    startPoint = offset
                                    currentPoint = offset
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentPoint = change.position
                                    finalLine = Pair(
                                        Offset(startPoint?.x ?: 0f, startPoint?.y ?: 0f),
                                        Offset(currentPoint?.x ?: 0f, currentPoint?.y ?: 0f)
                                    )
                                },
                                onDragEnd = {

                                    startPoint = null
                                    currentPoint = null
                                }
                            )
                        }

                        2 -> {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    startPoint = offset
                                    currentPoint = offset
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentPoint = change.position

                                    finalRect = RectF(
                                        (startPoint?.x ?: 0f),
                                        (startPoint?.y ?: 0f),
                                        (currentPoint?.x ?: 0f),
                                        (currentPoint?.y ?: 0f)
                                    )
                                },
                                onDragEnd = {
//                                    finalRect = RectF(
//                                        (startPoint?.x ?: 0f),
//                                        (startPoint?.y ?: 0f),
//                                        (currentPoint?.x ?: 0f),
//                                        (currentPoint?.y ?: 0f)
//                                    )
                                    startPoint = null
                                    currentPoint = null
                                }
                            )
                        }
                    }

//
//                    awaitPointerEventScope {
//                        while (true) {
//                            val event = awaitPointerEvent()
//                            val position = event.changes.first().position
//                            when {
//                                event.changes.first().pressed -> {
//                                    Log.w(">>>", "$event")
//                                    startWallPoint.value = position
//                                }
//
//                                startWallPoint.value != null && event.changes.first().pressed -> {
//                                    val start = startWallPoint.value!!
//                                    currentWallPreview.value = RectF(
//                                        minOf(start.x, position.x),
//                                        minOf(start.y, position.y),
//                                        maxOf(start.x, position.x),
//                                        maxOf(start.y, position.y)
//                                    )
//                                }
//
//                                startWallPoint.value != null && !event.changes.first().pressed -> {
//                                    val start = startWallPoint.value!!
//                                    val end = position
//                                    walls.add(
//                                        RectF(
//                                            minOf(start.x, end.x),
//                                            minOf(start.y, end.y),
//                                            maxOf(start.x, end.x),
//                                            maxOf(start.y, end.y)
//                                        )
//                                    )
//                                    startWallPoint.value = null
//                                    currentWallPreview.value = null
//                                }
//                            }
//                        }
//                    }
                }
                .graphicsLayer(
                    scaleX = scale.value,
                    scaleY = scale.value,
                    translationX = offset.value.x,
                    translationY = offset.value.y,
                    rotationZ = rotation.value
                )
        ) {
            // 지도 이미지
            Image(
                painter = BitmapPainter(imageBitmap), // 로드한 PGM 또는 PNG
                contentDescription = "Map",
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            )

            // 캔버스 그리기
            Canvas(
                modifier = Modifier
                    .wrapContentSize()
            ) {
                finalRect?.let {
                    drawRect(
                        color = Color(0x80FF0000),
                        topLeft = (Offset(
                            it.left,
                            it.top
                        )),
                        size = Size(it.width(), it.height())
                    )
                }

                finalLine?.let {
                    drawLine(
                        color = Color(0x80FF0000),
                        start = it.first,
                        end = it.second,
                        strokeWidth = 4f
                    )
                }

                for (point in points) {
                    drawCircle(
                        color = Color.Red,
                        radius = 10f,
                        center = point
                    )
                }

                // 3개 이상의 점이 있을 때 다각형 그리기
                if (points.size >= 3) {
                    drawPolygon(points, Color.Blue)
                }

//
//                startPoint?.let { sp ->
//                    currentPoint?.let { cp ->
//                        drawRect(
//                            color = Color.Red.copy(alpha = 0.5f),
//                            topLeft = Offset(
//                                min(sp.x, cp.x),
//                                min(sp.y, cp.y)
//                            ),
//                            size = Size(
//                                abs(sp.x - cp.x),
//                                abs(sp.y - cp.y)
//                            )
//                        )
//                    }
//                }
                // 공간 생성 포인트 시각화
                points.forEach { point ->
                    drawCircle(
                        Color.Blue,
                        radius = 10f,
                        center = (point + offset.value)
                    )
                }

                // 가상 벽(사각형) 시각화
                walls.forEach { rect ->
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(rect.left, rect.top),
                        size = Size(rect.width(), rect.height())
                    )
                }

                // 진입 금지 영역 시각화
                noEntryZones.forEach { rect ->
                    drawRect(
                        color = Color(0x80FF0000),
                        topLeft = (Offset(
                            rect.left,
                            rect.top
                        )),
                        size = Size(rect.width(), rect.height())
                    )
                }
            }
        }
        // 편집 기능 버튼
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            buttonLabels.forEachIndexed { index, label ->
                Button(
                    onClick = {
                        selectedIndex = if (selectedIndex == index) -1 else index
                        when (index) {
                            0 -> makePoints()
                            1 -> makeWalls()
                            2 -> {
                                noEntryZones.add(RectF(200f, 200f, 400f, 400f))
                            }

                            3 -> rotation.value += 90f
                        }
                    },
                    modifier = Modifier.background(if (selectedIndex == index) Color.Gray else Color.LightGray)
                ) {
                    Text(text = label, color = Color.White)
                }
            }
        }
    }
}

fun makePoints() {
}

fun makeWalls() {}
fun makeNoEntryZone() {

}

fun createDummyImageBitmap(width: Int, height: Int): ImageBitmap {
    val androidBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(androidBitmap)
    val paint = android.graphics.Paint().apply { color = android.graphics.Color.RED }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    return androidBitmap.asImageBitmap()
}

/**
 * 다각형을 그리는 함수
 */
fun DrawScope.drawPolygon(points: List<Offset>, color: Color) {
    for (i in points.indices) {
        val start = points[i]
        val end = points[(i + 1) % points.size] // 마지막 점에서 첫 번째 점으로 연결
        drawLine(
            color = color,
            start = start,
            end = end,
            strokeWidth = 4f
        )
    }
}
@Composable
@Preview(showBackground = true)
fun preview() {
    MapEditorScreen(createDummyImageBitmap(100, 100))
}