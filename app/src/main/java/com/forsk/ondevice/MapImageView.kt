package com.forsk.ondevice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.util.Pair
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.View
import android.widget.Toast
import java.util.Random
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class MapImageView(context: Context, attrs: AttributeSet?) :
    View(context, attrs) {
    var strMode: String = "맵 탐색"
    var strMenu: String = "Zoom"

    private var bitmap: Bitmap? = null // 표시할 비트맵
    private var bitmapRotate: Bitmap? = null

    @JvmField
    var ScreenWidth: Int = 0
    @JvmField
    var ScreenHeight: Int = 0

    var viewWidth: Int = 0
    var viewHeight: Int = 0

    var aspect_rate: Double = 0.0 // 최대 ZoomOut rate

    var zoom_rate: Double = 0.0
    var zoom_rate_offset: Double = 0.0 // 확대/축소 시 기준 zoom rate

    var StartPos_x: Int = 0
    var StartPos_y: Int = 0
    var StartPos_x_offset: Int = 0
    var StartPos_y_offset: Int = 0

    var Origin_StartPos_x: Int = 0
    var Origin_StartPos_y: Int = 0

    var strTouchStatus: String = "None"
    var roiType: String = "default"

    var nTouchDownPosX: Float = 0f
    var nTouchDownPosY: Float = 0f
    var nTouchUpPosX: Float = 0f
    var nTouchUpPosY: Float = 0f


    @JvmField
    var m_RoiObjects: ArrayList<CDrawObj>

    var m_isCapture: Boolean = false // 마우스 누른 상태에서 이동 여부

    var m_roiviewflag: Boolean = true // roi를 dispaly 여부

    //그리기 객체 사용
    @JvmField
    var m_drawing: Boolean = false // roi을 그리는 중인지
    var m_drawstart: Boolean = false
    var m_DnPoint: Point = Point(-1, -1) // 객체의 rect 시작점
    var m_ptOld: Point = Point(-1, -1) // freehand 같은 경우를 표현하기 위해서 필요하다.
    var m_bIsMouseDown: Boolean = false
    var m_Select: Int = -1 // 선택된 트래커 핸들..
    var mouse_down_pos_x: Int = -1
    var mouse_down_pos_y: Int = -1

    //현재 작업중인 그리기 객체 타입
    var currentSelectedIndex: Int = -1 // 선택된 m_RoiObjects 인덱스
    @JvmField
    var m_RoiCurObject: CDrawObj? = null // 선택된 roi
    var m_CurType: String = "default" //현재 설정된 roi 타입(커서) 설정
    var m_isselected: Boolean = false // roi 선택 여부

    // 커서 종류
    var m_objSelect: Int = -1 // 마우스 모양


    var paint: Paint = Paint()

    private val scaleGestureDetector: ScaleGestureDetector
    private val matrix: Matrix
    var ratateAngle: Int = 0
        private set

    private var scaleFactor = 1.0f
    private val translateX = 0f // X축 이동
    private val translateY = 0f // Y축 이동

    private val focusX = 0f // 확대 중심 X좌표
    private val focusY = 0f // 확대 중심 Y좌표
    private val offsetX = 0f
    private val offsetY = 0f

    private val random: Random // 랜덤 생성기

    // 공간 개수
    var roomNum: Int = 0

    private var RoiSelectedListener: OnRoiSelectedListener? = null
    private var RoiCreateListener: OnRoiCreateListener? = null
    private var RoiChangedListener: OnRoiChangedListener? = null

    val selectedObjectPosition: Pair<Int, Int>?
        get() {
            if ((currentSelectedIndex != -1) && (currentSelectedIndex < m_RoiObjects.size)) {
                val x: Int
                val y: Int
                if (m_RoiCurObject!!.roi_type == "roi_polygon") {
                    val mbr =
                        m_RoiObjects[currentSelectedIndex].GetMBRCenter()
                    x =
                        (mbr.x * m_RoiObjects[currentSelectedIndex].m_zoom + StartPos_x - 57).toInt()
                    y =
                        (mbr.y * m_RoiObjects[currentSelectedIndex].m_zoom + StartPos_y - 15).toInt()
                } else if (m_RoiCurObject!!.roi_type == "roi_line") {
                    x =
                        (m_RoiObjects[currentSelectedIndex].m_MBR.left * m_RoiObjects[currentSelectedIndex].m_zoom + StartPos_x).toInt()
                    y =
                        (m_RoiObjects[currentSelectedIndex].m_MBR.top * m_RoiObjects[currentSelectedIndex].m_zoom + StartPos_y + 100).toInt()
                } else {
                    x =
                        (((m_RoiObjects[currentSelectedIndex].m_MBR.left + m_RoiObjects[currentSelectedIndex].m_MBR.right) / 2)
                                * m_RoiObjects[currentSelectedIndex].m_zoom + StartPos_x - 55).toInt()
                    y =
                        (m_RoiObjects[currentSelectedIndex].m_MBR.top * m_RoiObjects[currentSelectedIndex].m_zoom + StartPos_y + 120).toInt()
                }
                return Pair(x, y)
            }
            return null // 선택된 객체가 없으면 null 반환
        }

    fun clearSelection() {
        currentSelectedIndex = -1
        // 선택 안된 것을 activity에 전달
        RoiSelectedListener!!.onRoiSelected(currentSelectedIndex)
        invalidate() // 화면을 다시 그리도록 요청
    }

    fun CountRoomNum(): Int {
        var count = 0

        for (i in m_RoiObjects.indices) {
            if (m_RoiObjects[i].roi_type == "roi_polygon") count++
        }
        return count
    }

    init {
        paint.color = Color.BLUE
        paint.style = Paint.Style.FILL

        m_RoiObjects = ArrayList()

        // 랜덤 생성기 초기화
        random = Random()

        // Matrix 초기화
        matrix = Matrix()

        // ScaleGestureDetector 초기화
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())

        // 선택 객체 초기화
        currentSelectedIndex = -1
        m_RoiCurObject = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        //String strMode = "맵 탐색";
        //String strMenu = "Zoom";
        //Log.d(TAG,"mouse GetPointCount: " + event.getPointerCount());

        if (event.pointerCount > 1) {
            scaleGestureDetector.onTouchEvent(event)
        } else {
            zoom_rate_offset = zoom_rate
            StartPos_x_offset = StartPos_x
            StartPos_y_offset = StartPos_y

            if (event.action == MotionEvent.ACTION_DOWN) {
                MouseDown(event.x, event.y)
            }
            if (event.action == MotionEvent.ACTION_UP) {
                MouseUp(event.x, event.y)
            }
            if (event.action == MotionEvent.ACTION_MOVE) {
                MouseMove(event.x, event.y)
            }
        }

        return true
    }

    private inner class ScaleListener : SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // 스케일 값 가져오기
            scaleFactor *= detector.scaleFactor

            zoom_rate = zoom_rate_offset * scaleFactor

            //scaleFactor = Math.max((float)aspect_rate, Math.min(scaleFactor, 5.0f));

            // 꽉찬 전체 이미지의 aspect_rate가 1.0보다 큰 경우와, 1.0보다 작은 경우를 고려한다.
            //scaleFactor = Math.max((float)aspect_rate, Math.min(scaleFactor, Math.max((float)(aspect_rate*5.0f), 5.0f)));

            // 최소 확대비율은 꽉찬 이미지의 1/2배까지만 축소
            // 최대 확대비율은 꽉찬 이미지(확대 배율 1.0이상)의 5.0배와 원본 이미지(확대 배율이 1.0미만)의 5.0배 중에 큰 값들 사용한다.
            //scaleFactor = Math.max((float)(aspect_rate/2.0f), Math.min(scaleFactor, Math.max((float)(aspect_rate*5.0f), 5.0f)));

            //Log.d(TAG,"zoom_rate: "+ zoom_rate);
            //Log.d(TAG,"zoom_rate_offset: "+ zoom_rate_offset);
            //Log.d(TAG, "scaleFactor: " + scaleFactor);
            val values = FloatArray(9)
            matrix.getValues(values)

            //Log.d(TAG, "values[Matrix.MTRANS_X] : " + values[Matrix.MTRANS_X]);
            //Log.d(TAG, "values[Matrix.MTRANS_Y] : " + values[Matrix.MTRANS_Y]);
            //matrix.reset(); // matrix의 값을 초기화 한다.

            // 현재 Matrix 값에 스케일 적용
            matrix.setScale(zoom_rate.toFloat(), zoom_rate.toFloat())

            val focus_x = detector.focusX
            val focus_y = detector.focusY

            // 포커싱 된 부분을 이미지의 위치를 확인하여 해당 포커싱된 부분이 이미지의 가운데로 오게 한다.

            // 이동 값을 적용 (중앙 고정)
            //translateX = (getWidth() - bitmap.getWidth() * scaleFactor) / 2;
            //translateY = (getHeight() - bitmap.getHeight() * scaleFactor) / 2;
            //if(bitmap != null)
            run {
                val CenterToImgX =
                    ((width / 2 - StartPos_x_offset) / zoom_rate_offset).toInt() // 선터 좌료를 원본 이미지 좌표로 변환
                val ImgStartPosX =
                    ((width / 2) - CenterToImgX * zoom_rate).toFloat() // 원본이미지의 좌표를 변경된 이미지의 줌심좌표로 수정
                val translateX =
                    ((width / 2) - (((width / 2 - StartPos_x_offset) / zoom_rate_offset).toInt()) * zoom_rate).toFloat()
                val translateY =
                    ((height / 2) - (((height / 2 - StartPos_y_offset) / zoom_rate_offset).toInt()) * zoom_rate).toFloat()


                //translateX = (float)(StartPos_x_offset + (getWidth()/2.0)*(1.0f - scaleFactor)*zoom_rate_offset );
                //translateX = (float)(StartPos_y_offset + (getHeight()/2.0)*(1.0f - scaleFactor)*zoom_rate_offset );
                //translateY = (float)(StartPos_y_offset + ((getHeight()/2.0)*zoom_rate_offset)*(1.0f - scaleFactor) );
                //Log.d(TAG, "translateX : " + translateX);
                //Log.d(TAG, "translateY : " + translateY);
                matrix.postTranslate(translateX, translateY)
            }

            // 화면 다시 그리기
            invalidate()
            return true
        }
    }

    fun reDraw() {
        this.invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        //Log.d(TAG, "OnDraw(...)");
        val values = FloatArray(9)
        matrix.getValues(values)
        // 변환된 이미지의 시작 좌표
        //StartPos_x = (int) (values[Matrix.MTRANS_X]);
        //StartPos_y = (int) (values[Matrix.MTRANS_Y]);
        Log.d(TAG, "StartPos_x : $StartPos_x")
        Log.d(TAG, "StartPos_y : $StartPos_y")

        //Log.d(TAG, "values[Matrix.MTRANS_X] : "+values[Matrix.MTRANS_X]);
        //Log.d(TAG, "values[Matrix.MTRANS_Y] : "+values[Matrix.MTRANS_Y]);

        // 이미지 비율을 유지하면서 화면에 꽉차게 그려준다.
        if (bitmapRotate != null) {
            // View 크기 가져오기

            val viewWidth = width
            val viewHeight = height

            // 원본 비트맵의 특정 영역
            val srcRect = Rect(0, 0, bitmapRotate!!.width, bitmap!!.height)

            // View 전체에 이미지를 확대해서 표시
            val destRect = Rect(
                StartPos_x,
                StartPos_y,
                (StartPos_x + bitmapRotate!!.width * zoom_rate).toInt(),
                (StartPos_y + bitmap!!.height * zoom_rate).toInt()
            )


            // 캔버스에 그리기
            canvas.drawBitmap(bitmapRotate!!, srcRect, destRect, null)
        } else if (bitmap != null) {
            // Matrix 변환을 사용하여 Bitmap 그리기
            //canvas.drawBitmap(bitmap, matrix, null);


            // View 크기 가져오기


            val viewWidth = width
            val viewHeight = height

            // 원본 비트맵의 특정 영역
            val srcRect = Rect(0, 0, bitmap!!.width, bitmap!!.height)

            // View 전체에 이미지를 확대해서 표시
            val destRect = Rect(
                StartPos_x,
                StartPos_y,
                (StartPos_x + bitmap!!.width * zoom_rate).toInt(),
                (StartPos_y + bitmap!!.height * zoom_rate).toInt()
            )


            // 캔버스에 그리기
            canvas.drawBitmap(bitmap!!, srcRect, destRect, null)
        }

        val iconDrawable = resources.getDrawable(R.drawable.benjamin_direction, null)
        val rotateDrawable = resources.getDrawable(R.drawable.ic_rotate, null)

        // 시작위치 만큼 + 해줘야 일치한다.
        val pt = Point(StartPos_x, StartPos_y)
        var x: Int
        var y: Int


        //Log.d(TAG, "m_RoiObjects.size() : " + (int)(m_RoiObjects.size()));
        var i = 0
        while (i < m_RoiObjects.size) {
            if (strMenu == "핀 회전") {
                if (m_RoiObjects[i].roi_type == "roi_polygon") {
                    // 선택된 것만 회전한다.
                    if (i == currentSelectedIndex) {
                        m_RoiObjects[i].iconDrawable = iconDrawable
                        m_RoiObjects[i].rotateDrawable = rotateDrawable
                    } else {
                        m_RoiObjects[i].iconDrawable = iconDrawable
                        m_RoiObjects[i].rotateDrawable = null
                    }
                }
            } else {
                m_RoiObjects[i].iconDrawable = null
            }


            m_RoiObjects[i].SetZoom(zoom_rate)
            if (strMenu == "수정") {
                if (m_CurType == m_RoiObjects[i].roi_type && i == currentSelectedIndex) {
                    if (bitmapRotate != null) {
                        m_RoiObjects[i].Draw(canvas, pt, bitmapRotate, true, true)
                    } else if (bitmap != null) {
                        m_RoiObjects[i].Draw(canvas, pt, bitmap, true, true)
                    }
                } else {
                    if (bitmapRotate != null) {
                        m_RoiObjects[i].Draw(canvas, pt, bitmapRotate, false, false)
                    } else if (bitmap != null) {
                        m_RoiObjects[i].Draw(canvas, pt, bitmap, false, false)
                    }
                }
            } else {
                if (i == currentSelectedIndex) {
                    if (bitmapRotate != null) {
                        m_RoiObjects[i].Draw(canvas, pt, bitmapRotate, true, false)
                    } else if (bitmap != null) {
                        m_RoiObjects[i].Draw(canvas, pt, bitmap, true, false)
                    }
                } else {
                    if (bitmapRotate != null) {
                        m_RoiObjects[i].Draw(canvas, pt, bitmapRotate, false, false)
                    } else if (bitmap != null) {
                        m_RoiObjects[i].Draw(canvas, pt, bitmap, false, false)
                    }
                }
            }
            //m_RoiObjects.get(i).DrawLabel(canvas);
            // 241222 jihyeon 핀 회전일 때 아이콘 변경
            if (strMenu == "핀 회전") {
                //RotatePinIcon();
            } else if (m_RoiObjects[i].roi_type == "roi_polygon") {
                val mbr = m_RoiObjects[i].GetMBRCenter()
                x = (mbr.x * m_RoiObjects[i].m_zoom + pt.x).toInt()
                y = (mbr.y * m_RoiObjects[i].m_zoom + pt.y).toInt()
                // 배경으로 VectorDrawable 그리기
                val drawable_ic = resources.getDrawable(R.drawable.ic_location, null)
                drawable_ic.setBounds(x - 30, y - 80, x + 30, y + 10) //(60, 90)

                //Log.d(TAG,"PIN X: " + mbr.x + " " + mbr.y);
                val drawable = resources.getDrawable(R.drawable.pin_name, null)
                drawable.setBounds(x - 65, y - 146, x + 64, y - 80) //(129, 66)
                drawable.draw(canvas)


                // 텍스트 추가
                val paint = Paint()
                paint.color = Color.WHITE
                paint.textSize = 36f
                paint.textAlign = Paint.Align.CENTER

                // 선택할 경우 하얀 화면에 검은색 굴자, 선택이 안될 경우 검은색 화면에 하얀색 글자
                if (i == currentSelectedIndex) {
                    drawable.setColorFilter(Color.parseColor("#FFFFFF"), PorterDuff.Mode.SRC_IN)
                    paint.color = Color.BLACK
                } else {
                    // default black color
                    paint.color = Color.WHITE
                }

                drawable_ic.draw(canvas)
                drawable.draw(canvas)
                canvas.drawText(m_RoiObjects[i].m_label, x.toFloat(), (y - 100).toFloat(), paint)
            }
            i++
        }

        if (m_RoiCurObject != null) {
            val oldc = m_RoiCurObject!!.GetLineColor()
            m_RoiCurObject!!.SetLineColor(Color.rgb(253, 60, 60))
            if (m_RoiCurObject!!.roi_type == "roi_rect") {
                m_RoiCurObject!!.SetFillColor(Color.argb(178, 255, 70, 80)) //투명도 30
                //m_RoiCurObject.SetLineColor(Color.rgb(255,70,80));
            }
            //else if(m_RoiCurObject.roi_type.equals("roi_polygon")){
            //m_RoiCurObject.SetFillColor(getRandomColorArgb(50));
            //}
            m_RoiCurObject!!.SetZoom(zoom_rate)

            //m_RoiCurObject.Draw(canvas, zoom_rate);
            if (strMenu == "수정") {
                if (m_CurType == m_RoiCurObject!!.roi_type) {
                    if (bitmapRotate != null) {
                        m_RoiCurObject!!.Draw(canvas, pt, bitmapRotate, true, true)
                    }
                    if (bitmap != null) {
                        m_RoiCurObject!!.Draw(canvas, pt, bitmap, true, true)
                    }
                } else {
                    if (bitmapRotate != null) {
                        m_RoiCurObject!!.Draw(canvas, pt, bitmapRotate, false, false)
                    } else if (bitmap != null) {
                        m_RoiCurObject!!.Draw(canvas, pt, bitmap, false, false)
                    }
                }
            } else {
                m_RoiCurObject!!.Draw(canvas, pt, bitmap, false, false)
            }
            if (strMenu == "삭제") {
                // 삭제 메뉴일 때 토글바 처리
                val position =
                    selectedObjectPosition
                if (position != null) {
                    // Context를 MapEditorActivity로 캐스팅
                    if (context is MapEditorActivity) {
                        val activity = context as MapEditorActivity
                        activity.showDeleteToggleBar(position) // Activity의 메서드 호출
                    }
                }
            }
            //m_RoiCurObject.SetLineColor(oldc);
        }
    }

    fun setBitmap(map: Bitmap?) {
        //Log.d(TAG, "setBitmap(...)");
        this.bitmap = map


        //Log.d(TAG, "ScreenWidth : " + ScreenWidth);
        //Log.d(TAG, "ScreenHeight : " + ScreenHeight);
        if ((ScreenWidth < 1) || (ScreenHeight < 1)) return

        val viewWidth = this.width
        val viewHeight = this.height


        //Log.d(TAG, "viewWidth : " + viewWidth);
        //Log.d(TAG, "viewHeight : " + viewHeight);
        val imageW = bitmap!!.width
        val imageH = bitmap!!.height

        //Log.d(TAG, "imageW : " + imageW);
        //Log.d(TAG, "imageH : " + imageH);

        // 가로세로 비율을 구한다.
        val hRatio = (viewWidth).toDouble() / imageW
        val vRatio = (viewHeight).toDouble() / imageH

        //Log.d(TAG, "hRatio : " + hRatio);
        //Log.d(TAG, "vRatio : " + vRatio);
        aspect_rate = min(hRatio, vRatio)

        // 확대 비율
        zoom_rate = aspect_rate

        //Log.d(TAG, "zoom_rate : " + zoom_rate);
        zoom_rate_offset = zoom_rate

        // 그려지는 이미지의 중심 좌표
        val draw_image_center_x = (imageW / 2).toDouble()
        val draw_image_center_y = (imageH / 2).toDouble()

        // 이미지를 그릴 위치를 구한다.
        //StartPos_x = (int) ((viewWidth - imageW * zoom_rate) / 2);
        StartPos_x = ((width - bitmap!!.width * zoom_rate) / 2).toInt()
        //StartPos_y = (int) ((viewHeight - imageH * zoom_rate) / 2);
        StartPos_y = ((height - bitmap!!.height * zoom_rate) / 2).toInt()

        //Log.d(TAG, "StartPos_x : "+StartPos_x);
        //Log.d(TAG, "StartPos_y : "+StartPos_y);
        StartPos_x_offset = StartPos_x
        StartPos_y_offset = StartPos_y

        matrix.setScale(zoom_rate.toFloat(), zoom_rate.toFloat())

        // 이동 값을 적용 (중앙 고정)
        val translateX = ((width - bitmap!!.width * zoom_rate) / 2).toFloat()
        val translateY = ((height - bitmap!!.height * zoom_rate) / 2).toFloat()

        //Log.d(TAG, "translateX : " + translateX );
        //Log.d(TAG, "translateY : " + translateY );
        matrix.postTranslate(translateX, translateY)


        invalidate() // 화면을 다시 그리도록 요청
    }

    fun GetBitmapWidth(): Int {
        if (bitmap == null) {
            return 0
        }
        return bitmap!!.width
    }

    fun GetBitmapHeight(): Int {
        if (bitmap == null) {
            return 0
        }
        return bitmap!!.height
    }

    fun SetMode(str: String) {
        //Log.d(TAG, "SetMode("+str+")");
        CObject_CurRoiCancelFunc()
        m_drawstart = false
        this.strMode = str
        // 241222 jihyeon 이전 모드가 핀 회전 모드였으면, 초기화
        if (strMenu == "핀 회전") {
            for (roiObject in m_RoiObjects) {
                roiObject.clearIconDrawable()
                roiObject.clearRotateDrawable()
            }
        }
        strMenu = "default"
        if (strMode == "맵 탐색") {
            this.m_CurType = "default"
        } else if (strMode == "공간 생성") {
            this.m_CurType = "roi_polygon"
        } else if (strMode == "가상벽") {
            this.m_CurType = "roi_line"
        } else if (strMode == "금지공간") {
            this.m_CurType = "roi_rect"
        }
        invalidate()
    }

    fun GetMode(): String {
        return this.strMode
    }

    fun SetMenu(str: String) {
        //Toast.makeText(getContext().getApplicationContext(), "SetMenu("+str+")", Toast.LENGTH_SHORT).show();
        // 241222 jihyeon 이전 모드가 핀 회전 모드였으면, 초기화
        if (str != "핀 회전" && strMenu == "핀 회전") {
            for (roiObject in m_RoiObjects) {
                roiObject.clearIconDrawable()
            }
        }
        if (str == "추가") {
            SetMode(strMode) // m_CurType를 재 설정해준다.
            m_drawstart = true
        } else if (str == "수정") {
            CObject_CurRoiCancelFunc()
            m_drawstart = false
        } else if (str == "선택") {
            if (strMenu == "추가") {
                roi_AddObject()
            } else {
                CObject_CurRoiCancelFunc()
            }
            m_drawstart = false
        } else if (str == "삭제") {
        } else if (str == "핀 회전") {
            // RotatePinIcon();
            // TODO 기능 구현 필요
        }

        strMenu = str
        roi_CreateObject() // 새로운 roi를 만든다.

        invalidate()
    }

    // 241222 jihyeon 핀 회전 모드 아이콘
    private fun RotatePinIcon() {
        val iconDrawable = resources.getDrawable(R.drawable.benjamin_direction, null)
        val rotateDrawable = resources.getDrawable(R.drawable.ic_rotate, null)
        // 모든 ROI 객체의 핀을 새로운 Drawable로 설정
        for (roiObject in m_RoiObjects) {
            if (roiObject.roi_type == "roi_polygon") {
                roiObject.iconDrawable = iconDrawable
                roiObject.rotateDrawable = rotateDrawable
            }
        }

        // 화면 갱신
        invalidate()
    }

    private fun RotatePinIcon(nIndex: Int) {
        if (nIndex > (m_RoiObjects.size - 1)) return
        val roiObject = m_RoiObjects[nIndex]

        val iconDrawable = resources.getDrawable(R.drawable.benjamin_direction, null)
        val rotateDrawable = resources.getDrawable(R.drawable.ic_rotate, null)
        if (roiObject.roi_type == "roi_polygon") {
            roiObject.iconDrawable = iconDrawable
            roiObject.rotateDrawable = rotateDrawable
        }
    }

    fun GetMenu(): String {
        return this.strMenu
    }

    fun SetRoiType(str: String) {
        this.roiType = str
    }

    fun GetRoiType(): String {
        return this.roiType
    }

    fun SetCurType(str: String) {
        this.m_CurType = str
    }

    fun GetCurType(): String {
        return this.m_CurType
    }

    fun ZoomIn() {
        zoom_rate += 0.2
        // 최대 5배 확대만 가능하게
        if (zoom_rate > (aspect_rate * 5.0)) zoom_rate = aspect_rate * 5.0

        invalidate() // 화면을 다시 그리도록 요청
    }

    fun ZoomOut() {
        zoom_rate -= 0.2
        // 화면에 꽉차게
        if (aspect_rate > zoom_rate) zoom_rate = aspect_rate

        invalidate() // 화면을 다시 그리도록 요청
    }

    fun MouseDown(x: Float, y: Float) {
        Log.d(TAG, "mouseDown($x,$y)")

        nTouchDownPosX = (x * zoom_rate).toInt().toFloat()
        nTouchUpPosX = nTouchDownPosX
        nTouchDownPosY = (y * zoom_rate).toInt().toFloat()
        nTouchUpPosY = nTouchDownPosY

        //241218 성웅 strMode 맵탐색에서 strMenu 이동으로 변경
        // TODO 추후 정리 필요
        if (strMenu == "이동") {
            //if (strMenu.equals("이동") || strMenu.equals("핀 회전")) {
            m_DnPoint.x = x.toInt()
            m_DnPoint.y = y.toInt()

            m_ptOld.x = x.toInt()
            m_ptOld.y = y.toInt()
        } else {
            // 정수로 변환
            val pt_x = (x).toInt()
            //int pt_x = (int) ((x-StartPos_x)/zoom_rate);
            val pt_y = (y).toInt()

            //int pt_y = (int) ((y-StartPos_y)/zoom_rate);
            //pt_y = img_map.height - parseInt(pt_y);
            val point = Point(pt_x, pt_y)

            Log.d(TAG, "----------------------------------")
            Log.d(TAG, "m_isCapture : $m_isCapture")
            Log.d(TAG, "m_drawing : $m_drawing")
            Log.d(TAG, "m_roiviewflag : $m_roiviewflag")
            Log.d(TAG, "m_CurType : $m_CurType")
            Log.d(TAG, "m_objSelect : $m_objSelect")
            Log.d(TAG, "m_RoiCurIndex : " + currentSelectedIndex)

            // 그리기 상태
            if (m_drawstart == true) {
                if (m_drawing == false) {
                    //console.log(point);
                    if (!CObject_CreateObject(m_CurType, point)) {
                        m_drawstart = false
                        return
                    }

                    //console.log(point);
                    m_DnPoint.x = point.x
                    m_DnPoint.y = point.y

                    m_ptOld.x = point.x
                    m_ptOld.y = point.y

                    m_Select = -1

                    m_drawing = true
                    m_isselected = false

                    m_isCapture = true
                } else {
                    if (m_CurType == "roi_polygon") {
                        //CPen pen,*pOldPen;
                        //pen.CreatePen(PS_DOT, 1, m_LineColor);		// 도트
                        //pOldPen = (CPen *)dc.SelectObject(&pen);
                        //dc.MoveTo((int)((float)m_ptOld.x*m_zoom), (int)((float)m_ptOld.y*m_zoom));
                        //dc.LineTo((int)((float)point.x*m_zoom), (int)((float)point.y*m_zoom));
                        //dc.SelectObject(pOldPen);		pen.DeleteObject();


                        //Log.d(TAG, "StartPos : ("+StartPos_x+","+StartPos_y+")");
                        //Log.d(TAG, "zoom_rate : "+zoom_rate);
//                        Point pt = new Point((int) ((point.x - StartPos_x) / zoom_rate), (int) ((point.y - StartPos_y) / zoom_rate));
//                        m_RoiCurObject.AddPoint(pt);
//
//                        m_ptOld = point;


                        val pt = Point(
                            ((point.x - StartPos_x) / zoom_rate).toInt(),
                            ((point.y - StartPos_y) / zoom_rate).toInt()
                        )
                        m_RoiCurObject!!.AddPoint(pt)
                        m_ptOld = point
                    }
                    m_DnPoint.x = point.x
                    m_DnPoint.y = point.y


                    if (m_RoiCurObject != null) {
                        // 해당 rio에 대해서 Tracker pointer를 생성해준다.
                        m_RoiCurObject!!.MakeTracker(StartPos_x, StartPos_y)

                        // 현재 그려지는 객체의 위치가 변경되었다는 것을 통보해준다.
                        RoiCreateListener!!.onRoiCreate()
                    }
                    invalidate() // 화면을 다시 그리도록 요청
                }
            } else if (m_drawing == false) {
                val pt = Point(
                    ((point.x - StartPos_x) / zoom_rate).toInt(),
                    ((point.y - StartPos_y) / zoom_rate).toInt()
                )
                m_objSelect = CObject_FindObject(pt, true)

                Log.d(TAG, "m_objSelect : $m_objSelect")

                SetCursorType()

                m_DnPoint.x = point.x
                m_DnPoint.y = point.y

                // 현재 좌표가 ROI 객체 내부이면 이동시작
                if ((m_objSelect != -1) && (m_roiviewflag == true)) {
                    m_isselected = true


                    // CObject_Draw();
                    m_isCapture = true
                } else  // ROI 외부이면 DrawTracker 없앰.
                {
                    m_isselected = false
                    m_Select = -1
                    currentSelectedIndex = -1

                    m_isCapture = false
                    m_drawing = false
                    m_drawstart = m_drawing

                    //map_image_draw();
                    //CObject_Draw();
                }
                if (this.currentSelectedIndex > -1) {
                    // 해당 rio에 대해서 Tracker pointer를 생성해준다.
                    m_RoiObjects[currentSelectedIndex].MakeTracker(StartPos_x, StartPos_y)
                }
                RoiSelectedListener!!.onRoiSelected(currentSelectedIndex)

                invalidate() // 화면을 다시 그리도록 요청
            }
        }
        m_bIsMouseDown = true
    }

    fun MouseMove(x: Float, y: Float) {
        //Log.d(TAG, "MouseMove( "+x+","+y+" )");

        /*
        if (strMenu.equals("이동"))
        //if(strMode.equals("맵 탐색") )
        {
            //Log.d(TAG, "m_DnPoint( "+m_DnPoint.x+","+m_DnPoint.y+")");
            int dx = (int) ((x - m_DnPoint.x));
            int dy = (int) ((y - m_DnPoint.y));


            boolean bIsRedraw = false;
            if (dx != 0) {
                //Log.d(TAG, "StartPos_x : "+StartPos_x);
                StartPos_x += dx;
                //Log.d(TAG, "StartPos_x : "+StartPos_x);
                m_DnPoint.x = (int) x;

                bIsRedraw = true;
            }
            if (dy != 0) {
                //Log.d(TAG, "StartPos_y : "+StartPos_y);
                StartPos_y += dy;
                //Log.d(TAG, "StartPos_y : "+StartPos_y);
                m_DnPoint.y = (int) y;

                bIsRedraw = true;
            }

            if (bIsRedraw == true) {
                //Log.d(TAG,"check mouse move: ");

                matrix.postTranslate((float) dx, (float) dy);
                //Log.d(TAG,"check mouse move: ");

                if(this.m_RoiCurIndex > -1)
                {
                    // 해당 rio에 대해서 Tracker pointer를 생성해준다.
                    m_RoiObjects.get(m_RoiCurIndex).MakeTracker(StartPos_x, StartPos_y);
                    // 변경된 것을 Activity에 전달해준다.
                    RoiChangedListener.onRoiChanged(m_RoiCurIndex);
                }

                invalidate(); // 화면을 다시 그리도록 요청
            }

        }
        */

        if (strMenu == "핀 회전") {
            if (currentSelectedIndex != -1) {
                val iconCenterX =
                    ((m_RoiObjects[currentSelectedIndex].m_MBR_center.x * zoom_rate + StartPos_x)).toFloat() // +30 - 30 상쇄됨
                val iconCenterY =
                    ((m_RoiObjects[currentSelectedIndex].m_MBR_center.y * zoom_rate + StartPos_y)).toFloat() // +40 - 40 상쇄됨

                val deltaAngle = calculateAngle(iconCenterX, iconCenterY, x, y)

                //            if(deltaAngle > 360)
                //                deltaAngle -= 360;
                //            else if(deltaAngle < -360)
                //                deltaAngle += 360;

                //Log.d(TAG,"Delta Angle:" +  Math.toDegrees(deltaAngle));
                m_RoiObjects[currentSelectedIndex].setAngle(deltaAngle)


                // 화면을 갱신해준다.
                invalidate()
            }
        } else {
            // 정수로 변환
            val pt_x = (x).toInt()
            //int pt_x = (int) ((x-StartPos_x)/zoom_rate);
            val pt_y = (y).toInt()

            //int pt_y = (int) ((y-StartPos_y)/zoom_rate);
            //pt_y = img_map.height - parseInt(pt_y);
            val point = Point(pt_x, pt_y)

            //Log.d(TAG, "----------------------------------");
            //Log.d(TAG, "m_isCapture : " + m_isCapture);
            //Log.d(TAG, "m_drawing : " + m_drawing);
            //Log.d(TAG, "m_roiviewflag : " + m_roiviewflag);
            //Log.d(TAG, "m_CurType : " + m_CurType);
            //Log.d(TAG, "m_objSelect : " + m_objSelect);
            //Log.d(TAG, "m_RoiCurIndex : " + m_RoiCurIndex);
            if (m_isCapture == true)  // 마우스 누른 상태에서 이동중이면
            {
                if (m_drawing == true) {
                    if (m_CurType == "roi_line" || m_CurType == "roi_rect") {
                        CObject_MoveToRect(m_DnPoint, point)

                        if (m_RoiCurObject != null) {
                            // 해당 rio에 대해서 Tracker pointer를 생성해준다.
                            m_RoiCurObject!!.MakeTracker(StartPos_x, StartPos_y)
                            // 현재 그리고 있는 것이 변경되었다고 알려준다.
                            RoiCreateListener!!.onRoiCreate()
                        }
                    }
                } else {
                    if (m_objSelect == 0) {
                        // 여기에서 선택된 객체의 위치 이동을 구현해야 한다.
                        // 20241212 jihyeon 좌표 이동 시 반대 방향으로 이동하는 경우가 있어 CObject_MoveToRect를 CObject_MoveTo로 바꾸어 실행
                        //Log.d(TAG, "m_objSelect == 0")

                        CObject_MoveTo(m_DnPoint, point)
                    } else if (currentSelectedIndex > -1) {
                        Log.d(TAG, "m_RoiCurIndex :  " + currentSelectedIndex)
                        Log.d(
                            TAG,
                            "m_RoiObjects.get(m_RoiCurIndex).roi_type : " + m_RoiObjects[currentSelectedIndex].roi_type
                        )
                        if (m_RoiObjects[currentSelectedIndex].roi_type != "roi_point") {
                            // 선택된 객체의 모양을 변경한다.
                            //  Log.d(TAG, "else if Point : " + point.x + ", " + point.y);
                            // Log.d(TAG, "DPoint: " + m_DnPoint.x + ", " + m_DnPoint.y);
                            if (strMenu == "수정") {
                                CObject_MovePointTo(point, m_DnPoint, m_objSelect)
                            } else {
                                CObject_MoveHandleTo(point, m_DnPoint, m_objSelect)
                            }
                        } else if (m_RoiObjects[currentSelectedIndex].roi_type != "roi_polygon") {
                            CObject_MoveToRect(m_DnPoint, point)

                            // 해당 rio에 대해서 Tracker pointer를 생성해준다.
                            m_RoiObjects[currentSelectedIndex].MakeTracker(StartPos_x, StartPos_y)
                            // 변경된 것을 Activity에 전달해준다.
                            RoiChangedListener!!.onRoiChanged(currentSelectedIndex)

                            invalidate() // 화면을 다시 그리도록 요청
                        }
                    }
                    m_DnPoint.x = point.x
                    m_DnPoint.y = point.y
                }
            } else {
                if ((m_drawing == false) && m_roiviewflag)  // 마우스 이동시, 객체 찾아서 커서의 모양을 바꾼다.
                {
                    //m_objSelect = CObject_FindObject(point, false);

                    //SetCursorType();

                    ////if(m_objSelect != -1)
                    ////{
                    ////    SetCursorType();
                    ////    //CObject_Draw();   // 선택된 경우 그려주는 색상변경
                    ////}
                    //Log.d(TAG, "m_DnPoint( "+m_DnPoint.x+","+m_DnPoint.y+")");

                    val dx = ((x - m_DnPoint.x)).toInt()
                    val dy = ((y - m_DnPoint.y)).toInt()


                    var bIsRedraw = false

                    // 선택된 것이 없는 경우에 한하여 이동한다.
                    if (currentSelectedIndex < 0) {
                        if (dx != 0) {
                            //Log.d(TAG, "StartPos_x : "+StartPos_x);
                            StartPos_x += dx
                            //Log.d(TAG, "StartPos_x : "+StartPos_x);
                            m_DnPoint.x = x.toInt()

                            bIsRedraw = true
                        }
                        if (dy != 0) {
                            //Log.d(TAG, "StartPos_y : "+StartPos_y);
                            StartPos_y += dy
                            //Log.d(TAG, "StartPos_y : "+StartPos_y);
                            m_DnPoint.y = y.toInt()

                            bIsRedraw = true
                        }
                    }

                    if (bIsRedraw == true) {
                        //Log.d(TAG,"check mouse move: ");

                        matrix.postTranslate(dx.toFloat(), dy.toFloat())

                        //Log.d(TAG,"check mouse move: ");
                        if (this.currentSelectedIndex > -1) {
                            // 해당 rio에 대해서 Tracker pointer를 생성해준다.
                            m_RoiObjects[currentSelectedIndex].MakeTracker(StartPos_x, StartPos_y)
                            // 변경된 것을 Activity에 전달해준다.
                            RoiChangedListener!!.onRoiChanged(currentSelectedIndex)
                        }

                        invalidate() // 화면을 다시 그리도록 요청
                    }
                } else if (m_drawing == true) {
                    // 그리기 모드
                    //if(m_CurType == 'roi_multiline') // 이동 괴적이 시작과 끝만 있는 것이 아닌 경우
                    //{
                    //    CObject_MoveTo(m_DnPoint, point);
                    //    m_DnPoint.x = point.x;
                    //    m_DnPoint.y = point.y;
                    //}
                    if (m_CurType == "roi_polygon") {
                        CObject_MoveTo(m_DnPoint, point)
                        //CObject_MoveToRect(m_DnPoint, point);
                        m_DnPoint = point
                        if (currentSelectedIndex > -1) {
                            // 해당 rio에 대해서 Tracker pointer를 생성해준다.
                            m_RoiObjects[currentSelectedIndex].MakeTracker(StartPos_x, StartPos_y)
                            // 변경된 것을 Activity에 전달해준다.
                            RoiChangedListener!!.onRoiChanged(currentSelectedIndex)
                        }
                        if (m_RoiCurObject != null) {
                            // 해당 rio에 대해서 Tracker pointer를 생성해준다.
                            m_RoiCurObject!!.MakeTracker(StartPos_x, StartPos_y)
                            // 변경된 것을 Activity에 전달해준다.
                            RoiChangedListener!!.onRoiChanged(currentSelectedIndex)
                        }

                        invalidate() // 화면을 다시 그리도록 요청
                    }
                }
            }
        }
    }

    fun MouseUp(x: Float, y: Float) {
        Log.d(TAG, "MouseUp( $x,$y )")

        nTouchUpPosX = (x * zoom_rate).toInt().toFloat()
        nTouchUpPosY = (y * zoom_rate).toInt().toFloat()
        if (strMode == "맵 탐색" && strMenu == "이동") {
            val dx = nTouchUpPosX - nTouchDownPosX
            val dy = nTouchUpPosY - nTouchDownPosY

            //Log.d(TAG, "Move("+dx+","+dy+")");

            //MoveMap(dx,dy);
        } else {
            // 정수로 변환

            val pt_x = (x).toInt()
            val pt_y = (y).toInt()

            //pt_y = img_map.height - parseInt(pt_y);
            val point = Point(pt_x, pt_y)
            m_bIsMouseDown = false

            //            Log.d(TAG, "----------------------------------");
//            Log.d(TAG, "m_isCapture : " + m_isCapture);
//            Log.d(TAG, "m_drawing : " + m_drawing);
//            Log.d(TAG, "m_roiviewflag : " + m_roiviewflag);
//            Log.d(TAG, "m_CurType : " + m_CurType);
//            Log.d(TAG, "m_objSelect : " + m_objSelect);
//            Log.d(TAG, "m_RoiCurIndex : " + m_RoiCurIndex);
//            Log.d(TAG, "point : (" + point.x + "," + point.y+")");
            if (m_isCapture)  // 누른 상태에서 마우스 이동중
            {
                m_isCapture = false // 누른 상태로 마우스 이동 해제
                if (m_drawing == true) {
                    if (m_CurType == "roi_point") {
                        m_RoiCurObject!!.m_endroiflag = true
                        if (CObject_AddCurObject(point)) {
                            m_drawstart = true // 그리기가 끝남.
                            m_drawing = false
                            m_isselected = true
                        } else {
                            m_drawing = false
                            m_isselected = false
                            m_drawstart = false
                            m_CurType = "default"
                        }
                    } else if (m_CurType == "roi_line") {
                        m_RoiCurObject!!.m_endroiflag = true // 그리기가 끝났음을 나타냄
                        if (CObject_AddCurObject(point)) {
                            m_drawing = false
                            m_drawstart = true
                            m_isselected = true
                        } else {
                            m_drawing = false
                            m_isselected = false
                            m_drawstart = false

                            m_DnPoint.x = -1
                            m_DnPoint.y = -1

                            m_ptOld.x = -1
                            m_ptOld.y = -1
                        }
                    } else if (m_CurType == "roi_rect") {
                        m_RoiCurObject!!.m_endroiflag = true // 그리기가 끝났음을 나타냄
                        if (CObject_AddCurObject(point)) {
                            m_drawing = false
                            m_drawstart = true
                            m_isselected = true
                        } else {
                            m_drawing = false
                            m_isselected = false
                            m_drawstart = false

                            m_DnPoint.x = -1
                            m_DnPoint.y = -1

                            m_ptOld.x = -1
                            m_ptOld.y = -1
                        }
                    } else if (m_CurType == "roi_polygon") {
                        //Point pt = new Point((int)(point.x*zoom_rate - StartPos_x),(int)(point.y*zoom_rate - StartPos_y));
                        //m_RoiCurObject.AddPoint(pt);

                        //if (m_RoiCurObject.m_Points.size() < 3) {
                        //    m_RoiCurObject = null;
                        //    m_isselected = false;
                        //    m_RoiCurIndex = -1;
                        //    m_drawstart = true;
                        //    m_drawing = false;
                        //}
                    }

                    //map_image_draw();
                    //CObject_Draw();
                    if (currentSelectedIndex > -1) {
                        // 해당 rio에 대해서 Tracker pointer를 생성해준다.
                        m_RoiObjects[currentSelectedIndex].MakeTracker(StartPos_x, StartPos_y)
                        // 선택된 것을 Activity에 전달해준다.
                        RoiSelectedListener!!.onRoiSelected(currentSelectedIndex)
                    }
                    if (this.m_RoiCurObject != null) {
                        // 해당 rio에 대해서 Tracker pointer를 생성해준다.
                        m_RoiCurObject!!.MakeTracker(StartPos_x, StartPos_y)
                        // 선택된 것을 Activity에 전달해준다.
                        RoiCreateListener!!.onRoiCreate()
                    }
                    invalidate() // 화면을 다시 그리도록 요청

                    SetCursorType()
                } else if (m_Select != 0) {
                    //SetNormalizeRect();
                    m_isselected = true

                    //map_image_draw();
                    if (currentSelectedIndex > -1) {
                        // 해당 rio에 대해서 Tracker pointer를 생성해준다.
                        m_RoiObjects[currentSelectedIndex].MakeTracker(StartPos_x, StartPos_y)
                        // 변경된 것을 Activity에 전달해준다.
                        RoiChangedListener!!.onRoiChanged(currentSelectedIndex)
                    }
                    invalidate()

                    //CObject_Draw();
                    SetCursorType()
                }
            }
        }
    }

    fun MoveMap(dx: Float, dy: Float) {
        StartPos_x += dx.toInt()
        StartPos_y += dy.toInt()

        val imageW = bitmap!!.width
        val imageH = bitmap!!.height

        // 화면을 벗어나는 경우의 예외 처리가 필요함
        if (StartPos_x <= ((viewWidth - imageW * zoom_rate) / 2).toInt()) {
            StartPos_x = ((viewWidth - imageW * zoom_rate) / 2).toInt()
        }

        if (StartPos_y <= ((viewHeight - imageH * zoom_rate) / 2).toInt()) {
            StartPos_y = ((viewHeight - imageH * zoom_rate) / 2).toInt()
        }

        if (this.currentSelectedIndex > -1) {
            // 해당 rio에 대해서 Tracker pointer를 생성해준다.
            m_RoiObjects[currentSelectedIndex].MakeTracker(StartPos_x, StartPos_y)
            // 변경된 것을 Activity에 전달해준다.
            RoiChangedListener!!.onRoiChanged(currentSelectedIndex)
        }
        invalidate() // 화면을 다시 그리도록 요청
    }


    fun roi_AddPoint() {
        //Log.d(TAG, "roi_AddPoint()");

        strMode = "Add Roi"
        roiType = "roi_point"

        CObject_CurRoiCancelFunc()
        m_CurType = "roi_point"
        m_drawstart = true
    }

    fun roi_AddLine() {
        strMode = "Roi Add"
        roiType = "roi_line"

        CObject_CurRoiCancelFunc()
        m_CurType = "roi_line"
        m_drawstart = true
    }

    fun roi_AddRect() {
        strMode = "Roi Add"
        roiType = "roi_rect"

        CObject_CurRoiCancelFunc()
        m_CurType = "roi_rect"
        m_drawstart = true
    }

    fun roi_AddPolygon() {
        strMode = "Roi Add"
        roiType = "roi_polygon"

        val pt = Point(0, 0)
        //CObject_AddCurObject(pt);
        m_CurType = "roi_polygon"
        m_drawstart = true
    }

    fun roi_AddObject() {
        Log.d(TAG, "roi_AddObject(): ")
        if (m_RoiCurObject == null) return

        // line 생성
        if ((m_Select == -1) && m_RoiCurObject!!.roi_type == "roi_line") {
            // 객체 완성
            currentSelectedIndex = m_RoiObjects.size
            m_RoiObjects.add(m_RoiCurObject!!)

            ////Log.d(TAG, "m_RoiObjects.add(m_RoiCurObject): " + m_RoiCurObject.roi_type.equals("roi_line"));
            m_drawstart = true
            m_drawing = false
            m_RoiCurObject!!.m_endroiflag = true
            m_isCapture = false
            m_isselected = true

            // 해당 rio에 대해서 Tracker pointer를 생성해준다.
            m_RoiCurObject!!.MakeTracker(StartPos_x, StartPos_y)
            // 추가된 것을 Activity에 전달해준다.
            RoiCreateListener!!.onRoiCreate()

            invalidate()

            return
        }

        // rect 생성
        if ((m_Select == -1) && m_RoiCurObject!!.roi_type == "roi_rect") {
            // 객체 완성
            currentSelectedIndex = m_RoiObjects.size
            m_RoiObjects.add(m_RoiCurObject!!)
            Log.d(
                TAG,
                "m_RoiObjects.add(m_RoiCurObject): " + (m_RoiCurObject!!.roi_type == "roi_rect")
            )

            m_drawstart = true
            m_drawing = false
            m_RoiCurObject!!.m_endroiflag = true
            m_isCapture = false
            m_isselected = true

            // 해당 rio에 대해서 Tracker pointer를 생성해준다.
            m_RoiCurObject!!.MakeTracker(StartPos_x, StartPos_y)
            // 추가된 것을 Activity에 전달해준다.
            RoiCreateListener!!.onRoiCreate()

            invalidate()

            return
        }

        if (m_drawing) {
            if (m_RoiCurObject!!.m_Points.size < 3) {
                m_RoiCurObject = null
                currentSelectedIndex = -1
                m_drawstart = true
                m_drawing = false
                m_isselected = false
            } else {
                if (m_Select == -1 && m_RoiCurObject!!.roi_type == "roi_polygon") {
                    val pt = Point(0, 0)
                    m_RoiCurObject!!.AddEndPoint(pt, false) // 끝점 제거

                    roomNum++
                    m_RoiCurObject!!.m_label = "공간$roomNum"
                    // 객체 완성
                    currentSelectedIndex = m_RoiObjects.size
                    m_RoiObjects.add(m_RoiCurObject!!)
                    Log.d(TAG, "m_RoiObjects.add(m_RoiCurObject) " + currentSelectedIndex)

                    m_drawstart = true
                    m_drawing = false
                    m_RoiCurObject!!.m_endroiflag = true
                    m_isCapture = false
                    m_isselected = true

                    // 해당 rio에 대해서 Tracker pointer를 생성해준다.
                    m_RoiCurObject!!.MakeTracker(StartPos_x, StartPos_y)
                    // 추가된 것을 Activity에 전달해준다.
                    RoiCreateListener!!.onRoiCreate()

                    invalidate()

                    return
                }
            }
            if (m_RoiCurObject != null) {
                // 해당 rio에 대해서 Tracker pointer를 생성해준다.
                m_RoiCurObject!!.MakeTracker(StartPos_x, StartPos_y)
                // 추가된 것을 Activity에 전달해준다.
                RoiCreateListener!!.onRoiCreate()
            }

            invalidate()
            SetCursorType()
        }
    }

    fun roi_CreateObject() {
        Log.d(TAG, "roi_CreateObject(): ")

        if (strMenu != "추가") return

        m_drawstart = false

        val distance = 100
        val viewWidth = width
        val viewHeight = height
        // 화면의 중심 좌표를 이미지 좌표로 생성한다.
        val nLeft = ((((viewWidth / 2.0) - distance) - StartPos_x) / zoom_rate).toInt()
        val nTop = ((((viewHeight / 2.0) - distance) - StartPos_y) / zoom_rate).toInt()

        val nRight = ((((viewWidth / 2.0) + distance) - StartPos_x) / zoom_rate).toInt()
        val nBottom = ((((viewHeight / 2.0) + distance) - StartPos_y) / zoom_rate).toInt()





        Log.d(TAG, "getWidth() : $viewWidth")
        Log.d(TAG, "getHeight() : $viewHeight")
        Log.d(TAG, "distance : $distance")
        Log.d(TAG, "nLeft : $nLeft")
        Log.d(TAG, "nTop : $nTop")
        Log.d(TAG, "nRight : $nRight")
        Log.d(TAG, "nBottom : $nBottom")
        Log.d(TAG, "StartPos_x : $StartPos_x")
        Log.d(TAG, "StartPos_y : $StartPos_y")
        Log.d(TAG, "zoom_rate : $zoom_rate")
        Log.d(TAG, "m_CurType : $m_CurType")

        // line 생성
        if (m_CurType == "roi_polygon") {
            // 화면의 1/4 크기로 polygon(정사각형)으로 공간 생성한다.
            // 화면의 가로세로 가운데가 로봇이 이동 가능 지역이 아니면,
            // 가운데 점을 기준으로 1 pixel로 회전하면서 가장 가까운 점을 찾아서 그곳을 무게 중심으로 하는 공간을 생성한다.

            m_RoiCurObject = CDrawObj("roi_polygon", nLeft, nTop, nRight, nBottom)
            val pt = Point(nLeft, nTop)
            m_RoiCurObject!!.AddPoint(pt)
            val pt2 = Point(nRight, nTop)
            m_RoiCurObject!!.AddPoint(pt2)
            val pt3 = Point(nRight, nBottom)
            m_RoiCurObject!!.AddPoint(pt3)
            val pt4 = Point(nLeft, nBottom)
            m_RoiCurObject!!.AddEndPoint(pt4, true)

            // 먼저 적용한다.
            currentSelectedIndex = m_RoiObjects.size
            m_RoiObjects.add(m_RoiCurObject!!)
        }
        // line 생성
        if (m_CurType == "roi_line") {
            // 화면의 1/4 크기로 위에서 아래로 line으로 가상벽을 생성한다.
            // 도형의 무게 줌심은 가로,세로 가운데이다.

            m_RoiCurObject = CDrawObj("roi_line", nLeft, nTop, nRight, nBottom)

            // 먼저 적용한다.
            currentSelectedIndex = m_RoiObjects.size
            m_RoiObjects.add(m_RoiCurObject!!)
        }
        // line 생성
        if (m_CurType == "roi_rect") {
            // 화면의 1/4 크기로 정사각형 rect로 금지 공간을 생성한다.
            // 도형의 무게 줌심은 가로,세로 가운데이다.
            // 스테이션이 금지공간에 표함되면 안된다.

            m_RoiCurObject = CDrawObj("roi_rect", nLeft, nTop, nRight, nBottom)

            // 먼저 적용한다.
            currentSelectedIndex = m_RoiObjects.size
            m_RoiObjects.add(m_RoiCurObject!!)
        } else {
            //return;
        }

        if (m_RoiCurObject != null) {
            // 해당 rio에 대해서 Tracker pointer를 생성해준다.
            m_RoiCurObject!!.MakeTracker(StartPos_x, StartPos_y)
            // 추가된 것을 Activity에 전달해준다.
            RoiCreateListener!!.onRoiCreate()
        }

        strMenu = "수정"

        invalidate()
        SetCursorType()
    }

    fun roi_RemoveObject() {
        if (!m_drawstart && !m_drawing) {
            if (CObject_DelCurObject()) {
                //roomNum = CountRoomNum(); 공간 이름을 갱신할 것인가?
                // 변경된 것을 Activity에 전달해준다.
                RoiChangedListener!!.onRoiChanged(currentSelectedIndex)
                invalidate()
            }
        }
    }

    fun roi_FindObject() {
        strMode = "Roi Find"

        m_CurType = "default"

        CObject_CurRoiCancelFunc()

        strMode = "Roi Find & Move"
    }

    fun CObject_AddCurObject(point: Point): Boolean {
        //Log.d(TAG,"CObject_AddCurObject("+point.x+","+point.y+")");

        if (m_RoiCurObject == null) {
            //Log.d(TAG, "m_RoiCurObject == null");
            return false
        }


        val pt_x = (point.x / zoom_rate).toInt()
        val pt_y = (point.y / zoom_rate).toInt()

        val pt = Point(pt_x, pt_y)

        // 좌상단 좌표를 시작점으로 하고 마지막 점과 같으면
        val sp = Point(m_RoiCurObject!!.m_MBR.left, m_RoiCurObject!!.m_MBR.top)
        //console.log('CObject_AddCurObject::( ('+pt_x+','+pt_y+') ('+sp.x+','+sp.y+') )');
        if ((sp.x == pt_x) && (sp.y == pt_y)) {
            if (m_RoiCurObject!!.roi_type != "roi_point") {
                m_RoiCurObject = null
                currentSelectedIndex = -1
                return false
            }
        }

        if (m_RoiCurObject!!.roi_type == "roi_point") {
            m_RoiCurObject!!.m_MBR.right = pt_x
            m_RoiCurObject!!.m_MBR.bottom = pt_y

            currentSelectedIndex = m_RoiObjects.size
            m_RoiObjects.add(m_RoiCurObject!!)
            //Log.d(TAG, "m_RoiObjects.add(m_RoiCurObject)");
        } else if (m_RoiCurObject!!.roi_type == "roi_polygon") {
            m_RoiCurObject!!.AddEndPoint(pt, false)

            currentSelectedIndex = m_RoiObjects.size
            m_RoiObjects.add(m_RoiCurObject!!)

            //Log.d(TAG, "m_RoiObjects.add(m_RoiCurObject)");
        }
        //console.log(m_RoiCurObject);
        return true
    }

    fun CObject_CreateObject(objType: String, point: Point): Boolean {
        m_RoiCurObject = null
        currentSelectedIndex = -1

        //Log.d(TAG, "CObject_CreateObject("+objType+",("+point.x+","+point.y+") )");
        val pt_x = ((point.x - StartPos_x) / zoom_rate).toInt()
        val pt_y = ((point.y - StartPos_y) / zoom_rate).toInt()

        //Log.d(TAG, "CObject_CreateObject("+objType+",("+pt_x+","+pt_y+") )");
        when (objType) {
            "roi_point" ->                 //console.log('pt('+pt_x+','+pt_y+')');
                m_RoiCurObject = CDrawObj("roi_point", pt_x, pt_y, pt_x, pt_y)

            "roi_line" -> {
                //console.log('pt('+pt_x+','+pt_y+')');
                //m_RoiCurObject = new CDrawObj('roi_point', pt_x, pt_y, pt_x, pt_y);
                m_RoiCurObject = CDrawObj("roi_line", pt_x, pt_y, pt_x, pt_y)
                //console.log(m_RoiCurObject.m_MBR);
                //console.log(m_RoiCurObject);
                //m_RoiCurObject.set_width(img_map.width);
                //m_RoiCurObject.set_height(img_map.height);
                ////m_RoiCurObject.setZoom(zoom_rate);
                //m_RoiCurObject.set_orient(map_orient_x, map_orient_y);
                //m_RoiCurObject.set_resolution(map_resolution);
                m_RoiCurObject!!.SetFillColor(randomColor)
            }

            "roi_rect" ->                 //console.log('pt('+pt_x+','+pt_y+')');
                //m_RoiCurObject = new CDrawObj('roi_point', pt_x, pt_y, pt_x, pt_y);
                m_RoiCurObject = CDrawObj("roi_rect", pt_x, pt_y, pt_x, pt_y)


            "roi_polygon" -> {
                m_RoiCurObject = CDrawObj("roi_polygon", pt_x, pt_y, pt_x, pt_y)
                val pt = Point(pt_x, pt_y)
                m_RoiCurObject!!.AddPoint(pt)
            }

            else -> {}
        }


        //console.log(m_RoiCurObject);
        return true
    }

    fun CObject_DelCurObject(): Boolean {
        //console.log('CObject_DelCurObject()');
        //console.log('m_RoiCurIndex: '+m_RoiCurIndex);

        if (currentSelectedIndex == -1) {
            return false
        }

        m_RoiObjects.removeAt(currentSelectedIndex)
        m_RoiCurObject = null
        currentSelectedIndex = -1

        //map_image_draw();
        // 변경된 것을 Activity에 전달해준다.
        RoiChangedListener!!.onRoiChanged(currentSelectedIndex)
        invalidate() // 화면을 다시 그리도록 요청

        //CObject_Draw();
        return true
    }

    fun CObject_CurRoiCancelFunc() {
        //console.log('CObject_CurRoiCancelFunc()');

        if (m_drawing) {
            m_drawing = false
            m_drawstart = false
            m_RoiCurObject = null
            currentSelectedIndex = -1

            m_CurType = "default"

            m_isselected = false

            //map_image_draw();
            // 변경된 것을 Activity에 전달해준다.
            RoiChangedListener!!.onRoiChanged(currentSelectedIndex)
            invalidate() // 화면을 다시 그리도록 요청
            //CObject_Draw();
        } else if (m_drawstart) {
            m_drawing = false
            m_drawstart = false

            m_CurType = "default"

            m_isselected = false
        }
    }

    // 마우스 좌표만큼 선택한 객체을 수정한다.
    fun CObject_MoveHandleTo(point: Point, point_dn: Point, nHandle: Int) {
        Log.d(
            TAG,
            "CObject_MoveHandleTo( (" + point_dn.x + "," + point_dn.y + "),(" + point.x + "," + point.y + ")," + nHandle + ")"
        )

        if (m_RoiCurObject == null) return

        val pt1 = Point(0, 0)
        pt1.x = ((point.x - StartPos_x) / zoom_rate).toInt()
        pt1.y = ((point.y - StartPos_y) / zoom_rate).toInt()

        val pt2 = Point(0, 0)
        pt2.x = ((point_dn.x - StartPos_x) / zoom_rate).toInt()
        pt2.y = ((point_dn.y - StartPos_y) / zoom_rate).toInt()

        //console.log('CObject_MoveHandleTo(,,'+nHandle+')');
        // 현재 객체를 수정한다.
        //m_RoiCurObject.MoveHandleTo(point, point_dn, nHandle);
        //Log.d(TAG, "m_RoiCurIndex : " + m_RoiCurIndex);
        if (currentSelectedIndex > -1) {
            //m_RoiObjects[m_RoiCurIndex] = m_RoiCurObject;
            //m_RoiObjects.get(m_RoiCurIndex).MoveHandleTo(point, point_dn, nHandle);
            if (strMenu == "선택") {
                m_RoiObjects[currentSelectedIndex].MoveTo(pt1, pt2)
            } else {
                //m_RoiObjects.get(m_RoiCurIndex).MoveHandleTo(point, point_dn, nHandle);
                m_RoiObjects[currentSelectedIndex].MoveHandleTo(pt1, pt2, nHandle)
            }
            m_RoiCurObject = m_RoiObjects[currentSelectedIndex]
        } else {
            //m_RoiCurObject.MoveHandleTo(point, point_dn, nHandle);
            m_RoiCurObject!!.MoveHandleTo(pt1, pt2, nHandle)
        }

        //map_image_draw();
        // 해당 rio에 대해서 Tracker pointer를 생성해준다.
        m_RoiCurObject!!.MakeTracker(StartPos_x, StartPos_y)
        // 변경된 것을 Activity에 전달해준다.
        RoiCreateListener!!.onRoiCreate()

        invalidate() // 화면을 다시 그리도록 요청
        //CObject_Draw();
    }

    fun CObject_MovePointTo(point: Point, point_dn: Point, nHandle: Int) {
        Log.d(
            TAG,
            "CObject_MovePointTo( (" + point_dn.x + "," + point_dn.y + "),(" + point.x + "," + point.y + ")," + nHandle + ")"
        )

        if (m_RoiCurObject == null) return

        val pt1 = Point(0, 0)
        pt1.x = ((point.x - StartPos_x) / zoom_rate).toInt()
        pt1.y = ((point.y - StartPos_y) / zoom_rate).toInt()

        val pt2 = Point(0, 0)
        pt2.x = ((point_dn.x - StartPos_x) / zoom_rate).toInt()
        pt2.y = ((point_dn.y - StartPos_y) / zoom_rate).toInt()

        if (currentSelectedIndex > -1) {
            if (strMenu == "선택") {
                m_RoiObjects[currentSelectedIndex].MoveTo(pt1, pt2)
            } else if (strMenu == "수정") {
                m_RoiObjects[currentSelectedIndex].MovePointTo(pt1, pt2, nHandle)
            } else {
                //m_RoiObjects.get(m_RoiCurIndex).MoveHandleTo(point, point_dn, nHandle);
                m_RoiObjects[currentSelectedIndex].MoveHandleTo(pt1, pt2, nHandle)
            }
            m_RoiCurObject = m_RoiObjects[currentSelectedIndex]
        } else {
            //m_RoiCurObject.MoveHandleTo(point, point_dn, nHandle);
            m_RoiCurObject!!.MoveHandleTo(pt1, pt2, nHandle)
        }

        //map_image_draw();
        // 해당 rio에 대해서 Tracker pointer를 생성해준다.
        m_RoiCurObject!!.MakeTracker(StartPos_x, StartPos_y)
        // 변경된 것을 Activity에 전달해준다.
        RoiCreateListener!!.onRoiCreate()
        invalidate() // 화면을 다시 그리도록 요청
        //CObject_Draw();
    }

    // roi 영역의 시작점(point.dn)과 끝점(point)
    // roi_line을 그릴 때 사용한다.
    fun CObject_MoveToRect(point_dn: Point, point: Point) {
        //console.log('CObject_MoveToRect( ('+point_dn.x+','+point_dn.y+'),('+point.x+','+point.y+') )');
        if (m_RoiCurObject == null) return


        //console.log(m_RoiCurObject);

        //var pt_x = parseInt(point.x/zoom_rate)*map_resolution + map_orient_x;
        //var pt_y = (img_map.height-parseInt(point.y/zoom_rate))*map_resolution + map_orient_y;


        // 왼쪽 하단이 원점(0,0)이다.
        val pt1 = Point(0, 0)
        pt1.x = ((point_dn.x - StartPos_x) / zoom_rate).toInt()
        pt1.y = ((point_dn.y - StartPos_y) / zoom_rate).toInt()

        val pt2 = Point(0, 0)
        pt2.x = ((point.x - StartPos_x) / zoom_rate).toInt()
        pt2.y = ((point.y - StartPos_y) / zoom_rate).toInt()

        //console.log(pt1);

        // 이미지 시작 좌표이동

        // 선택된 객체이동
        m_RoiCurObject!!.MoveToRect(pt1, pt2)
        if (currentSelectedIndex > -1) {
            //m_RoiObjects.get(m_RoiCurIndex) = m_RoiCurObject;
            m_RoiObjects[currentSelectedIndex] = m_RoiCurObject!!
            //m_RoiObjects[m_RoiCurIndex].MoveTo(point, point_dn);
            //m_RoiCurObject = m_RoiObjects[m_RoiCurIndex];
        }


        //map_image_draw();
        // 해당 rio에 대해서 Tracker pointer를 생성해준다.
        m_RoiCurObject!!.MakeTracker(StartPos_x, StartPos_y)
        // 변경된 것을 Activity에 전달해준다.
        RoiCreateListener!!.onRoiCreate()
        invalidate() // 화면을 다시 그리도록 요청
        //CObject_Draw();
    }

    fun CObject_MoveToPoint(point_dn: Point, point: Point) {
        Log.d(
            TAG,
            "CObject_MoveToPoint( (" + point_dn.x + "," + point_dn.y + "),(" + point.x + "," + point.y + ") )"
        )
        if (m_RoiCurObject == null) return

        //m_objSelect ( -1: 선택 안됨, 0: 이동 )
        if (m_objSelect < 1) return

        // 왼쪽 하단이 원점(0,0)이다.
        val pt1 = Point(0, 0)
        pt1.x = ((point_dn.x - StartPos_x) / zoom_rate).toInt()
        pt1.y = ((point_dn.y - StartPos_y) / zoom_rate).toInt()

        val pt2 = Point(0, 0)
        pt2.x = ((point.x - StartPos_x) / zoom_rate).toInt()
        pt2.y = ((point.y - StartPos_y) / zoom_rate).toInt()

        //console.log(pt1);

        // 선택된 객체이
        m_RoiCurObject!!.MovePointTo(pt1, pt2, m_objSelect)
        if (currentSelectedIndex > -1) {
            m_RoiObjects[currentSelectedIndex] = m_RoiCurObject!!
        }
        // 해당 rio에 대해서 Tracker pointer를 생성해준다.
        m_RoiCurObject!!.MakeTracker(StartPos_x, StartPos_y)
        // 변경된 것을 Activity에 전달해준다.
        RoiCreateListener!!.onRoiCreate()
        invalidate() // 화면을 다시 그리도록 요청
    }

    // 마우스 좌표만큼 선택한 객체를 이동한다.
    fun CObject_MoveTo(point_dn: Point, point: Point) {
        //Log.d(TAG,"CObject_MoveTo( ("+point_dn.x+","+point_dn.y+"),("+point.x+","+point.y+") )");
        if (m_RoiCurObject == null) return


        //console.log(m_RoiCurObject);

        //var pt_x = parseInt(point.x/zoom_rate)*map_resolution + map_orient_x;
        //var pt_y = (img_map.height-parseInt(point.y/zoom_rate))*map_resolution + map_orient_y;
        val pt1 = Point(0, 0)
        pt1.x = ((point.x - StartPos_x) / zoom_rate).toInt()
        pt1.y = ((point.y - StartPos_y) / zoom_rate).toInt()

        val pt2 = Point(0, 0)
        pt2.x = ((point_dn.x - StartPos_x) / zoom_rate).toInt()
        pt2.y = ((point_dn.y - StartPos_y) / zoom_rate).toInt()


        //console.log(point);
        // 선택된 객체이동

        // 241216 Bitmap밖으로 안벗어나게 변화
        val origin_MBR_center = m_RoiCurObject!!.m_MBR_center
        val x = m_RoiCurObject!!.m_MBR_center.x
        val y = m_RoiCurObject!!.m_MBR_center.y
        val dx = pt1.x - pt2.x
        val dy = pt1.y - pt2.y
        val pixelColor: Int

        // 241216 핀이 bitmap 영역 밖으로 나가지 않도록 변경
        if (m_RoiCurObject!!.roi_type === "roi_polygon") {
            if ((x + dx > 0) && (y + dy > 0) &&
                (x + dx <= bitmap!!.width) && (y + dy <= bitmap!!.height)
            ) {
                pixelColor = bitmap!!.getPixel(x + dx, y + dy)
                if ((pixelColor and 0x00FFFFFF) == 0x00969696) {
                    m_RoiCurObject!!.MoveTo(pt1, pt2)
                }
            }
        } else {
            m_RoiCurObject!!.MoveTo(pt1, pt2)
        }

        // 해당 rio에 대해서 Tracker pointer를 생성해준다.
        m_RoiCurObject!!.MakeTracker(StartPos_x, StartPos_y)
        // 변경된 것을 Activity에 전달해준다.
        RoiChangedListener!!.onRoiChanged(currentSelectedIndex)

        if (currentSelectedIndex > -1) {
            //m_RoiObjects.get(m_RoiCurIndex) = m_RoiCurObject;
            m_RoiObjects[currentSelectedIndex] = m_RoiCurObject!!

            //m_RoiObjects[m_RoiCurIndex].MoveTo(point, point_dn);
            //m_RoiCurObject = m_RoiObjects[m_RoiCurIndex];
            // 해당 rio에 대해서 Tracker pointer를 생성해준다.
            m_RoiObjects[currentSelectedIndex].MakeTracker(StartPos_x, StartPos_y)
            // 변경된 것을 Activity에 전달해준다.
            RoiChangedListener!!.onRoiChanged(currentSelectedIndex)
        }

        //map_image_draw();
        // 해당 rio에 대해서 Tracker pointer를 생성해준다.
        m_RoiCurObject!!.MakeTracker(StartPos_x, StartPos_y)
        // 변경된 것을 Activity에 전달해준다.
        RoiCreateListener!!.onRoiCreate()
        invalidate() // 화면갱신
        //CObject_Draw();
    }

    /*
    public void  CObject_Draw(Canvas canvas){
        //console.log(m_RoiObjects);
        int obj = 0;
        for(obj=0; obj<m_RoiObjects.size(); obj++)
        {
            m_RoiObjects.get(obj).SetZoom(zoom_rate);
            //m_RoiObjects.get(obj).Draw(canvas, zoom_rate);
            m_RoiObjects.get(obj).Draw(canvas);
        }

        // 선택 안되어도 그리기 상태이면 보여야 한다.
        //if (m_RoiCurObject != null && m_isselected == true)
        if (m_RoiCurObject != null )
        {
            int oldc = m_RoiCurObject.GetLineColor();
            m_RoiCurObject.SetLineColor(Color.rgb(255,255,0));
            m_RoiCurObject.SetZoom(zoom_rate);
            //m_RoiCurObject.Draw(canvas, zoom_rate);
            m_RoiCurObject.Draw(canvas);
            m_RoiCurObject.SetLineColor(oldc);
        }
    }
    */
    fun CObject_FindObject(point: Point, msMove: Boolean): Int {
        //Log.d(TAG, "CObject_FindObject( ("+point.x+","+point.y+","+msMove+"),"+msMove+" )");

        // 현재 객체에서 포인트 테스트

        Log.d(TAG, "m_RoiCurIndex : " + currentSelectedIndex)

        // 원본 이미지에서 검사하는 픽셍의 크기
        val cw = (50 / zoom_rate).toInt()
        val ch = (50 / zoom_rate).toInt()

        if (currentSelectedIndex != -1) {
            var fRet = -1
            if (m_RoiCurObject != null) {
                Log.d(TAG, "m_CurType : $m_CurType")

                //Log.d(TAG, "m_RoiCurObject.roi_type : " + m_RoiCurObject.roi_type);
                if (m_CurType == m_RoiCurObject!!.roi_type) {
                    fRet = if (strMenu == "수정") {
                        m_RoiCurObject!!.PointInPoint(point, cw, ch)
                    } else {
                        m_RoiCurObject!!.PointInHandle(point, cw, ch)
                    }
                }
            } else {
                Log.d(TAG, "m_RoiCurObject == null")
                if (strMenu == "수정") {
                    fRet = m_RoiObjects[currentSelectedIndex].PointInPoint(point, cw, ch)
                } else {
                    fRet = m_RoiObjects[currentSelectedIndex].PointInHandle(point, cw, ch)
                    if (fRet > 0) {
                        fRet = 0 // "수정"이 아닌 경우에는 이동으로 설정한다.
                    }
                }
            }

            //Log.d(TAG, "fRet : " + fRet);
            //console.log(fRet);
            if ((m_RoiObjects[currentSelectedIndex].roi_type == "roi_point") && (fRet == 1)) {
                // point의 경우에는 커서를 이동으로 설정한다.
                return 0
            }
            if (fRet != 0) return fRet
        }

        //console.log('전체 체크');
        // 모든 객체에 대해서 포인트 테스트
        var obj = 0
        //Log.d(TAG, "m_RoiObjects.size() : " + m_RoiObjects.size() );
        //for(obj=0;obj<1;obj++)    // for test
        obj = 0
        while (obj < m_RoiObjects.size) {
            ////Log.d(TAG, "m_CurType : " + m_CurType);
            ////Log.d(TAG, "m_RoiObjects.get("+obj+").roi_type : " + m_RoiObjects.get(obj).roi_type);
            if (m_CurType == m_RoiObjects[obj].roi_type) {
                if (m_RoiObjects[obj].PointInRect(point)) {
                    if (msMove)  // 마우스를 누른 경우
                    {
                        m_RoiCurObject = m_RoiObjects[obj]
                        currentSelectedIndex = obj
                    }
                    return 0
                }
            }

            obj++
        }

        // 이동중이면
        if (msMove) {
            m_RoiCurObject = null
            currentSelectedIndex = -1
            //console.log('CObject_FindObject()::m_RoiCurIndex: '+m_RoiCurIndex);
        }

        return -1
    }

    // 참조: 구글검색 "javascript 마우스 커서 변경"
    fun SetCursorType(): Boolean {
        // 그리기 상태
        if (m_drawstart == true) {
            if (m_CurType !== "default")  // 사용자 지정 커서 타입
            {
                //::SetCursor(AfxGetApp()->LoadCursor(IDC_CURSOR));
                return true
            }
        } else {
            //console.log('SetCursorType('+m_objSelect+')');
            //객체 선택 위한 마우스 커서 변경
            return when (m_objSelect) {
                0 -> {
                    //$('.work_main_map_div').css('cursor', 'move');
                    //::SetCursor(AfxGetApp() -> LoadStandardCursor(IDC_SIZEALL));
                    true
                }

                1, 8 -> {
                    //$('.work_main_map_div').css('cursor', 'nw-resize');
                    //::SetCursor(AfxGetApp() -> LoadStandardCursor(IDC_SIZENWSE));
                    true
                }

                2, 7 -> {
                    //$('.work_main_map_div').css('cursor', 'n-resize');
                    //::SetCursor(AfxGetApp() -> LoadStandardCursor(IDC_SIZENS));
                    true
                }

                3, 6 -> {
                    //$('.work_main_map_div').css('cursor', 'ne-resize');
                    //::SetCursor(AfxGetApp() -> LoadStandardCursor(IDC_SIZENESW));
                    true
                }

                4, 5 -> {
                    //$('.work_main_map_div').css('cursor', 'ew-resize');
                    //::SetCursor(AfxGetApp() -> LoadStandardCursor(IDC_SIZEWE));
                    true
                }

                else -> {
                    //$('.work_main_map_div').css('cursor', 'default');
                    true
                }
            }
            //if(m_CurType != 'default')	// 커서 타입
            //{
            //    $('.work_main_map_div').css('cursor', 'default');
            //    //::SetCursor(AfxGetApp() -> LoadStandardCursor(IDC_ARROW));
            //    return true;
            //}
        }
        return false
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

    private fun getRandomColorArgb(blur: Int): Int {
        // RGB 값을 랜덤으로 생성
        val red = (Math.random() * 255).toInt() // 0~255
        val green = (Math.random() * 255).toInt() // 0~255
        val blue = (Math.random() * 255).toInt() // 0~255

        return Color.argb(blur, red, green, blue) // 랜덤 색상 반환
    }


    var stringName: String?
        get() {
            if (currentSelectedIndex < 0) {
                return null
            }

            return m_RoiObjects[currentSelectedIndex].GetString()
        }
        set(strName) {
            if (currentSelectedIndex < 0) {
                return
            }

            m_RoiObjects[currentSelectedIndex].SetString(strName)
            if (m_RoiCurObject != null) {
                m_RoiCurObject!!.SetString(strName)
            }
        }

    fun SetModeAndMenu(sMode: String, sMenu: String) {
        SetMode(sMode)
        SetMenu(sMenu)
    }

    fun SetLabel(newLabel: String) {
        if (m_RoiCurObject == null) {
            return  // 현재 선택된 객체가 없으면 종료
        }

        // 중복된 라벨인지 확인
        if (isLabelDuplicate(newLabel)) {
            Toast.makeText(context.applicationContext, "공간명이 중복되지 않도록 설정해주세요.", Toast.LENGTH_SHORT)
                .show()
            return
        }

        // 중복이 아니면 라벨 설정
        m_RoiCurObject!!.m_label = newLabel
        // 해당 rio에 대해서 Tracker pointer를 생성해준다.
        m_RoiCurObject!!.MakeTracker(StartPos_x, StartPos_y)
        // 변경된 것을 Activity에 전달해준다.
        RoiCreateListener!!.onRoiCreate()
        invalidate()
    }


    private fun isLabelDuplicate(label: String): Boolean {
        for (roiObject in m_RoiObjects) {
            if (roiObject !== m_RoiCurObject && label == roiObject.m_label) {
                return true // 중복된 라벨 발견
            }
        }
        return false // 중복 없음
    }

    fun AddPoint_Polygon(point: Point?) {
        m_RoiCurObject!!.AddPoint(point)
    }

    fun CObject_LoadObject(objType: String, point: Point): Boolean {
        m_RoiCurObject = null
        currentSelectedIndex = -1

        //Log.d(TAG, "CObject_CreateObject("+objType+",("+point.x+","+point.y+") )");
        val pt_x = point.x
        val pt_y = point.y

        //Log.d(TAG, "CObject_CreateObject("+objType+",("+pt_x+","+pt_y+") )");
        when (objType) {
            "roi_point" ->                 //console.log('pt('+pt_x+','+pt_y+')');
                m_RoiCurObject = CDrawObj("roi_point", pt_x, pt_y, pt_x, pt_y)

            "roi_line" -> {
                m_RoiCurObject = CDrawObj("roi_line", pt_x, pt_y, pt_x, pt_y)

                m_RoiCurObject!!.SetFillColor(randomColor)
            }

            "roi_rect" -> m_RoiCurObject = CDrawObj("roi_rect", pt_x, pt_y, pt_x, pt_y)

            "roi_polygon" -> {
                m_RoiCurObject = CDrawObj("roi_polygon", pt_x, pt_y, pt_x, pt_y)
                val pt = Point(pt_x, pt_y)
                m_RoiCurObject!!.AddPoint(pt)
            }

            else -> {}
        }


        //console.log(m_RoiCurObject);
        return true
    }

    fun CObject_LoadRect(point1: Point, point2: Point) {
        //m_RoiCurIndex = m_RoiObjects.size()-1;

        if (m_RoiCurObject == null) return


        // 왼쪽 하단이 원점(0,0)이다.
        m_RoiCurObject!!.m_MBR.right = point1.x
        m_RoiCurObject!!.m_MBR.bottom = point1.y
        m_RoiCurObject!!.m_MBR.left = point2.x
        m_RoiCurObject!!.m_MBR.top = point2.y
        if (currentSelectedIndex > -1) {
            m_RoiObjects[currentSelectedIndex] = m_RoiCurObject!!
        }

        // 해당 rio에 대해서 Tracker pointer를 생성해준다.
        m_RoiCurObject!!.MakeTracker(StartPos_x, StartPos_y)
        // 변경된 것을 Activity에 전달해준다.
        RoiCreateListener!!.onRoiCreate()
        invalidate() // 화면을 다시 그리도록 요청
    }

    private fun calculateAngle(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        // atan2로 각도 계산
        val angle = -(atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())) as Float

        return angle
    }

    private fun hideDeleteToggleBar() {
        val deleteToggleBar = findViewById<View>(R.id.delete_toggle_bar)
        if (deleteToggleBar != null) {
            deleteToggleBar.visibility = INVISIBLE
        }
    }

    // 인터페이스 정의
    interface OnRoiSelectedListener {
        fun onRoiSelected(indexSelected: Int)
    }

    // Activity에서 인터페이스를 설정할 수 있는 메서드
    fun setRoiSelectedListener(listener: OnRoiSelectedListener?) {
        RoiSelectedListener = listener
    }

    // 인터페이스 정의
    interface OnRoiCreateListener {
        fun onRoiCreate()
    }

    // Activity에서 인터페이스를 설정할 수 있는 메서드
    fun setRoiCreateListener(listener: OnRoiCreateListener?) {
        RoiCreateListener = listener
    }

    // 인터페이스 정의
    interface OnRoiChangedListener {
        fun onRoiChanged(indexSelected: Int)
    }

    // Activity에서 인터페이스를 설정할 수 있는 메서드
    fun setRoiChangedListener(listener: OnRoiChangedListener?) {
        RoiChangedListener = listener
    }

    fun setRotateMap90() {
        ratateAngle += 90 // 90도 회전
        if (ratateAngle >= 360) {
            ratateAngle = ratateAngle % 360
        }


        // setBitmap
        val values = FloatArray(9)
        matrix.getValues(values)

        Log.d(TAG, "StartPos_x : $StartPos_x")
        Log.d(TAG, "StartPos_y : $StartPos_y")

        Log.d(TAG, "values[Matrix.MTRANS_X] before: " + values[Matrix.MTRANS_X])
        Log.d(TAG, "values[Matrix.MTRANS_Y] before: " + values[Matrix.MTRANS_Y])

        val viewWidth = width
        val viewHeight = height
        Log.d(TAG, "getWidth() : $width")
        Log.d(TAG, "getHeight() : $height")

        Log.d(TAG, "bitmap.getWidth() :  " + bitmap!!.width)
        Log.d(TAG, "bitmap.getHeight() :  " + bitmap!!.height)

        Log.d(TAG, "zoom_rate : $zoom_rate")

        val xOld = StartPos_x
        val yOld = StartPos_y
        Log.d(TAG, "m_RoiObjects.size() : " + m_RoiObjects.size)
        var i = 0
        while (i < m_RoiObjects.size) {
            rotateObj(m_RoiObjects[i])

            i++
        }
        // 현재 그려주는 객체도 회전해준다.
        if (currentSelectedIndex < 0) {
            if (this.m_RoiCurObject != null) {
                // 그리는 중이면
                rotateObj(m_RoiCurObject!!)
            }
        }

        // roi을 회전후에 이미지를 회전한다.
        var centerX = (StartPos_x + (bitmap!!.width * zoom_rate) / 2f).toInt()
        var centerY = (StartPos_y + (bitmap!!.height * zoom_rate) / 2f).toInt()
        if (bitmapRotate != null) {
            centerX = (StartPos_x + (bitmapRotate!!.width * zoom_rate) / 2f).toInt()
            centerY = (StartPos_y + (bitmapRotate!!.height * zoom_rate) / 2f).toInt()
        }
        Log.d(TAG, "centerX : $centerX")
        Log.d(TAG, "centerY : $centerY")

        bitmapRotate = null
        val matrix_rotate = Matrix()
        matrix_rotate.postRotate(ratateAngle.toFloat(), bitmap!!.width / 2f, bitmap!!.height / 2f)
        bitmapRotate = Bitmap.createBitmap(
            bitmap!!,
            0,
            0,
            bitmap!!.width,
            bitmap!!.height,
            matrix_rotate,
            true
        )
        Log.d(TAG, "bitmap.getWidth() : " + bitmap!!.width)
        Log.d(TAG, "bitmap.getHeight() : " + bitmap!!.height)
        Log.d(TAG, "bitmapRotate.getWidth() : " + bitmapRotate!!.width)
        Log.d(TAG, "bitmapRotate.getHeight() : " + bitmapRotate!!.height)

        val translateX = (centerX - (bitmapRotate!!.width / 2f) * zoom_rate).toFloat()
        val translateY = (centerY - (bitmapRotate!!.height / 2f) * zoom_rate).toFloat()

        Log.d(TAG, "translateX : $translateX")
        Log.d(TAG, "translateY : $translateY")
        StartPos_x = translateX.toInt()
        StartPos_y = translateY.toInt()

        i = 0
        while (i < m_RoiObjects.size) {
            m_RoiObjects[i].MakeTracker(StartPos_x, StartPos_y)

            i++
        }
        if ((currentSelectedIndex != -1) && (currentSelectedIndex < m_RoiObjects.size)) {
            m_RoiObjects[currentSelectedIndex].MakeTracker(StartPos_x, StartPos_y)
            RoiChangedListener!!.onRoiChanged(currentSelectedIndex)
        }

        // 현재 그려주는 객체도 회전해준다.
        if (m_RoiCurObject != null) {
            m_RoiCurObject!!.MakeTracker(StartPos_x, StartPos_y)
            // 변경된 것을 Activity에 전달해준다.
            RoiCreateListener!!.onRoiCreate()
        }
        invalidate()
    }

    fun rotateObj(obj: CDrawObj) {
        // 이미지의 중심점을 기준으로 모들 Point, mMBR을 90도씩 회전한다.

        val left: Int
        val top: Int
        val right: Int
        val bottom: Int
        var ptx: Int
        var pty: Int

        var roi_center_x: Int
        var roi_center_y: Int

        var dx: Double
        var dy: Double

        //  이미지의 중심에서 (left,top)까지의 거리를 구한다.
        var pointDistance: Double

        // 두 점 간의 각도 계산
        var lineAngle: Double

        var i: Int
        var j: Int
        when (obj.roi_type) {
            "roi_line" -> {
                // mMBR만 회전한다.
                left = obj.m_MBR.left
                top = obj.m_MBR.top
                right = obj.m_MBR.right
                bottom = obj.m_MBR.bottom

                roi_center_x = (bitmap!!.width / 2f).toInt()
                roi_center_y = (bitmap!!.height / 2f).toInt()
                if (bitmapRotate != null) {
                    Log.d(TAG, "bitmapRotate != null")
                    roi_center_x = (bitmapRotate!!.width / 2f).toInt()
                    roi_center_y = (bitmapRotate!!.height / 2f).toInt()
                }

                //Log.d(TAG, "roi_center_x : "+roi_center_x);
                //Log.d(TAG, "roi_center_y : "+roi_center_y);
                //obj.m_MBR.left = top;
                //obj.m_MBR.top = left;
                //obj.m_MBR.right = bottom;
                //obj.m_MBR.bottom = right;
                dx = (roi_center_x - left).toDouble()
                dy = (roi_center_y - top).toDouble()

                //Log.d(TAG, "dx :"+dx);
                //Log.d(TAG, "dy :"+dy);

                //  이미지의 중심에서 (left,top)까지의 거리를 구한다.
                pointDistance = sqrt(dx * dx + dy * dy)

                //Log.d(TAG, "pointDistance : " + pointDistance);

                // 두 점 간의 각도 계산
                lineAngle = atan2((roi_center_x - left).toDouble(), (roi_center_y - top).toDouble())

                //Log.d(TAG, "lineAngle : "+ lineAngle);
                obj.m_MBR.top =
                    (roi_center_x + pointDistance * cos(lineAngle + Math.PI.toFloat() / 2.0f) as Float).toInt()
                obj.m_MBR.left =
                    (roi_center_y + pointDistance * sin(lineAngle + Math.PI.toFloat() / 2.0f) as Float).toInt()

                dx = (roi_center_x - right).toDouble()
                dy = (roi_center_y - bottom).toDouble()

                //Log.d(TAG, "dx :"+dx);
                //Log.d(TAG, "dy :"+dy);

                //  이미지의 중심에서 (left,top)까지의 거리를 구한다.
                pointDistance = sqrt(dx * dx + dy * dy)

                //Log.d(TAG, "pointDistance : " + pointDistance);

                // 두 점 간의 각도 계산
                lineAngle =
                    atan2((roi_center_x - right).toDouble(), (roi_center_y - bottom).toDouble())

                //Log.d(TAG, "lineAngle : "+ lineAngle);
                obj.m_MBR.bottom =
                    (roi_center_x + pointDistance * cos(lineAngle + Math.PI.toFloat() / 2.0f) as Float).toInt()
                obj.m_MBR.right =
                    (roi_center_y + pointDistance * sin(lineAngle + Math.PI.toFloat() / 2.0f) as Float).toInt()
            }

            "roi_rect" -> {
                //Log.d(TAG, "obj.m_MBR("+i+") :( "+obj.m_MBR.left+", "+obj.m_MBR.top+", "+obj.m_MBR.right+", "+obj.m_MBR.bottom + " )");

                // mMBR만 회전한다.
                left = obj.m_MBR.left
                top = obj.m_MBR.top
                right = obj.m_MBR.right
                bottom = obj.m_MBR.bottom

                roi_center_x = (bitmap!!.width / 2f).toInt()
                roi_center_y = (bitmap!!.height / 2f).toInt()
                if (bitmapRotate != null) {
                    Log.d(TAG, "bitmapRotate != null")
                    roi_center_x = (bitmapRotate!!.width / 2f).toInt()
                    roi_center_y = (bitmapRotate!!.height / 2f).toInt()
                }

                //Log.d(TAG, "roi_center_x : "+roi_center_x);
                //Log.d(TAG, "roi_center_y : "+roi_center_y);
                //obj.m_MBR.left = top;
                //obj.m_MBR.top = left;
                //obj.m_MBR.right = bottom;
                //obj.m_MBR.bottom = right;
                dx = (roi_center_x - left).toDouble()
                dy = (roi_center_y - top).toDouble()

                //Log.d(TAG, "dx :"+dx);
                //Log.d(TAG, "dy :"+dy);

                //  이미지의 중심에서 (left,top)까지의 거리를 구한다.
                pointDistance = sqrt(dx * dx + dy * dy)

                //Log.d(TAG, "pointDistance : " + pointDistance);

                // 두 점 간의 각도 계산
                lineAngle = atan2((roi_center_x - left).toDouble(), (roi_center_y - top).toDouble())

                //Log.d(TAG, "lineAngle : "+ lineAngle);
                obj.m_MBR_rotate.top =
                    (roi_center_x + pointDistance * cos(lineAngle + Math.PI.toFloat() / 2.0f) as Float).toInt()
                obj.m_MBR_rotate.right =
                    (roi_center_y + pointDistance * sin(lineAngle + Math.PI.toFloat() / 2.0f) as Float).toInt()

                dx = (roi_center_x - right).toDouble()
                dy = (roi_center_y - bottom).toDouble()

                //Log.d(TAG, "dx :"+dx);
                //Log.d(TAG, "dy :"+dy);

                //  이미지의 중심에서 (left,top)까지의 거리를 구한다.
                pointDistance = sqrt(dx * dx + dy * dy)

                //Log.d(TAG, "pointDistance : " + pointDistance);

                // 두 점 간의 각도 계산
                lineAngle =
                    atan2((roi_center_x - right).toDouble(), (roi_center_y - bottom).toDouble())

                //Log.d(TAG, "lineAngle : "+ lineAngle);
                obj.m_MBR_rotate.bottom =
                    (roi_center_x + pointDistance * cos(lineAngle + Math.PI.toFloat() / 2.0f) as Float).toInt()
                obj.m_MBR_rotate.left =
                    (roi_center_y + pointDistance * sin(lineAngle + Math.PI.toFloat() / 2.0f) as Float).toInt()

                //Log.d(TAG, "obj.m_MBR_rotate("+i+") :( "+obj.m_MBR_rotate.left+", "+obj.m_MBR_rotate.top+", "+obj.m_MBR_rotate.right+", "+obj.m_MBR_rotate.bottom + " )");
                obj.m_MBR.left = obj.m_MBR_rotate.left
                obj.m_MBR.top = obj.m_MBR_rotate.top
                obj.m_MBR.right = obj.m_MBR_rotate.right
                obj.m_MBR.bottom = obj.m_MBR_rotate.bottom
            }

            "roi_polygon" -> {
                // mMBR 회전한다.
                left = obj.m_MBR.left
                top = obj.m_MBR.top
                right = obj.m_MBR.right
                bottom = obj.m_MBR.bottom

                roi_center_x = (bitmap!!.width / 2f).toInt()
                roi_center_y = (bitmap!!.height / 2f).toInt()
                if (bitmapRotate != null) {
                    Log.d(TAG, "bitmapRotate != null")
                    roi_center_x = (bitmapRotate!!.width / 2f).toInt()
                    roi_center_y = (bitmapRotate!!.height / 2f).toInt()
                }

                //Log.d(TAG, "roi_center_x : "+roi_center_x);
                //Log.d(TAG, "roi_center_y : "+roi_center_y);
                //obj.m_MBR.left = top;
                //obj.m_MBR.top = left;
                //obj.m_MBR.right = bottom;
                //obj.m_MBR.bottom = right;
                dx = (roi_center_x - left).toDouble()
                dy = (roi_center_y - top).toDouble()

                //Log.d(TAG, "dx :"+dx);
                //Log.d(TAG, "dy :"+dy);

                //  이미지의 중심에서 (left,top)까지의 거리를 구한다.
                pointDistance = sqrt(dx * dx + dy * dy)

                //Log.d(TAG, "pointDistance : " + pointDistance);

                // 두 점 간의 각도 계산
                lineAngle = atan2((roi_center_x - left).toDouble(), (roi_center_y - top).toDouble())

                //Log.d(TAG, "lineAngle : "+ lineAngle);
                obj.m_MBR.top =
                    (roi_center_x + pointDistance * cos(lineAngle + Math.PI.toFloat() / 2.0f) as Float).toInt()
                obj.m_MBR.right =
                    (roi_center_y + pointDistance * sin(lineAngle + Math.PI.toFloat() / 2.0f) as Float).toInt()

                dx = (roi_center_x - right).toDouble()
                dy = (roi_center_y - bottom).toDouble()

                //Log.d(TAG, "dx :"+dx);
                //Log.d(TAG, "dy :"+dy);

                //  이미지의 중심에서 (left,top)까지의 거리를 구한다.
                pointDistance = sqrt(dx * dx + dy * dy)

                //Log.d(TAG, "pointDistance : " + pointDistance);

                // 두 점 간의 각도 계산
                lineAngle =
                    atan2((roi_center_x - right).toDouble(), (roi_center_y - bottom).toDouble())

                //Log.d(TAG, "lineAngle : "+ lineAngle);
                obj.m_MBR.bottom =
                    (roi_center_x + pointDistance * cos(lineAngle + Math.PI.toFloat() / 2.0f) as Float).toInt()
                obj.m_MBR.left =
                    (roi_center_y + pointDistance * sin(lineAngle + Math.PI.toFloat() / 2.0f) as Float).toInt()

                //Log.d(TAG, "obj.m_MBR("+i+") :( "+obj.m_MBR.left+", "+obj.m_MBR.top+", "+obj.m_MBR.right+", "+obj.m_MBR.bottom + " )");

                // Points 회전한다.
                j = 0
                while (j < obj.m_Points.size) {
                    ptx = obj.m_Points[j].x
                    pty = obj.m_Points[j].y

                    dx = (roi_center_x - ptx).toDouble()
                    dy = (roi_center_y - pty).toDouble()

                    //Log.d(TAG, "dx :"+dx);
                    //Log.d(TAG, "dy :"+dy);

                    //  이미지의 중심에서 (x,y)까지의 거리를 구한다.
                    pointDistance = sqrt(dx * dx + dy * dy)

                    //Log.d(TAG, "pointDistance : " + pointDistance);

                    // 두 점 간의 각도 계산
                    lineAngle =
                        atan2((roi_center_x - ptx).toDouble(), (roi_center_y - pty).toDouble())

                    //Log.d(TAG, "lineAngle : "+ lineAngle);
                    obj.m_Points[j].y =
                        (roi_center_x + pointDistance * cos(lineAngle + Math.PI.toFloat() / 2.0f) as Float).toInt()
                    obj.m_Points[j].x =
                        (roi_center_y + pointDistance * sin(lineAngle + Math.PI.toFloat() / 2.0f) as Float).toInt()

                    j++
                }

                //obj.NormalizeRect();

                // 핀의 좌표도 회전해 준다.
                ptx = obj.m_MBR_center.x
                pty = obj.m_MBR_center.y

                dx = (roi_center_x - ptx).toDouble()
                dy = (roi_center_y - pty).toDouble()

                //Log.d(TAG, "dx :"+dx);
                //Log.d(TAG, "dy :"+dy);

                //  이미지의 중심에서 (x,y)까지의 거리를 구한다.
                pointDistance = sqrt(dx * dx + dy * dy)

                //Log.d(TAG, "pointDistance : " + pointDistance);

                // 두 점 간의 각도 계산
                lineAngle = atan2((roi_center_x - ptx).toDouble(), (roi_center_y - pty).toDouble())

                //Log.d(TAG, "lineAngle : "+ lineAngle);
                obj.m_MBR_center.y =
                    (roi_center_x + pointDistance * cos(lineAngle + Math.PI.toFloat() / 2.0f) as Float).toInt()
                obj.m_MBR_center.x =
                    (roi_center_y + pointDistance * sin(lineAngle + Math.PI.toFloat() / 2.0f) as Float).toInt()
            }
        }
    }


    companion object {
        private const val TAG = "MapImageView"
    }
}
