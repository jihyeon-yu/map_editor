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
    var roiType: String, nLeft: Int, nTop: Int, nRight: Int, nBottom: Int
) {
    var mMBR: Rect
    var mMBRCenter: Point
    var mPoints: ArrayList<Point?>
    var mEndRoiFlag: Boolean = false // 객체의 생성이 끝났는가?

    var label: String = "test" // 라벨 이름
    var labelViewFlag: Boolean = true

    var closed: Boolean = false // 닫혔는지 아닌지..

    var labelPaint: Paint // 라벨 글자 색상
    var roiPaint: Paint // roi 색상

    private val path: Path // 폴리곤 경로
    var fillPaint: Paint // 채우기 색상
    private val random: Random // 랜덤 생성기

    var zoom: Double = 0.0

    // 241222 jihyeon 핀 회전 모드 아이콘 생성
    var iconDrawable: Drawable? = null // 핀 회전 아이콘

    var rotateDrawable: Drawable? = null
    var angle: Float = 0.0f

    var rectDashpaint: Paint
    var dashPoints: ArrayList<Point>
    var dashViewflag: Boolean = true

    init {
        mMBR = Rect(nLeft, nTop, nRight, nBottom)
        mMBRCenter = Point(((nLeft + nRight) / 2), ((nTop + nBottom) / 2))

        mPoints = ArrayList()

        labelPaint = Paint()
        labelPaint.color = -0x7f010000 // 반투명 빨간색 (#80FF0000)
        labelPaint.isAntiAlias = true // 텍스트를 부드럽게 렌더링
        labelPaint.textSize = 48f

        //canvas.drawText("This is RED text", 50, 100, labelpaint);
        roiPaint = Paint()
        roiPaint.color = Color.RED // 반투명 파란색 (#80FF0000)
        roiPaint.strokeWidth = 10f
        if (roiType == ROI_TYPE_RECT) {
            roiPaint.color = Color.RED
        }
        roiPaint.style = Paint.Style.STROKE // 사각형 내부를 없음


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

        if (roiType == ROI_TYPE_POLYGON) {
            fillPaint.color = getRandomColorArgb(50)
        } else if (roiType == ROI_TYPE_RECT) {
            fillPaint.color = Color.argb(178, 255, 70, 80)
        }

        rectDashpaint = Paint()
        rectDashpaint.color = Color.WHITE // 반투명 파란색 (#80FF0000)
        rectDashpaint.strokeWidth = 5f
        rectDashpaint.style = Paint.Style.STROKE // 사각형 내부를 없음
        rectDashpaint.setPathEffect(DashPathEffect(floatArrayOf(10f, 10f), 0f))

        dashPoints = ArrayList()
        var i = 0
        i = 0
        while (i < 4) {
            val pt = Point(0, 0)
            dashPoints.add(pt)
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

    fun setPosition(rect: Rect) {
        // 실수 좌표계로 변환하여 대입한다.

        // polygon의 경우에는 m_Points들도 같이 이동해야한다.

        mMBR.left = rect.left
        mMBR.top = rect.top
        mMBR.right = rect.right
        mMBR.bottom = rect.bottom

        mMBRCenter = Point(
            ((rect.left + rect.right) / 2),
            ((rect.top + rect.bottom) / 2)
        )
    }

    fun drawLabel(canvas: Canvas?, pt_Start: Point?) {
        val x: Int
        val y: Int

        when (this.roiType) {
            ROI_TYPE_POLYGON -> {
                //x = this.m_MBR.left + 2;
                //y = this.m_MBR.top + 2;
                x = mMBRCenter.x
                y = mMBRCenter.y
            }
        }
    }


    // 2024.12.11 lyt94 bDrawPoint 추가
    fun draw(canvas: Canvas, ptStart: Point, bitmap: Bitmap, bSelected: Boolean, bEdit: Boolean) {
        //Log.d(TAG, "Draw(...)");
        //Log.d(TAG, "roi_type : "+roi_type);
        //Log.d(TAG, "m_MBR : ("+m_MBR.left+","+m_MBR.top+","+m_MBR.right+","+m_MBR.bottom+")");

        when (this.roiType) {
            ROI_TYPE_POINT -> canvas.drawCircle(
                (mMBR.left.toFloat() * zoom + ptStart.x).toInt().toFloat(),
                (mMBR.top.toFloat() * zoom + ptStart.y).toInt().toFloat(),
                5f,
                roiPaint
            )

            ROI_TYPE_LINE -> {
                if (bSelected || bEdit) {
                    canvas.drawCircle(
                        (mMBR.left.toFloat() * zoom + ptStart.x).toInt().toFloat(),
                        (mMBR.top.toFloat() * zoom + ptStart.y).toInt().toFloat(),
                        5f,
                        roiPaint
                    )
                }
                canvas.drawLine(
                    (mMBR.left * zoom + ptStart.x).toInt().toFloat(),
                    (mMBR.top * zoom + ptStart.y).toInt().toFloat(),
                    (mMBR.right * zoom + ptStart.x).toInt().toFloat(),
                    (mMBR.bottom * zoom + ptStart.y).toInt().toFloat(),
                    roiPaint
                )
                if (bSelected || bEdit) {
                    canvas.drawCircle(
                        (mMBR.right.toFloat() * zoom + ptStart.x).toInt().toFloat(),
                        (mMBR.bottom.toFloat() * zoom + ptStart.y).toInt().toFloat(),
                        5f,
                        roiPaint
                    )
                }

                if (dashViewflag && bSelected) {
                    val lengthExtension = 5
                    val x1 = (mMBR.left * zoom + ptStart.x).toInt()
                    val y1 = (mMBR.top * zoom + ptStart.y).toInt()
                    val x2 = (mMBR.right * zoom + ptStart.x).toInt()
                    val y2 = (mMBR.bottom * zoom + ptStart.y).toInt()

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
                        ((x1 + x2) / 2.0f) + distance * cos(lineAngle + Math.PI.toFloat() / 2.0f).toFloat()
                    val offsetY1 =
                        ((y1 + y2) / 2.0f) + distance * sin(lineAngle + Math.PI.toFloat() / 2.0f).toFloat()

                    val offsetX2 =
                        ((x1 + x2) / 2.0f) + distance * cos(lineAngle - Math.PI.toFloat() / 2.0f).toFloat()
                    val offsetY2 =
                        ((y1 + y2) / 2.0f) + distance * sin(lineAngle - Math.PI.toFloat() / 2.0f).toFloat()

                    //                    canvas.drawCircle(offsetX1, offsetY1, 10, Rectpaint); // 첫 번째 선의 중심점
//                    canvas.drawCircle(offsetX2, offsetY2, 10, Rectpaint); // 두 번째 선의 중심점

                    // 줌심에서 수직으로 떨어진 두 점에서 두 점의 기울기를 이용해서 두 점의 거리보다 distance 만큼 더 긴 곳의 두 점을 각각 구한다.
                    val newlineHalfDistance = (lineDistance / 2.0) + distance
                    dashPoints[0].x =
                        (offsetX1 + newlineHalfDistance * cos(lineAngle).toFloat()).toInt()
                    dashPoints[0].y =
                        (offsetY1 + newlineHalfDistance * sin(lineAngle).toFloat()).toInt()
                    dashPoints[1].x =
                        (offsetX1 + newlineHalfDistance * cos(lineAngle + Math.PI.toFloat()).toFloat()).toInt()
                    dashPoints[1].y =
                        (offsetY1 + newlineHalfDistance * sin(lineAngle + Math.PI.toFloat()).toFloat()).toInt()
                    dashPoints[3].x =
                        (offsetX2 + newlineHalfDistance * cos(lineAngle).toFloat()).toInt()
                    dashPoints[3].y =
                        (offsetY2 + newlineHalfDistance * sin(lineAngle).toFloat()).toInt()
                    dashPoints[2].x =
                        (offsetX2 + newlineHalfDistance * cos(lineAngle + Math.PI.toFloat()).toFloat()).toInt()
                    dashPoints[2].y =
                        (offsetY2 + newlineHalfDistance * sin(lineAngle + Math.PI.toFloat()).toFloat()).toInt()

                    var i = 0
                    while (i < 4) {
                        canvas.drawLine(
                            dashPoints[i % 4].x.toFloat(),
                            dashPoints[i % 4].y.toFloat(),
                            dashPoints[(i + 1) % 4].x.toFloat(),
                            dashPoints[(i + 1) % 4].y.toFloat(),
                            rectDashpaint
                        )
                        i++
                    }
                }
            }

            ROI_TYPE_RECT -> {
                //Rectpaint.setStyle(Paint.Style.FILL); // 사각형 내부를 채우기
                canvas.drawRect(
                    (mMBR.left * zoom + ptStart.x).toInt().toFloat(),
                    (mMBR.top * zoom + ptStart.y).toInt().toFloat(),
                    (mMBR.right * zoom + ptStart.x).toInt().toFloat(),
                    (mMBR.bottom * zoom + ptStart.y).toInt().toFloat(),
                    fillPaint
                )
                canvas.drawRect(
                    (mMBR.left * zoom + ptStart.x).toInt().toFloat(),
                    (mMBR.top * zoom + ptStart.y).toInt().toFloat(),
                    (mMBR.right * zoom + ptStart.x).toInt().toFloat(),
                    (mMBR.bottom * zoom + ptStart.y).toInt().toFloat(),
                    roiPaint
                )

                if (bSelected || bEdit) {
                    canvas.drawCircle(
                        (mMBR.left.toFloat() * zoom + ptStart.x).toInt().toFloat(),
                        (mMBR.top.toFloat() * zoom + ptStart.y).toInt().toFloat(),
                        5f,
                        roiPaint
                    )
                    canvas.drawCircle(
                        (mMBR.left.toFloat() * zoom + ptStart.x).toInt().toFloat(),
                        (mMBR.bottom.toFloat() * zoom + ptStart.y).toInt().toFloat(),
                        5f,
                        roiPaint
                    )
                    canvas.drawCircle(
                        (mMBR.right.toFloat() * zoom + ptStart.x).toInt().toFloat(),
                        (mMBR.bottom.toFloat() * zoom + ptStart.y).toInt().toFloat(),
                        5f,
                        roiPaint
                    )
                    canvas.drawCircle(
                        (mMBR.right.toFloat() * zoom + ptStart.x).toInt().toFloat(),
                        (mMBR.top.toFloat() * zoom + ptStart.y).toInt().toFloat(),
                        5f,
                        roiPaint
                    )
                }
                if (dashViewflag && bSelected) {
                    val distance = 50f

                    // normalize rect
                    var nTemp = 0
                    if (mMBR.left > mMBR.right) {
                        nTemp = mMBR.left
                        mMBR.left = mMBR.right
                        mMBR.right = nTemp
                    }
                    if (mMBR.top > mMBR.bottom) {
                        nTemp = mMBR.bottom
                        mMBR.bottom = mMBR.left
                        mMBR.left = nTemp
                    }
                    dashPoints[0].x =
                        ((mMBR.left.toFloat() * zoom + ptStart.x) - distance).toInt()
                    dashPoints[0].y =
                        ((mMBR.top.toFloat() * zoom + ptStart.y) - distance).toInt()
                    dashPoints[1].x =
                        ((mMBR.right.toFloat() * zoom + ptStart.x) + distance).toInt()
                    dashPoints[1].y =
                        ((mMBR.top.toFloat() * zoom + ptStart.y) - distance).toInt()
                    dashPoints[2].x =
                        ((mMBR.right.toFloat() * zoom + ptStart.x) + distance).toInt()
                    dashPoints[2].y =
                        ((mMBR.bottom.toFloat() * zoom + ptStart.y) + distance).toInt()
                    dashPoints[3].x =
                        ((mMBR.left.toFloat() * zoom + ptStart.x) - distance).toInt()
                    dashPoints[3].y =
                        ((mMBR.bottom.toFloat() * zoom + ptStart.y) + distance).toInt()

                    var i = 0
                    while (i < 4) {
                        canvas.drawLine(
                            dashPoints[i % 4].x.toFloat(),
                            dashPoints[i % 4].y.toFloat(),
                            dashPoints[(i + 1) % 4].x.toFloat(),
                            dashPoints[(i + 1) % 4].y.toFloat(),
                            rectDashpaint
                        )
                        i++
                    }
                }
            }

            ROI_TYPE_POLYGON -> {
                //Log.d(TAG, "m_Points.size() : "+(m_Points.size()));


                // Path를 정의하여 폴리곤 만들기
                path.reset()

                // Path의 경계 좌표를 계산
                var left = Int.MAX_VALUE
                var top = Int.MAX_VALUE
                var right = Int.MIN_VALUE
                var bottom = Int.MIN_VALUE

                var p = 0
                while (p < mPoints.size) {
                    val x = mPoints[p]!!.x
                    val y = mPoints[p]!!.y

                    // 경계 업데이트
                    if (x < left) left = x
                    if (y < top) top = y
                    if (x > right) right = x
                    if (y > bottom) bottom = y

                    if (p == 0) {
                        path.moveTo(
                            (mPoints[p]!!.x.toFloat() * zoom + ptStart.x).toInt().toFloat(),
                            (mPoints[p]!!.y.toFloat() * zoom + ptStart.y).toInt().toFloat()
                        ) // 첫 번째 점
                    } else {
                        path.lineTo(
                            (mPoints[p]!!.x.toFloat() * zoom + ptStart.x).toInt().toFloat(),
                            (mPoints[p]!!.y.toFloat() * zoom + ptStart.y).toInt().toFloat()
                        ) // 첫 번째 점
                    }
                    p++
                }
                path.close() // 시작점으로 닫기
                val bounds = intArrayOf(left, top, right, bottom)

                //Log.d(TAG, "fillPaint.getColor() : "+fillPaint.getColor());
                if (mEndRoiFlag == true) {
                    // 채우기 색상 먼저 그리기
                    if (bEdit) {
                        canvas.drawPath(path, fillPaint)
                    } else {
                        try {
                            drawMatchPoints(bitmap, canvas, fillPaint, bounds, ptStart)
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
                    canvas.drawPath(path, roiPaint)
                    //canvas.drawPath(path, fillPaint);
                    var p = 0
                    while (p < mPoints.size) {
                        //Log.d(TAG, "m_Points.get("+p+") : ("+(int)(m_Points.get(p).x)+","+(int)(m_Points.get(p).y)+")");
                        //Log.d(TAG, "m_zoom : ("+m_zoom);
                        //Log.d(TAG, "pt_Start : ("+(int)(pt_Start.x)+","+(int)(pt_Start.y)+")");
                        canvas.drawCircle(
                            (mPoints[p]!!.x.toFloat() * zoom + ptStart.x).toInt().toFloat(),
                            (mPoints[p]!!.y.toFloat() * zoom + ptStart.y).toInt().toFloat(),
                            5f,
                            roiPaint
                        )

                        if (p == 0) {
                            canvas.drawLine(
                                (mPoints[p]!!.x.toFloat() * zoom + ptStart.x).toInt().toFloat(),
                                (mPoints[p]!!.y.toFloat() * zoom + ptStart.y).toInt().toFloat(),
                                (mPoints[mPoints.size - 1]!!.x.toFloat() * zoom + ptStart.x).toInt()
                                    .toFloat(),
                                (mPoints[mPoints.size - 1]!!.y.toFloat() * zoom + ptStart.y).toInt()
                                    .toFloat(),
                                roiPaint
                            )
                        }
                        if (p > 0) {
                            canvas.drawLine(
                                (mPoints[p - 1]!!.x.toFloat() * zoom + ptStart.x).toInt()
                                    .toFloat(),
                                (mPoints[p - 1]!!.y.toFloat() * zoom + ptStart.y).toInt()
                                    .toFloat(),
                                (mPoints[p]!!.x.toFloat() * zoom + ptStart.x).toInt().toFloat(),
                                (mPoints[p]!!.y.toFloat() * zoom + ptStart.y).toInt().toFloat(),
                                roiPaint
                            )
                        }
                        p++
                    }
                    //canvas.drawCircle((int)((float)m_MBR_center.x*m_zoom + pt_Start.x), (int)((float)m_MBR_center.y*m_zoom + pt_Start.y), 11, Rectpaint);
                }

                if (bEdit) {
                    var p = 0
                    while (p < mPoints.size) {
                        canvas.drawCircle(
                            (mPoints[p]!!.x.toFloat() * zoom + ptStart.x).toInt().toFloat(),
                            (mPoints[p]!!.y.toFloat() * zoom + ptStart.y).toInt().toFloat(),
                            5f,
                            roiPaint
                        )
                        p++
                    }
                }
            }
        }
        //if (m_endroiflag == true && m_labelviewflag)
        if (labelViewFlag) {
            drawLabel(canvas, ptStart)
        }

        if (iconDrawable != null) {
            // 아이콘 크기 86.89 * 86.89
            val iconWidth = 86.89f * 2 // 아이콘의 너비
            val iconHeight = 86.89f * 2 // 아이콘의 높이

            // 중심점을 기준으로 아이콘의 Bounds 설정
            val iconLeft = ((mMBRCenter.x * zoom + ptStart.x) - (iconWidth / 2)).toInt()
            val iconTop = ((mMBRCenter.y * zoom + ptStart.y) - (iconHeight / 2)).toInt()
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

    fun addPoint(point: Point?) {
        //Log.d(TAG, "AddPoint("+point.x+","+point.y+")");
        mPoints.add(point)
        //Log.d(TAG, "m_MBR : ("+m_MBR.left+","+m_MBR.top+","+m_MBR.right+","+m_MBR.bottom+")");
        // MBR 구하기
        mMBR = Rect(10000, 10000, 0, 0)
        for (p in mPoints.indices) {
            if (mMBR.left > mPoints[p]!!.x) mMBR.left = mPoints[p]!!.x
            if (mMBR.right < mPoints[p]!!.x) mMBR.right = mPoints[p]!!.x
            if (mMBR.top > mPoints[p]!!.y) mMBR.top = mPoints[p]!!.y
            if (mMBR.bottom < mPoints[p]!!.y) mMBR.bottom = mPoints[p]!!.y
        }
        //Log.d(TAG, "m_MBR : ("+m_MBR.left+","+m_MBR.top+","+m_MBR.right+","+m_MBR.bottom+")");
        mMBRCenter.x = ((mMBR.left + mMBR.right) / 2)
        mMBRCenter.y = ((mMBR.top + mMBR.bottom) / 2)
    }

    fun AddEndPoint(point: Point?, flag: Boolean) {
        //Log.d(TAG, "AddEndPoint(...)");
        // 마지막 점은 제외하고 시작 포인트와 끝 포인트를 같게 함
        if (flag) {
            mPoints.add(point)
        }

        // MBR 구하기
        mMBR = Rect(10000, 10000, 0, 0)
        for (p in mPoints.indices) {
            if (mMBR.left > mPoints[p]!!.x) mMBR.left = mPoints[p]!!.x
            if (mMBR.right < mPoints[p]!!.x) mMBR.right = mPoints[p]!!.x
            if (mMBR.top > mPoints[p]!!.y) mMBR.top = mPoints[p]!!.y
            if (mMBR.bottom < mPoints[p]!!.y) mMBR.bottom = mPoints[p]!!.y
        }
        //Log.d(TAG, "m_MBR : ("+m_MBR.left+","+m_MBR.top+","+m_MBR.right+","+m_MBR.bottom+")");
        mEndRoiFlag = true
        mMBRCenter.x = ((mMBR.left + mMBR.right) / 2)
        mMBRCenter.y = ((mMBR.top + mMBR.bottom) / 2)
    }


    fun GetPointCount(): Int {
        var point_count = 0
        when (this.roiType) {
            ROI_TYPE_POINT -> point_count = 1
            ROI_TYPE_LINE -> point_count = 2
            ROI_TYPE_RECT -> point_count = 4
            ROI_TYPE_POLYGON -> point_count = mPoints.size
        }
        return point_count
    }

    fun GetPoint(nHandle: Int): Point {
        var nHandle = nHandle
        var x = -1
        var y = -1
        when (this.roiType) {
            ROI_TYPE_LINE -> when (nHandle) {
                1 -> {
                    x = mMBR.left
                    y = mMBR.top
                }

                2 -> {
                    x = mMBR.right
                    y = mMBR.bottom
                }

            }

            ROI_TYPE_POINT -> when (nHandle) {
                1 -> {
                    x = mMBR.left
                    y = mMBR.top
                }
            }

            ROI_TYPE_POLYGON -> if (nHandle < mPoints.size) {
                nHandle = mPoints.size
            }
        }
        val pt = Point(x, y)
        return pt
    }

    // 마우스를 올리면 변경 가능한 핸들 수
    // 점, 선은 2 나머지는 8을 리턴
    fun GetHandleCount(): Int {
        var handle_count = 0
        when (this.roiType) {
            ROI_TYPE_POINT -> handle_count = 2
            ROI_TYPE_LINE -> handle_count = 2
            ROI_TYPE_RECT -> handle_count = 8
            ROI_TYPE_POLYGON -> handle_count = mPoints.size
        }
        return handle_count
    }

    fun GetHandle(nHandle: Int): Point {
        var nHandle = nHandle
        when (this.roiType) {
            ROI_TYPE_LINE -> {
                if (nHandle == 2) {
                    nHandle = 8
                }

                if (nHandle == 1) {
                    nHandle = 1
                }
            }

            ROI_TYPE_POINT -> if (nHandle == 1) {
                nHandle = 1
            }

            ROI_TYPE_POLYGON -> if (nHandle == 1) {
                nHandle = mPoints.size
            }

            else -> nHandle = 8
        }


        var x: Int
        var y: Int
        val xcenter: Int
        val ycenter: Int

        y = -1
        x = y // 선택된 Tracker의 좌표 초기화

        if (this.roiType == ROI_TYPE_POLYGON) {
            if (nHandle < mPoints.size) {
                x = mPoints[nHandle]!!.x
                y = mPoints[nHandle]!!.y
            }
        } else {
            //데이터 크기의 중심 좌표 설정(Tracker 2, 4, 5, 7 해당하는 위치 나타냄)
            val temp = mMBR
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

        when (this.roiType) {
            ROI_TYPE_LINE -> {
                val sx = (mMBR.left.toFloat()).toInt()
                val sy = (mMBR.top.toFloat()).toInt()
                val ex = (mMBR.right.toFloat()).toInt()
                val ey = (mMBR.bottom.toFloat()).toInt()

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

            ROI_TYPE_POINT -> {
                //Log.d(TAG, "point : ("+point.x+","+point.y+")");
                //Log.d(TAG, "this.m_MBR.left - 50 : "+(this.m_MBR.left - 50));
                //Log.d(TAG, "this.m_MBR.left + 50 : "+(this.m_MBR.left + 50));
                //Log.d(TAG, "((this.m_MBR.left - 50)<point.x) : "+((this.m_MBR.left - 50)<point.x));
                if (((mMBR.left - 50) < point.x) && ((mMBR.left + 50) > point.x)) {
                    //Log.d(TAG, "this.m_MBR.top - 50 : "+(this.m_MBR.top - 50));
                    //Log.d(TAG, "this.m_MBR.top + 50 : "+(this.m_MBR.top + 50));
                    if (((mMBR.top - 50) < point.y) && ((mMBR.top + 50) > point.y)) {
                        return true
                    }
                }
            }

            ROI_TYPE_RECT, ROI_TYPE_POLYGON -> {
                if (((mMBR.left) < point.x) && ((mMBR.right) > point.x)) {
                    if (((mMBR.top) < point.y) && ((mMBR.bottom) > point.y)) {
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
                if ((this.roiType == ROI_TYPE_LINE) && nHandle == 2) return 8
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
        when (roiType) {
            ROI_TYPE_LINE -> if (((mMBR.left - cw) <= pt.x) && (pt.x <= (mMBR.left + cw)) && ((mMBR.top - ch) <= pt.y) && (pt.y <= (mMBR.top + ch))) {
                return 1 // left, top
            } else if (((mMBR.right - cw) <= pt.x) && (pt.x <= (mMBR.right + cw)) && ((mMBR.bottom - ch) <= pt.y) && (pt.y <= (mMBR.bottom + ch))) {
                return 8 // right, bottom
            }

            ROI_TYPE_RECT -> if (((mMBR.left - cw) <= pt.x) && (pt.x <= (mMBR.left + cw)) && ((mMBR.top - ch) <= pt.y) && (pt.y <= (mMBR.top + ch))) {
                return 1 // left, top
            } else if (((mMBR.right - cw) <= pt.x) && (pt.x <= (mMBR.right + cw)) && ((mMBR.top - ch) <= pt.y) && (pt.y <= (mMBR.top + ch))) {
                return 3 // right, top
            } else if (((mMBR.left - cw) <= pt.x) && (pt.x <= (mMBR.left + cw)) && ((mMBR.bottom - ch) <= pt.y) && (pt.y <= (mMBR.bottom + ch))) {
                return 6 // left, bottom
            } else if (((mMBR.right - cw) <= pt.x) && (pt.x <= (mMBR.right + cw)) && ((mMBR.bottom - ch) <= pt.y) && (pt.y <= (mMBR.bottom + ch))) {
                return 8 // right, bottom
            }

            ROI_TYPE_POLYGON -> {
                var i = 0
                i = 0
                while (i < mPoints.size) {
                    if (((mPoints[i]!!.x - cw) <= pt.x) && (pt.x <= (mPoints[i]!!.x + cw)) && ((mPoints[i]!!.y - ch) <= pt.y) && (pt.y <= (mPoints[i]!!.y + ch))) {
                        Log.d(
                            TAG,
                            "m_Points.get($i) : (" + mPoints[i]!!.x + "," + mPoints[i]!!.y + ")"
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
        if (roiType == ROI_TYPE_POLYGON) {
            for (p in mPoints.indices) {
                val point = Point(0, 0)

                point.x = mPoints[p]!!.x - dx
                point.y = mPoints[p]!!.y - dy

                mPoints[p] = point
            }
        }
        mMBR.left = pt1.x
        mMBR.top = pt1.y
        mMBR.right = pt2.x
        mMBR.bottom = pt2.y
    }

    fun MoveTo(pt1: Point, pt2: Point) {
        //Log.d(TAG, "MoveTo( ("+pt1.x+","+pt1.y+"),("+pt2.x+","+pt2.y+") )");
        // pt1 : mouse down
        // pt2 : mouse move

        // 실수 좌표계로 변환하여 대입한다.
        //var zoom = this.m_zoom;

        val dx = pt1.x - pt2.x
        val dy = pt1.y - pt2.y
        if (roiType == ROI_TYPE_POLYGON) {
            // 중심 좌표만 이동한다.
            mMBRCenter.x += dx
            mMBRCenter.y += dy

            // 예외처리
            if (mMBRCenter.x < mMBR.left) mMBRCenter.x = mMBR.left
            if (mMBRCenter.x > mMBR.right) mMBRCenter.x = mMBR.right
            if (mMBRCenter.y < mMBR.top) mMBRCenter.y = mMBR.top
            if (mMBRCenter.y > mMBR.bottom) mMBRCenter.y = mMBR.bottom

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
            mMBR.left += dx
            mMBR.top += dy
            mMBR.right += dx
            mMBR.bottom += dy
        }
    }

    fun MoveHandleTo(pt1: Point, pt2: Point, nHandle: Int) {
        //console.log(pt1);
        // 실수 좌표계로 변환하여 대입한다.
        val zoom = this.zoom

        // 왜 좌표가 2배 차이가 날까?
        val dx = (pt1.x - pt2.x)
        val dy = (pt1.y - pt2.y)

        when (nHandle) {
            1 -> {
                mMBR.left += dx
                mMBR.top += dy
            }

            2 -> {
                mMBR.top += dy
            }

            3 -> {
                mMBR.right += dx
                mMBR.top += dy
            }

            4 -> {
                mMBR.left += dx
            }

            5 -> {
                mMBR.right += dx
            }

            6 -> {
                mMBR.left += dx
                mMBR.bottom += dy
            }

            7 -> {
                mMBR.bottom += dy
            }

            8 -> {
                mMBR.right += dx
                mMBR.bottom += dy
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

        when (roiType) {
            ROI_TYPE_LINE -> MoveHandleTo(pt1, pt2, nHandle)
            ROI_TYPE_RECT -> MoveHandleTo(pt1, pt2, nHandle)
            ROI_TYPE_POLYGON -> if (nHandle <= mPoints.size) {
                mPoints[nHandle - 1]!!.x += dx
                mPoints[nHandle - 1]!!.y += dy
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

        val floatZoom = zoom.toFloat()
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
        val size = mPoints.size

        for (i in 0 until size) {
            val p1 = mPoints[i]
            val p2 = mPoints[(i + 1) % size]

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
        const val ROI_TYPE_POINT = "roi_point"
        const val ROI_TYPE_LINE = "roi_line"
        const val ROI_TYPE_RECT = "roi_rect"
        const val ROI_TYPE_POLYGON = "roi_polygon"
    }
}