package com.forsk.ondevice


import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
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

    val points = remember { mutableStateListOf<Offset>() } // 공간 생성 포인트 저장
    val walls = remember { mutableStateListOf<RectF>() } // 가상 벽 저장
    val noEntryZones = remember { mutableStateListOf<RectF>() } // 진입 금지 영역 저장
    var startWallPoint = remember { mutableStateOf<Offset?>(null) }
    var currentWallPreview = remember { mutableStateOf<RectF?>(null) }

    Column() {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .weight(1f)
                .clipToBounds()
                .background(Color.Gray)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale.value *= zoom
                        offset.value += pan
                    }
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
            Canvas(modifier = Modifier
                .wrapContentSize()
                //    .clipToBounds()
//            .graphicsLayer(
//                scaleX = scale.value,
//                scaleY = scale.value,
//                translationX = offset.value.x,
//                translationY = offset.value.y,
//                rotationZ = rotation.value
//            )
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val position = event.changes.first().position
                            when {
                                event.changes.first().pressed -> {
                                    startWallPoint.value = position
                                }

                                startWallPoint.value != null && event.changes.first().pressed -> {
                                    val start = startWallPoint.value!!
                                    currentWallPreview.value = RectF(
                                        minOf(start.x, position.x),
                                        minOf(start.y, position.y),
                                        maxOf(start.x, position.x),
                                        maxOf(start.y, position.y)
                                    )
                                }

                                startWallPoint.value != null && !event.changes.first().pressed -> {
                                    val start = startWallPoint.value!!
                                    val end = position
                                    walls.add(
                                        RectF(
                                            minOf(start.x, end.x),
                                            minOf(start.y, end.y),
                                            maxOf(start.x, end.x),
                                            maxOf(start.y, end.y)
                                        )
                                    )
                                    startWallPoint.value = null
                                    currentWallPreview.value = null
                                }
                            }
                        }
                    }
                }) {
                // 공간 생성 포인트 시각화
                points.forEach { point ->
                    drawCircle(
                        Color.Blue,
                        radius = 10f * scale.value,
                        center = (point + offset.value) * scale.value
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
            var selectedIndex by remember { mutableStateOf(-1) }
            val buttonLabels = listOf("공간 생성", "가상벽 추가", "진입금지 영역 추가", "90도 회전")

            buttonLabels.forEachIndexed { index, label ->
                Button(
                    onClick = {
                        selectedIndex = index
                        when (index) {
                            0 -> points.clear()
                            1 -> walls.clear()
                            2 -> noEntryZones.add(RectF(200f, 200f, 400f, 400f))
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

fun createDummyImageBitmap(width: Int, height: Int): ImageBitmap {
    val androidBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(androidBitmap)
    val paint = android.graphics.Paint().apply { color = android.graphics.Color.RED }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    return androidBitmap.asImageBitmap()
}

@Composable
@Preview(showBackground = true)
fun preview() {
    MapEditorScreen(createDummyImageBitmap(100, 100))
}