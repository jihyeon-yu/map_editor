package com.forsk.ondevice

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.Log
import java.util.Random
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class CDrawObj(// roi_point, roi_line, roi_rect
    var roi_type: String, nLeft: Int, nTop: Int, nRight: Int, nBottom: Int
) {
    var m_MBR: Rect
    var m_MBR_center: Point
    var m_Points: ArrayList<Point?>
    var m_endroiflag: Boolean = false // 객체의 생성이 끝났는가?

    var m_label: String = "test" // 라벨 이름
    var m_labelviewflag: Boolean = true

    var m_Closed: Boolean = false // 닫혔는지 아닌지..

    var labelpaint: Paint // 라벨 글자 색상
    var Rectpaint: Paint // roi 색상


    private val path: Path // 폴리곤 경로
    var fillPaint: Paint // 채우기 색상
    private val random: Random // 랜덤 생성기

    var m_zoom: Double = 0.0

    // 241222 jihyeon 핀 회전 모드 아이콘 생성
    var iconDrawable: Drawable? = null // 핀 회전 아이콘

    var rotateDrawable: Drawable? = null
    var angle: Float = 0.0f

    var RectDashpaint: Paint
    var m_DashPoints: ArrayList<Point>
    var bDashViewflag: Boolean = true

    init {
        m_MBR = Rect(nLeft, nTop, nRight, nBottom)
        m_MBR_center = Point(((nLeft + nRight) / 2), ((nTop + nBottom) / 2))

        m_Points = ArrayList()

        roi_type = roi_type

        labelpaint = Paint()
        labelpaint.color = -0x7f010000 // 반투명 빨간색 (#80FF0000)
        labelpaint.isAntiAlias = true // 텍스트를 부드럽게 렌더링
        labelpaint.textSize = 48f


        //canvas.drawText("This is RED text", 50, 100, labelpaint);
        Rectpaint = Paint()
        Rectpaint.color = Color.RED // 반투명 파란색 (#80FF0000)
        Rectpaint.strokeWidth = 10f
        if (roi_type == "roi_rect") {
            Rectpaint.color = Color.RED
        }
        Rectpaint.style = Paint.Style.STROKE // 사각형 내부를 없음


        // 랜덤 생성기 초기화
        random = Random()

        // 폴리곤 경로 초기화
        path = Path()

        // 채우기 페인트 설정
        fillPaint = Paint()

        // 랜덤 색상 생성
        fillPaint.color = randomColor
        fillPaint.style = Paint.Style.FILL // 채우기 스타일
        fillPaint.isAntiAlias = true

        if (roi_type == "roi_polygon") {
            fillPaint.color = getRandomColorArgb(50)
        } else if (roi_type == "roi_rect") {
            fillPaint.color = Color.argb(178, 255, 70, 80)
        }

        RectDashpaint = Paint()
        RectDashpaint.color = Color.WHITE // 반투명 파란색 (#80FF0000)
        RectDashpaint.strokeWidth = 5f
        RectDashpaint.style = Paint.Style.STROKE // 사각형 내부를 없음
        RectDashpaint.setPathEffect(DashPathEffect(floatArrayOf(10f, 10f), 0f))

        m_DashPoints = ArrayList()
        var i = 0
        i = 0
        while (i < 4) {
            val pt = Point(0, 0)
            m_DashPoints.add(pt)
            i++
        }
    }

    private val randomColor: Int
        // 랜덤 색상 생성 함수
        get() {
            // RGB 값을 랜덤으로 생성
            val red = (Math.random() * 255).toInt() // 0~255
            val green = (Math.random() * 255).toInt() // 0~255
            val blue = (Math.random() * 255).toInt() // 0~255

            //int red = random.nextInt(256);    // 0~255
            //int green = random.nextInt(256); // 0~255
            //int blue = random.nextInt(256);  // 0~255
            return Color.rgb(red, green, blue) // 랜덤 색상 반환
        }


    // 241222 jihyeon 핀 회전 모드 아이콘 제거
    fun clearIconDrawable() {
        this.iconDrawable = null
    }

    // 241222 jihyeon 핀 회전 모드 아이콘 제거
    fun clearRotateDrawable() {
        this.rotateDrawable = null
    }

    fun SetPosition(rect: Rect) {
        // 실수 좌표계로 변환하여 대입한다.

        // polygon의 경우에는 m_Points들도 같이 이동해야한다.

        m_MBR.left = rect.left
        m_MBR.top = rect.top
        m_MBR.right = rect.right
        m_MBR.bottom = rect.bottom

        m_MBR_center = Point(
            ((rect.left + rect.right) / 2),
            ((rect.top + rect.bottom) / 2)
        )
    }

    fun GetPosition(): Rect {
        return this.m_MBR
    }

    fun SetZoom(z: Double) {
        this.m_zoom = z
    }

    fun GetZoom(): Double {
        return this.m_zoom
    }

    fun GetString(): String {
        return this.m_label
    }

    fun SetString(m_label: String) {
        this.m_label = m_label
    }

    fun SetTextColor(color: Int) {
        labelpaint.color = color
    }

    fun SetLineColor(color: Int) {
        Rectpaint.color = color
    }

    fun GetLineColor(): Int {
        return Rectpaint.color
    }

    fun SetDashLineColor(color: Int) {
        RectDashpaint.color = color
    }

    fun GetDashLineColor(): Int {
        return RectDashpaint.color
    }


    fun SetFillColor(color: Int) {
        fillPaint.color = color
    }

    fun GetFillColor(): Int {
        return fillPaint.color
    }

    fun GetMBRCenter(): Point {
        return this.m_MBR_center
    }

    fun DrawLabel(canvas: Canvas?, pt_Start: Point?) {
        val x: Int
        val y: Int

        when (this.roi_type) {
            "roi_polygon" -> {
                //x = this.m_MBR.left + 2;
                //y = this.m_MBR.top + 2;
                x = m_MBR_center.x
                y = m_MBR_center.y
            }
        }
    }

    // 2024.12.11 lyt94 bDrawPoint 추가
    fun Draw(canvas: Canvas, pt_Start: Point, bitmap: Bitmap, bSelected: Boolean, bEdit: Boolean) {
        //Log.d(TAG, "Draw(...)");

        //Log.d(TAG, "roi_type : "+roi_type);
        //Log.d(TAG, "m_MBR : ("+m_MBR.left+","+m_MBR.top+","+m_MBR.right+","+m_MBR.bottom+")");

        when (this.roi_type) {
            "roi_point" -> canvas.drawCircle(
                (m_MBR.left.toFloat() * m_zoom + pt_Start.x).toInt().toFloat(),
                (m_MBR.top.toFloat() * m_zoom + pt_Start.y).toInt().toFloat(),
                5f,
                Rectpaint
            )

            "roi_line" -> {
                if (bSelected || bEdit) {
                    canvas.drawCircle(
                        (m_MBR.left.toFloat() * m_zoom + pt_Start.x).toInt().toFloat(),
                        (m_MBR.top.toFloat() * m_zoom + pt_Start.y).toInt().toFloat(),
                        5f,
                        Rectpaint
                    )
                }
                canvas.drawLine(
                    (m_MBR.left * m_zoom + pt_Start.x).toInt().toFloat(),
                    (m_MBR.top * m_zoom + pt_Start.y).toInt().toFloat(),
                    (m_MBR.right * m_zoom + pt_Start.x).toInt().toFloat(),
                    (m_MBR.bottom * m_zoom + pt_Start.y).toInt().toFloat(),
                    Rectpaint
                )
                if (bSelected || bEdit) {
                    canvas.drawCircle(
                        (m_MBR.right.toFloat() * m_zoom + pt_Start.x).toInt().toFloat(),
                        (m_MBR.bottom.toFloat() * m_zoom + pt_Start.y).toInt().toFloat(),
                        5f,
                        Rectpaint
                    )
                }

                if (bDashViewflag && bSelected) {
                    val lengthExtension = 5
                    val x1 = (m_MBR.left * m_zoom + pt_Start.x).toInt()
                    val y1 = (m_MBR.top * m_zoom + pt_Start.y).toInt()
                    val x2 = (m_MBR.right * m_zoom + pt_Start.x).toInt()
                    val y2 = (m_MBR.bottom * m_zoom + pt_Start.y).toInt()

                    // 두 점 간의 각도 계산
                    val lineAngle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())

                    //Log.d(TAG, "lineAngle : " + lineAngle);

                    // 두 점 길이를 구한다.
                    val dx = x2 - x1
                    val dy = y2 - y1
                    val lineDistance = sqrt((dx * dx + dy * dy).toDouble())
                    Log.d(TAG, "lineDistance : $lineDistance")

                    // 두 점의 중앙에서 직각으로 50px 점을 각각 구한다.
                    // 메모리 누스를 피하기 위해서 new를 사용하지 않는다.

                    // 중심에서 수직으로 떨어진 두 점 계산
                    val distance = 50f
                    val offsetX1 =
                        ((x1 + x2) / 2.0f) + distance * cos(lineAngle + Math.PI.toFloat() / 2.0f) as Float
                    val offsetY1 =
                        ((y1 + y2) / 2.0f) + distance * sin(lineAngle + Math.PI.toFloat() / 2.0f) as Float

                    val offsetX2 =
                        ((x1 + x2) / 2.0f) + distance * cos(lineAngle - Math.PI.toFloat() / 2.0f) as Float
                    val offsetY2 =
                        ((y1 + y2) / 2.0f) + distance * sin(lineAngle - Math.PI.toFloat() / 2.0f) as Float

                    //                    canvas.drawCircle(offsetX1, offsetY1, 10, Rectpaint); // 첫 번째 선의 중심점
//                    canvas.drawCircle(offsetX2, offsetY2, 10, Rectpaint); // 두 번째 선의 중심점

                    // 줌심에서 수직으로 떨어진 두 점에서 두 점의 기울기를 이용해서 두 점의 거리보다 distance 만큼 더 긴 곳의 두 점을 각각 구한다.
                    val newlineHalfDistance = (lineDistance / 2.0) + distance
                    m_DashPoints[0].x =
                        (offsetX1 + newlineHalfDistance * cos(lineAngle) as Float).toInt()
                    m_DashPoints[0].y =
                        (offsetY1 + newlineHalfDistance * sin(lineAngle) as Float).toInt()
                    m_DashPoints[1].x =
                        (offsetX1 + newlineHalfDistance * cos(lineAngle + Math.PI.toFloat()) as Float).toInt()
                    m_DashPoints[1].y =
                        (offsetY1 + newlineHalfDistance * sin(lineAngle + Math.PI.toFloat()) as Float).toInt()
                    m_DashPoints[3].x =
                        (offsetX2 + newlineHalfDistance * cos(lineAngle) as Float).toInt()
                    m_DashPoints[3].y =
                        (offsetY2 + newlineHalfDistance * sin(lineAngle) as Float).toInt()
                    m_DashPoints[2].x =
                        (offsetX2 + newlineHalfDistance * cos(lineAngle + Math.PI.toFloat()) as Float).toInt()
                    m_DashPoints[2].y =
                        (offsetY2 + newlineHalfDistance * sin(lineAngle + Math.PI.toFloat()) as Float).toInt()

                    var i = 0
                    i = 0
                    while (i < 4) {
                        canvas.drawLine(
                            m_DashPoints[i % 4].x.toFloat(),
                            m_DashPoints[i % 4].y.toFloat(),
                            m_DashPoints[(i + 1) % 4].x.toFloat(),
                            m_DashPoints[(i + 1) % 4].y.toFloat(),
                            RectDashpaint
                        )
                        i++
                    }
                }
            }

            "roi_rect" -> {
                //Rectpaint.setStyle(Paint.Style.FILL); // 사각형 내부를 채우기
                canvas.drawRect(
                    (m_MBR.left * m_zoom + pt_Start.x).toInt().toFloat(),
                    (m_MBR.top * m_zoom + pt_Start.y).toInt().toFloat(),
                    (m_MBR.right * m_zoom + pt_Start.x).toInt().toFloat(),
                    (m_MBR.bottom * m_zoom + pt_Start.y).toInt().toFloat(),
                    fillPaint
                )
                canvas.drawRect(
                    (m_MBR.left * m_zoom + pt_Start.x).toInt().toFloat(),
                    (m_MBR.top * m_zoom + pt_Start.y).toInt().toFloat(),
                    (m_MBR.right * m_zoom + pt_Start.x).toInt().toFloat(),
                    (m_MBR.bottom * m_zoom + pt_Start.y).toInt().toFloat(),
                    Rectpaint
                )

                if (bSelected || bEdit) {
                    canvas.drawCircle(
                        (m_MBR.left.toFloat() * m_zoom + pt_Start.x).toInt().toFloat(),
                        (m_MBR.top.toFloat() * m_zoom + pt_Start.y).toInt().toFloat(),
                        5f,
                        Rectpaint
                    )
                    canvas.drawCircle(
                        (m_MBR.left.toFloat() * m_zoom + pt_Start.x).toInt().toFloat(),
                        (m_MBR.bottom.toFloat() * m_zoom + pt_Start.y).toInt().toFloat(),
                        5f,
                        Rectpaint
                    )
                    canvas.drawCircle(
                        (m_MBR.right.toFloat() * m_zoom + pt_Start.x).toInt().toFloat(),
                        (m_MBR.bottom.toFloat() * m_zoom + pt_Start.y).toInt().toFloat(),
                        5f,
                        Rectpaint
                    )
                    canvas.drawCircle(
                        (m_MBR.right.toFloat() * m_zoom + pt_Start.x).toInt().toFloat(),
                        (m_MBR.top.toFloat() * m_zoom + pt_Start.y).toInt().toFloat(),
                        5f,
                        Rectpaint
                    )
                }
                if (bDashViewflag && bSelected) {
                    val distance = 50f

                    // normalize rect
                    var nTemp = 0
                    if (m_MBR.left > m_MBR.right) {
                        nTemp = m_MBR.left
                        m_MBR.left = m_MBR.right
                        m_MBR.right = nTemp
                    }
                    if (m_MBR.top > m_MBR.bottom) {
                        nTemp = m_MBR.bottom
                        m_MBR.bottom = m_MBR.left
                        m_MBR.left = nTemp
                    }
                    m_DashPoints[0].x =
                        ((m_MBR.left.toFloat() * m_zoom + pt_Start.x) - distance).toInt()
                    m_DashPoints[0].y =
                        ((m_MBR.top.toFloat() * m_zoom + pt_Start.y) - distance).toInt()
                    m_DashPoints[1].x =
                        ((m_MBR.right.toFloat() * m_zoom + pt_Start.x) + distance).toInt()
                    m_DashPoints[1].y =
                        ((m_MBR.top.toFloat() * m_zoom + pt_Start.y) - distance).toInt()
                    m_DashPoints[2].x =
                        ((m_MBR.right.toFloat() * m_zoom + pt_Start.x) + distance).toInt()
                    m_DashPoints[2].y =
                        ((m_MBR.bottom.toFloat() * m_zoom + pt_Start.y) + distance).toInt()
                    m_DashPoints[3].x =
                        ((m_MBR.left.toFloat() * m_zoom + pt_Start.x) - distance).toInt()
                    m_DashPoints[3].y =
                        ((m_MBR.bottom.toFloat() * m_zoom + pt_Start.y) + distance).toInt()

                    var i = 0
                    i = 0
                    while (i < 4) {
                        canvas.drawLine(
                            m_DashPoints[i % 4].x.toFloat(),
                            m_DashPoints[i % 4].y.toFloat(),
                            m_DashPoints[(i + 1) % 4].x.toFloat(),
                            m_DashPoints[(i + 1) % 4].y.toFloat(),
                            RectDashpaint
                        )
                        i++
                    }
                }
            }

            "roi_polygon" -> {
                //Log.d(TAG, "m_Points.size() : "+(m_Points.size()));


                // Path를 정의하여 폴리곤 만들기
                path.reset()

                // Path의 경계 좌표를 계산
                var left = Int.MAX_VALUE
                var top = Int.MAX_VALUE
                var right = Int.MIN_VALUE
                var bottom = Int.MIN_VALUE

                var p = 0
                while (p < m_Points.size) {
                    val x = m_Points[p]!!.x
                    val y = m_Points[p]!!.y

                    // 경계 업데이트
                    if (x < left) left = x
                    if (y < top) top = y
                    if (x > right) right = x
                    if (y > bottom) bottom = y

                    if (p == 0) {
                        path.moveTo(
                            (m_Points[p]!!.x.toFloat() * m_zoom + pt_Start.x).toInt().toFloat(),
                            (m_Points[p]!!.y.toFloat() * m_zoom + pt_Start.y).toInt().toFloat()
                        ) // 첫 번째 점
                    } else {
                        path.lineTo(
                            (m_Points[p]!!.x.toFloat() * m_zoom + pt_Start.x).toInt().toFloat(),
                            (m_Points[p]!!.y.toFloat() * m_zoom + pt_Start.y).toInt().toFloat()
                        ) // 첫 번째 점
                    }
                    p++
                }
                path.close() // 시작점으로 닫기
                val bounds = intArrayOf(left, top, right, bottom)

                //Log.d(TAG, "fillPaint.getColor() : "+fillPaint.getColor());
                if (m_endroiflag == true) {
                    // 채우기 색상 먼저 그리기
                    if (bEdit) {
                        canvas.drawPath(path, fillPaint)
                    } else {
                        try {
                            drawMatchPoints(bitmap, canvas, fillPaint, bounds, pt_Start)
                        } catch (ie: IllegalStateException) {
                            Log.d(TAG, "Fail drawMatchPoints: " + ie.localizedMessage)
                        } catch (ie: InterruptedException) {
                            Log.d(TAG, "Fail drawMatchPoints: " + ie.localizedMessage)
                        }
                    }

                    //drawMatchPoints(bitmap,canvas,fillPaint, bounds, pt_Start);
                    // 선만 그리기
                } else {
                    // 그리는 중이면
                    // 외곽선 그리기
                    // 20241212 jihyeon
                    // 선택 점 그리기
                    canvas.drawPath(path, Rectpaint)
                    //canvas.drawPath(path, fillPaint);
                    var p = 0
                    while (p < m_Points.size) {
                        //Log.d(TAG, "m_Points.get("+p+") : ("+(int)(m_Points.get(p).x)+","+(int)(m_Points.get(p).y)+")");
                        //Log.d(TAG, "m_zoom : ("+m_zoom);
                        //Log.d(TAG, "pt_Start : ("+(int)(pt_Start.x)+","+(int)(pt_Start.y)+")");
                        canvas.drawCircle(
                            (m_Points[p]!!.x.toFloat() * m_zoom + pt_Start.x).toInt().toFloat(),
                            (m_Points[p]!!.y.toFloat() * m_zoom + pt_Start.y).toInt().toFloat(),
                            5f,
                            Rectpaint
                        )

                        if (p == 0) {
                            canvas.drawLine(
                                (m_Points[p]!!.x.toFloat() * m_zoom + pt_Start.x).toInt().toFloat(),
                                (m_Points[p]!!.y.toFloat() * m_zoom + pt_Start.y).toInt().toFloat(),
                                (m_Points[m_Points.size - 1]!!.x.toFloat() * m_zoom + pt_Start.x).toInt()
                                    .toFloat(),
                                (m_Points[m_Points.size - 1]!!.y.toFloat() * m_zoom + pt_Start.y).toInt()
                                    .toFloat(),
                                Rectpaint
                            )
                        }
                        if (p > 0) {
                            canvas.drawLine(
                                (m_Points[p - 1]!!.x.toFloat() * m_zoom + pt_Start.x).toInt()
                                    .toFloat(),
                                (m_Points[p - 1]!!.y.toFloat() * m_zoom + pt_Start.y).toInt()
                                    .toFloat(),
                                (m_Points[p]!!.x.toFloat() * m_zoom + pt_Start.x).toInt().toFloat(),
                                (m_Points[p]!!.y.toFloat() * m_zoom + pt_Start.y).toInt().toFloat(),
                                Rectpaint
                            )
                        }
                        p++
                    }
                    //canvas.drawCircle((int)((float)m_MBR_center.x*m_zoom + pt_Start.x), (int)((float)m_MBR_center.y*m_zoom + pt_Start.y), 11, Rectpaint);
                }

                if (bEdit) {
                    var p = 0
                    while (p < m_Points.size) {
                        canvas.drawCircle(
                            (m_Points[p]!!.x.toFloat() * m_zoom + pt_Start.x).toInt().toFloat(),
                            (m_Points[p]!!.y.toFloat() * m_zoom + pt_Start.y).toInt().toFloat(),
                            5f,
                            Rectpaint
                        )
                        p++
                    }
                }
            }
        }
        //if (m_endroiflag == true && m_labelviewflag)
        if (m_labelviewflag) {
            DrawLabel(canvas, pt_Start)
        }

        if (iconDrawable != null) {
            // 아이콘 크기 86.89 * 86.89
            val iconWidth = 86.89f * 2 // 아이콘의 너비
            val iconHeight = 86.89f * 2 // 아이콘의 높이

            // 중심점을 기준으로 아이콘의 Bounds 설정
            val iconLeft = ((m_MBR_center.x * m_zoom + pt_Start.x) - (iconWidth / 2)).toInt()
            val iconTop = ((m_MBR_center.y * m_zoom + pt_Start.y) - (iconHeight / 2)).toInt()
            val iconRight = (iconLeft + iconWidth).toInt()
            val iconBottom = (iconTop + iconHeight).toInt()

            val iconCenterX = (iconLeft + iconRight) / 2
            val iconCenterY = (iconTop + iconBottom) / 2

            // 사각형 dashpoint
            val dashedPaint = Paint()
            dashedPaint.style = Paint.Style.STROKE
            dashedPaint.setPathEffect(DashPathEffect(floatArrayOf(10f, 10f), 0f))
            dashedPaint.color = Color.WHITE
            dashedPaint.strokeWidth = 5f
            val rect = Rect(iconLeft, iconTop, iconRight, iconBottom)

            if (rotateDrawable != null) {
                rotateDrawable!!.setBounds(
                    iconRight - 26,
                    iconBottom - 26,
                    iconRight + 26,
                    iconBottom + 26
                )
            }
            iconDrawable!!.setBounds(iconLeft, iconTop, iconRight, iconBottom)
            // Canvas 회전 및 아이콘 그리기
            canvas.save() // 현재 Canvas 상태 저장
            // 반시계 + -> 시계 + 로 반전
            // 0° ~ 360°로 변환 (음수 각도 처리)
            val degrees = -Math.toDegrees(angle.toDouble()).toFloat()

            //            if (degrees < 0) {
//                degrees += 360;
//            }
            //Log.d(TAG,"degrees: " + degrees);
            canvas.rotate(degrees, iconCenterX.toFloat(), iconCenterY.toFloat()) // 아이콘 중심 기준 회전

            // 회전된 상태에서 그리기
            iconDrawable!!.draw(canvas)

            if (rotateDrawable != null) {
                canvas.drawRect(rect, dashedPaint)
                rotateDrawable!!.draw(canvas)
            }

            canvas.restore() // Canvas 상태 복원
        }
    }

    fun AddPoint(point: Point?) {
        //Log.d(TAG, "AddPoint("+point.x+","+point.y+")");
        m_Points.add(point)
        //Log.d(TAG, "m_MBR : ("+m_MBR.left+","+m_MBR.top+","+m_MBR.right+","+m_MBR.bottom+")");
        // MBR 구하기
        m_MBR = Rect(10000, 10000, 0, 0)
        for (p in m_Points.indices) {
            if (m_MBR.left > m_Points[p]!!.x) m_MBR.left = m_Points[p]!!.x
            if (m_MBR.right < m_Points[p]!!.x) m_MBR.right = m_Points[p]!!.x
            if (m_MBR.top > m_Points[p]!!.y) m_MBR.top = m_Points[p]!!.y
            if (m_MBR.bottom < m_Points[p]!!.y) m_MBR.bottom = m_Points[p]!!.y
        }
        //Log.d(TAG, "m_MBR : ("+m_MBR.left+","+m_MBR.top+","+m_MBR.right+","+m_MBR.bottom+")");
        m_MBR_center.x = ((m_MBR.left + m_MBR.right) / 2)
        m_MBR_center.y = ((m_MBR.top + m_MBR.bottom) / 2)
    }

    fun AddEndPoint(point: Point?, flag: Boolean) {
        //Log.d(TAG, "AddEndPoint(...)");
        // 마지막 점은 제외하고 시작 포인트와 끝 포인트를 같게 함
        if (flag) {
            m_Points.add(point)
        }

        // MBR 구하기
        m_MBR = Rect(10000, 10000, 0, 0)
        for (p in m_Points.indices) {
            if (m_MBR.left > m_Points[p]!!.x) m_MBR.left = m_Points[p]!!.x
            if (m_MBR.right < m_Points[p]!!.x) m_MBR.right = m_Points[p]!!.x
            if (m_MBR.top > m_Points[p]!!.y) m_MBR.top = m_Points[p]!!.y
            if (m_MBR.bottom < m_Points[p]!!.y) m_MBR.bottom = m_Points[p]!!.y
        }
        //Log.d(TAG, "m_MBR : ("+m_MBR.left+","+m_MBR.top+","+m_MBR.right+","+m_MBR.bottom+")");
        m_endroiflag = true
        m_MBR_center.x = ((m_MBR.left + m_MBR.right) / 2)
        m_MBR_center.y = ((m_MBR.top + m_MBR.bottom) / 2)
    }


    fun GetPointCount(): Int {
        var point_count = 0
        when (this.roi_type) {
            "roi_point" -> point_count = 1
            "roi_line" -> point_count = 2
            "roi_rect" -> point_count = 4
            "roi_polygon" -> point_count = m_Points.size
        }
        return point_count
    }

    fun GetPoint(nHandle: Int): Point {
        var nHandle = nHandle
        var x = -1
        var y = -1
        when (this.roi_type) {
            "roi_line" -> when (nHandle) {
                1 -> {
                    x = m_MBR.left
                    y = m_MBR.top
                }

                2 -> {
                    x = m_MBR.right
                    y = m_MBR.bottom
                }

            }

            "roi_point" -> when (nHandle) {
                1 -> {
                    x = m_MBR.left
                    y = m_MBR.top
                }
            }

            "roi_polygon" -> if (nHandle < m_Points.size) {
                nHandle = m_Points.size
            }
        }
        val pt = Point(x, y)
        return pt
    }

    // 마우스를 올리면 변경 가능한 핸들 수
    // 점, 선은 2 나머지는 8을 리턴
    fun GetHandleCount(): Int {
        var handle_count = 0
        when (this.roi_type) {
            "roi_point" -> handle_count = 2
            "roi_line" -> handle_count = 2
            "roi_rect" -> handle_count = 8
            "roi_polygon" -> handle_count = m_Points.size
        }
        return handle_count
    }

    fun GetHandle(nHandle: Int): Point {
        var nHandle = nHandle
        when (this.roi_type) {
            "roi_line" -> {
                if (nHandle == 2) {
                    nHandle = 8
                }

                if (nHandle == 1) {
                    nHandle = 1
                }
            }

            "roi_point" -> if (nHandle == 1) {
                nHandle = 1
            }

            "roi_polygon" -> if (nHandle == 1) {
                nHandle = m_Points.size
            }

            else -> nHandle = 8
        }


        var x: Int
        var y: Int
        val xcenter: Int
        val ycenter: Int

        y = -1
        x = y // 선택된 Tracker의 좌표 초기화

        if (this.roi_type == "roi_polygon") {
            if (nHandle < m_Points.size) {
                x = m_Points[nHandle]!!.x
                y = m_Points[nHandle]!!.y
            }
        } else {
            //데이터 크기의 중심 좌표 설정(Tracker 2, 4, 5, 7 해당하는 위치 나타냄)
            val temp = m_MBR
            xcenter = temp.left + temp.width() / 2
            ycenter = temp.top + temp.height() / 2

            when (nHandle) {
                1 -> {
                    x = temp.left
                    y = temp.top
                }

                2 -> {
                    x = xcenter
                    y = temp.top
                }

                3 -> {
                    x = temp.right
                    y = temp.top
                }

                4 -> {
                    x = temp.left
                    y = ycenter
                }

                5 -> {
                    x = temp.right
                    y = ycenter
                }

                6 -> {
                    x = temp.left
                    y = temp.bottom
                }

                7 -> {
                    x = xcenter
                    y = temp.bottom
                }

                8 -> {
                    x = temp.right
                    y = temp.bottom
                }

                9 -> {
                    x = xcenter
                    y = ycenter
                }
            }
        }

        val pt = Point(x, y)
        return pt
    }

    fun PointInRect(point: Point): Boolean {
        //Log.d(TAG, "PointInRect("+point.x+","+point.y+")");

        //Log.d(TAG, "roi_type : "+roi_type);
        //Log.d(TAG, "m_MBR : ("+m_MBR.left+","+m_MBR.top+","+m_MBR.right+","+m_MBR.bottom+")");

        //double zoom = this.m_zoom;

        when (this.roi_type) {
            "roi_line" -> {
                val sx = (m_MBR.left.toFloat()).toInt()
                val sy = (m_MBR.top.toFloat()).toInt()
                val ex = (m_MBR.right.toFloat()).toInt()
                val ey = (m_MBR.bottom.toFloat()).toInt()

                if (sx < ex) {
                    if (point.x > ex || point.x < sx) return false
                } else {
                    if (point.x < ex || point.x > sx) return false
                }

                if (sy < ey) {
                    if (point.y > ey || point.y < sy) return false
                } else {
                    if (point.y < ey || point.y > sy) return false
                }

                val a = (ey - sy).toDouble()
                val b = (sx - ex).toDouble()

                if ((a * a + b * b) != 0.0) {
                    val c = -a * sx.toDouble() - sy.toDouble() * b
                    val d =
                        abs(a * point.x.toDouble() + b * point.y.toDouble() + c) / sqrt(a * a + b * b)
                    if (d <= 20.0) return true
                }
            }

            "roi_point" -> {
                //Log.d(TAG, "point : ("+point.x+","+point.y+")");
                //Log.d(TAG, "this.m_MBR.left - 50 : "+(this.m_MBR.left - 50));
                //Log.d(TAG, "this.m_MBR.left + 50 : "+(this.m_MBR.left + 50));
                //Log.d(TAG, "((this.m_MBR.left - 50)<point.x) : "+((this.m_MBR.left - 50)<point.x));
                if (((m_MBR.left - 50) < point.x) && ((m_MBR.left + 50) > point.x)) {
                    //Log.d(TAG, "this.m_MBR.top - 50 : "+(this.m_MBR.top - 50));
                    //Log.d(TAG, "this.m_MBR.top + 50 : "+(this.m_MBR.top + 50));
                    if (((m_MBR.top - 50) < point.y) && ((m_MBR.top + 50) > point.y)) {
                        return true
                    }
                }
            }

            "roi_rect", "roi_polygon" -> {
                if (((m_MBR.left) < point.x) && ((m_MBR.right) > point.x)) {
                    if (((m_MBR.top) < point.y) && ((m_MBR.bottom) > point.y)) {
                        //Log.d(TAG,"Y!" + this.m_MBR.top + " " + point.y + " " + this.m_MBR.bottom);
                        //Log.d(TAG,"X!" + this.m_MBR.left + " " + point.x + " " + this.m_MBR.right);
                        return true
                    }
                }
            }
        }

        return false
    }

    fun PointInHandle(point: Point, cw: Int, ch: Int): Int //Tracker를 찾기위한 함수
    {
        //Log.d(TAG, "PointInHandle( ("+point.x+","+point.y+"),"+cw+","+ch+")");
        //Log.d(TAG, "m_MBR : ("+m_MBR.left+","+m_MBR.top+","+m_MBR.right+","+m_MBR.bottom+")");
        //Log.d(TAG, "GetHandleCount() : " + GetHandleCount() );
        val pt = Point(point.x, point.y)

        var nHandle = 1
        nHandle = 1
        while (nHandle <= this.GetHandleCount()) {
            val pt_handle = this.GetHandle(nHandle) // 핸들의 좌표를 얻어옴.

            //Log.d(TAG, "pt_handle : ("+pt_handle.x+","+pt_handle.y+")");

            //Tracker에 해당하는 사각형 생성
            val rect = Rect(pt_handle.x - cw, pt_handle.y - ch, pt_handle.x + cw, pt_handle.y + ch)

            //if (rect.PtInRect(pt)) // 동작하지 않는다?
            if (((pt_handle.x - cw) <= point.x) && (point.x <= (pt_handle.x + cw)) && ((pt_handle.y - ch) <= point.y) && (point.y <= (pt_handle.y + ch))) {
                //console.log(this.roi_type);
                if ((this.roi_type == "roi_line") && nHandle == 2) return 8
                //if (m_Type == NIP_ELLIPSE_ROI && nHandle == 1)		return  8;
                return nHandle
            }
            nHandle++
        }
        return 0
    }

    fun PointInPoint(point: Point, cw: Int, ch: Int): Int //Tracker를 찾기위한 함수
    {
        Log.d(TAG, "PointInPoint( (" + point.x + "," + point.y + ") " + cw + ", " + ch + ")")

        val pt = Point(point.x, point.y)
        when (roi_type) {
            "roi_line" -> if (((m_MBR.left - cw) <= pt.x) && (pt.x <= (m_MBR.left + cw)) && ((m_MBR.top - ch) <= pt.y) && (pt.y <= (m_MBR.top + ch))) {
                return 1 // left, top
            } else if (((m_MBR.right - cw) <= pt.x) && (pt.x <= (m_MBR.right + cw)) && ((m_MBR.bottom - ch) <= pt.y) && (pt.y <= (m_MBR.bottom + ch))) {
                return 8 // right, bottom
            }

            "roi_rect" -> if (((m_MBR.left - cw) <= pt.x) && (pt.x <= (m_MBR.left + cw)) && ((m_MBR.top - ch) <= pt.y) && (pt.y <= (m_MBR.top + ch))) {
                return 1 // left, top
            } else if (((m_MBR.right - cw) <= pt.x) && (pt.x <= (m_MBR.right + cw)) && ((m_MBR.top - ch) <= pt.y) && (pt.y <= (m_MBR.top + ch))) {
                return 3 // right, top
            } else if (((m_MBR.left - cw) <= pt.x) && (pt.x <= (m_MBR.left + cw)) && ((m_MBR.bottom - ch) <= pt.y) && (pt.y <= (m_MBR.bottom + ch))) {
                return 6 // left, bottom
            } else if (((m_MBR.right - cw) <= pt.x) && (pt.x <= (m_MBR.right + cw)) && ((m_MBR.bottom - ch) <= pt.y) && (pt.y <= (m_MBR.bottom + ch))) {
                return 8 // right, bottom
            }

            "roi_polygon" -> {
                var i = 0
                i = 0
                while (i < m_Points.size) {
                    if (((m_Points[i]!!.x - cw) <= pt.x) && (pt.x <= (m_Points[i]!!.x + cw)) && ((m_Points[i]!!.y - ch) <= pt.y) && (pt.y <= (m_Points[i]!!.y + ch))) {
                        Log.d(
                            TAG,
                            "m_Points.get($i) : (" + m_Points[i]!!.x + "," + m_Points[i]!!.y + ")"
                        )
                        return i + 1 // 0인 경우는 이동이므로 1부터 시작한다.
                    }
                    i++
                }
            }
        }
        return -1
    }

    fun MoveToRect(pt1: Point, pt2: Point) {
        //console.log('MoveToRect( ('+pt1.x+','+pt1.y+'), ('+pt2.x+','+pt2.y+') )');

        //this.m_MBR.NormalizeRect();

        val dx = pt1.x - pt2.x
        val dy = pt1.y - pt2.y
        // m_Points도 이동한다.
        if (roi_type == "roi_polygon") {
            for (p in m_Points.indices) {
                val point = Point(0, 0)

                point.x = m_Points[p]!!.x - dx
                point.y = m_Points[p]!!.y - dy

                m_Points[p] = point
            }
        }
        m_MBR.left = pt1.x
        m_MBR.top = pt1.y
        m_MBR.right = pt2.x
        m_MBR.bottom = pt2.y
    }

    fun MoveTo(pt1: Point, pt2: Point) {
        //Log.d(TAG, "MoveTo( ("+pt1.x+","+pt1.y+"),("+pt2.x+","+pt2.y+") )");
        // pt1 : mouse down
        // pt2 : mouse move

        // 실수 좌표계로 변환하여 대입한다.
        //var zoom = this.m_zoom;

        val dx = pt1.x - pt2.x
        val dy = pt1.y - pt2.y
        if (roi_type == "roi_polygon") {
            // 중심 좌표만 이동한다.
            m_MBR_center.x += dx
            m_MBR_center.y += dy

            // 예외처리
            if (m_MBR_center.x < m_MBR.left) m_MBR_center.x = m_MBR.left
            if (m_MBR_center.x > m_MBR.right) m_MBR_center.x = m_MBR.right
            if (m_MBR_center.y < m_MBR.top) m_MBR_center.y = m_MBR.top
            if (m_MBR_center.y > m_MBR.bottom) m_MBR_center.y = m_MBR.bottom

            //    m_Points.get(m_Points.size()-1).x += dx;
            //    m_Points.get(m_Points.size()-1).y += dy;
            //
            //    // MBR 구하기
            //    m_MBR = new Rect(10000, 10000, 0, 0);
            //    for (int p=0; p<m_Points.size(); p++)
            //    {
            //        if (m_MBR.left > m_Points.get(p).x)    m_MBR.left   = m_Points.get(p).x;
            //        if (m_MBR.right < m_Points.get(p).x)   m_MBR.right  = m_Points.get(p).x;
            //       if (m_MBR.top > m_Points.get(p).y)     m_MBR.top    = m_Points.get(p).y;
            //        if (m_MBR.bottom < m_Points.get(p).y)  m_MBR.bottom = m_Points.get(p).y;
            //    }
        } else {
            //this.m_MBR.left = pt1.x;
            //this.m_MBR.top = pt1.y;
            //this.m_MBR.right = pt2.x;
            //this.m_MBR.bottom = pt2.y;
            m_MBR.left += dx
            m_MBR.top += dy
            m_MBR.right += dx
            m_MBR.bottom += dy
        }
    }

    fun MoveHandleTo(pt1: Point, pt2: Point, nHandle: Int) {
        //console.log(pt1);
        // 실수 좌표계로 변환하여 대입한다.
        val zoom = this.m_zoom

        // 왜 좌표가 2배 차이가 날까?
        val dx = (pt1.x - pt2.x)
        val dy = (pt1.y - pt2.y)

        when (nHandle) {
            1 -> {
                m_MBR.left += dx
                m_MBR.top += dy
            }

            2 -> {
                m_MBR.top += dy
            }

            3 -> {
                m_MBR.right += dx
                m_MBR.top += dy
            }

            4 -> {
                m_MBR.left += dx
            }

            5 -> {
                m_MBR.right += dx
            }

            6 -> {
                m_MBR.left += dx
                m_MBR.bottom += dy
            }

            7 -> {
                m_MBR.bottom += dy
            }

            8 -> {
                m_MBR.right += dx
                m_MBR.bottom += dy
            }
        }

        //console.log(str);

        // 일부 ROI의 경우에는 괘적을 그림.
        // if(POLYGON || FREEHAND || FREEHANDLINE){}
        // else (point) //
        // 여기서는 point 만 이동한다.
    }

    fun MovePointTo(pt1: Point, pt2: Point, nHandle: Int) {
        if (nHandle < 1) return  // 포인터 이동이 아님.


        val dx = (pt1.x - pt2.x)
        val dy = (pt1.y - pt2.y)

        when (roi_type) {
            "roi_line" -> MoveHandleTo(pt1, pt2, nHandle)
            "roi_rect" -> MoveHandleTo(pt1, pt2, nHandle)
            "roi_polygon" -> if (nHandle <= m_Points.size) {
                m_Points[nHandle - 1]!!.x += dx
                m_Points[nHandle - 1]!!.y += dy
            }
        }
    }

    private fun getRandomColorArgb(blur: Int): Int {
        // RGB 값을 랜덤으로 생성

        val red = (Math.random() * 255).toInt() // 0~255
        val green = (Math.random() * 255).toInt() // 0~255
        val blue = (Math.random() * 255).toInt() // 0~255

        //int red = random.nextInt(256);    // 0~255
        //int green = random.nextInt(256); // 0~255
        //int blue = random.nextInt(256);  // 0~255
        return Color.argb(blur, red, green, blue) // 랜덤 색상 반환
    }


    @Throws(InterruptedException::class)
    fun drawMatchPoints(
        bitmap: Bitmap,
        canvas: Canvas,
        paint: Paint,
        bounds: IntArray,
        pt_Start: Point
    ) {
        // Bounds를 기반으로 영역 가져오기
        val left =
            max(0.0, bounds[0].toDouble()).toInt() // Bitmap 경계를 벗어나지 않도록 제한
        val top = max(0.0, bounds[1].toDouble()).toInt()
        val right = min(bitmap.width.toDouble(), bounds[2].toDouble()).toInt()
        val bottom = min(bitmap.height.toDouble(), bounds[3].toDouble()).toInt()
        val width = right - left
        val height = bottom - top

        // 픽셀 데이터를 가져오기
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, left, top, width, height)

        val floatZoom = m_zoom.toFloat()
        paint.strokeWidth = 5f

        // 점 좌표를 저장할 리스트
        val points = ArrayList<Float>()

        // 픽셀 데이터를 검사하며 조건에 맞는 좌표를 리스트에 추가
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixelColor = pixels[y * width + x]
                if ((pixelColor and 0x00FFFFFF) == 0x00969696) { // RGB만 비교, Alpha 제외
                    points.add((x + left) * floatZoom + pt_Start.x) // X 좌표
                    points.add((y + top) * floatZoom + pt_Start.y) // Y 좌표
                }
            }
        }

        // 리스트를 배열로 변환
        val pointArray = FloatArray(points.size)
        for (i in points.indices) {
            pointArray[i] = points[i]
        }

        // 모든 점을 한 번에 그리기
        canvas.drawPoints(pointArray, paint)
    }

    fun isPointInPolygon(point: Point): Boolean {
        var intersectCount = 0
        val size = m_Points.size

        for (i in 0 until size) {
            val p1 = m_Points[i]
            val p2 = m_Points[(i + 1) % size]

            // Check if the point is on a horizontal edge of the polygon
            if (p1!!.y == p2!!.y && p1.y == point.y && point.x >= min(
                    p1.x.toDouble(),
                    p2.x.toDouble()
                ) && point.x <= max(
                    p1.x.toDouble(), p2.x.toDouble()
                )
            ) {
                return true
            }

            // Check if the ray intersects the edge
            if (point.y > min(p1.y.toDouble(), p2.y.toDouble()) && point.y <= max(
                    p1.y.toDouble(), p2.y.toDouble()
                ) && point.x <= max(p1.x.toDouble(), p2.x.toDouble())
            ) {
                // Compute the x-coordinate of the intersection
                val xinters = (point.y - p1.y).toDouble() * (p2.x - p1.x) / (p2.y - p1.y) + p1.x
                if (xinters == point.x.toDouble()) { // Point is on the edge
                    return true
                }
                if (p1.x == p2.x || point.x < xinters) { // Ray intersects the edge
                    intersectCount++
                }
            }
        }

        // Point is inside the polygon if intersectCount is odd
        return (intersectCount % 2) == 1
    }

    companion object {
        private const val TAG = "CDrawObj"
    }
}