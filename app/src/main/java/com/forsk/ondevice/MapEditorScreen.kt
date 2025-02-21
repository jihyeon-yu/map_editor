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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
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
    val virtualWall = remember { mutableStateListOf<Pair<Offset, Offset>>() }
    val areaRect = remember { mutableStateListOf<RectF>() } // 공간 저장
    val noEntryZones = remember { mutableStateListOf<RectF>() } // 진입 금지 영역 저장

    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var currentPoint by remember { mutableStateOf<Offset?>(null) }
    var finalRect by remember { mutableStateOf<RectF?>(null) }
    var finalLine by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }

    var selectedIndex by remember { mutableStateOf(-1) }
    val buttonLabels = listOf("공간 생성", "가상벽 추가", "금지 공간 추가", "90도 회전")
    LaunchedEffect(startPoint, currentPoint) {
        Log.w(">>>", "$startPoint $currentPoint")
    }
    val points = remember { mutableStateListOf<Offset>() }

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
                                points.add(offset)
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
                                    finalLine?.let {
                                        virtualWall.add(it)
                                    }
                                    finalLine = null
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
                                    startPoint = null
                                    currentPoint = null
                                    finalRect?.let {
                                        noEntryZones.add(it)
                                    }
                                    finalRect = null
                                }
                            )
                        }
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
            Canvas(
                modifier = Modifier
                    .wrapContentSize()
            ) {

                val pixelMap = imageBitmap.toPixelMap() // 이미지 픽셀 데이터 가져오기

                fun getOriginalColor(x: Float, y: Float): Color {
                    return if (x.toInt() in 0 until pixelMap.width && y.toInt() in 0 until pixelMap.height) {
                        pixelMap[x.toInt(), y.toInt()]
                    } else {
                        Color.Transparent
                    }
                }
                val ignoreColor = Color(51, 51, 51).toArgb()

                fun shouldReplaceWithImageColor(rect: RectF): Boolean {
                    for (x in rect.left.toInt() until rect.right.toInt()) {
                        for (y in rect.top.toInt() until rect.bottom.toInt()) {
                            if (x in 0 until pixelMap.width && y in 0 until pixelMap.height) {
                                Log.w(">>>", "shouldReplaceWithImageColor4")
                                val pixelColor = pixelMap[x, y].toArgb()
                                Log.w(">>>", "pixelColor : $pixelColor ${pixelColor == ignoreColor}" )
                                if (pixelColor == ignoreColor) {
                                    Log.w(">>>", "shouldReplaceWithImageColor5")
                                    return true // 특정 색상이 포함된 경우, 원본 이미지 색상을 유지해야 함
                                }
                            }
                            else {
                                Log.w(">>>", "shouldReplaceWithImageColor6 $x $y ${pixelMap.width} $pixelMap")
                            }
                        }
                    }
                    return false
                }


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

                virtualWall.forEach {
                    drawLine(
                        color = Color(0x80FF0000),
                        start = it.first,
                        end = it.second,
                        strokeWidth = 4f
                    )
                }

                // 특정 색상이 포함된 경우, 원래 이미지 색상을 유지
                areaRect.forEach { rect ->
                    if (shouldReplaceWithImageColor(rect)) {
                        Log.w(">>>", "true 여기옴?")
                        for (x in rect.left.toInt() until rect.right.toInt()) {
                            for (y in rect.top.toInt() until rect.bottom.toInt()) {
                                val originalColor = getOriginalColor(x.toFloat(), y.toFloat())
                                drawRect(
                                    color = originalColor,
                                    topLeft = Offset(x.toFloat(), y.toFloat()),
                                    size = Size(1f, 1f) // 픽셀 단위로 복구
                                )
                            }
                        }
                    } else {
                        Log.w(">>>", "else 여기옴?")
                        drawRect(
                            alpha = 0.3f,
                            color = Color.Yellow,
                            topLeft = Offset(rect.left, rect.top),
                            size = Size(rect.width(), rect.height())
                        )
                    }
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
                    points.sortedBy { it.x }
                    drawPolygon(points, Color.Blue)
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
                        selectedIndex = if (selectedIndex == index) {
                            when (selectedIndex) {
                                0 -> {
                                    val left = points.minOf { it.x }
                                    val right = points.maxOf { it.x }
                                    val top = points.minOf { it.y }
                                    val bottom = points.maxOf { it.y }
                                    areaRect.add(RectF(left, top, right, bottom))
                                    points.clear()
                                }
                            }
                            -1
                        } else index
                        when (index) {
                            0 -> makePoints()
                            1 -> makeWalls()
                            2 -> {

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