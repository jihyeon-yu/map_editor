package com.forsk.ondevice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.util.Log
import android.util.Pair
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.View
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import com.forsk.ondevice.CDrawObj.Companion.ROI_TYPE_LINE
import com.forsk.ondevice.CDrawObj.Companion.ROI_TYPE_POINT
import com.forsk.ondevice.CDrawObj.Companion.ROI_TYPE_POLYGON
import com.forsk.ondevice.CDrawObj.Companion.ROI_TYPE_RECT
import java.util.Random
import kotlin.math.atan2
import kotlin.math.min

class MapImageView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var strMode: String = "맵 탐색"
    var strMenu: String = "Zoom"

    private var bitmap: Bitmap? = null // 표시할 비트맵

    private var viewWidth: Int = 0
    private var viewHeight: Int = 0

    private var aspectRate: Double = 0.0 // 최대 ZoomOut rate

    var zoomRate: Double = 0.0
    var zoomRateOffset: Double = 0.0 // 확대/축소 시 기준 zoom rate

    private var startPosX: Int = 0
    private var startPosY: Int = 0
    private var startPosXOffset: Int = 0
    private var startPosYOffset: Int = 0

    var strTouchStatus: String = "None"
    private var roiType: String = "default"

    private var nTouchDownPosX: Float = 0f
    private var nTouchDownPosY: Float = 0f
    private var nTouchUpPosX: Float = 0f
    private var nTouchUpPosY: Float = 0f

    var roiObjects: ArrayList<CDrawObj>
    var isCapture: Boolean = false // 마우스 누른 상태에서 이동 여부
    var roiViewFlag: Boolean = true // roi를 dispaly 여부

    //그리기 객체 사용
    var drawing: Boolean = false // roi을 그리는 중인지
    var drawStart: Boolean = false
    var dnPoint: Point = Point(-1, -1) // 객체의 rect 시작점
    var ptOld: Point = Point(-1, -1) // freehand 같은 경우를 표현하기 위해서 필요하다.
    var isMouseDown: Boolean = false
    var select: Int = -1 // 선택된 트래커 핸들..

    //현재 작업중인 그리기 객체 타입
    var currentSelectedIndex: Int = -1 // 선택된 m_RoiObjects 인덱스
    var roiCurObject: CDrawObj? = null // 선택된 roi
    var curType: String = "default" //현재 설정된 roi 타입(커서) 설정
    //var isSelected: Boolean = false // roi 선택 여부

    // 커서 종류
    var objSelect: Int = -1 // 마우스 모양

    var paint: Paint = Paint()

    private val scaleGestureDetector: ScaleGestureDetector
    private val matrix: Matrix

    private var scaleFactor = 1.0f

    private val random: Random // 랜덤 생성기

    // 공간 개수
    var roomNum: Int = 0

    val selectedObjectPosition: Pair<Int?, Int?>?
        get() {
            if (currentSelectedIndex != -1 && currentSelectedIndex < roiObjects.size) {
                val x: Int
                val y: Int
                when (roiCurObject?.roiType) {
                    ROI_TYPE_POLYGON -> {
                        val mbr =
                            roiObjects[currentSelectedIndex].mMBRCenter
                        x =
                            (mbr.x * roiObjects[currentSelectedIndex].zoom + startPosX - 57).toInt()
                        y =
                            (mbr.y * roiObjects[currentSelectedIndex].zoom + startPosY - 15).toInt()
                    }

                    ROI_TYPE_LINE -> {
                        x =
                            (roiObjects[currentSelectedIndex].mMBR.left * roiObjects[currentSelectedIndex].zoom + startPosX).toInt()
                        y =
                            (roiObjects[currentSelectedIndex].mMBR.top * roiObjects[currentSelectedIndex].zoom + startPosY + 100).toInt()
                    }

                    else -> {
                        x =
                            (((roiObjects[currentSelectedIndex].mMBR.left + roiObjects[currentSelectedIndex].mMBR.right) / 2)
                                    * roiObjects[currentSelectedIndex].zoom + startPosX - 55).toInt()
                        y =
                            (roiObjects[currentSelectedIndex].mMBR.top * roiObjects[currentSelectedIndex].zoom + startPosY + 120).toInt()
                    }
                }
                return Pair(x, y)
            }
            return null // 선택된 객체가 없으면 null 반환
        }

    fun clearSelection() {
        currentSelectedIndex = -1
        invalidate() // 화면을 다시 그리도록 요청
    }

    init {
        paint.color = Color.BLUE
        paint.style = Paint.Style.FILL

        roiObjects = ArrayList()

        // 랜덤 생성기 초기화
        random = Random()

        // Matrix 초기화
        matrix = Matrix()

        // ScaleGestureDetector 초기화
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())

        // 선택 객체 초기화
        currentSelectedIndex = -1
        roiCurObject = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount > 1) {
            scaleGestureDetector.onTouchEvent(event)
        } else {
            zoomRateOffset = zoomRate
            startPosXOffset = startPosX
            startPosYOffset = startPosY

            when (event.action) {
                MotionEvent.ACTION_DOWN -> MouseDown(event.x, event.y)
                MotionEvent.ACTION_UP -> MouseUp(event.x, event.y)
                MotionEvent.ACTION_MOVE -> MouseMove(event.x, event.y)
            }
        }

        return true
    }

    private inner class ScaleListener : SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // 스케일 값 가져오기
            scaleFactor *= detector.scaleFactor
            zoomRate = zoomRateOffset * scaleFactor

            val values = FloatArray(9)
            matrix.getValues(values)

            // 현재 Matrix 값에 스케일 적용
            matrix.setScale(zoomRate.toFloat(), zoomRate.toFloat())

            run {
                val centerToImgX =
                    ((width / 2 - startPosXOffset) / zoomRateOffset).toInt() // 선터 좌료를 원본 이미지 좌표로 변환
                val imgStartPosX =
                    ((width / 2) - centerToImgX * zoomRate).toFloat() // 원본이미지의 좌표를 변경된 이미지의 줌심좌표로 수정
                val translateX =
                    ((width / 2) - (((width / 2 - startPosXOffset) / zoomRateOffset).toInt()) * zoomRate).toFloat()
                val translateY =
                    ((height / 2) - (((height / 2 - startPosYOffset) / zoomRateOffset).toInt()) * zoomRate).toFloat()

                matrix.postTranslate(translateX, translateY)
            }

            // 화면 다시 그리기
            invalidate()
            return true
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val values = FloatArray(9)
        matrix.getValues(values)

        // 변환된 이미지의 시작 좌표
        startPosX = (values[Matrix.MTRANS_X]).toInt()
        startPosY = (values[Matrix.MTRANS_Y]).toInt()

        // 이미지 비율을 유지하면서 화면에 꽉차게 그려준다.
        if (bitmap != null) {
            canvas.drawBitmap(bitmap!!, matrix, null)
        }

        val iconDrawable = resources.getDrawable(R.drawable.benjamin_direction, null)
        val rotateDrawable = resources.getDrawable(R.drawable.ic_rotate, null)

        // 시작위치 만큼 + 해줘야 일치한다.
        val pt = Point(startPosX, startPosY)
        var x: Int
        var y: Int


        //Log.d(TAG, "m_RoiObjects.size() : " + (int)(m_RoiObjects.size()));
        var i = 0
        while (i < roiObjects.size) {
            if (strMenu == "핀 회전") {
                if (roiObjects[i].roiType == ROI_TYPE_POLYGON) {
                    // 선택된 것만 회전한다.
                    if (i == currentSelectedIndex) {
                        roiObjects[i].iconDrawable = iconDrawable
                        roiObjects[i].rotateDrawable = rotateDrawable
                    } else {
                        roiObjects[i].iconDrawable = iconDrawable
                        roiObjects[i].rotateDrawable = null
                    }
                }
            } else {
                roiObjects[i].iconDrawable = null
            }


            roiObjects[i].zoom = zoomRate
            if (strMenu == "수정") {
                if (curType == roiObjects[i].roiType && i == currentSelectedIndex) {
                    roiObjects[i].draw(canvas, pt, bitmap!!, true, true)
                } else {
                    roiObjects[i].draw(canvas, pt, bitmap!!, false, false)
                }
            } else {
                if (i == currentSelectedIndex) {
                    roiObjects[i].draw(canvas, pt, bitmap!!, true, false)
                } else {
                    roiObjects[i].draw(canvas, pt, bitmap!!, false, false)
                }
            }
            //m_RoiObjects.get(i).DrawLabel(canvas);
            // 241222 jihyeon 핀 회전일 때 아이콘 변경
            if (strMenu == "핀 회전") {
                //RotatePinIcon();
            } else if (roiObjects[i].roiType == ROI_TYPE_POLYGON) {
                val mbr = roiObjects[i].mMBRCenter
                x = (mbr.x * roiObjects[i].zoom + pt.x).toInt()
                y = (mbr.y * roiObjects[i].zoom + pt.y).toInt()
                // 배경으로 VectorDrawable 그리기
                val drawable_ic = AppCompatResources.getDrawable(context, R.drawable.ic_location)
                drawable_ic?.setBounds(x - 30, y - 80, x + 30, y + 10) //(60, 90)

                val drawable = AppCompatResources.getDrawable(context, R.drawable.pin_name)
                drawable?.setBounds(x - 65, y - 146, x + 64, y - 80) //(129, 66)
                drawable?.draw(canvas)

                // 텍스트 추가
                val paint = Paint()
                paint.color = Color.WHITE
                paint.textSize = 36f
                paint.textAlign = Paint.Align.CENTER

                // 선택할 경우 하얀 화면에 검은색 굴자, 선택이 안될 경우 검은색 화면에 하얀색 글자
                if (i == currentSelectedIndex) {
                    drawable?.setColorFilter(Color.parseColor("#FFFFFF"), PorterDuff.Mode.SRC_IN)
                    paint.color = Color.BLACK
                } else {
                    // default black color
                    paint.color = Color.WHITE
                }

                drawable_ic?.draw(canvas)
                drawable?.draw(canvas)
                canvas.drawText(roiObjects[i].label, x.toFloat(), (y - 100).toFloat(), paint)
            }
            i++
        }

        roiCurObject?.let {
            it.roiPaint.color = Color.rgb(253, 60, 60)

            if (it.roiType == ROI_TYPE_RECT) {
                it.fillPaint.color = Color.argb(178, 255, 70, 80) //투명도 30
            }
            roiCurObject!!.zoom = zoomRate

            //m_RoiCurObject.Draw(canvas, zoom_rate);
            if (strMenu == "수정") {
                if (curType == roiCurObject?.roiType) {
                    roiCurObject!!.draw(canvas, pt, bitmap!!, true, true)
                } else {
                    roiCurObject!!.draw(canvas, pt, bitmap!!, false, false)
                }
            } else {
                roiCurObject!!.draw(canvas, pt, bitmap!!, false, false)
            }
            if (strMenu == "삭제") {
                // 삭제 메뉴일 때 토글바 처리
                val position = selectedObjectPosition
                if (position != null) {
                    // Context를 MapEditorActivity로 캐스팅
                    if (context is MapEditorActivity) {
                        val activity = context as MapEditorActivity
                        activity.showDeleteToggleBar(position) // Activity의 메서드 호출
                    }
                }
            }
        }
    }

    fun setBitmap(map: Bitmap?) {
        this.bitmap = map

        if ((this.width < 1) || (this.height < 1)) return

        val viewWidth = this.width
        val viewHeight = this.height

        val imageW = bitmap!!.width
        val imageH = bitmap!!.height

        // 가로세로 비율을 구한다.
        val hRatio = (viewWidth).toDouble() / imageW
        val vRatio = (viewHeight).toDouble() / imageH

        aspectRate = min(hRatio, vRatio)

        // 확대 비율
        zoomRate = aspectRate

        //Log.d(TAG, "zoom_rate : " + zoom_rate);
        zoomRateOffset = zoomRate

        // 이미지를 그릴 위치를 구한다.
        startPosX = ((width - bitmap!!.width * zoomRate) / 2).toInt()
        startPosY = ((height - bitmap!!.height * zoomRate) / 2).toInt()

        startPosXOffset = startPosX
        startPosYOffset = startPosY

        matrix.setScale(zoomRate.toFloat(), zoomRate.toFloat())

        // 이동 값을 적용 (중앙 고정)
        val translateX = ((width - bitmap!!.width * zoomRate) / 2).toFloat()
        val translateY = ((height - bitmap!!.height * zoomRate) / 2).toFloat()

        matrix.postTranslate(translateX, translateY)
        invalidate() // 화면을 다시 그리도록 요청
    }

    fun getBitmapWidth(): Int = bitmap?.width ?: 0
    fun getBitmapHeight(): Int = bitmap?.height ?: 0

    fun setMode(str: String) {
        //Log.d(TAG, "SetMode("+str+")");
        cancelCurObject()
        drawStart = false
        this.strMode = str
        // 241222 jihyeon 이전 모드가 핀 회전 모드였으면, 초기화
        if (strMenu == "핀 회전") {
            for (roiObject in roiObjects) {
                roiObject.clearIconDrawable()
                roiObject.clearRotateDrawable()
            }
        }
        strMenu = "default"
        when (strMode) {
            "맵 탐색" -> this.curType = "default"
            "공간 생성" -> this.curType = ROI_TYPE_POLYGON
            "가상벽" -> this.curType = ROI_TYPE_LINE
            "금지공간" -> this.curType = ROI_TYPE_RECT
        }
        invalidate()
    }

    fun setMenu(str: String) {
        //Toast.makeText(getContext().getApplicationContext(), "SetMenu("+str+")", Toast.LENGTH_SHORT).show();
        // 241222 jihyeon 이전 모드가 핀 회전 모드였으면, 초기화
        if (str != "핀 회전" && strMenu == "핀 회전") {
            for (roiObject in roiObjects) {
                roiObject.clearIconDrawable()
            }
        }
        when (str) {
            "추가" -> {
                setMode(strMode) // m_CurType를 재 설정해준다.
                drawStart = true
            }

            "수정" -> {
                cancelCurObject()
                drawStart = false
            }

            "선택" -> {
                if (strMenu == "추가") {
                    roiAddObject()
                } else {
                    cancelCurObject()
                }
                drawStart = false
            }

            "삭제" -> {
            }

            "핀 회전" -> {
                rotatePinIcon();
                // TODO 기능 구현 필요
            }
        }

        strMenu = str
        invalidate()
    }

    // 241222 jihyeon 핀 회전 모드 아이콘
    private fun rotatePinIcon() {
        val iconDrawable =
            ResourcesCompat.getDrawable(resources, R.drawable.benjamin_direction, null)
        val rotateDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_rotate, null)

        for (roiObject in roiObjects) {
            if (roiObject.roiType == ROI_TYPE_POLYGON) {
                roiObject.iconDrawable = iconDrawable
                roiObject.rotateDrawable = rotateDrawable
            }
        }

        // 화면 갱신
        invalidate()
    }

    private fun rotatePinIcon(nIndex: Int) {
        if (nIndex > (roiObjects.size - 1)) return
        val roiObject = roiObjects[nIndex]

        val iconDrawable = resources.getDrawable(R.drawable.benjamin_direction, null)
        val rotateDrawable = resources.getDrawable(R.drawable.ic_rotate, null)
        if (roiObject.roiType == ROI_TYPE_POLYGON) {
            roiObject.iconDrawable = iconDrawable
            roiObject.rotateDrawable = rotateDrawable
        }
    }

    fun ZoomIn() {
        zoomRate += 0.2
        // 최대 5배 확대만 가능하게
        if (zoomRate > (aspectRate * 5.0)) zoomRate = aspectRate * 5.0

        invalidate() // 화면을 다시 그리도록 요청
    }

    fun ZoomOut() {
        zoomRate -= 0.2
        // 화면에 꽉차게
        if (aspectRate > zoomRate) zoomRate = aspectRate

        invalidate() // 화면을 다시 그리도록 요청
    }

    fun MouseDown(x: Float, y: Float) {
        //Log.d(TAG, "mouseDown("+x+","+y+")");

        nTouchDownPosX = (x * zoomRate).toInt().toFloat()
        nTouchUpPosX = nTouchDownPosX
        nTouchDownPosY = (y * zoomRate).toInt().toFloat()
        nTouchUpPosY = nTouchDownPosY

        //241218 성웅 strMode 맵탐색에서 strMenu 이동으로 변경
        // TODO 추후 정리 필요
        if (strMenu == "이동") {
            //if (strMenu.equals("이동") || strMenu.equals("핀 회전")) {
            dnPoint.x = x.toInt()
            dnPoint.y = y.toInt()

            ptOld.x = x.toInt()
            ptOld.y = y.toInt()
        } else {
            // 정수로 변환
            val pt_x = (x).toInt()
            //int pt_x = (int) ((x-StartPos_x)/zoom_rate);
            val pt_y = (y).toInt()

            //int pt_y = (int) ((y-StartPos_y)/zoom_rate);
            //pt_y = img_map.height - parseInt(pt_y);
            val point = Point(pt_x, pt_y)

            Log.d(TAG, "----------------------------------")
            Log.d(TAG, "m_isCapture : $isCapture")
            Log.d(TAG, "m_drawing : $drawing")
            Log.d(TAG, "m_roiviewflag : $roiViewFlag")
            Log.d(TAG, "m_CurType : $curType")
            Log.d(TAG, "m_objSelect : $objSelect")
            Log.d(TAG, "m_RoiCurIndex : " + currentSelectedIndex)

            // 그리기 상태
            if (drawStart == true) {
                if (drawing == false) {
                    //console.log(point);
                    if (!createCObject(curType, point)) {
                        drawStart = false
                        return
                    }

                    //console.log(point);
                    dnPoint.x = point.x
                    dnPoint.y = point.y

                    ptOld.x = point.x
                    ptOld.y = point.y

                    select = -1

                    drawing = true
                    isSelected = false

                    isCapture = true
                } else {
                    if (curType == ROI_TYPE_POLYGON) {
                        val pt = Point(
                            ((point.x - startPosX) / zoomRate).toInt(),
                            ((point.y - startPosY) / zoomRate).toInt()
                        )
                        roiCurObject!!.addPoint(pt)
                        ptOld = point
                    }
                    dnPoint.x = point.x
                    dnPoint.y = point.y

                    invalidate() // 화면을 다시 그리도록 요청
                }
            } else if (drawing == false) {
                val pt = Point(
                    ((point.x - startPosX) / zoomRate).toInt(),
                    ((point.y - startPosY) / zoomRate).toInt()
                )
                objSelect = findObject(pt, true)

                Log.d(TAG, "m_objSelect : $objSelect")

                setCursorType()

                // 현재 좌표가 ROI 객체 내부이면 이동시작
                if ((objSelect != -1) && (roiViewFlag == true)) {
                    dnPoint.x = point.x
                    dnPoint.y = point.y

                    isSelected = true


                    // CObject_Draw();
                    isCapture = true
                } else  // ROI 외부이면 DrawTracker 없앰.
                {
                    isSelected = false
                    select = -1
                    currentSelectedIndex = -1

                    isCapture = false
                    drawing = false
                    drawStart = drawing

                    //map_image_draw();
                    //CObject_Draw();
                }
                invalidate() // 화면을 다시 그리도록 요청
            }
        }
        isMouseDown = true
    }

    fun MouseMove(x: Float, y: Float) {
        when (strMenu) {
            "이동" -> {
                val dx = ((x - dnPoint.x)).toInt()
                val dy = ((y - dnPoint.y)).toInt()

                var bIsRedraw = false

                if (dx != 0) {
                    startPosX += dx
                    dnPoint.x = x.toInt()
                    bIsRedraw = true
                }

                if (dy != 0) {
                    startPosY += dy
                    dnPoint.y = y.toInt()

                    bIsRedraw = true
                }

                if (bIsRedraw) {
                    matrix.postTranslate(dx.toFloat(), dy.toFloat())
                    invalidate() // 화면을 다시 그리도록 요청
                }
            }

            "핀 회전" -> {
                if (currentSelectedIndex != -1) {
                    val iconCenterX =
                        ((roiObjects[currentSelectedIndex].mMBRCenter.x * zoomRate + startPosX)).toFloat() // +30 - 30 상쇄됨
                    val iconCenterY =
                        ((roiObjects[currentSelectedIndex].mMBRCenter.y * zoomRate + startPosY)).toFloat() // +40 - 40 상쇄됨

                    val deltaAngle = calculateAngle(iconCenterX, iconCenterY, x, y)

                    roiObjects[currentSelectedIndex].angle = deltaAngle

                    // 화면을 갱신해준다.
                    invalidate()
                }
            }

            else -> {
                // 정수로 변환
                val pt_x = (x).toInt()
                val pt_y = (y).toInt()

                val point = Point(pt_x, pt_y)

                if (isCapture)  // 마우스 누른 상태에서 이동중이면
                {
                    if (drawing) {
                        if (curType == ROI_TYPE_LINE || curType == ROI_TYPE_RECT) {
                            moveToRect(dnPoint, point)
                        }
                    } else {
                        if (objSelect == 0) {
                            moveTo(dnPoint, point)
                        } else if (currentSelectedIndex > -1) {
                            Log.d(TAG, "m_RoiCurIndex :  " + currentSelectedIndex)
                            Log.d(
                                TAG,
                                "m_RoiObjects.get(m_RoiCurIndex).roi_type : " + roiObjects[currentSelectedIndex].roiType
                            )
                            if (roiObjects[currentSelectedIndex].roiType != ROI_TYPE_POINT) {
                                // 선택된 객체의 모양을 변경한다.
                                //  Log.d(TAG, "else if Point : " + point.x + ", " + point.y);
                                // Log.d(TAG, "DPoint: " + m_DnPoint.x + ", " + m_DnPoint.y);
                                if (strMenu == "수정") {
                                    movePointTo(point, dnPoint, objSelect)
                                } else {
                                    moveHandleTo(point, dnPoint, objSelect)
                                }
                            } else if (roiObjects[currentSelectedIndex].roiType != ROI_TYPE_POLYGON) {
                                moveToRect(dnPoint, point)


                                invalidate() // 화면을 다시 그리도록 요청
                            }
                        }
                        dnPoint.x = point.x
                        dnPoint.y = point.y
                    }
                } else {
                    if ((drawing == false) && roiViewFlag)  // 마우스 이동시, 객체 찾아서 커서의 모양을 바꾼다.
                    {
                        //m_objSelect = CObject_FindObject(point, false);

                        //SetCursorType();

                        ////if(m_objSelect != -1)
                        ////{
                        ////    SetCursorType();
                        ////    //CObject_Draw();   // 선택된 경우 그려주는 색상변경
                        ////}
                    } else if (drawing == true) {
                        // 그리기 모드
                        //if(m_CurType == 'roi_multiline') // 이동 괴적이 시작과 끝만 있는 것이 아닌 경우
                        //{
                        //    CObject_MoveTo(m_DnPoint, point);
                        //    m_DnPoint.x = point.x;
                        //    m_DnPoint.y = point.y;
                        //}
                        if (curType == ROI_TYPE_POLYGON) {
                            moveTo(dnPoint, point)
                            //CObject_MoveToRect(m_DnPoint, point);
                            dnPoint = point

                            invalidate() // 화면을 다시 그리도록 요청
                        }
                    }
                }
            }
        }
    }

    fun MouseUp(x: Float, y: Float) {
        //Log.d(TAG, "MouseUp( "+x+","+y+" )");

        nTouchUpPosX = (x * zoomRate).toInt().toFloat()
        nTouchUpPosY = (y * zoomRate).toInt().toFloat()
        if (strMode == "맵 탐색" && strMenu == "이동") {

        } else {
            // 정수로 변환

            val pt_x = (x).toInt()
            val pt_y = (y).toInt()

            val point = Point(pt_x, pt_y)
            isMouseDown = false

            if (isCapture)  // 누른 상태에서 마우스 이동중
            {
                isCapture = false // 누른 상태로 마우스 이동 해제
                if (drawing) {
                    when (curType) {
                        ROI_TYPE_POINT -> {
                            roiCurObject!!.mEndRoiFlag = true
                            if (CObject_AddCurObject(point)) {
                                drawStart = true // 그리기가 끝남.
                                drawing = false
                                isSelected = true
                            } else {
                                drawing = false
                                isSelected = false
                                drawStart = false
                                curType = "default"
                            }
                        }
                        ROI_TYPE_LINE -> {
                            roiCurObject!!.mEndRoiFlag = true // 그리기가 끝났음을 나타냄
                            if (CObject_AddCurObject(point)) {
                                drawing = false
                                drawStart = true
                                isSelected = true
                            } else {
                                drawing = false
                                isSelected = false
                                drawStart = false

                                dnPoint.x = -1
                                dnPoint.y = -1

                                ptOld.x = -1
                                ptOld.y = -1
                            }
                        }
                        ROI_TYPE_RECT -> {
                            roiCurObject!!.mEndRoiFlag = true // 그리기가 끝났음을 나타냄
                            if (CObject_AddCurObject(point)) {
                                drawing = false
                                drawStart = true
                                isSelected = true
                            } else {
                                drawing = false
                                isSelected = false
                                drawStart = false

                                dnPoint.x = -1
                                dnPoint.y = -1

                                ptOld.x = -1
                                ptOld.y = -1
                            }
                        }
                        ROI_TYPE_POLYGON -> {
                        }
                    }

                    //map_image_draw();
                    //CObject_Draw();
                    invalidate() // 화면을 다시 그리도록 요청

                    setCursorType()
                } else if (select != 0) {
                    //SetNormalizeRect();
                    isSelected = true

                    //map_image_draw();
                    invalidate()

                    //CObject_Draw();
                    setCursorType()
                }
            }
        }
    }

    fun MoveMap(dx: Float, dy: Float) {
        // 그려주는 onDraw()함수에 버그가 있음.

        startPosX += dx.toInt()
        startPosY += dy.toInt()

        val imageW = bitmap!!.width
        val imageH = bitmap!!.height

        // 화면을 벗어나는 경우의 예외 처리가 필요함
        if (startPosX <= ((viewWidth - imageW * zoomRate) / 2).toInt()) {
            startPosX = ((viewWidth - imageW * zoomRate) / 2).toInt()
        }

        if (startPosY <= ((viewHeight - imageH * zoomRate) / 2).toInt()) {
            startPosY = ((viewHeight - imageH * zoomRate) / 2).toInt()
        }


        invalidate() // 화면을 다시 그리도록 요청
    }

    fun roiAddObject() {
        Log.d(TAG, "roi_AddObject(): ")
        if (roiCurObject == null) return

        // line 생성
        if ((select == -1) && roiCurObject?.roiType == ROI_TYPE_LINE) {
            // 객체 완성
            currentSelectedIndex = roiObjects.size
            roiObjects.add(roiCurObject!!)

            ////Log.d(TAG, "m_RoiObjects.add(m_RoiCurObject): " + m_RoiCurObject.roi_type.equals(ROI_TYPE_LINE));
            drawStart = true
            drawing = false
            roiCurObject!!.mEndRoiFlag = true
            isCapture = false
            isSelected = true

            invalidate()

            return
        }

        // rect 생성
        if ((select == -1) && roiCurObject?.roiType == ROI_TYPE_RECT) {
            // 객체 완성
            currentSelectedIndex = roiObjects.size
            roiObjects.add(roiCurObject!!)
            Log.d(
                TAG,
                "m_RoiObjects.add(m_RoiCurObject): " + (roiCurObject?.roiType == ROI_TYPE_RECT)
            )

            drawStart = true
            drawing = false
            roiCurObject!!.mEndRoiFlag = true
            isCapture = false
            isSelected = true

            invalidate()

            return
        }

        if (drawing) {
            if (roiCurObject!!.mPoints.size < 3) {
                roiCurObject = null
                currentSelectedIndex = -1
                drawStart = true
                drawing = false
                isSelected = false
            } else {
                if (select == -1 && roiCurObject?.roiType == ROI_TYPE_POLYGON) {
                    val pt = Point(0, 0)
                    roiCurObject!!.AddEndPoint(pt, false) // 끝점 제거

                    roomNum++
                    roiCurObject!!.label = "공간$roomNum"
                    // 객체 완성
                    currentSelectedIndex = roiObjects.size
                    roiObjects.add(roiCurObject!!)
                    Log.d(TAG, "m_RoiObjects.add(m_RoiCurObject) " + currentSelectedIndex)

                    drawStart = true
                    drawing = false
                    roiCurObject!!.mEndRoiFlag = true
                    isCapture = false
                    isSelected = true

                    invalidate()

                    return
                }
            }
            invalidate()
            setCursorType()
        }
    }

    fun removeObject() {
        if (!drawStart && !drawing) {
            if (delCurObject()) {
                //roomNum = CountRoomNum(); 공간 이름을 갱신할 것인가?
                invalidate()
            }
        }
    }

    fun roi_FindObject() {
        strMode = "Roi Find"

        curType = "default"

        cancelCurObject()

        strMode = "Roi Find & Move"
    }

    fun CObject_AddCurObject(point: Point): Boolean {
        //Log.d(TAG,"CObject_AddCurObject("+point.x+","+point.y+")");

        if (roiCurObject == null) {
            //Log.d(TAG, "m_RoiCurObject == null");
            return false
        }


        val pt_x = (point.x / zoomRate).toInt()
        val pt_y = (point.y / zoomRate).toInt()

        val pt = Point(pt_x, pt_y)

        // 좌상단 좌표를 시작점으로 하고 마지막 점과 같으면
        val sp = Point(roiCurObject!!.mMBR.left, roiCurObject!!.mMBR.top)
        //console.log('CObject_AddCurObject::( ('+pt_x+','+pt_y+') ('+sp.x+','+sp.y+') )');
        if ((sp.x == pt_x) && (sp.y == pt_y)) {
            if (roiCurObject?.roiType != ROI_TYPE_POINT) {
                roiCurObject = null
                currentSelectedIndex = -1
                return false
            }
        }

        if (roiCurObject?.roiType == ROI_TYPE_POINT) {
            roiCurObject!!.mMBR.right = pt_x
            roiCurObject!!.mMBR.bottom = pt_y

            currentSelectedIndex = roiObjects.size
            roiObjects.add(roiCurObject!!)
            //Log.d(TAG, "m_RoiObjects.add(m_RoiCurObject)");
        } else if (roiCurObject?.roiType == ROI_TYPE_POLYGON) {
            roiCurObject!!.AddEndPoint(pt, false)

            currentSelectedIndex = roiObjects.size
            roiObjects.add(roiCurObject!!)

            //Log.d(TAG, "m_RoiObjects.add(m_RoiCurObject)");
        }
        //console.log(m_RoiCurObject);
        return true
    }

    fun createCObject(objType: String, point: Point): Boolean {
        roiCurObject = null
        currentSelectedIndex = -1

        val ptX = ((point.x - startPosX) / zoomRate).toInt()
        val ptY = ((point.y - startPosY) / zoomRate).toInt()

        when (objType) {
            ROI_TYPE_POINT -> roiCurObject = CDrawObj(ROI_TYPE_POINT, ptX, ptY, ptX, ptY)

            ROI_TYPE_LINE -> {
                roiCurObject = CDrawObj(ROI_TYPE_LINE, ptX, ptY, ptX, ptY)
                roiCurObject!!.fillPaint.color = randomColor
            }

            ROI_TYPE_RECT -> roiCurObject = CDrawObj(ROI_TYPE_RECT, ptX, ptY, ptX, ptY)

            ROI_TYPE_POLYGON -> {
                val pt = Point(ptX, ptY)
                roiCurObject = CDrawObj(ROI_TYPE_POLYGON, ptX, ptY, ptX, ptY).apply {
                    addPoint(pt)
                }
            }

            else -> {}
        }

        return true
    }

    fun delCurObject(): Boolean {
        if (currentSelectedIndex == -1) {
            return false
        }

        roiObjects.removeAt(currentSelectedIndex)
        roiCurObject = null
        currentSelectedIndex = -1

        invalidate() // 화면을 다시 그리도록 요청
        return true
    }

    fun cancelCurObject() {
        if (drawing) {
            drawing = false
            drawStart = false
            roiCurObject = null
            currentSelectedIndex = -1

            curType = "default"

            isSelected = false

            //map_image_draw();
            invalidate() // 화면을 다시 그리도록 요청
            //CObject_Draw();
        } else if (drawStart) {
            drawing = false
            drawStart = false

            curType = "default"

            isSelected = false
        }
    }

    // 마우스 좌표만큼 선택한 객체을 수정한다.
    fun moveHandleTo(point: Point, pointDn: Point, nHandle: Int) {
        Log.d(
            TAG,
            "CObject_MoveHandleTo( (" + pointDn.x + "," + pointDn.y + "),(" + point.x + "," + point.y + ")," + nHandle + ")"
        )

        if (roiCurObject == null) return

        val pt1 = Point(0, 0)
        pt1.x = ((point.x - startPosX) / zoomRate).toInt()
        pt1.y = ((point.y - startPosY) / zoomRate).toInt()

        val pt2 = Point(0, 0)
        pt2.x = ((pointDn.x - startPosX) / zoomRate).toInt()
        pt2.y = ((pointDn.y - startPosY) / zoomRate).toInt()
        if (currentSelectedIndex > -1) {
            if (strMenu == "선택") {
                roiObjects[currentSelectedIndex].MoveTo(pt1, pt2)
            } else {
                roiObjects[currentSelectedIndex].moveHandleTo(pt1, pt2, nHandle)
            }
            roiCurObject = roiObjects[currentSelectedIndex]
        } else {
            roiCurObject!!.moveHandleTo(pt1, pt2, nHandle)
        }

        invalidate() // 화면을 다시 그리도록 요청
    }

    fun movePointTo(point: Point, point_dn: Point, nHandle: Int) {
        Log.d(
            TAG,
            "CObject_MovePointTo( (" + point_dn.x + "," + point_dn.y + "),(" + point.x + "," + point.y + ")," + nHandle + ")"
        )

        if (roiCurObject == null) return

        val pt1 = Point(0, 0)
        pt1.x = ((point.x - startPosX) / zoomRate).toInt()
        pt1.y = ((point.y - startPosY) / zoomRate).toInt()

        val pt2 = Point(0, 0)
        pt2.x = ((point_dn.x - startPosX) / zoomRate).toInt()
        pt2.y = ((point_dn.y - startPosY) / zoomRate).toInt()

        if (currentSelectedIndex > -1) {
            when (strMenu) {
                "선택" -> roiObjects[currentSelectedIndex].MoveTo(pt1, pt2)
                "수정" -> roiObjects[currentSelectedIndex].MovePointTo(pt1, pt2, nHandle)
                else -> roiObjects[currentSelectedIndex].moveHandleTo(pt1, pt2, nHandle)
            }
            roiCurObject = roiObjects[currentSelectedIndex]
        } else {
            roiCurObject!!.moveHandleTo(pt1, pt2, nHandle)
        }
        invalidate() // 화면을 다시 그리도록 요청
    }

    // roi 영역의 시작점(point.dn)과 끝점(point)
    // roi_line을 그릴 때 사용한다.
    fun moveToRect(pointDn: Point, point: Point) {
        if (roiCurObject == null) return

        // 왼쪽 하단이 원점(0,0)이다.
        val pt1 = Point(0, 0)
        pt1.x = ((pointDn.x - startPosX) / zoomRate).toInt()
        pt1.y = ((pointDn.y - startPosY) / zoomRate).toInt()

        val pt2 = Point(0, 0)
        pt2.x = ((point.x - startPosX) / zoomRate).toInt()
        pt2.y = ((point.y - startPosY) / zoomRate).toInt()

        // 이미지 시작 좌표이동

        // 선택된 객체이동
        roiCurObject!!.MoveToRect(pt1, pt2)
        if (currentSelectedIndex > -1) {
            roiObjects[currentSelectedIndex] = roiCurObject!!
        }

        invalidate() // 화면을 다시 그리도록 요청
    }

    // 마우스 좌표만큼 선택한 객체를 이동한다.
    fun moveTo(point_dn: Point, point: Point) {
        if (roiCurObject == null) return
        val roiCurObject = roiCurObject!!

        val pt1 = Point(0, 0)
        pt1.x = ((point.x - startPosX) / zoomRate).toInt()
        pt1.y = ((point.y - startPosY) / zoomRate).toInt()

        val pt2 = Point(0, 0)
        pt2.x = ((point_dn.x - startPosX) / zoomRate).toInt()
        pt2.y = ((point_dn.y - startPosY) / zoomRate).toInt()

        // 241216 Bitmap밖으로 안벗어나게 변화
        val x = roiCurObject.mMBRCenter.x
        val y = roiCurObject.mMBRCenter.y
        val dx = pt1.x - pt2.x
        val dy = pt1.y - pt2.y
        val pixelColor: Int

        // 241216 핀이 bitmap 영역 밖으로 나가지 않도록 변경
        if (roiCurObject.roiType === ROI_TYPE_POLYGON) {
            if ((x + dx > 0) && (y + dy > 0) &&
                (x + dx <= bitmap!!.width) && (y + dy <= bitmap!!.height)
            ) {
                pixelColor = bitmap!!.getPixel(x + dx, y + dy)
                if ((pixelColor and 0x00FFFFFF) == 0x00969696) {
                    roiCurObject.MoveTo(pt1, pt2)
                }
            }
        } else {
            roiCurObject.MoveTo(pt1, pt2)
        }

        if (currentSelectedIndex > -1) {
            //m_RoiObjects.get(m_RoiCurIndex) = m_RoiCurObject;
            roiObjects[currentSelectedIndex] = roiCurObject!!
            //m_RoiObjects[m_RoiCurIndex].MoveTo(point, point_dn);
            //m_RoiCurObject = m_RoiObjects[m_RoiCurIndex];
        }

        //map_image_draw();
        invalidate() // 화면갱신
        //CObject_Draw();
    }

    fun findObject(point: Point, msMove: Boolean): Int {
        // 현재 객체에서 포인트 테스트

        Log.d(TAG, "m_RoiCurIndex : " + currentSelectedIndex)

        // 원본 이미지에서 검사하는 픽셍의 크기
        val cw = (50 / zoomRate).toInt()
        val ch = (50 / zoomRate).toInt()

        if (currentSelectedIndex != -1) {
            var fRet = -1
            if (roiCurObject != null) {
                Log.d(TAG, "m_CurType : $curType")
                if (curType == roiCurObject?.roiType) {
                    fRet = if (strMenu == "수정") {
                        roiCurObject!!.PointInPoint(point, cw, ch)
                    } else {
                        roiCurObject!!.PointInHandle(point, cw, ch)
                    }
                }
            } else {
                Log.d(TAG, "m_RoiCurObject == null")
                if (strMenu == "수정") {
                    fRet = roiObjects[currentSelectedIndex].PointInPoint(point, cw, ch)
                } else {
                    fRet = roiObjects[currentSelectedIndex].PointInHandle(point, cw, ch)
                    if (fRet > 0) {
                        fRet = 0 // "수정"이 아닌 경우에는 이동으로 설정한다.
                    }
                }
            }

            if ((roiObjects[currentSelectedIndex].roiType == ROI_TYPE_POINT) && (fRet == 1)) {
                // point의 경우에는 커서를 이동으로 설정한다.
                return 0
            }
            if (fRet != 0) return fRet
        }

        var obj = 0
        obj = 0
        while (obj < roiObjects.size) {
            if (curType == roiObjects[obj].roiType) {
                if (roiObjects[obj].PointInRect(point)) {
                    if (msMove)  // 마우스를 누른 경우
                    {
                        roiCurObject = roiObjects[obj]
                        currentSelectedIndex = obj
                    }
                    return 0
                }
            }

            obj++
        }

        // 이동중이면
        if (msMove) {
            roiCurObject = null
            currentSelectedIndex = -1
        }

        return -1
    }

    // 참조: 구글검색 "javascript 마우스 커서 변경"
    fun setCursorType(): Boolean {
        // 그리기 상태
        return when {
            drawStart -> true
            curType !== "default" -> true                    // 사용자 지정 커서 타입
            else -> false
        }
    }

    private val randomColor: Int
        // 랜덤 색상 생성 함수
        get() {
            // RGB 값을 랜덤으로 생성

            val red = (Math.random() * 255).toInt() // 0~255
            val green = (Math.random() * 255).toInt() // 0~255
            val blue = (Math.random() * 255).toInt() // 0~255

            return Color.rgb(red, green, blue) // 랜덤 색상 반환
        }

    fun setLabel(newLabel: String) {
        if (roiCurObject == null) {
            return  // 현재 선택된 객체가 없으면 종료
        }

        // 중복된 라벨인지 확인
        if (isLabelDuplicate(newLabel)) {
//            Toast.makeText(context.applicationContext, "공간명이 중복되지 않도록 설정해주세요.", Toast.LENGTH_SHORT)
//                .show()
            return
        }

        // 중복이 아니면 라벨 설정
        roiCurObject!!.label = newLabel
        invalidate()
    }


    private fun isLabelDuplicate(label: String): Boolean {
        for (roiObject in roiObjects) {
            if (roiObject !== roiCurObject && label == roiObject.label) {
                return true // 중복된 라벨 발견
            }
        }
        return false // 중복 없음
    }

    fun addPointPolygon(point: Point?) {
        roiCurObject!!.addPoint(point)
    }

    fun loadObject(objType: String, point: Point): Boolean {
        roiCurObject = null
        currentSelectedIndex = -1

        //Log.d(TAG, "CObject_CreateObject("+objType+",("+point.x+","+point.y+") )");
        val pt_x = point.x
        val pt_y = point.y

        //Log.d(TAG, "CObject_CreateObject("+objType+",("+pt_x+","+pt_y+") )");
        when (objType) {
            ROI_TYPE_POINT ->                 //console.log('pt('+pt_x+','+pt_y+')');
                roiCurObject = CDrawObj(ROI_TYPE_POINT, pt_x, pt_y, pt_x, pt_y)

            ROI_TYPE_LINE -> {
                roiCurObject = CDrawObj(ROI_TYPE_LINE, pt_x, pt_y, pt_x, pt_y)
                roiCurObject!!.fillPaint.color = randomColor
            }

            ROI_TYPE_RECT -> roiCurObject = CDrawObj(ROI_TYPE_RECT, pt_x, pt_y, pt_x, pt_y)

            ROI_TYPE_POLYGON -> {
                roiCurObject = CDrawObj(ROI_TYPE_POLYGON, pt_x, pt_y, pt_x, pt_y)
                val pt = Point(pt_x, pt_y)
                roiCurObject!!.addPoint(pt)
            }

            else -> {}
        }


        //console.log(m_RoiCurObject);
        return true
    }

    fun loadRect(point1: Point, point2: Point) {
        //m_RoiCurIndex = m_RoiObjects.size()-1;

        if (roiCurObject == null) return


        // 왼쪽 하단이 원점(0,0)이다.
        roiCurObject!!.mMBR.right = point1.x
        roiCurObject!!.mMBR.bottom = point1.y
        roiCurObject!!.mMBR.left = point2.x
        roiCurObject!!.mMBR.top = point2.y
        if (currentSelectedIndex > -1) {
            roiObjects[currentSelectedIndex] = roiCurObject!!
        }

        invalidate() // 화면을 다시 그리도록 요청
    }

    private fun calculateAngle(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        // atan2로 각도 계산
        val angle = -(atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())).toFloat()
        return angle
    }

    companion object {
        private const val TAG = "MapImageView"
    }
}