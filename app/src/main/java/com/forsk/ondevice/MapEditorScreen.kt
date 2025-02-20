package com.forsk.ondevice


import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.FloatingActionButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

// 데이터 클래스 예시: 가상벽과 진입금지 영역
data class VirtualWall(val start: Offset, val end: Offset)
data class NoEntryArea(val topLeft: Offset, val size: Size)

@Composable
fun MapEditor(
    image: ImageBitmap,  // pgm/png 이미지 파일은 외부에서 ImageBitmap으로 변환되어 전달된다고 가정 (→ 확실하지 않음)
    modifier: Modifier = Modifier
) {
    // 화면 이동, 확대/축소, 90도 회전을 위한 상태
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotation by remember { mutableStateOf(0) } // 0, 90, 180, 270 등

    // 터치된 포인트들 (예: 공간 분할을 위한 입력)
    var touchPoints by remember { mutableStateOf(listOf<Offset>()) }

    // 가상벽과 진입금지 영역 리스트 (필요시 추가/수정 기능 구현)
    var virtualWalls by remember { mutableStateOf(listOf<VirtualWall>()) }
    var noEntryAreas by remember { mutableStateOf(listOf<NoEntryArea>()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // 핀치 투 줌과 드래그를 통한 이동 구현 (회전은 detectTransformGestures의 rotation 값은 무시)
                detectTransformGestures { _, pan, zoom, _ ->
                    scale *= zoom
                    offset += pan
                }
            }
    ) {
        // 지도와 편집 요소들을 그릴 Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({
                translate(offset.x, offset.y)
                //scale(scale)
                rotate(rotation.toFloat())
            }) {
                // 지도 이미지 그리기
                drawImage(image)

                // [예시] 터치 포인트들을 기반으로 한 영역 표시
                // (요구사항: 터치된 포인트의 최장 가장자리를 기준으로 사각형화 – 아래는 단순 축 정렬 바운딩 박스임 → 확실하지 않음)
                if (touchPoints.isNotEmpty()) {
                    val minX = touchPoints.minOf { it.x }
                    val maxX = touchPoints.maxOf { it.x }
                    val minY = touchPoints.minOf { it.y }
                    val maxY = touchPoints.maxOf { it.y }
                    drawRect(
                        color = Color.Red.copy(alpha = 0.3f),
                        topLeft = Offset(minX, minY),
                        size = Size(maxX - minX, maxY - minY)
                    )
                }

                // 가상벽 그리기
                virtualWalls.forEach { wall ->
                    drawLine(
                        color = Color.Blue,
                        start = wall.start,
                        end = wall.end,
                        strokeWidth = 4f
                    )
                }

                // 진입금지 영역 그리기
                noEntryAreas.forEach { area ->
                    drawRect(
                        color = Color.Black.copy(alpha = 0.2f),
                        topLeft = area.topLeft,
                        size = area.size,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                    )
                }
            }
        }

        // 90도 회전 버튼 (맵 전체를 90도씩 회전)
        FloatingActionButton(
            onClick = { rotation = (rotation + 90) % 360 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {

        }
    }
}
