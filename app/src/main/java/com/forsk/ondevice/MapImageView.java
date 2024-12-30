package com.forsk.ondevice;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Random;

import android.graphics.Matrix;
import android.widget.Toast;

public class MapImageView extends View {

    private static final String TAG = "MapImageView";

    String strMode = "맵 탐색";
    String strMenu = "Zoom";

    private Bitmap bitmap; // 표시할 비트맵
    private Bitmap scaledBitmap;

    int ScreenWidth = 0;
    int ScreenHeight = 0;

    int viewWidth = 0;
    int viewHeight = 0;

    double aspect_rate = 0.0;   // 최대 ZoomOut rate

    double zoom_rate = 0.0;
    double zoom_rate_offset = 0.0;  // 확대/축소 시 기준 zoom rate

    int StartPos_x = 0;
    int StartPos_y = 0;
    int StartPos_x_offset = 0;
    int StartPos_y_offset = 0;

    int Origin_StartPos_x = 0;
    int Origin_StartPos_y = 0;

    String strTouchStatus = "None";
    String roiType = "default";

    float nTouchDownPosX = 0;
    float nTouchDownPosY = 0;
    float nTouchUpPosX = 0;
    float nTouchUpPosY = 0;


    ArrayList<CDrawObj> m_RoiObjects;

    boolean m_isCapture = false;    // 마우스 누른 상태에서 이동 여부

    boolean m_roiviewflag = true; // roi를 dispaly 여부

    //그리기 객체 사용
    boolean m_drawing = false;  // roi을 그리는 중인지
    boolean m_drawstart = false;
    Point m_DnPoint = new Point(-1, -1);    // 객체의 rect 시작점
    Point m_ptOld = new Point(-1, -1);    // freehand 같은 경우를 표현하기 위해서 필요하다.
    boolean m_bIsMouseDown = false;
    int m_Select = -1;    // 선택된 트래커 핸들..
    int mouse_down_pos_x = -1;
    int mouse_down_pos_y = -1;

    //현재 작업중인 그리기 객체 타입
    int m_RoiCurIndex = -1; // 선택된 m_RoiObjects 인덱스
    CDrawObj m_RoiCurObject = null; // 선택된 roi
    String m_CurType = "default";  //현재 설정된 roi 타입(커서) 설정
    boolean m_isselected = false;   // roi 선택 여부

    // 커서 종류
    int m_objSelect = -1;   // 마우스 모양


    Paint paint;

    private ScaleGestureDetector scaleGestureDetector;
    private Matrix matrix;

    private float scaleFactor = 1.0f;
    private float translateX = 0;     // X축 이동
    private float translateY = 0;     // Y축 이동

    private float focusX = 0f; // 확대 중심 X좌표
    private float focusY = 0f; // 확대 중심 Y좌표
    private float offsetX = 0f;
    private float offsetY = 0f;

    private Random random;    // 랜덤 생성기

    // 공간 개수
    int roomNum = 0;

    public int getCurrentSelectedIndex() {
        return m_RoiCurIndex;
    }

    public Pair<Integer, Integer> getSelectedObjectPosition() {
        if (m_RoiCurIndex != -1 && m_RoiCurIndex < m_RoiObjects.size()) {
            int x;
            int y;
            if (m_RoiCurObject.roi_type.equals("roi_polygon")) {
                Point mbr = m_RoiObjects.get(m_RoiCurIndex).GetMBRCenter();
                x = (int) (mbr.x * m_RoiObjects.get(m_RoiCurIndex).m_zoom + StartPos_x - 57);
                y = (int) (mbr.y * m_RoiObjects.get(m_RoiCurIndex).m_zoom + StartPos_y - 15);
            } else if (m_RoiCurObject.roi_type.equals("roi_line")){
                x = (int) (m_RoiObjects.get(m_RoiCurIndex).m_MBR.left * m_RoiObjects.get(m_RoiCurIndex).m_zoom + StartPos_x);
                y = (int) (m_RoiObjects.get(m_RoiCurIndex).m_MBR.top * m_RoiObjects.get(m_RoiCurIndex).m_zoom + StartPos_y + 100);
            } else {
                x = (int) (((m_RoiObjects.get(m_RoiCurIndex).m_MBR.left + m_RoiObjects.get(m_RoiCurIndex).m_MBR.right) / 2)
                        * m_RoiObjects.get(m_RoiCurIndex).m_zoom + StartPos_x - 55);
                y = (int) (m_RoiObjects.get(m_RoiCurIndex).m_MBR.top * m_RoiObjects.get(m_RoiCurIndex).m_zoom + StartPos_y + 120);
            }
            return new Pair<>(x, y);
        }
        return null; // 선택된 객체가 없으면 null 반환
    }

    public void clearSelection() {
        m_RoiCurIndex = -1;
        invalidate(); // 화면을 다시 그리도록 요청
    }

    public int CountRoomNum() {
        int count = 0;

        for (int i = 0; i < m_RoiObjects.size(); i++) {
            if (m_RoiObjects.get(i).roi_type.equals("roi_polygon")) count++;
        }
        return count;
    }

    public MapImageView(Context context, AttributeSet attrs) {
        super(context, attrs);

        paint = new Paint();
        paint.setColor(Color.BLUE);
        paint.setStyle(Paint.Style.FILL);

        m_RoiObjects = new ArrayList<CDrawObj>();

        // 랜덤 생성기 초기화
        random = new Random();

        // Matrix 초기화
        matrix = new Matrix();

        // ScaleGestureDetector 초기화
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        //String strMode = "맵 탐색";
        //String strMenu = "Zoom";
        //Log.d(TAG,"mouse GetPointCount: " + event.getPointerCount());
        if (event.getPointerCount() > 1) {
            scaleGestureDetector.onTouchEvent(event);
        } else {
            zoom_rate_offset = zoom_rate;
            StartPos_x_offset = StartPos_x;
            StartPos_y_offset = StartPos_y;

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                MouseDown(event.getX(), event.getY());
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                MouseUp(event.getX(), event.getY());
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                MouseMove(event.getX(), event.getY());
            }
        }

        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            // 스케일 값 가져오기
            scaleFactor *= detector.getScaleFactor();

            zoom_rate = zoom_rate_offset * scaleFactor;
            //scaleFactor = Math.max((float)aspect_rate, Math.min(scaleFactor, 5.0f));

            // 꽉찬 전체 이미지의 aspect_rate가 1.0보다 큰 경우와, 1.0보다 작은 경우를 고려한다.
            //scaleFactor = Math.max((float)aspect_rate, Math.min(scaleFactor, Math.max((float)(aspect_rate*5.0f), 5.0f)));

            // 최소 확대비율은 꽉찬 이미지의 1/2배까지만 축소
            // 최대 확대비율은 꽉찬 이미지(확대 배율 1.0이상)의 5.0배와 원본 이미지(확대 배율이 1.0미만)의 5.0배 중에 큰 값들 사용한다.
            //scaleFactor = Math.max((float)(aspect_rate/2.0f), Math.min(scaleFactor, Math.max((float)(aspect_rate*5.0f), 5.0f)));

            //Log.d(TAG,"zoom_rate: "+ zoom_rate);
            //Log.d(TAG,"zoom_rate_offset: "+ zoom_rate_offset);
            //Log.d(TAG, "scaleFactor: " + scaleFactor);

            float[] values = new float[9];
            matrix.getValues(values);
            //Log.d(TAG, "values[Matrix.MTRANS_X] : " + values[Matrix.MTRANS_X]);
            //Log.d(TAG, "values[Matrix.MTRANS_Y] : " + values[Matrix.MTRANS_Y]);
            //matrix.reset(); // matrix의 값을 초기화 한다.

            // 현재 Matrix 값에 스케일 적용
            matrix.setScale((float) zoom_rate, (float) zoom_rate);

            float focus_x = detector.getFocusX();
            float focus_y = detector.getFocusY();
            // 포커싱 된 부분을 이미지의 위치를 확인하여 해당 포커싱된 부분이 이미지의 가운데로 오게 한다.

            // 이동 값을 적용 (중앙 고정)
            //translateX = (getWidth() - bitmap.getWidth() * scaleFactor) / 2;
            //translateY = (getHeight() - bitmap.getHeight() * scaleFactor) / 2;
            //if(bitmap != null)
            {

                int CenterToImgX = (int) ((getWidth() / 2 - StartPos_x_offset) / zoom_rate_offset); // 선터 좌료를 원본 이미지 좌표로 변환
                float ImgStartPosX = (float) ((int) (getWidth() / 2) - CenterToImgX * zoom_rate); // 원본이미지의 좌표를 변경된 이미지의 줌심좌표로 수정
                float translateX = (float) ((int) (getWidth() / 2) - ((int) ((getWidth() / 2 - StartPos_x_offset) / zoom_rate_offset)) * zoom_rate);
                float translateY = (float) ((int) (getHeight() / 2) - ((int) ((getHeight() / 2 - StartPos_y_offset) / zoom_rate_offset)) * zoom_rate);

                //translateX = (float)(StartPos_x_offset + (getWidth()/2.0)*(1.0f - scaleFactor)*zoom_rate_offset );
                //translateX = (float)(StartPos_y_offset + (getHeight()/2.0)*(1.0f - scaleFactor)*zoom_rate_offset );
                //translateY = (float)(StartPos_y_offset + ((getHeight()/2.0)*zoom_rate_offset)*(1.0f - scaleFactor) );
                //Log.d(TAG, "translateX : " + translateX);
                //Log.d(TAG, "translateY : " + translateY);


                matrix.postTranslate(translateX, translateY);
                //matrix.postTranslate(5.0f, 5.0f);
            }

            // 화면 다시 그리기
            invalidate();
            return true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        //Log.d(TAG, "OnDraw(...)");

        float[] values = new float[9];
        matrix.getValues(values);
        // 변환된 이미지의 시작 좌표
        StartPos_x = (int) (values[Matrix.MTRANS_X]);
        StartPos_y = (int) (values[Matrix.MTRANS_Y]);

        //Log.d(TAG, "values[Matrix.MTRANS_X] : "+values[Matrix.MTRANS_X]);
        //Log.d(TAG, "values[Matrix.MTRANS_Y] : "+values[Matrix.MTRANS_Y]);

        // 이미지 비율을 유지하면서 화면에 꽉차게 그려준다.
        if (bitmap != null) {

            // Matrix 변환을 사용하여 Bitmap 그리기
            canvas.drawBitmap(bitmap, matrix, null);
            /*
            // View 크기 가져오기
            int viewWidth = getWidth();
            int viewHeight = getHeight();

            // 원본 비트맵의 특정 영역
            Rect srcRect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

            // View 전체에 이미지를 확대해서 표시
            Rect destRect = new Rect(StartPos_x, StartPos_y, (int) (StartPos_x + bitmap.getWidth() * zoom_rate), (int) (StartPos_y + bitmap.getHeight() * zoom_rate));


            // 캔버스에 그리기
            canvas.drawBitmap(bitmap, srcRect, destRect, null);

            */
        }

        //Log.d(TAG, "m_RoiObjects.size() : " + (int)(m_RoiObjects.size()));


        int i;

        Drawable iconDrawable = getResources().getDrawable(R.drawable.benjamin_direction, null);
        Drawable rotateDrawable = getResources().getDrawable(R.drawable.ic_rotate, null);

        // 시작위치 만큼 + 해줘야 일치한다.
        Point pt = new Point(StartPos_x, StartPos_y);
        int x, y;
        for (i = 0; i < m_RoiObjects.size(); i++) {

            if (strMenu.equals("핀 회전")) {
                if (m_RoiObjects.get(i).roi_type.equals("roi_polygon")) {
                    // 선택된 것만 회전한다.
                    if (i == m_RoiCurIndex) {
                        m_RoiObjects.get(i).setIconDrawable(iconDrawable);
                        m_RoiObjects.get(i).setRotateDrawable(rotateDrawable);
                    } else {
                        m_RoiObjects.get(i).setIconDrawable(iconDrawable);
                        m_RoiObjects.get(i).setRotateDrawable(null);
                    }
                }
            }
            else
            {
                m_RoiObjects.get(i).setIconDrawable(null);
            }


            m_RoiObjects.get(i).SetZoom(zoom_rate);
            if (strMenu.equals("수정")) {
                if (m_CurType.equals(m_RoiObjects.get(i).roi_type)) {
                    m_RoiObjects.get(i).Draw(canvas, pt, bitmap, true, true);
                } else {
                    m_RoiObjects.get(i).Draw(canvas, pt, bitmap, false, false);
                }
            } else {
                if(i == m_RoiCurIndex)
                {
                    m_RoiObjects.get(i).Draw(canvas, pt, bitmap, true, false);
                }
                else {
                    m_RoiObjects.get(i).Draw(canvas, pt, bitmap, false, false);
                }
            }
            //m_RoiObjects.get(i).DrawLabel(canvas);
            // 241222 jihyeon 핀 회전일 때 아이콘 변경
            if (strMenu.equals("핀 회전")) {

                //RotatePinIcon();

            } else if (m_RoiObjects.get(i).roi_type.equals("roi_polygon")) {

                Point mbr = m_RoiObjects.get(i).GetMBRCenter();
                x = (int) (mbr.x * m_RoiObjects.get(i).m_zoom + pt.x);
                y = (int) (mbr.y * m_RoiObjects.get(i).m_zoom + pt.y);
                // 배경으로 VectorDrawable 그리기
                Drawable drawable_ic = getResources().getDrawable(R.drawable.ic_location, null);
                drawable_ic.setBounds(x - 30, y - 80, x + 30, y + 10); //(60, 90)
                //Log.d(TAG,"PIN X: " + mbr.x + " " + mbr.y);

                Drawable drawable = getResources().getDrawable(R.drawable.pin_name, null);
                drawable.setBounds(x - 65, y - 146, x + 64, y - 80); //(129, 66)
                drawable.draw(canvas);


                // 텍스트 추가
                Paint paint = new Paint();
                paint.setColor(Color.WHITE);
                paint.setTextSize(36);
                paint.setTextAlign(Paint.Align.CENTER);

                // 선택할 경우 하얀 화면에 검은색 굴자, 선택이 안될 경우 검은색 화면에 하얀색 글자
                if(i == m_RoiCurIndex){
                    drawable.setColorFilter(Color.parseColor("#FFFFFF"), PorterDuff.Mode.SRC_IN);
                    paint.setColor(Color.BLACK);
                }
                else{
                    // default black color
                    paint.setColor(Color.WHITE);
                }

                drawable_ic.draw(canvas);
                drawable.draw(canvas);
                canvas.drawText(m_RoiObjects.get(i).m_label, x, y - 100, paint);
            }
        }

        if (m_RoiCurObject != null) {
            int oldc = m_RoiCurObject.GetLineColor();
            m_RoiCurObject.SetLineColor(Color.rgb(253,60,60));
            if(m_RoiCurObject.roi_type.equals("roi_rect"))
            {
                m_RoiCurObject.SetFillColor(Color.argb(178, 255, 70, 80)) ; //투명도 30
                //m_RoiCurObject.SetLineColor(Color.rgb(255,70,80));
            }
            //else if(m_RoiCurObject.roi_type.equals("roi_polygon")){
            //m_RoiCurObject.SetFillColor(getRandomColorArgb(50));
            //}
            m_RoiCurObject.SetZoom(zoom_rate);
            //m_RoiCurObject.Draw(canvas, zoom_rate);

            if(strMenu.equals("수정"))
            {
                if(m_CurType.equals(m_RoiCurObject.roi_type)) {
                    m_RoiCurObject.Draw(canvas, pt, bitmap, true, true);
                } else {
                    m_RoiCurObject.Draw(canvas, pt, bitmap, false, false);
                }
            } else {
                m_RoiCurObject.Draw(canvas, pt, bitmap, false, false);
            }
            if (strMenu.equals("삭제")) {
                // 삭제 메뉴일 때 토글바 처리
                Pair<Integer, Integer> position = getSelectedObjectPosition();
                if (position != null) {
                    // Context를 MapEditorActivity로 캐스팅
                    if (getContext() instanceof MapEditorActivity) {
                        MapEditorActivity activity = (MapEditorActivity) getContext();
                        activity.showDeleteToggleBar(position); // Activity의 메서드 호출
                    }
                }
            }
            //m_RoiCurObject.SetLineColor(oldc);
        }


    }

    public void setBitmap(Bitmap map) {
        //Log.d(TAG, "setBitmap(...)");
        this.bitmap = map;


        //Log.d(TAG, "ScreenWidth : " + ScreenWidth);
        //Log.d(TAG, "ScreenHeight : " + ScreenHeight);
        if ((ScreenWidth < 1) || (ScreenHeight < 1)) return;

        int viewWidth = this.getWidth();
        int viewHeight = this.getHeight();
        //Log.d(TAG, "viewWidth : " + viewWidth);
        //Log.d(TAG, "viewHeight : " + viewHeight);


        int imageW = bitmap.getWidth();
        int imageH = bitmap.getHeight();
        //Log.d(TAG, "imageW : " + imageW);
        //Log.d(TAG, "imageH : " + imageH);

        // 가로세로 비율을 구한다.
        double hRatio = (double) (viewWidth) / imageW;
        double vRatio = (double) (viewHeight) / imageH;
        //Log.d(TAG, "hRatio : " + hRatio);
        //Log.d(TAG, "vRatio : " + vRatio);

        aspect_rate = Math.min(hRatio, vRatio);

        // 확대 비율
        zoom_rate = aspect_rate;
        //Log.d(TAG, "zoom_rate : " + zoom_rate);

        zoom_rate_offset = zoom_rate;

        // 그려지는 이미지의 중심 좌표
        double draw_image_center_x = imageW / 2;
        double draw_image_center_y = imageH / 2;

        // 이미지를 그릴 위치를 구한다.
        //StartPos_x = (int) ((viewWidth - imageW * zoom_rate) / 2);
        StartPos_x = (int) ((getWidth() - bitmap.getWidth() * zoom_rate) / 2);
        //StartPos_y = (int) ((viewHeight - imageH * zoom_rate) / 2);
        StartPos_y = (int) ((getHeight() - bitmap.getHeight() * zoom_rate) / 2);
        //Log.d(TAG, "StartPos_x : "+StartPos_x);
        //Log.d(TAG, "StartPos_y : "+StartPos_y);

        StartPos_x_offset = StartPos_x;
        StartPos_y_offset = StartPos_y;

        matrix.setScale((float) zoom_rate, (float) zoom_rate);

        // 이동 값을 적용 (중앙 고정)
        float translateX = (float) ((getWidth() - bitmap.getWidth() * zoom_rate) / 2);
        float translateY = (float) ((getHeight() - bitmap.getHeight() * zoom_rate) / 2);
        //Log.d(TAG, "translateX : " + translateX );
        //Log.d(TAG, "translateY : " + translateY );

        matrix.postTranslate(translateX, translateY);


        invalidate(); // 화면을 다시 그리도록 요청

    }

    public int GetBitmapWidth() {
        if (bitmap == null) {
            return 0;
        }
        return bitmap.getWidth();
    }

    public int GetBitmapHeight() {
        if (bitmap == null) {
            return 0;
        }
        return bitmap.getHeight();
    }

    public void SetMode(String str) {
        //Log.d(TAG, "SetMode("+str+")");
        CObject_CurRoiCancelFunc();
        m_drawstart = false;
        this.strMode = str;
        // 241222 jihyeon 이전 모드가 핀 회전 모드였으면, 초기화
        if (strMenu.equals("핀 회전")) {
            for (CDrawObj roiObject : m_RoiObjects) {
                roiObject.clearIconDrawable();
                roiObject.clearRotateDrawable();
            }
        }
        strMenu = "default";
        if (strMode.equals("맵 탐색")) {

            this.m_CurType = "default";


        } else if (strMode.equals("공간 생성")) {

            this.m_CurType = "roi_polygon";

        } else if (strMode.equals("가상벽")) {

            this.m_CurType = "roi_line";

        } else if (strMode.equals("금지공간")) {

            this.m_CurType = "roi_rect";

        }
        invalidate();


    }

    public String GetMode() {
        return this.strMode;
    }

    public void SetMenu(String str) {
        //Toast.makeText(getContext().getApplicationContext(), "SetMenu("+str+")", Toast.LENGTH_SHORT).show();
        // 241222 jihyeon 이전 모드가 핀 회전 모드였으면, 초기화
        if (!str.equals("핀 회전") && strMenu.equals("핀 회전")) {
            for (CDrawObj roiObject : m_RoiObjects) {
                roiObject.clearIconDrawable();
            }
        }
        if (str.equals("추가")) {
            SetMode(strMode); // m_CurType를 재 설정해준다.
            m_drawstart = true;

        } else if (str.equals("수정")) {

            CObject_CurRoiCancelFunc();
            m_drawstart = false;

        } else if (str.equals("선택")) {
            if (strMenu.equals("추가")) {
                roi_AddObject();
            } else {
                CObject_CurRoiCancelFunc();
            }
            m_drawstart = false;
        } else if (str.equals("삭제")) {

        } else if (str.equals("핀 회전")) {
            // RotatePinIcon();
            // TODO 기능 구현 필요
        }

        strMenu = str;
        invalidate();
    }

    // 241222 jihyeon 핀 회전 모드 아이콘
    private void RotatePinIcon() {
        Drawable iconDrawable = getResources().getDrawable(R.drawable.benjamin_direction, null);
        Drawable rotateDrawable = getResources().getDrawable(R.drawable.ic_rotate, null);
        // 모든 ROI 객체의 핀을 새로운 Drawable로 설정
        for (CDrawObj roiObject : m_RoiObjects) {
            if (roiObject.roi_type.equals("roi_polygon")) {
                roiObject.setIconDrawable(iconDrawable);
                roiObject.setRotateDrawable(rotateDrawable);
            }
        }

        // 화면 갱신
        invalidate();
    }
    private void RotatePinIcon(int nIndex) {
        if(nIndex > (m_RoiObjects.size()-1)) return ;
        CDrawObj roiObject = m_RoiObjects.get(nIndex);

        Drawable iconDrawable = getResources().getDrawable(R.drawable.benjamin_direction, null);
        Drawable rotateDrawable = getResources().getDrawable(R.drawable.ic_rotate, null);
        if (roiObject.roi_type.equals("roi_polygon")) {
            roiObject.setIconDrawable(iconDrawable);
            roiObject.setRotateDrawable(rotateDrawable);
        }

    }

    public String GetMenu() {
        return this.strMenu;
    }

    public void SetRoiType(String str) {
        this.roiType = str;
    }

    public String GetRoiType() {
        return this.roiType;
    }

    public void SetCurType(String str) {
        this.m_CurType = str;
    }

    public String GetCurType() {
        return this.m_CurType;
    }

    public void ZoomIn() {
        zoom_rate += 0.2;
        // 최대 5배 확대만 가능하게
        if (zoom_rate > (aspect_rate * 5.0))
            zoom_rate = aspect_rate * 5.0;

        invalidate(); // 화면을 다시 그리도록 요청
    }

    public void ZoomOut() {
        zoom_rate -= 0.2;
        // 화면에 꽉차게
        if (aspect_rate > zoom_rate)
            zoom_rate = aspect_rate;

        invalidate(); // 화면을 다시 그리도록 요청
    }

    public void MouseDown(float x, float y) {
        Log.d(TAG, "mouseDown("+x+","+y+")");

        nTouchUpPosX = nTouchDownPosX = (int) (x * zoom_rate);
        nTouchUpPosY = nTouchDownPosY = (int) (y * zoom_rate);

        //241218 성웅 strMode 맵탐색에서 strMenu 이동으로 변경
        // TODO 추후 정리 필요
        if (strMenu.equals("이동") ) {
        //if (strMenu.equals("이동") || strMenu.equals("핀 회전")) {
            m_DnPoint.x = (int) x;
            m_DnPoint.y = (int) y;

            m_ptOld.x = (int) x;
            m_ptOld.y = (int) y;

        }

        //else if(strMode.equals("Roi Find & Move"))
        //{
        //    // 클릭한 곳의 객체를 찾아서 선택된 것으로 설정한다.
        //
        //}
        else {
            // 정수로 변환
            int pt_x = (int) (x);
            //int pt_x = (int) ((x-StartPos_x)/zoom_rate);
            int pt_y = (int) (y);
            //int pt_y = (int) ((y-StartPos_y)/zoom_rate);
            //pt_y = img_map.height - parseInt(pt_y);

            Point point = new Point(pt_x, pt_y);

            Log.d(TAG, "----------------------------------");
            Log.d(TAG, "m_isCapture : " + m_isCapture);
            Log.d(TAG, "m_drawing : " + m_drawing);
            Log.d(TAG, "m_roiviewflag : " + m_roiviewflag);
            Log.d(TAG, "m_CurType : " + m_CurType);
            Log.d(TAG, "m_objSelect : " + m_objSelect);
            Log.d(TAG, "m_RoiCurIndex : " + m_RoiCurIndex);

            // 그리기 상태
            if (m_drawstart == true) {
                if (m_drawing == false) {
                    //console.log(point);
                    if (!CObject_CreateObject(m_CurType, point)) {
                        m_drawstart = false;
                        return;
                    }

                    //console.log(point);
                    m_DnPoint.x = point.x;
                    m_DnPoint.y = point.y;

                    m_ptOld.x = point.x;
                    m_ptOld.y = point.y;

                    m_Select = -1;

                    m_drawing = true;
                    m_isselected = false;

                    m_isCapture = true;

                } else {
                    if (m_CurType.equals("roi_polygon")) {
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
                        Point pt = new Point((int) ((point.x - StartPos_x) / zoom_rate), (int) ((point.y - StartPos_y) / zoom_rate));
                        m_RoiCurObject.AddPoint(pt);
                        m_ptOld = point;
                    }
                    m_DnPoint.x = point.x;
                    m_DnPoint.y = point.y;

                    invalidate(); // 화면을 다시 그리도록 요청
                }
            }
            // 그리기 상태가 아님. -> 눌린 좌표아래의 ROI객체를 찾는다.
            else if (m_drawing == false) {

                Point pt = new Point((int) ((point.x - StartPos_x) / zoom_rate), (int) ((point.y - StartPos_y) / zoom_rate));
                m_objSelect = CObject_FindObject(pt, true);

                Log.d(TAG, "m_objSelect : " + m_objSelect);

                SetCursorType();

                // 현재 좌표가 ROI 객체 내부이면 이동시작
                if ((m_objSelect != -1) && (m_roiviewflag == true)) {
                    m_DnPoint.x = point.x;
                    m_DnPoint.y = point.y;

                    m_isselected = true;

                    // CObject_Draw();


                    m_isCapture = true;
                } else    // ROI 외부이면 DrawTracker 없앰.
                {

                    m_isselected = false;
                    m_Select = -1;
                    m_RoiCurIndex = -1;

                    m_isCapture = false;
                    m_drawstart = m_drawing = false;

                    //map_image_draw();
                    //CObject_Draw();

                }
                invalidate(); // 화면을 다시 그리도록 요청
            }
        }
        m_bIsMouseDown = true;
    }

    public void MouseMove(float x, float y) {
        //Log.d(TAG, "MouseMove( "+x+","+y+" )");

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

                invalidate(); // 화면을 다시 그리도록 요청
            }

        }
        else if (strMenu.equals("핀 회전")){
            if(m_RoiCurIndex != -1) {
                float iconCenterX = (float) ((m_RoiObjects.get(m_RoiCurIndex).m_MBR_center.x * zoom_rate + StartPos_x)); // +30 - 30 상쇄됨
                float iconCenterY = (float) ((m_RoiObjects.get(m_RoiCurIndex).m_MBR_center.y * zoom_rate + StartPos_y)); // +40 - 40 상쇄됨

                float deltaAngle = calculateAngle(iconCenterX, iconCenterY, x, y);

                //            if(deltaAngle > 360)
                //                deltaAngle -= 360;
                //            else if(deltaAngle < -360)
                //                deltaAngle += 360;

                //Log.d(TAG,"Delta Angle:" +  Math.toDegrees(deltaAngle));
                m_RoiObjects.get(m_RoiCurIndex).setAngle(deltaAngle);
//                double anglef = deltaAngle - Math.toRadians(-60.9810);
//              Log.d(TAG,"Raddain:" +  Math.toDegrees(anglef));
//                m_RoiObjects.get(m_RoiCurIndex).setAngle((float)anglef);

                // 화면을 갱신해준다.
                invalidate();
            }
       }
        else {
            // 정수로 변환
            int pt_x = (int) (x);
            //int pt_x = (int) ((x-StartPos_x)/zoom_rate);
            int pt_y = (int) (y);
            //int pt_y = (int) ((y-StartPos_y)/zoom_rate);
            //pt_y = img_map.height - parseInt(pt_y);

            Point point = new Point(pt_x, pt_y);

            //Log.d(TAG, "----------------------------------");
            //Log.d(TAG, "m_isCapture : " + m_isCapture);
            //Log.d(TAG, "m_drawing : " + m_drawing);
            //Log.d(TAG, "m_roiviewflag : " + m_roiviewflag);
            //Log.d(TAG, "m_CurType : " + m_CurType);
            //Log.d(TAG, "m_objSelect : " + m_objSelect);
            //Log.d(TAG, "m_RoiCurIndex : " + m_RoiCurIndex);

            if (m_isCapture == true) // 마우스 누른 상태에서 이동중이면
            {

                if (m_drawing == true) {
                    if (m_CurType.equals("roi_line") || m_CurType.equals("roi_rect")) {
                        CObject_MoveToRect(m_DnPoint, point);
                    }
                } else {
                    if (m_objSelect == 0) {

                        // 여기에서 선택된 객체의 위치 이동을 구현해야 한다.
                        // 20241212 jihyeon 좌표 이동 시 반대 방향으로 이동하는 경우가 있어 CObject_MoveToRect를 CObject_MoveTo로 바꾸어 실행
                        //Log.d(TAG, "m_objSelect == 0")
                        CObject_MoveTo(m_DnPoint, point);


                    } else if (m_RoiCurIndex > -1) {
                        Log.d(TAG, "m_RoiCurIndex :  " + m_RoiCurIndex);
                        Log.d(TAG, "m_RoiObjects.get(m_RoiCurIndex).roi_type : " + m_RoiObjects.get(m_RoiCurIndex).roi_type);
                        if (!m_RoiObjects.get(m_RoiCurIndex).roi_type.equals("roi_point")) {
                            // 선택된 객체의 모양을 변경한다.
                            //  Log.d(TAG, "else if Point : " + point.x + ", " + point.y);
                            // Log.d(TAG, "DPoint: " + m_DnPoint.x + ", " + m_DnPoint.y);
                            if (strMenu.equals("수정")) {
                                CObject_MovePointTo(point, m_DnPoint, m_objSelect);
                            } else {
                                CObject_MoveHandleTo(point, m_DnPoint, m_objSelect);
                            }
                        } else if (!m_RoiObjects.get(m_RoiCurIndex).roi_type.equals("roi_polygon")) {

                            CObject_MoveToRect(m_DnPoint, point);


                            invalidate(); // 화면을 다시 그리도록 요청
                        }
                    }
                    m_DnPoint.x = point.x;
                    m_DnPoint.y = point.y;

                }
            } else {
                if ((m_drawing == false) && m_roiviewflag)            // 마우스 이동시, 객체 찾아서 커서의 모양을 바꾼다.
                {
                    //m_objSelect = CObject_FindObject(point, false);

                    //SetCursorType();

                    ////if(m_objSelect != -1)
                    ////{
                    ////    SetCursorType();
                    ////    //CObject_Draw();   // 선택된 경우 그려주는 색상변경
                    ////}

                } else if (m_drawing == true) {
                    // 그리기 모드
                    //if(m_CurType == 'roi_multiline') // 이동 괴적이 시작과 끝만 있는 것이 아닌 경우
                    //{
                    //    CObject_MoveTo(m_DnPoint, point);
                    //    m_DnPoint.x = point.x;
                    //    m_DnPoint.y = point.y;
                    //}
                    if (m_CurType.equals("roi_polygon")) {
                        CObject_MoveTo(m_DnPoint, point);
                        //CObject_MoveToRect(m_DnPoint, point);
                        m_DnPoint = point;

                        invalidate(); // 화면을 다시 그리도록 요청
                    }

                }
            }
        }
    }

    public void MouseUp(float x, float y) {
        Log.d(TAG, "MouseUp( "+x+","+y+" )");

        nTouchUpPosX = (int) (x * zoom_rate);
        nTouchUpPosY = (int) (y * zoom_rate);
        if (strMode.equals("맵 탐색") && strMenu.equals("이동")) {
            float dx = nTouchUpPosX - nTouchDownPosX;
            float dy = nTouchUpPosY - nTouchDownPosY;
            //Log.d(TAG, "Move("+dx+","+dy+")");

            //MoveMap(dx,dy);
        }
//        else if (strMenu.equals("핀 회전")){
//            // 실시간 각도 계산
//            float angle = calculateAngle(m_DnPoint.x, m_DnPoint.y, x, y);
//            upAngle = angle;
//            totalAngle = 0f;
//            Log.d(TAG,"Mouse UP X: " + x + ", y: " + y);
//            Log.d(TAG, "Current Angle: " + angle);
//            Log.d(TAG, "Curindex: " + m_RoiCurIndex + ", ojbselect: " +m_objSelect);
//            for (int i = 0; i < m_RoiObjects.size(); i++) {
//                if(i == m_RoiCurIndex && m_objSelect != -1){
//                    //m_RoiObjects.get(i).setAngle(upAngle);
//                    Log.d(TAG, "ANgle : "+ upAngle);
//                }
//            }
//        }
        //else if(strMode.equals("Roi Find & Move"))
        //{
        //
        //}
        else {

            // 정수로 변환
            int pt_x = (int) (x);
            int pt_y = (int) (y);
            //pt_y = img_map.height - parseInt(pt_y);

            Point point = new Point(pt_x, pt_y);
            m_bIsMouseDown = false;

//            Log.d(TAG, "----------------------------------");
//            Log.d(TAG, "m_isCapture : " + m_isCapture);
//            Log.d(TAG, "m_drawing : " + m_drawing);
//            Log.d(TAG, "m_roiviewflag : " + m_roiviewflag);
//            Log.d(TAG, "m_CurType : " + m_CurType);
//            Log.d(TAG, "m_objSelect : " + m_objSelect);
//            Log.d(TAG, "m_RoiCurIndex : " + m_RoiCurIndex);
//            Log.d(TAG, "point : (" + point.x + "," + point.y+")");

            if (m_isCapture) // 누른 상태에서 마우스 이동중
            {
                m_isCapture = false;    // 누른 상태로 마우스 이동 해제
                if (m_drawing == true) {
                    if (m_CurType.equals("roi_point")) {
                        m_RoiCurObject.m_endroiflag = true;
                        if (CObject_AddCurObject(point)) {
                            m_drawstart = true;    // 그리기가 끝남.
                            m_drawing = false;
                            m_isselected = true;
                        } else {
                            m_drawing = false;
                            m_isselected = false;
                            m_drawstart = false;
                            m_CurType = "default";
                        }

                    } else if (m_CurType.equals("roi_line")) {
                        m_RoiCurObject.m_endroiflag = true;    // 그리기가 끝났음을 나타냄
                        if (CObject_AddCurObject(point)) {
                            m_drawing = false;
                            m_drawstart = true;
                            m_isselected = true;
                        } else {
                            m_drawing = false;
                            m_isselected = false;
                            m_drawstart = false;

                            m_DnPoint.x = -1;
                            m_DnPoint.y = -1;

                            m_ptOld.x = -1;
                            m_ptOld.y = -1;
                        }
                    } else if (m_CurType.equals("roi_rect")) {
                        m_RoiCurObject.m_endroiflag = true;    // 그리기가 끝났음을 나타냄
                        if (CObject_AddCurObject(point)) {
                            m_drawing = false;
                            m_drawstart = true;
                            m_isselected = true;
                        } else {
                            m_drawing = false;
                            m_isselected = false;
                            m_drawstart = false;

                            m_DnPoint.x = -1;
                            m_DnPoint.y = -1;

                            m_ptOld.x = -1;
                            m_ptOld.y = -1;
                        }
                    } else if (m_CurType.equals("roi_polygon")) {

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
                    invalidate(); // 화면을 다시 그리도록 요청

                    SetCursorType();

                }
                // 객체의 Move 상태
                else if (m_Select != 0) {
                    //SetNormalizeRect();
                    m_isselected = true;

                    //map_image_draw();
                    invalidate();
                    //CObject_Draw();

                    SetCursorType();
                }
            }


        }
    }

    public void MoveMap(float dx, float dy) {

        // 그려주는 onDraw()함수에 버그가 있음.

        StartPos_x += (int) dx;
        StartPos_y += (int) dy;

        int imageW = bitmap.getWidth();
        int imageH = bitmap.getHeight();

        // 화면을 벗어나는 경우의 예외 처리가 필요함
        if (StartPos_x <= (int) ((viewWidth - imageW * zoom_rate) / 2)) {
            StartPos_x = (int) ((viewWidth - imageW * zoom_rate) / 2);
        }

        if (StartPos_y <= (int) ((viewHeight - imageH * zoom_rate) / 2)) {
            StartPos_y = (int) ((viewHeight - imageH * zoom_rate) / 2);
        }


        invalidate(); // 화면을 다시 그리도록 요청
    }


    public void roi_AddPoint() {
        //Log.d(TAG, "roi_AddPoint()");

        strMode = "Add Roi";
        roiType = "roi_point";

        CObject_CurRoiCancelFunc();
        m_CurType = "roi_point";
        m_drawstart = true;

    }

    public void roi_AddLine() {
        strMode = "Roi Add";
        roiType = "roi_line";

        CObject_CurRoiCancelFunc();
        m_CurType = "roi_line";
        m_drawstart = true;

    }

    public void roi_AddRect() {
        strMode = "Roi Add";
        roiType = "roi_rect";

        CObject_CurRoiCancelFunc();
        m_CurType = "roi_rect";
        m_drawstart = true;

    }

    public void roi_AddPolygon() {
        strMode = "Roi Add";
        roiType = "roi_polygon";

        Point pt = new Point(0, 0);
        //CObject_AddCurObject(pt);
        m_CurType = "roi_polygon";
        m_drawstart = true;
    }

    public void roi_AddObject() {
        Log.d(TAG, "roi_AddObject(): ");
        if (m_RoiCurObject == null) return;

        // line 생성
        if ((m_Select == -1) && m_RoiCurObject.roi_type.equals("roi_line")) {
            // 객체 완성
            m_RoiCurIndex = m_RoiObjects.size();
            m_RoiObjects.add(m_RoiCurObject);
            ////Log.d(TAG, "m_RoiObjects.add(m_RoiCurObject): " + m_RoiCurObject.roi_type.equals("roi_line"));

            m_drawstart = true;
            m_drawing = false;
            m_RoiCurObject.m_endroiflag = true;
            m_isCapture = false;
            m_isselected = true;

            invalidate();

            return;
        }

        // rect 생성
        if ((m_Select == -1) && m_RoiCurObject.roi_type.equals("roi_rect")) {
            // 객체 완성
            m_RoiCurIndex = m_RoiObjects.size();
            m_RoiObjects.add(m_RoiCurObject);
            Log.d(TAG, "m_RoiObjects.add(m_RoiCurObject): " + m_RoiCurObject.roi_type.equals("roi_rect"));

            m_drawstart = true;
            m_drawing = false;
            m_RoiCurObject.m_endroiflag = true;
            m_isCapture = false;
            m_isselected = true;

            invalidate();

            return;
        }

        if (m_drawing) {
            if (m_RoiCurObject.m_Points.size() < 3) {
                m_RoiCurObject = null;
                m_RoiCurIndex = -1;
                m_drawstart = true;
                m_drawing = false;
                m_isselected = false;
            } else {
                if (m_Select == -1 && m_RoiCurObject.roi_type.equals("roi_polygon")) {
                    Point pt = new Point(0, 0);
                    m_RoiCurObject.AddEndPoint(pt, false);    // 끝점 제거

                    roomNum++;
                    m_RoiCurObject.m_label = "공간" + roomNum;
                    // 객체 완성
                    m_RoiCurIndex = m_RoiObjects.size();
                    m_RoiObjects.add(m_RoiCurObject);
                    Log.d(TAG, "m_RoiObjects.add(m_RoiCurObject) " +m_RoiCurIndex);

                    m_drawstart = true;
                    m_drawing = false;
                    m_RoiCurObject.m_endroiflag = true;
                    m_isCapture = false;
                    m_isselected = true;

                    invalidate();

                    return;

                }
            }
            invalidate();
            SetCursorType();
        }

    }

    public void roi_RemoveObject() {
        if (!m_drawstart && !m_drawing) {
            if (CObject_DelCurObject()) {
                //roomNum = CountRoomNum(); 공간 이름을 갱신할 것인가?
                invalidate();
            }
        }
    }

    public void roi_FindObject() {

        strMode = "Roi Find";

        m_CurType = "default";

        CObject_CurRoiCancelFunc();

        strMode = "Roi Find & Move";

    }

    public boolean CObject_AddCurObject(Point point) {

        //Log.d(TAG,"CObject_AddCurObject("+point.x+","+point.y+")");
        if (m_RoiCurObject == null) {
            //Log.d(TAG, "m_RoiCurObject == null");
            return false;
        }


        int pt_x = (int) (point.x / zoom_rate);
        int pt_y = (int) (point.y / zoom_rate);

        Point pt = new Point(pt_x, pt_y);

        // 좌상단 좌표를 시작점으로 하고 마지막 점과 같으면
        Point sp = new Point(m_RoiCurObject.m_MBR.left, m_RoiCurObject.m_MBR.top);
        //console.log('CObject_AddCurObject::( ('+pt_x+','+pt_y+') ('+sp.x+','+sp.y+') )');
        if ((sp.x == pt_x) && (sp.y == pt_y)) {
            if (!m_RoiCurObject.roi_type.equals("roi_point")) {
                m_RoiCurObject = null;
                m_RoiCurIndex = -1;
                return false;
            }
        }

        if (m_RoiCurObject.roi_type.equals("roi_point")) {
            m_RoiCurObject.m_MBR.right = pt_x;
            m_RoiCurObject.m_MBR.bottom = pt_y;

            m_RoiCurIndex = m_RoiObjects.size();
            m_RoiObjects.add(m_RoiCurObject);
            //Log.d(TAG, "m_RoiObjects.add(m_RoiCurObject)");
        } else if (m_RoiCurObject.roi_type.equals("roi_polygon")) {
            m_RoiCurObject.AddEndPoint(pt, false);

            m_RoiCurIndex = m_RoiObjects.size();
            m_RoiObjects.add(m_RoiCurObject);
            //Log.d(TAG, "m_RoiObjects.add(m_RoiCurObject)");

        }
        //console.log(m_RoiCurObject);
        return true;
    }

    public boolean CObject_CreateObject(String objType, Point point) {

        m_RoiCurObject = null;
        m_RoiCurIndex = -1;

        //Log.d(TAG, "CObject_CreateObject("+objType+",("+point.x+","+point.y+") )");

        int pt_x = (int) ((point.x - StartPos_x) / zoom_rate);
        int pt_y = (int) ((point.y - StartPos_y) / zoom_rate);

        //Log.d(TAG, "CObject_CreateObject("+objType+",("+pt_x+","+pt_y+") )");

        switch (objType) {
            case "roi_point":
                //console.log('pt('+pt_x+','+pt_y+')');
                m_RoiCurObject = new CDrawObj("roi_point", pt_x, pt_y, pt_x, pt_y);

                //console.log(m_RoiCurObject.m_MBR);

                //m_RoiCurObject.set_width(img_map.width);
                //m_RoiCurObject.set_height(img_map.height);
                ////m_RoiCurObject.setZoom(zoom_rate);
                //m_RoiCurObject.set_orient(map_orient_x, map_orient_y);
                //m_RoiCurObject.set_resolution(map_resolution);
                break;

            case "roi_line":
                //console.log('pt('+pt_x+','+pt_y+')');
                //m_RoiCurObject = new CDrawObj('roi_point', pt_x, pt_y, pt_x, pt_y);
                m_RoiCurObject = new CDrawObj("roi_line", pt_x, pt_y, pt_x, pt_y);
                //console.log(m_RoiCurObject.m_MBR);
                //console.log(m_RoiCurObject);
                //m_RoiCurObject.set_width(img_map.width);
                //m_RoiCurObject.set_height(img_map.height);
                ////m_RoiCurObject.setZoom(zoom_rate);
                //m_RoiCurObject.set_orient(map_orient_x, map_orient_y);
                //m_RoiCurObject.set_resolution(map_resolution);
                m_RoiCurObject.SetFillColor(getRandomColor());


                break;
            case "roi_rect":
                //console.log('pt('+pt_x+','+pt_y+')');
                //m_RoiCurObject = new CDrawObj('roi_point', pt_x, pt_y, pt_x, pt_y);
                m_RoiCurObject = new CDrawObj("roi_rect", pt_x, pt_y, pt_x, pt_y);
                //console.log(m_RoiCurObject.m_MBR);
                //console.log(m_RoiCurObject);
                //m_RoiCurObject.set_width(img_map.width);
                //m_RoiCurObject.set_height(img_map.height);
                ////m_RoiCurObject.setZoom(zoom_rate);
                //m_RoiCurObject.set_orient(map_orient_x, map_orient_y);
                //m_RoiCurObject.set_resolution(map_resolution);
                //m_RoiCurObject.SetFillColor(getRandomColor());


                break;
            case "roi_polygon":
                m_RoiCurObject = new CDrawObj("roi_polygon", pt_x, pt_y, pt_x, pt_y);
                Point pt = new Point(pt_x, pt_y);
                m_RoiCurObject.AddPoint(pt);

                break;

            default:
                break;
        }

        //console.log(m_RoiCurObject);


        return true;

    }

    public boolean CObject_DelCurObject() {
        //console.log('CObject_DelCurObject()');
        //console.log('m_RoiCurIndex: '+m_RoiCurIndex);

        if (m_RoiCurIndex == -1) {
            return false;
        }

        m_RoiObjects.remove(m_RoiCurIndex);
        m_RoiCurObject = null;
        m_RoiCurIndex = -1;

        //map_image_draw();
        invalidate(); // 화면을 다시 그리도록 요청
        //CObject_Draw();

        return true;

    }

    public void CObject_CurRoiCancelFunc() {
        //console.log('CObject_CurRoiCancelFunc()');

        if (m_drawing) {
            m_drawing = false;
            m_drawstart = false;
            m_RoiCurObject = null;
            m_RoiCurIndex = -1;

            m_CurType = "default";

            m_isselected = false;

            //map_image_draw();
            invalidate(); // 화면을 다시 그리도록 요청
            //CObject_Draw();
        } else if (m_drawstart) {
            m_drawing = false;
            m_drawstart = false;

            m_CurType = "default";

            m_isselected = false;
        }

    }

    // 마우스 좌표만큼 선택한 객체을 수정한다.
    public void CObject_MoveHandleTo(Point point, Point point_dn, int nHandle) {
        Log.d(TAG, "CObject_MoveHandleTo( (" + point_dn.x + "," + point_dn.y + "),(" + point.x + "," + point.y + ")," + nHandle + ")");

        if (m_RoiCurObject == null) return;

        Point pt1 = new Point(0, 0);
        pt1.x = (int) ((point.x - StartPos_x) / zoom_rate);
        pt1.y = (int) ((point.y - StartPos_y) / zoom_rate);

        Point pt2 = new Point(0, 0);
        pt2.x = (int) ((point_dn.x - StartPos_x) / zoom_rate);
        pt2.y = (int) ((point_dn.y - StartPos_y) / zoom_rate);

        //console.log('CObject_MoveHandleTo(,,'+nHandle+')');
        // 현재 객체를 수정한다.
        //m_RoiCurObject.MoveHandleTo(point, point_dn, nHandle);
        //Log.d(TAG, "m_RoiCurIndex : " + m_RoiCurIndex);
        if (m_RoiCurIndex > -1) {
            //m_RoiObjects[m_RoiCurIndex] = m_RoiCurObject;
            //m_RoiObjects.get(m_RoiCurIndex).MoveHandleTo(point, point_dn, nHandle);
            if (strMenu.equals("선택")) {
                m_RoiObjects.get(m_RoiCurIndex).MoveTo(pt1, pt2);
            } else {
                //m_RoiObjects.get(m_RoiCurIndex).MoveHandleTo(point, point_dn, nHandle);
                m_RoiObjects.get(m_RoiCurIndex).MoveHandleTo(pt1, pt2, nHandle);
            }
            m_RoiCurObject = m_RoiObjects.get(m_RoiCurIndex);
        } else {
            //m_RoiCurObject.MoveHandleTo(point, point_dn, nHandle);
            m_RoiCurObject.MoveHandleTo(pt1, pt2, nHandle);
        }

        //map_image_draw();
        invalidate(); // 화면을 다시 그리도록 요청
        //CObject_Draw();
    }

    public void CObject_MovePointTo(Point point, Point point_dn, int nHandle) {
        Log.d(TAG, "CObject_MovePointTo( (" + point_dn.x + "," + point_dn.y + "),(" + point.x + "," + point.y + ")," + nHandle + ")");

        if (m_RoiCurObject == null) return;

        Point pt1 = new Point(0, 0);
        pt1.x = (int) ((point.x - StartPos_x) / zoom_rate);
        pt1.y = (int) ((point.y - StartPos_y) / zoom_rate);

        Point pt2 = new Point(0, 0);
        pt2.x = (int) ((point_dn.x - StartPos_x) / zoom_rate);
        pt2.y = (int) ((point_dn.y - StartPos_y) / zoom_rate);

        if (m_RoiCurIndex > -1) {

            if (strMenu.equals("선택")) {
                m_RoiObjects.get(m_RoiCurIndex).MoveTo(pt1, pt2);
            } else if (strMenu.equals("수정")) {
                m_RoiObjects.get(m_RoiCurIndex).MovePointTo(pt1, pt2, nHandle);
            } else {
                //m_RoiObjects.get(m_RoiCurIndex).MoveHandleTo(point, point_dn, nHandle);
                m_RoiObjects.get(m_RoiCurIndex).MoveHandleTo(pt1, pt2, nHandle);
            }
            m_RoiCurObject = m_RoiObjects.get(m_RoiCurIndex);
        } else {
            //m_RoiCurObject.MoveHandleTo(point, point_dn, nHandle);
            m_RoiCurObject.MoveHandleTo(pt1, pt2, nHandle);
        }

        //map_image_draw();
        invalidate(); // 화면을 다시 그리도록 요청
        //CObject_Draw();
    }

    // roi 영역의 시작점(point.dn)과 끝점(point)
    // roi_line을 그릴 때 사용한다.
    public void CObject_MoveToRect(Point point_dn, Point point) {
        //console.log('CObject_MoveToRect( ('+point_dn.x+','+point_dn.y+'),('+point.x+','+point.y+') )');
        if (m_RoiCurObject == null) return;

        //console.log(m_RoiCurObject);

        //var pt_x = parseInt(point.x/zoom_rate)*map_resolution + map_orient_x;
        //var pt_y = (img_map.height-parseInt(point.y/zoom_rate))*map_resolution + map_orient_y;


        // 왼쪽 하단이 원점(0,0)이다.
        Point pt1 = new Point(0, 0);
        pt1.x = (int) ((point_dn.x - StartPos_x) / zoom_rate);
        pt1.y = (int) ((point_dn.y - StartPos_y) / zoom_rate);

        Point pt2 = new Point(0, 0);
        pt2.x = (int) ((point.x - StartPos_x) / zoom_rate);
        pt2.y = (int) ((point.y - StartPos_y) / zoom_rate);

        //console.log(pt1);

        // 이미지 시작 좌표이동

        // 선택된 객체이동
        m_RoiCurObject.MoveToRect(pt1, pt2);
        if (m_RoiCurIndex > -1) {
            //m_RoiObjects.get(m_RoiCurIndex) = m_RoiCurObject;
            m_RoiObjects.set(m_RoiCurIndex, m_RoiCurObject);
            //m_RoiObjects[m_RoiCurIndex].MoveTo(point, point_dn);
            //m_RoiCurObject = m_RoiObjects[m_RoiCurIndex];
        }


        //map_image_draw();
        invalidate(); // 화면을 다시 그리도록 요청
        //CObject_Draw();
    }

    public void CObject_MoveToPoint(Point point_dn, Point point) {
        Log.d(TAG, "CObject_MoveToPoint( (" + point_dn.x + "," + point_dn.y + "),(" + point.x + "," + point.y + ") )");
        if (m_RoiCurObject == null) return;

        //m_objSelect ( -1: 선택 안됨, 0: 이동 )
        if (m_objSelect < 1) return;

        // 왼쪽 하단이 원점(0,0)이다.
        Point pt1 = new Point(0, 0);
        pt1.x = (int) ((point_dn.x - StartPos_x) / zoom_rate);
        pt1.y = (int) ((point_dn.y - StartPos_y) / zoom_rate);

        Point pt2 = new Point(0, 0);
        pt2.x = (int) ((point.x - StartPos_x) / zoom_rate);
        pt2.y = (int) ((point.y - StartPos_y) / zoom_rate);

        //console.log(pt1);

        // 선택된 객체이
        m_RoiCurObject.MovePointTo(pt1, pt2, m_objSelect);
        if (m_RoiCurIndex > -1) {
            m_RoiObjects.set(m_RoiCurIndex, m_RoiCurObject);
        }
        invalidate(); // 화면을 다시 그리도록 요청
    }

    // 마우스 좌표만큼 선택한 객체를 이동한다.
    public void CObject_MoveTo(Point point_dn, Point point) {
        //Log.d(TAG,"CObject_MoveTo( ("+point_dn.x+","+point_dn.y+"),("+point.x+","+point.y+") )");
        if (m_RoiCurObject == null) return;

        //console.log(m_RoiCurObject);

        //var pt_x = parseInt(point.x/zoom_rate)*map_resolution + map_orient_x;
        //var pt_y = (img_map.height-parseInt(point.y/zoom_rate))*map_resolution + map_orient_y;


        Point pt1 = new Point(0, 0);
        pt1.x = (int) ((point.x - StartPos_x) / zoom_rate);
        pt1.y = (int) ((point.y - StartPos_y) / zoom_rate);

        Point pt2 = new Point(0, 0);
        pt2.x = (int) ((point_dn.x - StartPos_x) / zoom_rate);
        pt2.y = (int) ((point_dn.y - StartPos_y) / zoom_rate);


        //console.log(point);
        // 선택된 객체이동

        // 241216 Bitmap밖으로 안벗어나게 변화
        Point origin_MBR_center =  m_RoiCurObject.m_MBR_center;
        int x = m_RoiCurObject.m_MBR_center.x;
        int y = m_RoiCurObject.m_MBR_center.y;
        int dx = pt1.x - pt2.x;
        int dy = pt1.y - pt2.y;
        int pixelColor;

        // 241216 핀이 bitmap 영역 밖으로 나가지 않도록 변경
        if(m_RoiCurObject.roi_type == "roi_polygon") {
            if ((x + dx > 0) && (y + dy > 0) &&
                    (x + dx <= bitmap.getWidth()) && (y + dy <= bitmap.getHeight())) {
                pixelColor = bitmap.getPixel(x + dx, y + dy);
                if ((pixelColor & 0x00FFFFFF) == 0x00969696) {
                    m_RoiCurObject.MoveTo(pt1, pt2);
                }
            }
        }
        else{
            m_RoiCurObject.MoveTo(pt1, pt2);
        }

        if(m_RoiCurIndex > -1)
        {
            //m_RoiObjects.get(m_RoiCurIndex) = m_RoiCurObject;
            m_RoiObjects.set(m_RoiCurIndex, m_RoiCurObject);
            //m_RoiObjects[m_RoiCurIndex].MoveTo(point, point_dn);
            //m_RoiCurObject = m_RoiObjects[m_RoiCurIndex];
        }

        //map_image_draw();
        invalidate();   // 화면갱신
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
    public int CObject_FindObject(Point point, boolean msMove) {
        //Log.d(TAG, "CObject_FindObject( ("+point.x+","+point.y+","+msMove+"),"+msMove+" )");

        // 현재 객체에서 포인트 테스트
        Log.d(TAG, "m_RoiCurIndex : " + m_RoiCurIndex);

        // 원본 이미지에서 검사하는 픽셍의 크기
        int cw = (int) (50 / zoom_rate);
        int ch = (int) (50 / zoom_rate);

        if (m_RoiCurIndex != -1) {
            int fRet = -1;
            if (m_RoiCurObject != null) {
                Log.d(TAG, "m_CurType : " + m_CurType);
                //Log.d(TAG, "m_RoiCurObject.roi_type : " + m_RoiCurObject.roi_type);

                if (m_CurType.equals(m_RoiCurObject.roi_type)) {
                    if (strMenu.equals("수정")) {
                        fRet = m_RoiCurObject.PointInPoint(point, cw, ch);
                    } else {
                        fRet = m_RoiCurObject.PointInHandle(point, cw, ch);
                    }
                }
            } else {
                Log.d(TAG, "m_RoiCurObject == null");
                if (strMenu.equals("수정")) {
                    fRet = m_RoiObjects.get(m_RoiCurIndex).PointInPoint(point, cw, ch);
                } else {
                    fRet = m_RoiObjects.get(m_RoiCurIndex).PointInHandle(point, cw, ch);
                    if (fRet > 0) {
                        fRet = 0;   // "수정"이 아닌 경우에는 이동으로 설정한다.
                    }
                }
            }

            //Log.d(TAG, "fRet : " + fRet);
            //console.log(fRet);
            if ((m_RoiObjects.get(m_RoiCurIndex).roi_type.equals("roi_point")) && (fRet == 1)) {
                // point의 경우에는 커서를 이동으로 설정한다.
                return 0;
            }
            if (fRet != 0) return fRet;

        }

        //console.log('전체 체크');
        // 모든 객체에 대해서 포인트 테스트
        int obj = 0;
        //Log.d(TAG, "m_RoiObjects.size() : " + m_RoiObjects.size() );
        //for(obj=0;obj<1;obj++)    // for test
        for (obj = 0; obj < m_RoiObjects.size(); obj++) {
            ////Log.d(TAG, "m_CurType : " + m_CurType);
            ////Log.d(TAG, "m_RoiObjects.get("+obj+").roi_type : " + m_RoiObjects.get(obj).roi_type);
            if (m_CurType.equals(m_RoiObjects.get(obj).roi_type)) {
                if (m_RoiObjects.get(obj).PointInRect(point)) {
                    if (msMove)    // 마우스를 누른 경우
                    {
                        m_RoiCurObject = m_RoiObjects.get(obj);
                        m_RoiCurIndex = obj;
                    }
                    return 0;
                }
            }

        }

        // 이동중이면
        if (msMove) {
            m_RoiCurObject = null;
            m_RoiCurIndex = -1;
            //console.log('CObject_FindObject()::m_RoiCurIndex: '+m_RoiCurIndex);
        }

        return -1;

    }

    // 참조: 구글검색 "javascript 마우스 커서 변경"
    public boolean SetCursorType() {
        // 그리기 상태
        if (m_drawstart == true) {
            if (m_CurType != "default")    // 사용자 지정 커서 타입
            {
                //::SetCursor(AfxGetApp()->LoadCursor(IDC_CURSOR));
                return true;
            }
        } else {
            //console.log('SetCursorType('+m_objSelect+')');
            //객체 선택 위한 마우스 커서 변경
            switch (m_objSelect) {
                case 0: {
                    //$('.work_main_map_div').css('cursor', 'move');
                    //::SetCursor(AfxGetApp() -> LoadStandardCursor(IDC_SIZEALL));
                    return true;
                }

                case 1:
                case 8: {
                    //$('.work_main_map_div').css('cursor', 'nw-resize');
                    //::SetCursor(AfxGetApp() -> LoadStandardCursor(IDC_SIZENWSE));
                    return true;
                }

                case 2:
                case 7: {
                    //$('.work_main_map_div').css('cursor', 'n-resize');
                    //::SetCursor(AfxGetApp() -> LoadStandardCursor(IDC_SIZENS));
                    return true;
                }

                case 3:
                case 6: {
                    //$('.work_main_map_div').css('cursor', 'ne-resize');
                    //::SetCursor(AfxGetApp() -> LoadStandardCursor(IDC_SIZENESW));
                    return true;
                }

                case 4:
                case 5: {
                    //$('.work_main_map_div').css('cursor', 'ew-resize');
                    //::SetCursor(AfxGetApp() -> LoadStandardCursor(IDC_SIZEWE));
                    return true;
                }
                default: {
                    //$('.work_main_map_div').css('cursor', 'default');
                    return true;
                }
            }
            //if(m_CurType != 'default')	// 커서 타입
            //{
            //    $('.work_main_map_div').css('cursor', 'default');
            //    //::SetCursor(AfxGetApp() -> LoadStandardCursor(IDC_ARROW));
            //    return true;
            //}
        }
        return false;
    }

    // 랜덤 색상 생성 함수
    private int getRandomColor() {
        // RGB 값을 랜덤으로 생성

        int red = (int) (Math.random() * 255);    // 0~255
        int green = (int) (Math.random() * 255);    // 0~255
        int blue = (int) (Math.random() * 255);    // 0~255

        return Color.rgb(red, green, blue); // 랜덤 색상 반환
    }

    private int getRandomColorArgb(int blur) {
        // RGB 값을 랜덤으로 생성
        int red = (int) (Math.random() * 255);    // 0~255
        int green = (int) (Math.random() * 255);    // 0~255
        int blue = (int) (Math.random() * 255);    // 0~255

        return Color.argb(blur, red, green, blue); // 랜덤 색상 반환
    }


    public String getStringName() {
        if (m_RoiCurIndex < 0) {
            return null;
        }

        return m_RoiObjects.get(m_RoiCurIndex).GetString();
    }

    public void setStringName(String strName) {
        if (m_RoiCurIndex < 0) {
            return;
        }

        m_RoiObjects.get(m_RoiCurIndex).SetString(strName);
        if (m_RoiCurObject != null) {
            m_RoiCurObject.SetString(strName);
        }
    }

    public void SetModeAndMenu(String sMode, String sMenu) {
        SetMode(sMode);
        SetMenu(sMenu);
    }

    public void SetLabel(String newLabel) {
        if (m_RoiCurObject == null) {
            return; // 현재 선택된 객체가 없으면 종료
        }

        // 중복된 라벨인지 확인
        if (isLabelDuplicate(newLabel)) {
            Toast.makeText(getContext().getApplicationContext(), "공간명이 중복되지 않도록 설정해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 중복이 아니면 라벨 설정
        m_RoiCurObject.m_label = newLabel;
        invalidate();
    }


    private boolean isLabelDuplicate(String label) {
        for (CDrawObj roiObject : m_RoiObjects) {
            if (roiObject != m_RoiCurObject && label.equals(roiObject.m_label)) {
                return true; // 중복된 라벨 발견
            }
        }
        return false; // 중복 없음
    }

    public void AddPoint_Polygon(Point point) {
        m_RoiCurObject.AddPoint(point);
    }

    public boolean CObject_LoadObject(String objType, Point point) {

        m_RoiCurObject = null;
        m_RoiCurIndex = -1;

        //Log.d(TAG, "CObject_CreateObject("+objType+",("+point.x+","+point.y+") )");

        int pt_x = point.x;
        int pt_y = point.y;

        //Log.d(TAG, "CObject_CreateObject("+objType+",("+pt_x+","+pt_y+") )");

        switch (objType) {
            case "roi_point":
                //console.log('pt('+pt_x+','+pt_y+')');
                m_RoiCurObject = new CDrawObj("roi_point", pt_x, pt_y, pt_x, pt_y);

                break;

            case "roi_line":

                m_RoiCurObject = new CDrawObj("roi_line", pt_x, pt_y, pt_x, pt_y);

                m_RoiCurObject.SetFillColor(getRandomColor());


                break;
            case "roi_rect":
                m_RoiCurObject = new CDrawObj("roi_rect", pt_x, pt_y, pt_x, pt_y);

                break;
            case "roi_polygon":
                m_RoiCurObject = new CDrawObj("roi_polygon", pt_x, pt_y, pt_x, pt_y);
                Point pt = new Point(pt_x, pt_y);
                m_RoiCurObject.AddPoint(pt);

                break;

            default:
                break;
        }

        //console.log(m_RoiCurObject);


        return true;

    }

    public void CObject_LoadRect(Point point1, Point point2) {
        //m_RoiCurIndex = m_RoiObjects.size()-1;

        if (m_RoiCurObject == null) return;


        // 왼쪽 하단이 원점(0,0)이다.
        m_RoiCurObject.m_MBR.right = point1.x;
        m_RoiCurObject.m_MBR.bottom = point1.y;
        m_RoiCurObject.m_MBR.left = point2.x;
        m_RoiCurObject.m_MBR.top = point2.y;
        if (m_RoiCurIndex > -1) {
            m_RoiObjects.set(m_RoiCurIndex, m_RoiCurObject);
        }

        invalidate(); // 화면을 다시 그리도록 요청
    }

    private float calculateAngle(float x1, float y1, float x2, float y2) {
        // atan2로 각도 계산
        float angle =  - (float) (Math.atan2(y2 - y1, x2 - x1));

        return angle;
    }

    private void hideDeleteToggleBar() {
        View deleteToggleBar = findViewById(R.id.delete_toggle_bar);
        if (deleteToggleBar != null) {
            deleteToggleBar.setVisibility(View.INVISIBLE);
        }
    }

}