package com.forsk.ondevice;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.io.FileNotFoundException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

import android.graphics.Region;

public class CDrawObj {

    private static final String TAG = "CDrawObj";

    String roi_type; // roi_point, roi_line, roi_rect

    Rect m_MBR; // 도형 영역 (사각형)
    Rect m_MBR_rotate;  // 도형 회전시 사용
    Point m_MBR_center; // 도형의 무게중심 (로봇위치)
    ArrayList<Point> m_Points;  // polygon의 point 리스트
    boolean m_endroiflag;    // 객체의 생성이 끝났는가?

    String m_label = "test";   // 라벨 기본 이름
    boolean m_labelviewflag = true; // 라벨 display 여보

    boolean m_Closed = false;    // 닫혔는지 아닌지..

    Paint labelpaint;   // 라벨 글자 색상
    Paint Rectpaint;    // roi 색상
    Paint PointPaint;   // Point용 paint
    Paint PointFillPaint;   // point 채우기 paint
    Paint Rectpaint_rotate;    // roi 색상


    private Path path;        // 폴리곤 경로
    Paint fillPaint;   // 채우기 색상
    private Random random;    // 랜덤 생성기

    double m_zoom = 0.0;    // 화대/축소 배율

    private Drawable iconDrawable; // 핀 회전 아이콘

    private Drawable rotateDrawable;    // 회전핀 배경
    float angle = 0.0f; // 회전 값

    boolean isSetTheta = false;

    Paint RectDashpaint;    // 메뉴 버튼 위치
    ArrayList<Point> m_DashPoints;  // 메뉴 버튼 네 모서리  point들
    boolean bDashViewflag = true;   // 메뉴 버튼 영역 display 여부

    public CDrawObj(String strType, int nLeft, int nTop, int nRight, int nBottom) {
        m_MBR = new Rect(nLeft, nTop, nRight, nBottom);
        m_MBR_rotate = new Rect(nLeft, nTop, nRight, nBottom);
        m_MBR_center = new Point((int) ((nLeft + nRight) / 2), (int) ((nTop + nBottom) / 2));

        m_Points = new ArrayList<Point>();
        m_endroiflag = false;

        roi_type = strType;

        labelpaint = new Paint();
        labelpaint.setColor(0x80FF0000); // 반투명 빨간색 (#80FF0000)
        labelpaint.setAntiAlias(true); // 텍스트를 부드럽게 렌더링
        labelpaint.setTextSize(48);
        //canvas.drawText("This is RED text", 50, 100, labelpaint);


        Rectpaint = new Paint();
        Rectpaint.setColor(Color.RED); // 반투명 파란색 (#80FF0000)
        Rectpaint.setStrokeWidth(10);
        if (roi_type.equals("roi_rect")) {
            Rectpaint.setColor(Color.RED);
        }
        Rectpaint.setStyle(Paint.Style.STROKE); // 사각형 내부를 없음

        PointPaint = new Paint();
        PointPaint.setColor(Color.RED);
        PointPaint.setStrokeWidth(5);
        PointPaint.setStyle(Paint.Style.STROKE); // 사각형 내부를 없음


        PointFillPaint = new Paint();
        PointFillPaint.setStyle(Paint.Style.FILL); // 채우기 스타일
        PointFillPaint.setAntiAlias(true);
        PointFillPaint.setColor(Color.argb(255, 255, 255, 255));


        Rectpaint_rotate = new Paint();
        Rectpaint_rotate.setColor(Color.GREEN); // 반투명 파란색 (#80FF0000)
        Rectpaint_rotate.setStrokeWidth(10);
        if (roi_type.equals("roi_rect")) {
            Rectpaint_rotate.setColor(Color.GREEN);
        }
        Rectpaint_rotate.setStyle(Paint.Style.STROKE); // 사각형 내부를 없음


        // 랜덤 생성기 초기화
        random = new Random();

        // 폴리곤 경로 초기화
        path = new Path();

        // 채우기 페인트 설정
        fillPaint = new Paint();

        // 랜덤 색상 생성
        fillPaint.setColor(getRandomColor());
        fillPaint.setStyle(Paint.Style.FILL); // 채우기 스타일
        fillPaint.setAntiAlias(true);

        if (roi_type.equals("roi_polygon")) {
            fillPaint.setColor(getRandomColorArgb(50));
        } else if (roi_type.equals("roi_rect")) {
            fillPaint.setColor(Color.argb(178, 255, 70, 80));
        }

        RectDashpaint = new Paint();
        RectDashpaint.setColor(Color.WHITE); // 반투명 파란색 (#80FF0000)
        RectDashpaint.setStrokeWidth(5);
        RectDashpaint.setStyle(Paint.Style.STROKE); // 사각형 내부를 없음
        RectDashpaint.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));

        m_DashPoints = new ArrayList<Point>();
        int i = 0;
        for (i = 0; i < 4; i++) {
            Point pt = new Point(0, 0);
            m_DashPoints.add(pt);
        }
    }

    // 도형 영역(사각형) normalize
    public void NormalizeRect() {
        int temp = 0;

        if (this.roi_type == "roi_polygon") {
            if (m_Points.size() > 0) {
                m_MBR = new Rect(10000, 10000, 0, 0);
                for (int p = 0; p < m_Points.size(); p++) {
                    if (m_MBR.left > m_Points.get(p).x) m_MBR.left = m_Points.get(p).x;
                    if (m_MBR.right < m_Points.get(p).x) m_MBR.right = m_Points.get(p).x;
                    if (m_MBR.top > m_Points.get(p).y) m_MBR.top = m_Points.get(p).y;
                    if (m_MBR.bottom < m_Points.get(p).y) m_MBR.bottom = m_Points.get(p).y;
                }
            }
        }
        if (m_MBR.left > m_MBR.right) {
            temp = m_MBR.left;
            m_MBR.left = m_MBR.right;
            m_MBR.right = temp;
        }
        if (m_MBR.top > m_MBR.bottom) {
            temp = m_MBR.top;
            m_MBR.top = m_MBR.bottom;
            m_MBR.bottom = temp;
        }
        //this.m_MBR_center.x = (int)((this.m_MBR.left + this.m_MBR.right)/2);
        //this.m_MBR_center.y = (int)((this.m_MBR.bottom + this.m_MBR.top)/2);
    }

    // 랜덤 색상 생성 함수
    private int getRandomColor() {
        // RGB 값을 랜덤으로 생성
        int red = (int) (Math.random() * 255);    // 0~255
        int green = (int) (Math.random() * 255);    // 0~255
        int blue = (int) (Math.random() * 255);    // 0~255

        //int red = random.nextInt(256);    // 0~255
        //int green = random.nextInt(256); // 0~255
        //int blue = random.nextInt(256);  // 0~255

        return Color.rgb(red, green, blue); // 랜덤 색상 반환
    }


    // 241222 jihyeon 핀 회전 모드 아이콘 생성
    public void setIconDrawable(Drawable drawable) {
        this.iconDrawable = drawable;
    }

    // 241222 jihyeon 핀 회전 모드 아이콘 제거
    public void clearIconDrawable() {
        this.iconDrawable = null;
    }

    public Drawable getIconDrawable() {
        return this.iconDrawable;
    }

    // 회전값 설정
    public void setAngle(float angle) {
        this.angle = angle;
    }

    // 회전값 회신
    public float getAngle() {
        return this.angle;
    }

    // 핀 회전 아이콘 이미지 설정
    public void setRotateDrawable(Drawable drawable) {
        this.rotateDrawable = drawable;
    }

    // 241222 jihyeon 핀 회전 모드 아이콘 제거
    public void clearRotateDrawable() {
        this.rotateDrawable = null;
    }

    // 핀회젅 배경이미지 회신
    public Drawable getRotateDrawable() {
        return this.rotateDrawable;
    }

    // 도형 타입 회신
    public String getType() {
        return roi_type;
    }

    // 도형의 영역 설정
    public void SetPosition(Rect rect) {
        // 실수 좌표계로 변환하여 대입한다.

        // polygon의 경우에는 m_Points들도 같이 이동해야한다.

        this.m_MBR.left = rect.left;
        this.m_MBR.top = rect.top;
        this.m_MBR.right = rect.right;
        this.m_MBR.bottom = rect.bottom;

        m_MBR_center = new Point((int) ((rect.left + rect.right) / 2), (int) ((rect.top + rect.bottom) / 2));
    }

    // 도형 영역 회신
    public Rect GetPosition() {
        return this.m_MBR;
    }

    // 확대/축소 배율 설정
    public void SetZoom(double z) {
        this.m_zoom = z;
    }

    // 화대/축소 배율 회신
    public double GetZoom() {
        return this.m_zoom;
    }

    // 라벨명 회신
    public String GetString() {
        return this.m_label;
    }

    // 라벨 설정
    public void SetString(String m_label) {
        this.m_label = m_label;
    }

    // 라벨 색상 설정
    public void SetTextColor(int color) {
        labelpaint.setColor(color);
    }

    // 도형 색상 설정
    public void SetLineColor(int color) {
        Rectpaint.setColor(color);
    }

    // 도형 색상 회신
    public int GetLineColor() {
        return Rectpaint.getColor();
    }

    // 도형메뉴 영역 색상 설정
    public void SetDashLineColor(int color) {
        RectDashpaint.setColor(color);
    }

    // 도형메뉴 영역 색상 회신
    public int GetDashLineColor() {
        return RectDashpaint.getColor();
    }

    // 도형 내부 색상 설정
    public void SetFillColor(int color) {
        fillPaint.setColor(color);
    }

    // 도형 내부 색상 회신
    public int GetFillColor() {
        return fillPaint.getColor();
    }

    // 도형의 무게중심 회신
    public Point GetMBRCenter() {
        return this.m_MBR_center;
    }

    // 라벨 그려주는 함수
    public void DrawLabel(Canvas canvas, Point pt_Start) {
        int x, y;

        switch (this.roi_type) {
            //case "roi_point":
            //case "roi_line":
            case "roi_polygon": {
                //x = this.m_MBR.left + 2;
                //y = this.m_MBR.top + 2;
                x = this.m_MBR_center.x;
                y = this.m_MBR_center.y;

                //canvas.drawText("test", (int) (x * this.m_zoom + pt_Start.x ), (int) (y * this.m_zoom + pt_Start.y), labelpaint);
            }
            break;


        }
    }

    // 도형 그려주는 함수
    // 2024.12.11 lyt94 bDrawPoint 추가
    public void Draw(Canvas canvas, Point pt_Start, Bitmap bitmap, boolean bSelected, boolean bEdit) {
        //Log.d(TAG, "Draw(...)");

        //Log.d(TAG, "roi_type : "+roi_type);
        //Log.d(TAG, "m_MBR : ("+m_MBR.left+","+m_MBR.top+","+m_MBR.right+","+m_MBR.bottom+")");

        switch (this.roi_type) {
            case "roi_point":
                //canvas.drawCircle((int) ((float) m_MBR.left * m_zoom + pt_Start.x), (int) ((float) m_MBR.top * m_zoom + pt_Start.y), 5, Rectpaint);

                // 아이콘을 그려준다.
                break;

            case "roi_line":
                canvas.drawLine((int) (this.m_MBR.left * m_zoom + pt_Start.x), (int) (this.m_MBR.top * m_zoom + pt_Start.y), (int) (this.m_MBR.right * m_zoom + pt_Start.x), (int) (this.m_MBR.bottom * m_zoom + pt_Start.y), Rectpaint);

                if (bSelected || bEdit) {
                    canvas.drawCircle((int) ((float) m_MBR.left * m_zoom + pt_Start.x), (int) ((float) m_MBR.top * m_zoom + pt_Start.y), 20, PointFillPaint);
                    canvas.drawCircle((int) ((float) m_MBR.left * m_zoom + pt_Start.x), (int) ((float) m_MBR.top * m_zoom + pt_Start.y), 20, PointPaint);

                    canvas.drawCircle((int) ((float) m_MBR.right * m_zoom + pt_Start.x), (int) ((float) m_MBR.bottom * m_zoom + pt_Start.y), 20, PointFillPaint);
                    canvas.drawCircle((int) ((float) m_MBR.right * m_zoom + pt_Start.x), (int) ((float) m_MBR.bottom * m_zoom + pt_Start.y), 20, PointPaint);
                }

                if (bDashViewflag && bSelected) {

                    /*
                    //for test

                    int lengthExtension = 5;
                    int x1 = (int) (m_MBR.left * m_zoom + pt_Start.x);
                    int y1 = (int) (m_MBR.top * m_zoom + pt_Start.y);
                    int x2 = (int) (m_MBR.right * m_zoom + pt_Start.x);
                    int y2 = (int) (m_MBR.bottom * m_zoom + pt_Start.y);

                    // 두 점 간의 각도 계산
                    double lineAngle = Math.atan2(y2 - y1, x2 - x1);
                    //Log.d(TAG, "lineAngle : " + lineAngle);

                    // 두 점 길이를 구한다.
                    int dx = x2 - x1;
                    int dy = y2 - y1;
                    double lineDistance = Math.sqrt(dx * dx + dy * dy);
                    Log.d(TAG, "lineDistance : " + lineDistance);

                    // 두 점의 중앙에서 직각으로 50px 점을 각각 구한다.
                    // 메모리 누스를 피하기 위해서 new를 사용하지 않는다.

                    // 중심에서 수직으로 떨어진 두 점 계산
                    float distance = 50;
                    float offsetX1 = ((x1 + x2) / 2.0f) + distance * (float) Math.cos(lineAngle + (float) Math.PI / 2.0f);
                    float offsetY1 = ((y1 + y2) / 2.0f) + distance * (float) Math.sin(lineAngle + (float) Math.PI / 2.0f);

                    float offsetX2 = ((x1 + x2) / 2.0f) + distance * (float) Math.cos(lineAngle - (float) Math.PI / 2.0f);
                    float offsetY2 = ((y1 + y2) / 2.0f) + distance * (float) Math.sin(lineAngle - (float) Math.PI / 2.0f);

//                    canvas.drawCircle(offsetX1, offsetY1, 10, Rectpaint); // 첫 번째 선의 중심점
//                    canvas.drawCircle(offsetX2, offsetY2, 10, Rectpaint); // 두 번째 선의 중심점

                    // 줌심에서 수직으로 떨어진 두 점에서 두 점의 기울기를 이용해서 두 점의 거리보다 distance 만큼 더 긴 곳의 두 점을 각각 구한다.
                    double newlineHalfDistance = (lineDistance / 2.0) + distance;

                    m_DashPoints.get(0).x = (int)( offsetX1 + newlineHalfDistance * (float) Math.cos(lineAngle + (float) Math.PI ) );
                    m_DashPoints.get(0).y = (int)( offsetY1 + newlineHalfDistance * (float) Math.sin(lineAngle + (float) Math.PI ) );
                    canvas.drawCircle(m_DashPoints.get(0).x, m_DashPoints.get(0).y,5,Rectpaint);
                    m_DashPoints.get(1).x = (int)( offsetX2 + newlineHalfDistance * (float) Math.cos(lineAngle + (float) Math.PI ) );
                    m_DashPoints.get(1).y = (int)( offsetY2 + newlineHalfDistance * (float) Math.sin(lineAngle + (float) Math.PI ) );
                    canvas.drawCircle(m_DashPoints.get(1).x, m_DashPoints.get(1).y,10,Rectpaint);
                    m_DashPoints.get(2).x = (int)( offsetX2 + newlineHalfDistance * (float) Math.cos(lineAngle) );
                    m_DashPoints.get(2).y = (int)( offsetY2 + newlineHalfDistance * (float) Math.sin(lineAngle) );
                    canvas.drawCircle(m_DashPoints.get(2).x, m_DashPoints.get(2).y,15,Rectpaint);
                    m_DashPoints.get(3).x = (int)( offsetX1 + newlineHalfDistance * (float) Math.cos(lineAngle) );
                    m_DashPoints.get(3).y = (int)( offsetY1 + newlineHalfDistance * (float) Math.sin(lineAngle) );
                    canvas.drawCircle(m_DashPoints.get(3).x, m_DashPoints.get(3).y,20,Rectpaint);
                     */

                    int i = 0;
                    for (i = 0; i < 4; i++) {
                        canvas.drawLine((int) m_DashPoints.get(i % 4).x, (int) m_DashPoints.get(i % 4).y, (int) m_DashPoints.get((i + 1) % 4).x, (int) m_DashPoints.get((i + 1) % 4).y, RectDashpaint);
                    }

                    /*
                    // 아이콘을 그려주는 위치는 해당 DashPoint에서 다음 DashPoint 의 각도를 계산하여
                    // 45도 회전한 위치의 Point와 해당 DashPoint를 사각형의 중심 좌표에 아이콘의 중심이 위치하게 그려준다.

                    int px,py;  // 45 이동한 위치의 포인트
                    int iconX, iconY;

                    int nDistance = 220;
                    int iconWidth = 100;
                    int iconHeight = 100;
                    double nAngle;

                    Point pt0 = m_DashPoints.get(0);
                    Point pt1 = m_DashPoints.get(1);
                    Point pt2 = m_DashPoints.get(2);
                    Point pt3 = m_DashPoints.get(3);

                    // 두 점 간의 각도 계산
                    nAngle = Math.atan2(pt1.y - pt0.y, pt1.x - pt0.x);

                    // 각도에서 45도 위치로 이동한다.
                    px = (int)( pt0.x - nDistance * (float) Math.cos(nAngle + (float) Math.PI/4.0) );
                    py = (int)( pt0.y - nDistance * (float) Math.sin(nAngle + (float) Math.PI/4.0) );

                    canvas.drawCircle(px, py, 10, Rectpaint); // 첫 번째 선의 중심점

                    canvas.drawLine(pt0.x, pt0.y, px, py, Rectpaint); //

                    iconX = (int)((px + pt0.x)/2.0 - iconWidth/2.0);
                    iconY = (int)((py + pt0.y)/2.0 - iconHeight/2.0);

                    canvas.drawRect(iconX, iconY, iconX+iconWidth, iconY+iconHeight, Rectpaint); // 아이콘이 그렺질 영역


                    /*
                    // 두 점 간의 각도 계산
                    nAngle = Math.atan2(pt2.y - pt1.y, pt2.x - pt1.x);

                    // 각도에서 45도 위치로 이동한다.
                    px = (int)( pt1.x - nDistance * (float) Math.cos(nAngle + (float) Math.PI/4.0) );
                    py = (int)( pt1.y - nDistance * (float) Math.sin(nAngle + (float) Math.PI/4.0) );

                    canvas.drawCircle(px, py, 10, Rectpaint); // 두번째 선의 중심점

                    iconX = (int)((px + pt1.x)/2.0 - iconWidth/2.0);
                    iconY = (int)((py + pt1.y)/2.0 - iconHeight/2.0);

                    canvas.drawRect(iconX, iconY, iconX+iconWidth, iconY+iconHeight, Rectpaint); // 아이콘이 그렺질 영역


                    // 두 점 간의 각도 계산
                    nAngle = Math.atan2(pt3.y - pt2.y, pt3.x - pt2.x);


                    // 각도에서 45도 위치로 이동한다.
                    px = (int)( pt2.x - nDistance * (float) Math.cos(nAngle + (float) Math.PI/4.0) );
                    py = (int)( pt2.y - nDistance * (float) Math.sin(nAngle + (float) Math.PI/4.0) );

                    canvas.drawCircle(px, py, 10, Rectpaint); // 세번번째 선의 중심점

                    iconX = (int)((px + pt2.x)/2.0 - iconWidth/2.0);
                    iconY = (int)((py + pt2.y)/2.0 - iconHeight/2.0);

                    canvas.drawRect(iconX, iconY, iconX+iconWidth, iconY+iconHeight, Rectpaint); // 아이콘이 그렺질 영역

                    // 두 점 간의 각도 계산
                    nAngle = Math.atan2(pt0.y - pt3.y, pt0.x - pt3.x);

                    // 각도에서 45도 위치로 이동한다.
                    px = (int)( pt3.x - nDistance * (float) Math.cos(nAngle + (float) Math.PI/4.0) );
                    py = (int)( pt3.y - nDistance * (float) Math.sin(nAngle + (float) Math.PI/4.0) );

                    canvas.drawCircle(px, py, 10, Rectpaint); // 네번번째 선의 중심점

                    iconX = (int)((px + pt3.x)/2.0 - iconWidth/2.0);
                    iconY = (int)((py + pt3.y)/2.0 - iconHeight/2.0);

                    canvas.drawRect(iconX, iconY, iconX+iconWidth, iconY+iconHeight, Rectpaint); // 아이콘이 그렺질 영역
                    */
                }
                break;

            case "roi_rect":
                //Rectpaint.setStyle(Paint.Style.FILL); // 사각형 내부를 채우기
                canvas.drawRect((int) (this.m_MBR.left * m_zoom + pt_Start.x), (int) (this.m_MBR.top * m_zoom + pt_Start.y), (int) (this.m_MBR.right * m_zoom + pt_Start.x), (int) (this.m_MBR.bottom * m_zoom + pt_Start.y), fillPaint);
                canvas.drawRect((int) (this.m_MBR.left * m_zoom + pt_Start.x), (int) (this.m_MBR.top * m_zoom + pt_Start.y), (int) (this.m_MBR.right * m_zoom + pt_Start.x), (int) (this.m_MBR.bottom * m_zoom + pt_Start.y), Rectpaint);

                //canvas.drawRect((int) (this.m_MBR_rotate.left * m_zoom + pt_Start.x), (int) (this.m_MBR_rotate.top * m_zoom + pt_Start.y), (int) (this.m_MBR_rotate.right * m_zoom + pt_Start.x), (int) (this.m_MBR_rotate.bottom * m_zoom + pt_Start.y), fillPaint_rotate);
                //canvas.drawRect((int) (this.m_MBR_rotate.left * m_zoom + pt_Start.x), (int) (this.m_MBR_rotate.top * m_zoom + pt_Start.y), (int) (this.m_MBR_rotate.right * m_zoom + pt_Start.x), (int) (this.m_MBR_rotate.bottom * m_zoom + pt_Start.y), Rectpaint_rotate);

                if (bSelected || bEdit) {
                    canvas.drawCircle((int) ((float) m_MBR.left * m_zoom + pt_Start.x), (int) ((float) m_MBR.top * m_zoom + pt_Start.y), 5, Rectpaint);
                    canvas.drawCircle((int) ((float) m_MBR.left * m_zoom + pt_Start.x), (int) ((float) m_MBR.bottom * m_zoom + pt_Start.y), 5, Rectpaint);
                    canvas.drawCircle((int) ((float) m_MBR.right * m_zoom + pt_Start.x), (int) ((float) m_MBR.bottom * m_zoom + pt_Start.y), 5, Rectpaint);
                    canvas.drawCircle((int) ((float) m_MBR.right * m_zoom + pt_Start.x), (int) ((float) m_MBR.top * m_zoom + pt_Start.y), 5, Rectpaint);
                }
                if (bDashViewflag && bSelected) {
                    float distance = 50;

                    // normalize rect
                    int nTemp = 0;
                    if (m_MBR.left > m_MBR.right) {
                        nTemp = m_MBR.left;
                        m_MBR.left = m_MBR.right;
                        m_MBR.right = nTemp;
                    }
                    if (m_MBR.top > m_MBR.bottom) {
                        nTemp = m_MBR.bottom;
                        m_MBR.bottom = m_MBR.left;
                        m_MBR.left = nTemp;
                    }
                    /*
                    m_DashPoints.get(0).x = (int) (((float) m_MBR.left * m_zoom + pt_Start.x) - distance);
                    m_DashPoints.get(0).y = (int) (((float) m_MBR.top * m_zoom + pt_Start.y) - distance);
                    m_DashPoints.get(1).x = (int) (((float) m_MBR.right * m_zoom + pt_Start.x) + distance);
                    m_DashPoints.get(1).y = (int) (((float) m_MBR.top * m_zoom + pt_Start.y) - distance);
                    m_DashPoints.get(2).x = (int) (((float) m_MBR.right * m_zoom + pt_Start.x) + distance);
                    m_DashPoints.get(2).y = (int) (((float) m_MBR.bottom * m_zoom + pt_Start.y) + distance);
                    m_DashPoints.get(3).x = (int) (((float) m_MBR.left * m_zoom + pt_Start.x) - distance);
                    m_DashPoints.get(3).y = (int) (((float) m_MBR.bottom * m_zoom + pt_Start.y) + distance);

                     */

                    int i = 0;
                    for (i = 0; i < 4; i++) {
                        canvas.drawLine((int) m_DashPoints.get(i % 4).x, (int) m_DashPoints.get(i % 4).y, (int) m_DashPoints.get((i + 1) % 4).x, (int) m_DashPoints.get((i + 1) % 4).y, RectDashpaint);
                    }

                    /*
                    // 아이콘을 그려주는 위치는 해당 DashPoint에서 다음 DashPoint 의 각도를 계산하여
                    // 45도 회전한 위치의 Point와 해당 DashPoint를 사각형의 중심 좌표에 아이콘의 중심이 위치하게 그려준다.

                    int px,py;  // 45 이동한 위치의 포인트
                    int iconX, iconY;

                    int nDistance = 120;
                    int iconWidth = 100;
                    int iconHeight = 100;
                    double nAngle;

                    Point pt0 = m_DashPoints.get(0);
                    Point pt1 = m_DashPoints.get(1);
                    Point pt2 = m_DashPoints.get(2);
                    Point pt3 = m_DashPoints.get(3);

                    // 두 점 간의 각도 계산
                    nAngle = Math.atan2(pt1.y - pt0.y, pt1.x - pt0.x);

                    // 각도에서 45도 위치로 이동한다.
                    px = (int)( pt0.x - nDistance * (float) Math.cos(nAngle + (float) Math.PI/4.0) );
                    py = (int)( pt0.y - nDistance * (float) Math.sin(nAngle + (float) Math.PI/4.0) );

                    canvas.drawCircle(px, py, 10, Rectpaint); // 첫 번째 선의 중심점

                    iconX = (int)((px + pt0.x)/2.0 - iconWidth/2.0);
                    iconY = (int)((py + pt0.y)/2.0 - iconHeight/2.0);

                    canvas.drawRect(iconX, iconY, iconX+iconWidth, iconY+iconHeight, Rectpaint); // 아이콘이 그렺질 영역

                    // 두 점 간의 각도 계산
                    nAngle = Math.atan2(pt2.y - pt1.y, pt2.x - pt1.x);

                    // 각도에서 45도 위치로 이동한다.
                    px = (int)( pt1.x - nDistance * (float) Math.cos(nAngle + (float) Math.PI/4.0) );
                    py = (int)( pt1.y - nDistance * (float) Math.sin(nAngle + (float) Math.PI/4.0) );

                    canvas.drawCircle(px, py, 10, Rectpaint); // 첫 번째 선의 중심점

                    iconX = (int)((px + pt1.x)/2.0 - iconWidth/2.0);
                    iconY = (int)((py + pt1.y)/2.0 - iconHeight/2.0);

                    canvas.drawRect(iconX, iconY, iconX+iconWidth, iconY+iconHeight, Rectpaint); // 아이콘이 그렺질 영역

                    // 두 점 간의 각도 계산
                    nAngle = Math.atan2(pt3.y - pt2.y, pt3.x - pt2.x);

                    // 각도에서 45도 위치로 이동한다.
                    px = (int)( pt2.x - nDistance * (float) Math.cos(nAngle + (float) Math.PI/4.0) );
                    py = (int)( pt2.y - nDistance * (float) Math.sin(nAngle + (float) Math.PI/4.0) );

                    canvas.drawCircle(px, py, 10, Rectpaint); // 첫 번째 선의 중심점

                    iconX = (int)((px + pt2.x)/2.0 - iconWidth/2.0);
                    iconY = (int)((py + pt2.y)/2.0 - iconHeight/2.0);

                    canvas.drawRect(iconX, iconY, iconX+iconWidth, iconY+iconHeight, Rectpaint); // 아이콘이 그렺질 영역

                    // 두 점 간의 각도 계산
                    nAngle = Math.atan2(pt0.y - pt3.y, pt0.x - pt3.x);

                    // 각도에서 45도 위치로 이동한다.
                    px = (int)( pt3.x - nDistance * (float) Math.cos(nAngle + (float) Math.PI/4.0) );
                    py = (int)( pt3.y - nDistance * (float) Math.sin(nAngle + (float) Math.PI/4.0) );

                    canvas.drawCircle(px, py, 10, Rectpaint); // 첫 번째 선의 중심점

                    iconX = (int)((px + pt3.x)/2.0 - iconWidth/2.0);
                    iconY = (int)((py + pt3.y)/2.0 - iconHeight/2.0);

                    canvas.drawRect(iconX, iconY, iconX+iconWidth, iconY+iconHeight, Rectpaint); // 아이콘이 그렺질 영역
                    */
                }

                break;

            case "roi_polygon":
                //Log.d(TAG, "m_Points.size() : "+(m_Points.size()));


                // Path를 정의하여 폴리곤 만들기
                path.reset();

                // Path의 경계 좌표를 계산
                int left = Integer.MAX_VALUE;
                int top = Integer.MAX_VALUE;
                int right = Integer.MIN_VALUE;
                int bottom = Integer.MIN_VALUE;

                for (int p = 0; p < m_Points.size(); p++) {
                    int x = m_Points.get(p).x;
                    int y = m_Points.get(p).y;

                    // 경계 업데이트
                    if (x < left) left = x;
                    if (y < top) top = y;
                    if (x > right) right = x;
                    if (y > bottom) bottom = y;

                    if (p == 0) {
                        path.moveTo((int) ((float) m_Points.get(p).x * m_zoom + pt_Start.x), (int) ((float) m_Points.get(p).y * m_zoom + pt_Start.y));  // 첫 번째 점
                    } else {
                        path.lineTo((int) ((float) m_Points.get(p).x * m_zoom + pt_Start.x), (int) ((float) m_Points.get(p).y * m_zoom + pt_Start.y));  // 첫 번째 점
                    }
                }
                path.close();          // 시작점으로 닫기
                int[] bounds = {left, top, right, bottom};
                //Log.d(TAG, "fillPaint.getColor() : "+fillPaint.getColor());

                if (m_endroiflag == true) {
                    // 채우기 색상 먼저 그리기
                    if (bEdit) {
                        canvas.drawPath(path, fillPaint);
                    } else {
                        try {
                            drawMatchPoints(bitmap, canvas, fillPaint, bounds, pt_Start);
                        } catch (IllegalStateException | InterruptedException ie) {
                            //Log.d(TAG, "Fail drawMatchPoints: " + ie.getLocalizedMessage());
                        }
                    }
                    //drawMatchPoints(bitmap,canvas,fillPaint, bounds, pt_Start);
                    // 선만 그리기

                } else {
                    // 그리는 중이면
                    // 외곽선 그리기
                    // 20241212 jihyeon
                    // 선택 점 그리기
                    canvas.drawPath(path, Rectpaint);
                    //canvas.drawPath(path, fillPaint);
                    for (int p = 0; p < m_Points.size(); p++) {
                        //Log.d(TAG, "m_Points.get("+p+") : ("+(int)(m_Points.get(p).x)+","+(int)(m_Points.get(p).y)+")");
                        //Log.d(TAG, "m_zoom : ("+m_zoom);
                        //Log.d(TAG, "pt_Start : ("+(int)(pt_Start.x)+","+(int)(pt_Start.y)+")");
                        canvas.drawCircle((int) ((float) m_Points.get(p).x * m_zoom + pt_Start.x), (int) ((float) m_Points.get(p).y * m_zoom + pt_Start.y), 5, Rectpaint);

                        if (p == 0) {
                            canvas.drawLine((int) ((float) m_Points.get(p).x * m_zoom + pt_Start.x), (int) ((float) m_Points.get(p).y * m_zoom + pt_Start.y), (int) ((float) m_Points.get(m_Points.size() - 1).x * m_zoom + pt_Start.x), (int) ((float) m_Points.get(m_Points.size() - 1).y * m_zoom + pt_Start.y), Rectpaint);
                        }
                        if (p > 0) {
                            canvas.drawLine((int) ((float) m_Points.get(p - 1).x * m_zoom + pt_Start.x), (int) ((float) m_Points.get(p - 1).y * m_zoom + pt_Start.y), (int) ((float) m_Points.get(p).x * m_zoom + pt_Start.x), (int) ((float) m_Points.get(p).y * m_zoom + pt_Start.y), Rectpaint);
                        }
                    }
                    //canvas.drawCircle((int)((float)m_MBR_center.x*m_zoom + pt_Start.x), (int)((float)m_MBR_center.y*m_zoom + pt_Start.y), 11, Rectpaint);
                }

                if (bEdit) {
                    for (int p = 0; p < m_Points.size(); p++) {
                        canvas.drawCircle((int) ((float) m_Points.get(p).x * m_zoom + pt_Start.x), (int) ((float) m_Points.get(p).y * m_zoom + pt_Start.y), 5, Rectpaint);
                    }
                }
                if (bDashViewflag && bSelected) {

                    // normalize rect
                    int nTemp = 0;
                    if (m_MBR.left > m_MBR.right) {
                        nTemp = m_MBR.left;
                        m_MBR.left = m_MBR.right;
                        m_MBR.right = nTemp;
                    }
                    if (m_MBR.top > m_MBR.bottom) {
                        nTemp = m_MBR.bottom;
                        m_MBR.bottom = m_MBR.left;
                        m_MBR.left = nTemp;
                    }
                    /*
                    m_DashPoints.get(0).x = (int) (((float) m_MBR.left * m_zoom + pt_Start.x) - distance);
                    m_DashPoints.get(0).y = (int) (((float) m_MBR.top * m_zoom + pt_Start.y) - distance);
                    m_DashPoints.get(1).x = (int) (((float) m_MBR.right * m_zoom + pt_Start.x) + distance);
                    m_DashPoints.get(1).y = (int) (((float) m_MBR.top * m_zoom + pt_Start.y) - distance);
                    m_DashPoints.get(2).x = (int) (((float) m_MBR.right * m_zoom + pt_Start.x) + distance);
                    m_DashPoints.get(2).y = (int) (((float) m_MBR.bottom * m_zoom + pt_Start.y) + distance);
                    m_DashPoints.get(3).x = (int) (((float) m_MBR.left * m_zoom + pt_Start.x) - distance);
                    m_DashPoints.get(3).y = (int) (((float) m_MBR.bottom * m_zoom + pt_Start.y) + distance);

                     */

                    int i = 0;
                    for (i = 0; i < 4; i++) {
                        canvas.drawLine((int) m_DashPoints.get(i % 4).x, (int) m_DashPoints.get(i % 4).y, (int) m_DashPoints.get((i + 1) % 4).x, (int) m_DashPoints.get((i + 1) % 4).y, RectDashpaint);
                    }

                }

                break;
        }
        //if (m_endroiflag == true && m_labelviewflag)
        if (m_labelviewflag) {
            DrawLabel(canvas, pt_Start);
        }

        if(roi_type.equals("roi_point")) {
            if (iconDrawable != null) {
                // 아이콘 크기 90 * 90
                float iconWidth = 90; // 아이콘의 너비
                float iconHeight = 90; // 아이콘의 높이

                // 중심점을 기준으로 아이콘의 Bounds 설정
                int iconLeft = (int) ((m_MBR_center.x * m_zoom + pt_Start.x) - (iconWidth / 2));
                int iconTop = (int) ((m_MBR_center.y * m_zoom + pt_Start.y) - iconHeight );
                int iconRight = (int) (iconLeft + iconWidth);
                int iconBottom = (int) (iconTop + iconHeight);

                iconDrawable.setBounds(iconLeft, iconTop, iconRight, iconBottom);

                // 그리기
                iconDrawable.draw(canvas);

            }
        }
        else if(roi_type.equals("roi_polygon")) {
            if (iconDrawable != null) {
                // 아이콘 크기 86.89 * 86.89
                float iconWidth = 86.89f * 2; // 아이콘의 너비
                float iconHeight = 86.89f * 2; // 아이콘의 높이

                // 중심점을 기준으로 아이콘의 Bounds 설정
                int iconLeft = (int) ((m_MBR_center.x * m_zoom + pt_Start.x) - (iconWidth / 2));
                int iconTop = (int) ((m_MBR_center.y * m_zoom + pt_Start.y) - (iconHeight / 2));
                int iconRight = (int) (iconLeft + iconWidth);
                int iconBottom = (int) (iconTop + iconHeight);

                int iconCenterX = (iconLeft + iconRight) / 2;
                int iconCenterY = (iconTop + iconBottom) / 2;

                // 사각형 dashpoint
                Paint dashedPaint = new Paint();
                dashedPaint.setStyle(Paint.Style.STROKE);
                dashedPaint.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));
                dashedPaint.setColor(Color.WHITE);
                dashedPaint.setStrokeWidth(5);
                Rect rect = new Rect(iconLeft, iconTop, iconRight, iconBottom);

                if (rotateDrawable != null) {
                    rotateDrawable.setBounds(iconRight - 26, iconBottom - 26, iconRight + 26, iconBottom + 26);
                }
                iconDrawable.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                // Canvas 회전 및 아이콘 그리기
                canvas.save(); // 현재 Canvas 상태 저장
                // 반시계 + -> 시계 + 로 반전
                // 0° ~ 360°로 변환 (음수 각도 처리)
                float degrees = -(float) Math.toDegrees(angle);

//            if (degrees < 0) {
//                degrees += 360;
//            }
                //Log.d(TAG,"degrees: " + degrees);

                canvas.rotate(degrees, iconCenterX, iconCenterY); // 아이콘 중심 기준 회전

                // 회전된 상태에서 그리기
                iconDrawable.draw(canvas);

                if (rotateDrawable != null) {
                    canvas.drawRect(rect, dashedPaint);
                    rotateDrawable.draw(canvas);

                }

                canvas.restore(); // Canvas 상태 복원

            }
        }

    }

    // 도형 메뉴 영역을 생성하는 함수
    public void MakeTracker(int start_x, int start_y) {
        //Log.d(TAG, "MakeTracker(" + start_x + ", " + start_y + ")");

        //Log.d(TAG, "m_MBR : (" + m_MBR.left + "," + m_MBR.top + "," + m_MBR.right + "," + m_MBR.bottom + ")");

        float distance = 50;
        int nTemp = 0;
        switch (this.roi_type) {
            case "roi_point":

                m_DashPoints.get(0).x = (int) (((float) m_MBR.left * m_zoom + start_x) - distance);
                m_DashPoints.get(0).y = (int) (((float) m_MBR.top * m_zoom + start_y) - distance);
                m_DashPoints.get(1).x = (int) (((float) m_MBR.left * m_zoom + start_x) + distance);
                m_DashPoints.get(1).y = (int) (((float) m_MBR.top * m_zoom + start_y) - distance);
                m_DashPoints.get(2).x = (int) (((float) m_MBR.left * m_zoom + start_x) + distance);
                m_DashPoints.get(2).y = (int) (((float) m_MBR.top * m_zoom + start_y) + distance);
                m_DashPoints.get(3).x = (int) (((float) m_MBR.left * m_zoom + start_x) - distance);
                m_DashPoints.get(3).y = (int) (((float) m_MBR.top * m_zoom + start_y) + distance);

                break;

            case "roi_line":

                int lengthExtension = 5;
                int x1 = (int) (m_MBR.left * m_zoom + start_x);
                int y1 = (int) (m_MBR.top * m_zoom + start_y);
                int x2 = (int) (m_MBR.right * m_zoom + start_x);
                int y2 = (int) (m_MBR.bottom * m_zoom + start_y);

                // 두 점 간의 각도 계산
                double lineAngle = Math.atan2(y2 - y1, x2 - x1);
                //Log.d(TAG, "lineAngle : " + lineAngle);

                // 두 점 길이를 구한다.
                int dx = x2 - x1;
                int dy = y2 - y1;
                double lineDistance = Math.sqrt(dx * dx + dy * dy);
                //Log.d(TAG, "lineDistance : " + lineDistance);

                // 두 점의 중앙에서 직각으로 50px 점을 각각 구한다.
                // 메모리 누스를 피하기 위해서 new를 사용하지 않는다.

                // 중심에서 수직으로 떨어진 두 점 계산

                float offsetX1 = ((x1 + x2) / 2.0f) + distance * (float) Math.cos(lineAngle + (float) Math.PI / 2.0f);
                float offsetY1 = ((y1 + y2) / 2.0f) + distance * (float) Math.sin(lineAngle + (float) Math.PI / 2.0f);

                float offsetX2 = ((x1 + x2) / 2.0f) + distance * (float) Math.cos(lineAngle - (float) Math.PI / 2.0f);
                float offsetY2 = ((y1 + y2) / 2.0f) + distance * (float) Math.sin(lineAngle - (float) Math.PI / 2.0f);

                //canvas.drawCircle(offsetX1, offsetY1, 10, Rectpaint); // 첫 번째 선의 중심점
                //canvas.drawCircle(offsetX2, offsetY2, 10, Rectpaint); // 두 번째 선의 중심점

                // 줌심에서 수직으로 떨어진 두 점에서 두 점의 기울기를 이용해서 두 점의 거리보다 distance 만큼 더 긴 곳의 두 점을 각각 구한다.
                double newlineHalfDistance = (lineDistance / 2.0) + distance;
                m_DashPoints.get(0).x = (int)( offsetX1 + newlineHalfDistance * (float) Math.cos(lineAngle + (float) Math.PI ) );
                m_DashPoints.get(0).y = (int)( offsetY1 + newlineHalfDistance * (float) Math.sin(lineAngle + (float) Math.PI ) );
                //canvas.drawCircle(m_DashPoints.get(0).x, m_DashPoints.get(0).y,5,Rectpaint);
                m_DashPoints.get(1).x = (int)( offsetX2 + newlineHalfDistance * (float) Math.cos(lineAngle + (float) Math.PI ) );
                m_DashPoints.get(1).y = (int)( offsetY2 + newlineHalfDistance * (float) Math.sin(lineAngle + (float) Math.PI ) );
                //canvas.drawCircle(m_DashPoints.get(1).x, m_DashPoints.get(1).y,10,Rectpaint);
                m_DashPoints.get(2).x = (int)( offsetX2 + newlineHalfDistance * (float) Math.cos(lineAngle) );
                m_DashPoints.get(2).y = (int)( offsetY2 + newlineHalfDistance * (float) Math.sin(lineAngle) );
                //canvas.drawCircle(m_DashPoints.get(2).x, m_DashPoints.get(2).y,15,Rectpaint);
                m_DashPoints.get(3).x = (int)( offsetX1 + newlineHalfDistance * (float) Math.cos(lineAngle) );
                m_DashPoints.get(3).y = (int)( offsetY1 + newlineHalfDistance * (float) Math.sin(lineAngle) );
                //canvas.drawCircle(m_DashPoints.get(3).x, m_DashPoints.get(3).y,20,Rectpaint);

                break;

            case "roi_rect":

                // normalize rect
                if (m_MBR.left > m_MBR.right) {
                    nTemp = m_MBR.left;
                    m_MBR.left = m_MBR.right;
                    m_MBR.right = nTemp;
                }
                if (m_MBR.top > m_MBR.bottom) {
                    nTemp = m_MBR.bottom;
                    m_MBR.bottom = m_MBR.left;
                    m_MBR.left = nTemp;
                }

                m_DashPoints.get(0).x = (int) (((float) m_MBR.left * m_zoom + start_x) - distance);
                m_DashPoints.get(0).y = (int) (((float) m_MBR.top * m_zoom + start_y) - distance);
                m_DashPoints.get(1).x = (int) (((float) m_MBR.right * m_zoom + start_x) + distance);
                m_DashPoints.get(1).y = (int) (((float) m_MBR.top * m_zoom + start_y) - distance);
                m_DashPoints.get(2).x = (int) (((float) m_MBR.right * m_zoom + start_x) + distance);
                m_DashPoints.get(2).y = (int) (((float) m_MBR.bottom * m_zoom + start_y) + distance);
                m_DashPoints.get(3).x = (int) (((float) m_MBR.left * m_zoom + start_x) - distance);
                m_DashPoints.get(3).y = (int) (((float) m_MBR.bottom * m_zoom + start_y) + distance);

                break;

            case "roi_polygon":

                // normalize rect
                if (m_MBR.left > m_MBR.right) {
                    nTemp = m_MBR.left;
                    m_MBR.left = m_MBR.right;
                    m_MBR.right = nTemp;
                }
                if (m_MBR.top > m_MBR.bottom) {
                    nTemp = m_MBR.bottom;
                    m_MBR.bottom = m_MBR.left;
                    m_MBR.left = nTemp;
                }

                m_DashPoints.get(0).x = (int) (((float) m_MBR.left * m_zoom + start_x) - distance);
                m_DashPoints.get(0).y = (int) (((float) m_MBR.top * m_zoom + start_y) - distance);
                m_DashPoints.get(1).x = (int) (((float) m_MBR.right * m_zoom + start_x) + distance);
                m_DashPoints.get(1).y = (int) (((float) m_MBR.top * m_zoom + start_y) - distance);
                m_DashPoints.get(2).x = (int) (((float) m_MBR.right * m_zoom + start_x) + distance);
                m_DashPoints.get(2).y = (int) (((float) m_MBR.bottom * m_zoom + start_y) + distance);
                m_DashPoints.get(3).x = (int) (((float) m_MBR.left * m_zoom + start_x) - distance);
                m_DashPoints.get(3).y = (int) (((float) m_MBR.bottom * m_zoom + start_y) + distance);

                break;
        }
    }

    // 도형의 Point 추가 함수
    public void AddPoint(Point point) {
        //Log.d(TAG, "AddPoint("+point.x+","+point.y+")");
        m_Points.add(point);
        //Log.d(TAG, "m_MBR : ("+m_MBR.left+","+m_MBR.top+","+m_MBR.right+","+m_MBR.bottom+")");
        // MBR 구하기
        m_MBR = new Rect(10000, 10000, 0, 0);
        for (int p = 0; p < m_Points.size(); p++) {
            if (m_MBR.left > m_Points.get(p).x) m_MBR.left = m_Points.get(p).x;
            if (m_MBR.right < m_Points.get(p).x) m_MBR.right = m_Points.get(p).x;
            if (m_MBR.top > m_Points.get(p).y) m_MBR.top = m_Points.get(p).y;
            if (m_MBR.bottom < m_Points.get(p).y) m_MBR.bottom = m_Points.get(p).y;
        }
        //Log.d(TAG, "m_MBR : ("+m_MBR.left+","+m_MBR.top+","+m_MBR.right+","+m_MBR.bottom+")");
        m_MBR_center.x = (int) ((m_MBR.left + m_MBR.right) / 2);
        m_MBR_center.y = (int) ((m_MBR.top + m_MBR.bottom) / 2);
    }

    // 도형의 끝점을 추가하고, 도형 그리기를 종료한다.
    public void AddEndPoint(Point point, boolean flag) {
        //Log.d(TAG, "AddEndPoint(...)");
        // 마지막 점은 제외하고 시작 포인트와 끝 포인트를 같게 함
        if (flag) {
            m_Points.add(point);
        }

        // MBR 구하기
        m_MBR = new Rect(10000, 10000, 0, 0);
        for (int p = 0; p < m_Points.size(); p++) {
            if (m_MBR.left > m_Points.get(p).x) m_MBR.left = m_Points.get(p).x;
            if (m_MBR.right < m_Points.get(p).x) m_MBR.right = m_Points.get(p).x;
            if (m_MBR.top > m_Points.get(p).y) m_MBR.top = m_Points.get(p).y;
            if (m_MBR.bottom < m_Points.get(p).y) m_MBR.bottom = m_Points.get(p).y;
        }
        //Log.d(TAG, "m_MBR : ("+m_MBR.left+","+m_MBR.top+","+m_MBR.right+","+m_MBR.bottom+")");
        m_endroiflag = true;
        m_MBR_center.x = (int) ((m_MBR.left + m_MBR.right) / 2);
        m_MBR_center.y = (int) ((m_MBR.top + m_MBR.bottom) / 2);
    }


    // 도형의 point 갯수를 회신
    public int GetPointCount() {
        int point_count = 0;
        switch (this.roi_type) {
            case "roi_point":
                point_count = 1;
                break;

            case "roi_line":
                point_count = 2;
                break;

            case "roi_rect":
                point_count = 4;
                break;

            case "roi_polygon":
                point_count = m_Points.size();
                break;
        }
        return point_count;
    }

    // 해당 핸들의 Point를 회신하는 함수
    public Point GetPoint(int nHandle) {
        int x = -1;
        int y = -1;
        switch (this.roi_type) {
            case "roi_line":
                switch (nHandle) {
                    case 1:        // 왼쪽 위
                    {
                        x = m_MBR.left;
                        y = m_MBR.top;
                        break;
                    }
                    case 2:        // 왼쪽 위
                    {
                        x = m_MBR.right;
                        y = m_MBR.bottom;
                        break;
                    }

                }
                break;

            case "roi_point":
                switch (nHandle) {
                    case 1:        // 왼쪽 위
                    {
                        x = m_MBR.left;
                        y = m_MBR.top;
                        break;
                    }
                }
                break;
            case "roi_polygon":
                if (nHandle < m_Points.size()) {
                    nHandle = this.m_Points.size();
                }
                break;

        }
        Point pt = new Point(x, y);
        return pt;

    }

    // 마우스를 올리면 변경 가능한 핸들 수
    // 점, 선은 2 나머지는 8을 리턴
    public int GetHandleCount() {
        int handle_count = 0;
        switch (this.roi_type) {
            case "roi_point":
                handle_count = 2;
                break;

            case "roi_line":
                handle_count = 2;
                break;

            case "roi_rect":
                handle_count = 8;
                break;

            case "roi_polygon":
                handle_count = this.m_Points.size();
                break;
        }
        return handle_count;
    }

    // 선택된 핸들의 Point 좌표를 회신한다.
    public Point GetHandle(int nHandle) {

        switch (this.roi_type) {
            case "roi_line":
                if (nHandle == 2) {
                    nHandle = 8;
                }

            case "roi_point":
                if (nHandle == 1) {
                    nHandle = 1;
                }
                break;
            case "roi_polygon":
                if (nHandle == 1) {
                    nHandle = this.m_Points.size();
                }
                break;
            default:
                nHandle = 8;
                break;

        }


        int x, y, xcenter, ycenter;

        x = y = -1;        // 선택된 Tracker의 좌표 초기화

        if (this.roi_type.equals("roi_polygon")) {
            if (nHandle < this.m_Points.size()) {
                x = this.m_Points.get(nHandle).x;
                y = this.m_Points.get(nHandle).y;
            }
        } else {
            //데이터 크기의 중심 좌표 설정(Tracker 2, 4, 5, 7 해당하는 위치 나타냄)
            Rect temp = m_MBR;
            xcenter = temp.left + temp.width() / 2;
            ycenter = temp.top + temp.height() / 2;

            switch (nHandle) {
                case 1:        // 왼쪽 위
                {
                    x = temp.left;
                    y = temp.top;
                    break;
                }
                case 2:        // 위쪽 중간
                {
                    x = xcenter;
                    y = temp.top;
                    break;
                }
                case 3:        // 오른쪽 위
                {
                    x = temp.right;
                    y = temp.top;
                    break;
                }
                case 4:        // 왼쪽 중간
                {
                    x = temp.left;
                    y = ycenter;
                    break;
                }
                case 5:        // 오른쪽 중간
                {
                    x = temp.right;
                    y = ycenter;
                    break;
                }
                case 6:        // 왼쪽 아래
                {
                    x = temp.left;
                    y = temp.bottom;
                    break;
                }
                case 7:        // 아래쪽 중간
                {
                    x = xcenter;
                    y = temp.bottom;
                    break;
                }
                case 8:        // 오른쪽 아래
                {
                    x = temp.right;
                    y = temp.bottom;
                    break;
                }
                case 9:        // 오른쪽 아래
                {
                    x = xcenter;
                    y = ycenter;
                    break;
                }
            }
        }

        Point pt = new Point(x, y);
        return pt;

    }

    // 선택된 좌표 point가 해당 도형내에 있는지 회신한다.
    public boolean PointInRect(Point point) {
        //Log.d(TAG, "PointInRect("+point.x+","+point.y+")");

        //Log.d(TAG, "roi_type : "+roi_type);
        //Log.d(TAG, "m_MBR : ("+m_MBR.left+","+m_MBR.top+","+m_MBR.right+","+m_MBR.bottom+")");

        //double zoom = this.m_zoom;

        switch (this.roi_type) {
            case "roi_line": // Line ROI
            {
                int sx = (int) ((float) m_MBR.left);
                int sy = (int) ((float) m_MBR.top);
                int ex = (int) ((float) m_MBR.right);
                int ey = (int) ((float) m_MBR.bottom);

                if (sx < ex) {
                    if (point.x > ex || point.x < sx) return false;
                } else {
                    if (point.x < ex || point.x > sx) return false;
                }

                if (sy < ey) {
                    if (point.y > ey || point.y < sy) return false;
                } else {
                    if (point.y < ey || point.y > sy) return false;
                }

                double a = (double) (ey - sy);
                double b = (double) (sx - ex);

                if ((a * a + b * b) != 0.0) {
                    double c = -a * (double) sx - (double) sy * b;
                    double d = Math.abs(a * (double) point.x + b * (double) point.y + c) / Math.sqrt(a * a + b * b);
                    if (d <= 20.0) return true;
                }

                break;
            }
            case "roi_point": // Point
            {
                //Log.d(TAG, "point : ("+point.x+","+point.y+")");
                //Log.d(TAG, "this.m_MBR.left - 50 : "+(this.m_MBR.left - 50));
                //Log.d(TAG, "this.m_MBR.left + 50 : "+(this.m_MBR.left + 50));
                //Log.d(TAG, "((this.m_MBR.left - 50)<point.x) : "+((this.m_MBR.left - 50)<point.x));
                if (((this.m_MBR.left - 50) < point.x) && ((this.m_MBR.left + 50) > point.x)) {
                    //Log.d(TAG, "this.m_MBR.top - 50 : "+(this.m_MBR.top - 50));
                    //Log.d(TAG, "this.m_MBR.top + 50 : "+(this.m_MBR.top + 50));
                    if (((this.m_MBR.top - 50) < point.y) && ((this.m_MBR.top + 50) > point.y)) {
                        return true;
                    }
                }
                break;
            }
            case "roi_rect": // Rectagle
            case "roi_polygon": // Polygon
            {
                if (((this.m_MBR.left) < point.x) && ((this.m_MBR.right) > point.x)) {
                    if (((this.m_MBR.top) < point.y) && ((this.m_MBR.bottom) > point.y)) {
                        //Log.d(TAG,"Y!" + this.m_MBR.top + " " + point.y + " " + this.m_MBR.bottom);
                        //Log.d(TAG,"X!" + this.m_MBR.left + " " + point.x + " " + this.m_MBR.right);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // 해당 Point의 어떤 handle를 갖는지 회신한다.
    public int PointInHandle(Point point, int cw, int ch)        //Tracker를 찾기위한 함수
    {
        //Log.d(TAG, "PointInHandle( ("+point.x+","+point.y+"),"+cw+","+ch+")");
        //Log.d(TAG, "m_MBR : ("+m_MBR.left+","+m_MBR.top+","+m_MBR.right+","+m_MBR.bottom+")");
        //Log.d(TAG, "GetHandleCount() : " + GetHandleCount() );
        Point pt = new Point(point.x, point.y);

        int nHandle = 1;
        for (nHandle = 1; nHandle <= this.GetHandleCount(); nHandle++) {
            Point pt_handle = this.GetHandle(nHandle);    // 핸들의 좌표를 얻어옴.
            //Log.d(TAG, "pt_handle : ("+pt_handle.x+","+pt_handle.y+")");

            //Tracker에 해당하는 사각형 생성
            Rect rect = new Rect(pt_handle.x - cw, pt_handle.y - ch, pt_handle.x + cw, pt_handle.y + ch);

            //if (rect.PtInRect(pt)) // 동작하지 않는다?
            if (((pt_handle.x - cw) <= point.x) && (point.x <= (pt_handle.x + cw)) && ((pt_handle.y - ch) <= point.y) && (point.y <= (pt_handle.y + ch))) {
                //console.log(this.roi_type);
                if ((this.roi_type.equals("roi_line")) && nHandle == 2) return 8;
                //if (m_Type == NIP_ELLIPSE_ROI && nHandle == 1)		return  8;
                return nHandle;
            }
        }
        return 0;
    }

    // 해당 포인트 핸들을 회신한다.
    // cw: 가로 오차범위
    // ch: 세로 오차범위
    public int PointInPoint(Point point, int cw, int ch)        //Tracker를 찾기위한 함수
    {
        //Log.d(TAG, "PointInPoint( (" + point.x + "," + point.y + ") " + cw + ", " + ch + ")");

        Point pt = new Point(point.x, point.y);
        switch (roi_type) {
            case "roi_line":
                if (((m_MBR.left - cw) <= pt.x) && (pt.x <= (m_MBR.left + cw)) && ((m_MBR.top - ch) <= pt.y) && (pt.y <= (m_MBR.top + ch))) {
                    return 1;   // left, top
                } else if (((m_MBR.right - cw) <= pt.x) && (pt.x <= (m_MBR.right + cw)) && ((m_MBR.bottom - ch) <= pt.y) && (pt.y <= (m_MBR.bottom + ch))) {
                    return 8;   // right, bottom
                }
                break;
            case "roi_rect":
                if (((m_MBR.left - cw) <= pt.x) && (pt.x <= (m_MBR.left + cw)) && ((m_MBR.top - ch) <= pt.y) && (pt.y <= (m_MBR.top + ch))) {
                    return 1;   // left, top
                } else if (((m_MBR.right - cw) <= pt.x) && (pt.x <= (m_MBR.right + cw)) && ((m_MBR.top - ch) <= pt.y) && (pt.y <= (m_MBR.top + ch))) {
                    return 3;   // right, top
                } else if (((m_MBR.left - cw) <= pt.x) && (pt.x <= (m_MBR.left + cw)) && ((m_MBR.bottom - ch) <= pt.y) && (pt.y <= (m_MBR.bottom + ch))) {
                    return 6;   // left, bottom
                } else if (((m_MBR.right - cw) <= pt.x) && (pt.x <= (m_MBR.right + cw)) && ((m_MBR.bottom - ch) <= pt.y) && (pt.y <= (m_MBR.bottom + ch))) {
                    return 8;   // right, bottom
                }
                break;
            case "roi_polygon":
                int i = 0;
                for (i = 0; i < m_Points.size(); i++) {
                    if (((m_Points.get(i).x - cw) <= pt.x) && (pt.x <= (m_Points.get(i).x + cw)) && ((m_Points.get(i).y - ch) <= pt.y) && (pt.y <= (m_Points.get(i).y + ch))) {
                        //Log.d(TAG, "m_Points.get(" + i + ") : (" + m_Points.get(i).x + "," + m_Points.get(i).y + ")");
                        return i + 1;   // 0인 경우는 이동이므로 1부터 시작한다.
                    }
                }
                break;
        }
        return -1;
    }

    // 해당 좌표 차이 만큰 도형과 무게중심을 이동한다.
    public void MoveToRect(Point pt1, Point pt2) {
        //console.log('MoveToRect( ('+pt1.x+','+pt1.y+'), ('+pt2.x+','+pt2.y+') )');

        //this.m_MBR.NormalizeRect();

        int dx = pt1.x - pt2.x;
        int dy = pt1.y - pt2.y;
        // m_Points도 이동한다.
        if (roi_type.equals("roi_polygon")) {

            for (int p = 0; p < m_Points.size(); p++) {
                Point point = new Point(0, 0);

                point.x = m_Points.get(p).x - dx;
                point.y = m_Points.get(p).y - dy;

                m_Points.set(p, point);
            }
        }

        this.m_MBR.left = pt1.x;
        this.m_MBR.top = pt1.y;
        this.m_MBR.right = pt2.x;
        this.m_MBR.bottom = pt2.y;

        this.m_MBR_center.x = (int)((this.m_MBR.left + this.m_MBR.right)/2);
        this.m_MBR_center.y = (int)((this.m_MBR.top + this.m_MBR.bottom)/2);

    }

    // 해당 좌표만큼 도형을 이동한다.
    public void MoveTo(Point pt1, Point pt2) {
        //Log.d(TAG, "MoveTo( ("+pt1.x+","+pt1.y+"),("+pt2.x+","+pt2.y+") )");
        // pt1 : mouse down
        // pt2 : mouse move

        // 실수 좌표계로 변환하여 대입한다.
        //var zoom = this.m_zoom;

        int dx = pt1.x - pt2.x;
        int dy = pt1.y - pt2.y;
        if (roi_type.equals("roi_polygon")) {
            // 중심 좌표만 이동한다.
            m_MBR_center.x += dx;
            m_MBR_center.y += dy;

            // 예외처리
            if (m_MBR_center.x < m_MBR.left) m_MBR_center.x = m_MBR.left;
            if (m_MBR_center.x > m_MBR.right) m_MBR_center.x = m_MBR.right;
            if (m_MBR_center.y < m_MBR.top) m_MBR_center.y = m_MBR.top;
            if (m_MBR_center.y > m_MBR.bottom) m_MBR_center.y = m_MBR.bottom;

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
            this.m_MBR.left += dx;
            this.m_MBR.top += dy;
            this.m_MBR.right += dx;
            this.m_MBR.bottom += dy;
        }
    }

    // 무게 중심을 기준으로 도형을 이동한다.
    public void MoveToCenter(Point pt1, Point pt2) {
        //Log.d(TAG, "MoveTo( ("+pt1.x+","+pt1.y+"),("+pt2.x+","+pt2.y+") )");
        // pt1 : mouse down
        // pt2 : mouse move

        // 실수 좌표계로 변환하여 대입한다.
        //var zoom = this.m_zoom;

        int dx = (int)(pt1.x - pt2.x);
        int dy = (int)(pt1.y - pt2.y);

        int halfWidth = (int)(m_MBR.width()/2);
        int halfHeight = (int)(m_MBR.height()/2);

        // 중심 좌표만 이동한다.
        m_MBR_center.x += dx;
        m_MBR_center.y += dy;

        // 바뀐 중심 좌표를 기준으로 영역을 다시 만들어준다.
        this.m_MBR.left = m_MBR_center.x - halfWidth;
        this.m_MBR.top = m_MBR_center.y - halfHeight;
        this.m_MBR.right = m_MBR_center.x + halfWidth;
        this.m_MBR.bottom = m_MBR_center.y + halfHeight;

        if (roi_type.equals("roi_polygon")) {
            // m_Points의 point들도 m_MBR을 기준으로 다시 생성한다.
            m_Points.clear();
            m_Points.add(new Point(m_MBR.left, m_MBR.top));
            m_Points.add(new Point(m_MBR.right, m_MBR.top));
            m_Points.add(new Point(m_MBR.right, m_MBR.bottom));
            m_Points.add(new Point(m_MBR.left, m_MBR.bottom));
        }
    }

    // 무게중심을 설정하고 무게 중심으로 도형을 이동한다.
    public void MoveToCenterPosition(int x, int y){
        // 중심 좌표만 이동한다.
        m_MBR_center.x = x;
        m_MBR_center.y = y;

        int halfWidth = (int)(m_MBR.width()/2);
        int halfHeight = (int)(m_MBR.height()/2);

        // 바뀐 중심 좌표를 기준으로 영역을 다시 만들어준다.
        this.m_MBR.left = m_MBR_center.x - halfWidth;
        this.m_MBR.top = m_MBR_center.y - halfHeight;
        this.m_MBR.right = m_MBR_center.x + halfWidth;
        this.m_MBR.bottom = m_MBR_center.y + halfHeight;

        if (roi_type.equals("roi_polygon")) {
            // m_Points의 point들도 m_MBR을 기준으로 다시 생성한다.
            m_Points.clear();
            m_Points.add(new Point(m_MBR.left, m_MBR.top));
            m_Points.add(new Point(m_MBR.right, m_MBR.top));
            m_Points.add(new Point(m_MBR.right, m_MBR.bottom));
            m_Points.add(new Point(m_MBR.left, m_MBR.bottom));
        }
    }


    //    switch (nHandle) {
    //        case 0:        // 선택
    //        case 1:        // 왼쪽 위
    //        case 2:        // 위쪽 중간
    //        case 3:        // 오른쪽 위
    //        case 4:        // 왼쪽 중간
    //        case 5:        // 오른쪽 중간
    //        case 6:        // 왼쪽 아래
    //        case 7:        // 아래쪽 중간
    //        case 8:        // 오른쪽 아래
    //        case 9:        // 오른쪽 아래
    //
    //    }
    // 핸들의 종류에 따라서 도형을 이동/변경 한다.
    public void MoveHandleTo(Point pt1, Point pt2, int nHandle) {
        //console.log(pt1);
        // 실수 좌표계로 변환하여 대입한다.
        double zoom = this.m_zoom;

        // 왜 좌표가 2배 차이가 날까?
        int dx = (pt1.x - pt2.x);
        int dy = (pt1.y - pt2.y);

        switch (nHandle) {
            //case 0: // point CObject_MoveTo()함수에서 처리한다.
            //{	break;	 }
            case 1: {
                this.m_MBR.left += dx;
                this.m_MBR.top += dy;
                break;
            }
            case 2: {
                this.m_MBR.top += dy;
                break;
            }

            case 3: {
                this.m_MBR.right += dx;
                this.m_MBR.top += dy;
                break;
            }

            case 4: {
                this.m_MBR.left += dx;
                break;
            }

            case 5: {
                this.m_MBR.right += dx;
                break;
            }

            case 6: {
                this.m_MBR.left += dx;
                this.m_MBR.bottom += dy;
                break;
            }

            case 7: {
                this.m_MBR.bottom += dy;
                break;
            }

            case 8: {

                this.m_MBR.right += dx;
                this.m_MBR.bottom += dy;
                if(roi_type.equals("roi_polygon")) {
                    this.m_MBR_center.x = (int)((this.m_MBR.left + this.m_MBR.right)/2);
                    this.m_MBR_center.y = (int)( (this.m_MBR.top + this.m_MBR.bottom)/2);
                    this.m_Points.clear();
                    this.m_Points.add(new Point(this.m_MBR.left, this.m_MBR.top));
                    this.m_Points.add(new Point(this.m_MBR.right, this.m_MBR.top));
                    this.m_Points.add(new Point(this.m_MBR.right, this.m_MBR.bottom));
                    this.m_Points.add(new Point(this.m_MBR.left, this.m_MBR.bottom));
                }
                break;
            }
        }

        // 일부 ROI의 경우에는 괘적을 그림.
        // if(POLYGON || FREEHAND || FREEHANDLINE){}
        // else (point) //
        // 여기서는 point 만 이동한다.
    }

    // 포인트 핸들의 Point를 이동한다.
    public void MovePointTo(Point pt1, Point pt2, int nHandle) {

        if (nHandle < 1) return; // 포인터 이동이 아님.

        int dx = (pt1.x - pt2.x);
        int dy = (pt1.y - pt2.y);

        switch (roi_type) {
            case "roi_line":
                MoveHandleTo(pt1, pt2, nHandle);
                break;

            case "roi_rect":
                MoveHandleTo(pt1, pt2, nHandle);
                break;

            case "roi_polygon":

                //if (nHandle <= m_Points.size()) {
                //    m_Points.get(nHandle - 1).x += dx;
                //    m_Points.get(nHandle - 1).y += dy;
                //}
                MoveHandleTo(pt1, pt2, nHandle);
                break;
        }
    }

    // polygon 내부 색상 랜덤 생성
    private int getRandomColorArgb(int blur) {
        // RGB 값을 랜덤으로 생성

        int red = (int) (Math.random() * 255);    // 0~255
        int green = (int) (Math.random() * 255);    // 0~255
        int blue = (int) (Math.random() * 255);    // 0~255

        //int red = random.nextInt(256);    // 0~255
        //int green = random.nextInt(256); // 0~255
        //int blue = random.nextInt(256);  // 0~255

        return Color.argb(blur, red, green, blue); // 랜덤 색상 반환
    }


    // 도형 내무에서 로봇이 이동 가능한 영역만 다른 색상으로 그려준다.
    public void drawMatchPoints(Bitmap bitmap, Canvas canvas, Paint paint, int[] bounds, Point pt_Start) throws InterruptedException {
        // Bounds를 기반으로 영역 가져오기
        int left = Math.max(0, bounds[0]); // Bitmap 경계를 벗어나지 않도록 제한
        int top = Math.max(0, bounds[1]);
        int right = Math.min(bitmap.getWidth(), bounds[2]);
        int bottom = Math.min(bitmap.getHeight(), bounds[3]);
        int width = right - left;
        int height = bottom - top;

        if (width <= 0) return;
        if (height <= 0) return;

        // 픽셀 데이터를 가져오기
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, left, top, width, height);

        float floatZoom = (float) m_zoom;
        paint.setStrokeWidth(5);

        // 점 좌표를 저장할 리스트
        ArrayList<Float> points = new ArrayList<>();

        // 픽셀 데이터를 검사하며 조건에 맞는 좌표를 리스트에 추가
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelColor = pixels[y * width + x];
                if ((pixelColor & 0x00FFFFFF) == 0x00969696) { // RGB만 비교, Alpha 제외
                    points.add((x + left) * floatZoom + pt_Start.x); // X 좌표
                    points.add((y + top) * floatZoom + pt_Start.y);  // Y 좌표
                }
            }
        }

        // 리스트를 배열로 변환
        float[] pointArray = new float[points.size()];
        for (int i = 0; i < points.size(); i++) {
            pointArray[i] = points.get(i);
        }

        // 모든 점을 한 번에 그리기
        canvas.drawPoints(pointArray, paint);
    }

    // 해당 point가 polygon 내에 위치하는 지 체크한다.
    public boolean isPointInPolygon(Point point) {
        int intersectCount = 0;
        int size = m_Points.size();

        for (int i = 0; i < size; i++) {
            Point p1 = m_Points.get(i);
            Point p2 = m_Points.get((i + 1) % size);

            // Check if the point is on a horizontal edge of the polygon
            if (p1.y == p2.y && p1.y == point.y &&
                    point.x >= Math.min(p1.x, p2.x) && point.x <= Math.max(p1.x, p2.x)) {
                return true;
            }

            // Check if the ray intersects the edge
            if (point.y > Math.min(p1.y, p2.y) && point.y <= Math.max(p1.y, p2.y) &&
                    point.x <= Math.max(p1.x, p2.x)) {
                // Compute the x-coordinate of the intersection
                double xinters = (double) (point.y - p1.y) * (p2.x - p1.x) / (p2.y - p1.y) + p1.x;
                if (xinters == point.x) { // Point is on the edge
                    return true;
                }
                if (p1.x == p2.x || point.x < xinters) { // Ray intersects the edge
                    intersectCount++;
                }
            }
        }

        // Point is inside the polygon if intersectCount is odd
        return (intersectCount % 2) == 1;
    }
}
