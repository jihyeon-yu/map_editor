package com.forsk.ondevice;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;

import com.forsk.ondevice.databinding.ActivityMapeditorBinding;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.opencv.android.OpenCVLoader;
import org.yaml.snakeyaml.Yaml;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;

// opencv 추가
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Core;

public class MapEditorActivity extends Activity {
    private static final String TAG = "OnDeviceMapEditor:MapEditorActivity";

    // 맵 최적화 so 라이브러리 위치 및 로딩
    private static final String NAME_LIBRARY_CASELAB_OPT =
//                                                              "mapoptimizationV2";
            "mapoptimization241217v11";

    static {
        try {
            System.loadLibrary(NAME_LIBRARY_CASELAB_OPT);
            Log.d("SKOnDeviceService", "SO library load success!");
        } catch (UnsatisfiedLinkError e) {
            Log.e("SKOnDeviceService", "SO library load error (UnsatisfiedLinkError): " + e.toString());
        } catch (SecurityException e) {
            Log.e("SKOnDeviceService", "SO library load error (SecurityException): " + e.toString());
        }
    }

    // 파일 입축력 권한
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE};

    // 접근 권한이 없을 때는 권한 변경창 띄우기
    public static void verifyStoragePermissions(Activity activity) {
        int permission = ActivityCompat.checkSelfPermission(activity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        }
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
        }
    }

    private MapImageView mapViewer;

    String strMode = "Zoom";

    // 설정파일
    String srcMapPgmFilePath = "";
    String srcMapYamlFilePath = "";
    String destMappingFilePath = "";

    // 로봇 지도 변환값
    double nResolution = 0.0;
    double origin_x = 0.0;
    double origin_y = 0.0;
    double origin_angle = 0.0;

    // 모드 상수 정의
    private static final int MODE_MAP_EXPLORATION = 0;
    private static final int MODE_SPACE_CREATION = 1;
    private static final int MODE_BLOCK_WALL = 2;
    private static final int MODE_BLOCK_AREA = 3;

    // 현재 모드 변수
    private int currentMode = MODE_MAP_EXPLORATION;

    private String PinName = "";

    private boolean lib_flag = true;

    private Mat transformationMatrix = null;

    private float rotated_angle = 0f;
    int original_image_height = 0;

    private ActivityMapeditorBinding activityMapeditorBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(null);
        setTheme(R.style.Theme_OnDevice);
        activityMapeditorBinding = ActivityMapeditorBinding.inflate(getLayoutInflater());
        setContentView(activityMapeditorBinding.getRoot());


    }

    @Override
    public void onResume() {
        super.onResume();
        setUI();


        // roi 최초 선택시 getWidth() 가 0으로 나와서 보이지 않는 곳에 그려준다.
        activityMapeditorBinding.roiDeleteLayout.setX(-1000);
        activityMapeditorBinding.roiDeleteLayout.setY(-1000);
        activityMapeditorBinding.roiDeleteLayout.setVisibility(View.VISIBLE);

        activityMapeditorBinding.roiCompleteLayout.setX(-1000);
        activityMapeditorBinding.roiCompleteLayout.setY(-1000);
        activityMapeditorBinding.roiCompleteLayout.setVisibility(View.VISIBLE);

        srcMapPgmFilePath = getIntent().getStringExtra("srcMapPgmFilePath");
        if (srcMapPgmFilePath == null) {
            //srcMapPgmFilePath = "/storage/emulated/0/Download/caffe_map.pgm";   // for test
            srcMapPgmFilePath = "/sdcard/Download/office.pgm";    // for test
        }
        srcMapYamlFilePath = getIntent().getStringExtra("srcMapYamlFilePath");
        if (srcMapYamlFilePath == null) {
            //srcMapYamlFilePath = "/storage/emulated/0/Download/caffe_map.yaml";   // for test
            //srcMapYamlFilePath = "/storage/emulated/0/Download/office.yaml";   // for test
            srcMapYamlFilePath = "/sdcard/Download/office.yaml";
        }
        destMappingFilePath = getIntent().getStringExtra("destMappingFilePath");
        if (destMappingFilePath == null) {
            //srcMappingFilePath = "/storage/emulated/0/Download/map_meta_sample.json";   // for test
            destMappingFilePath = "/sdcard/Download/map_meta_sample.json";    // for test
        }
    }

    public interface DialogCallback_OkCancel {
        void onConfirm(String strResult); // 선택된 텍스트를 반환
    }

    private void showCustomDialog_OK(DialogCallback_OkCancel callback) {
        // Inflate the custom layout
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_okcancel_with_buttons, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialogTheme);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        // Find views
        Button cancelButton = dialogView.findViewById(R.id.rename_pin_cancel_button);
        Button confirmButton = dialogView.findViewById(R.id.rename_pin_confirm_button);

        TextView textViewTitle = dialogView.findViewById(R.id.dialog_title);
        textViewTitle.setText("저장하시겠습니까?");

        TextView textViewMessage = dialogView.findViewById(R.id.dialog_message);
        textViewMessage.setText("작업하신 맵 편집 내용으로 저장합니다.");

        // Set button listeners
        cancelButton.setOnClickListener(v -> {
            callback.onConfirm("cancel");
            dialog.dismiss();
        });

        confirmButton.setOnClickListener(v -> {
            callback.onConfirm("ok");
            dialog.dismiss();

        });

        // Show the dialog
        dialog.show();

        // Adjust dialog size
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.WRAP_CONTENT; // 너비
            params.height = WindowManager.LayoutParams.WRAP_CONTENT; // 높이
            window.setAttributes(params);
        }
    }

    private void showCustomDialog_Cancel(DialogCallback_OkCancel callback) {
        // Inflate the custom layout
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_okcancel_with_buttons, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialogTheme);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        // Find views
        Button cancelButton = dialogView.findViewById(R.id.rename_pin_cancel_button);
        Button confirmButton = dialogView.findViewById(R.id.rename_pin_confirm_button);

        TextView textViewTitle = dialogView.findViewById(R.id.dialog_title);
        textViewTitle.setText("맵 편집을 취소하시겠습니까?");

        TextView textViewMessage = dialogView.findViewById(R.id.dialog_message);
        textViewMessage.setText("현재까지 진행한 맵 편집 정보가 저장되지 않습니다.");

        // Set button listeners
        cancelButton.setOnClickListener(v -> {
            callback.onConfirm("cancel");
            dialog.dismiss();
        });

        confirmButton.setOnClickListener(v -> {
            callback.onConfirm("ok");
            dialog.dismiss();

        });

        // Show the dialog
        dialog.show();

        // Adjust dialog size
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.WRAP_CONTENT; // 너비
            params.height = WindowManager.LayoutParams.WRAP_CONTENT; // 높이
            window.setAttributes(params);
        }
    }

    public interface DialogCallback {
        void onConfirm(String selectedText); // 선택된 텍스트를 반환
    }

    private void showCustomDialog(DialogCallback callback) {
        // Inflate the custom layout
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_scrollable_with_buttons, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialogTheme);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        // Find views
        RadioGroup radioGroup = dialogView.findViewById(R.id.radio_group);
        Button cancelButton = dialogView.findViewById(R.id.rename_pin_cancel_button);
        Button confirmButton = dialogView.findViewById(R.id.rename_pin_confirm_button);

        // Layout for items
        String[][] layout = {{"거실", "게스트룸", "드레스룸", "발코니"}, // 첫 번째 행
                {"복도", "서재", "아이방1", "아이방2"}, // 두 번째 행
                {"안방", "욕실1", "욕실2", "주방"},   // 세 번째 행
                {"침실", "현관", "", ""}             // 네 번째 행
        };

        // List to manage all RadioButtons
        ArrayList<RadioButton> allRadioButtons = new ArrayList<>();

        // 선택된 라디오 버튼의 텍스트를 추적하기 위한 변수
        final String[] selectedText = {null};
        // Dynamically add rows
        for (String[] row : layout) {
            // Create a horizontal LinearLayout for each row
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutParams(new RadioGroup.LayoutParams(RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.WRAP_CONTENT));
            int idCounter = 0; // 고유 ID 생성기
            // Add items to the row
            for (String item : row) {
                if (!item.isEmpty()) {
                    // Create and style the RadioButton
                    RadioButton radioButton = new RadioButton(this);
                    radioButton.setText(item);
                    radioButton.setId(idCounter++);
                    radioButton.setTextColor(getResources().getColor(android.R.color.white));
                    radioButton.setTextSize(20);

                    // Set LayoutParams with margins
                    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1 // 균등 분배
                    );
                    layoutParams.setMargins(10, 20, 10, 20); // 위아래 마진 추가
                    radioButton.setLayoutParams(layoutParams);

                    radioButton.setGravity(Gravity.LEFT); // 왼쪽 정렬

                    // Add to the list
                    allRadioButtons.add(radioButton);

                    // Add click listener to uncheck others
                    radioButton.setOnClickListener(v -> {
                        for (RadioButton rb : allRadioButtons) {
                            if (rb != v) {
                                rb.setChecked(false); // 다른 버튼은 해제
                            } else selectedText[0] = item;
                        }
                    });

                    // Add RadioButton to the row
                    rowLayout.addView(radioButton);
                } else {
                    // Add empty space for empty slots
                    View space = new View(this);
                    space.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1 // 빈 공간도 균등 분배
                    ));
                    rowLayout.addView(space);
                }
            }

            // Add the row to the RadioGroup
            radioGroup.addView(rowLayout);
        }

        // Set button listeners
        cancelButton.setOnClickListener(v -> dialog.dismiss());

        confirmButton.setOnClickListener(v -> {
            if (selectedText[0] != null) {
                PinName = selectedText[0];
                callback.onConfirm(selectedText[0]);
                dialog.dismiss();
            } else {
                //Log.d(TAG, "공간명이 선택되지 않음");
                dialog.dismiss();
            }
        });

        // Show the dialog
        dialog.show();

        // Adjust dialog size
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT; // 너비
            params.height = WindowManager.LayoutParams.WRAP_CONTENT; // 높이
            params.horizontalMargin = 0.05f; // 좌우 마진 (화면 비율로 계산)
            params.verticalMargin = 0.1f; // 상하 마진
            window.setAttributes(params);
        }
    }

    public void setUI() {
        // 상태 바 제거
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Mode-specific TextView 초기화
        //setToggleBarByMode(currentMode);

        // Description 초기 상태 설정
        updateModeDescription();

        // 초기에는 안 보이는 것으로 설정
        hideRoiCompleteToggleBar();

        activityMapeditorBinding.roiCompleteLayout.setOnClickListener(v -> {

            // 스테이션이 금지공간에 표함되면 안된다.
            if (!mapViewer.CheckStation()) {
                Toast.makeText(getApplicationContext(), "스테이션이 있는 위치에는 금지 공간 설정이 불가능합니다!", Toast.LENGTH_SHORT).show();
                return;
            }

            // 완료 버튼이 클릭되면 roi 생성완료
            hideRoiCompleteToggleBar();

            // roi가 선택 안된 것으로 설정
            mapViewer.CObject_UnSelect();

        });

        activityMapeditorBinding.roiDeleteLayout.setOnClickListener(v -> {
            // 완료 버튼이 클릭되면 roi 생성완료
            hideRoiCompleteToggleBar();

            mapViewer.roi_RemoveObject();

            // roi가 선택 안된 것으로 설정
            mapViewer.CObject_UnSelect();
        });

        activityMapeditorBinding.gobackButton.setOnClickListener(v -> {
            //Log.d(TAG, "canclebutton.setOnClickListener(...)");
            showCustomDialog_Cancel(strResult -> {
                if (strResult.equals("ok")) {

                    try {
                        String strFileName = "map_meta_sample.json";
                        String strPath = "/sdcard/Download";
                        File destFile = new File(strPath, strFileName);

//                        if (!destFile.isFile()) {
//                            if (roi_saveToEmptyFile(strPath, strFileName))
//                                Log.d(TAG, "Success create empty Json File");
//                            else Log.d(TAG, "Fail create empty File");
//                        } else {
//                            Log.d(TAG, "Already Exist Json FIle");
//                        }

                        Log.d(TAG, "send broadcast... ");
                        Intent intent = new Intent("sk.action.airbot.map.responseMapping");
                        intent.setPackage("com.sk.airbot.iotagent");
                        intent.putExtra("destMappingFilePath", strPath + "/" + strFileName);
                        intent.putExtra("resultCode", "MRC_000");

                        sendBroadcast(intent);
                        Log.d(TAG, "sent broadcast. ");

                        Thread.sleep(1000);
                        Log.d(TAG, "finish activity ");

                        finish();
                    } catch (InterruptedException ie) {
                        Log.e(TAG, "goBack Button InterruptedExtception: " + ie.getMessage());
                    }

                }
            });
        });

        activityMapeditorBinding.finishButton.setOnClickListener(v -> {
            Log.d(TAG, "finshButton.setOnClickListener(...)");

            showCustomDialog_OK(strResult -> {
                //Toast.makeText(getApplicationContext(),strResult, Toast.LENGTH_SHORT).show();
                if (strResult.equals("ok")) {
                    try {
                        String strFileName = "map_meta_sample.json";
                        String strPath = "/sdcard/Download";

                        // TODO 수정필요 srcMappingfilePath 경로로 확인해서 name,path구분

                        Log.d(TAG, "try roi_saveToFile()");
                        if (roi_saveToFile(strPath, strFileName)) {
                            Log.d(TAG, "send broadcast... ");
                            Intent intent = new Intent("sk.action.airbot.map.responseMapping");
                            intent.setPackage("com.sk.airbot.iotagent");
                            intent.putExtra("destMappingFilePath", strPath + "/" + strFileName);
                            intent.putExtra("resultCode", "MRC_000");

                            sendBroadcast(intent);
                            Log.d(TAG, "sent broadcast. ");

                            Thread.sleep(1000);

                            Log.d(TAG, "finish activity ");
                            //System.exit(0);
                            finish();
                        } else {
                            Toast.makeText(getApplicationContext(), "can not save JSON data !", Toast.LENGTH_SHORT).show();
                        }

                    } catch (InterruptedException ie) {
                        Log.e(TAG, "SendBroadcast Unexpected error: " + ie.getMessage());
                    }
                } else {

                }
            });


        });

        // 20241217 jihyeon
        // 공간 생성, 가상벽, 금지공간 버튼 분리

        activityMapeditorBinding.buttonSpaceCreation.setOnClickListener(v -> {
            currentMode = MODE_SPACE_CREATION;
            mapViewer.setMode("공간 생성");
            Log.d(TAG, "공간 생성 모드 활성화");
            selectedFunc(v.getId());
            setToggleBarByMode(currentMode);
            updateModeDescription();
        });

        activityMapeditorBinding.buttonBlockWall.setOnClickListener(v -> {
            currentMode = MODE_BLOCK_WALL;
            mapViewer.setMode("가상벽");
            Log.d(TAG, "가상벽 모드 활성화");
            selectedFunc(v.getId());
            setToggleBarByMode(currentMode);
            updateModeDescription();
        });

        activityMapeditorBinding.buttonBlockArea.setOnClickListener(v -> {
            currentMode = MODE_BLOCK_AREA;
            mapViewer.setMode("금지공간");
            Log.d(TAG, "금지공간 모드 활성화");
            selectedFunc(v.getId());
            setToggleBarByMode(currentMode);
            updateModeDescription();
        });

        // 닫기 버튼 클릭 시 toggle_bar 숨기기
        activityMapeditorBinding.toggleBar.fabMainBack.setOnClickListener(v -> {
            clickBackButton(extendToggleBar);
            extendToggleBar = !extendToggleBar;
        });

        activityMapeditorBinding.icRotateMap.setOnClickListener(v -> {
            // 250104 skmg
            // Map 90도 회전 버튼
            this.mapViewer.setRotateMap90();
        });

        activityMapeditorBinding.toggleBar.buttonAddObjectBackground.setOnClickListener(v -> {
            //Log.w(">>>", "123123213");

            // 공간을 최대 7개만 추가 가능하게 제한
            int i;
            int nCount = 0;
            for (i = 0; i < mapViewer.m_RoiObjects.size(); i++) {
                if (mapViewer.m_RoiObjects.get(i).roi_type.equals("roi_polygon")) nCount++;
            }
            if (nCount >= 7) {
                Toast.makeText(getApplicationContext(), "공간은 최대 7개까지 추가 가능합니다!", Toast.LENGTH_SHORT).show();
                return;
            }

            mapViewer.setMenu("추가");
            mapViewer.roi_CreateObject();
            updateToggleButtonStatus(v.getId());
            //activityMapeditorBinding.roiDeleteLayout.setVisibility(View.VISIBLE);
            //activityMapeditorBinding.roiCompleteLayout.setVisibility(View.VISIBLE);
        });

        activityMapeditorBinding.toggleBar.buttonPinMoveBackground.setOnClickListener(v -> {
            updateToggleButtonStatus(v.getId());
            //TODO : 핀 이동 구현

            if (mapViewer.strMenu.equals("핀 이동")) {
                updateToggleButtonStatus(-1);   // 아무 것도 선택안 된 것으로

                mapViewer.setMenu("선택");
                showRoiCompleteToggleBar();
            } else {
                updateToggleButtonStatus(v.getId());

                mapViewer.setMenu("핀 이동");
                hideRoiCompleteToggleBar();
            }

        });

        activityMapeditorBinding.toggleBar.buttonPinRotateBackground.setOnClickListener(v -> {

            //TODO : 핀 회전 구현
            if (mapViewer.strMenu.equals("핀 회전")) {
                updateToggleButtonStatus(-1);   // 아무 것도 선택안 된 것으로

                mapViewer.setMenu("선택");

            } else {
                updateToggleButtonStatus(v.getId());
                mapViewer.setMenu("핀 회전");
                hideRoiCompleteToggleBar();
            }

        });

        activityMapeditorBinding.toggleBar.buttonRenameBackground.setOnClickListener(v -> {
            if (mapViewer.m_RoiCurIndex < 0) {
                Toast.makeText(getApplicationContext(), "선택된 공간이 없습니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            strMode = "None";
            String oldPinName = PinName;
            showCustomDialog(selectedText -> {
                if ((mapViewer.m_RoiCurObject != null)) {
                    mapViewer.SetLabel(PinName);
                }
            });

            setToggleBarByMode(currentMode);
            updateToggleButtonStatus(v.getId());
        });

        mapViewer = activityMapeditorBinding.mapViewer;
        // 방법 1: ViewTreeObserver를 사용
        mapViewer.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // 가로와 세로 크기 얻기
                int width = mapViewer.getWidth();
                int height = mapViewer.getHeight();

                Log.d("ViewSize", "Width: " + width + ", Height: " + height);

                mapViewer.ScreenWidth = mapViewer.getWidth();
                mapViewer.ScreenHeight = mapViewer.getHeight();

                // 리스너 제거 (메모리 누수 방지)
                mapViewer.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                loadFile();

                try {
                    Bitmap bitmap = loadPGM(srcMapPgmFilePath);
                    if (bitmap != null) {
                        mapViewer.setBitmap(bitmap);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "IOException error occurred", e);
                }

                if (!loadYaml(srcMapYamlFilePath)) {
                    Log.d(TAG, "con not read " + srcMapYamlFilePath);
                }
                // 241218 성웅 맵 불러오기
                if (!loadJson(destMappingFilePath)) {
                    Log.d(TAG, "con not read " + destMappingFilePath);
                }


                hideRoiCompleteToggleBar();
            }
        });

        // MapImageView에서 Rio가 선택되면 수신
        mapViewer.setRoiSelectedListener(indexSelected -> {
            // ROI가 선택된 경우
            //Log.d(TAG, "onRoiSelected(" + indexSelected + ")");

            // 필요한 버튼을 선택된 ROI 객체에 Dash 역역에 보여준다.
            if ((indexSelected > -1) && (indexSelected < mapViewer.m_RoiObjects.size())) {
                //    [0]=====[1]
                //    ||      ||
                //    ||      ||
                //    [3]=====[2]


                // MapViewer의 시작위치를 가져온다.
                int[] location = new int[2];
                mapViewer.getLocationOnScreen(location);
                //int x0 = location[0]; // X 좌표
                //int y0 = location[1]; // Y 좌표
                //Log.d(TAG, "location : ("+x0+","+y0+")");

                //Log.d(TAG, "m_DashPoints.get(0) : " + mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(0).x + ", " + mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(0).y);
                //Log.d(TAG, "m_DashPoints.get(1) : " + mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(1).x + ", " + mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(1).y);
                //Log.d(TAG, "m_DashPoints.get(2) : " + mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(2).x + ", " + mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(2).y);
                //Log.d(TAG, "m_DashPoints.get(3) : " + mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(3).x + ", " + mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(3).y);

                Point pt0 = mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(0);
                Point pt1 = mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(1);
                Point pt2 = mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(2);
                Point pt3 = mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(3);

                activityMapeditorBinding.roiDeleteLayout.setVisibility(View.VISIBLE);
                activityMapeditorBinding.roiCompleteLayout.setVisibility(View.VISIBLE);

                // 선택된 객체 위치에 토글바 배치
                // 위치를 모서리 바깥에 위치하게 수정

                // 아이콘을 그려주는 위치는 해당 DashPoint에서 다음 DashPoint 의 각도를 계산하여
                // 45도 회전한 위치의 Point와 해당 DashPoint를 사각형의 중심 좌표에 아이콘의 중심이 위치하게 그려준다.
                int px, py;  // 45도 회전하여 nDistance 만큼 이동한 좌표
                int nDistance = mapViewer.roiButtonDistance;    // 아이콘 모서리와의 거리
                int iconX, iconY;   // 아이콘이 그려질 x,y 좌표
                //int iconWidth = 100;    // 아이콘 가로크기
                //int iconHeight = 100;   // 아이콘 높이
                double nAngle;  // 해당 모서리에서 다음 모시리와의 기울기

                // 두 점 간의 각도 계산
                nAngle = Math.atan2(pt1.y - pt0.y, pt1.x - pt0.x);    // 다음 DashPoint의 각도을 얻어서 45도 회전한 각도를 구한다.
                //Log.d(TAG, "nAngle : " + nAngle);

                // 각도에서 45도 위치로 이동한다.
                px = (int) (pt0.x - nDistance * (float) Math.cos(nAngle + (float) Math.PI / 4.0));
                py = (int) (pt0.y - nDistance * (float) Math.sin(nAngle + (float) Math.PI / 4.0));
                //Log.d(TAG, "(px1,py1) : ( " + px + ", " + py + " )");

                // 그려줄 위치를 nDistance 거리의 좌표와 해당 모서리의 사각형의 중심 좌표를 일치시켜준다.
                //iconX = (int) ((px + pt0.x) / 2.0 - iconWidth / 2.0);
                //iconY = (int) ((py + pt0.y) / 2.0 - iconHeight / 2.0);
                //Log.d(TAG, "px : " + px);
                //Log.d(TAG, "py : " + py);
                //Log.d(TAG, "pt0 : ( " + pt0.x + ", " + pt0.y + " )");

                //Log.d(TAG, "activityMapeditorBinding.roiDeleteLayout.getWidth() : " + activityMapeditorBinding.roiDeleteLayout.getWidth());

                iconX = (int) ((px + pt0.x) / 2.0 - activityMapeditorBinding.roiDeleteLayout.getWidth() / 2.0);
                iconY = (int) ((py + pt0.y) / 2.0 - activityMapeditorBinding.roiDeleteLayout.getHeight() / 2.0);
                //Log.d(TAG, "iconX : " + iconX);
                //Log.d(TAG, "iconY : " + iconY);
                //Log.d(TAG, "activityMapeditorBinding.roiDeleteLayout.getWidth() : " + activityMapeditorBinding.roiDeleteLayout.getWidth());

                // view의 시작 좌표에 맞추어 보정해준다.
                activityMapeditorBinding.roiDeleteLayout.setX(iconX + location[0]);
                activityMapeditorBinding.roiDeleteLayout.setY(iconY + location[1]);
                activityMapeditorBinding.roiDeleteLayout.setVisibility(View.VISIBLE);

                nAngle = Math.atan2(pt2.y - pt1.y, pt2.x - pt1.x);    // 다음 DashPoint의 각도을 얻어서 45도 회전한 각도를 구한다.
                //Log.d(TAG, "nAngle : " + nAngle);
                px = (int) (pt1.x - nDistance * (float) Math.cos(nAngle + (float) Math.PI / 4.0));
                py = (int) (pt1.y - nDistance * (float) Math.sin(nAngle + (float) Math.PI / 4.0));
                //Log.d(TAG, "(px1,py1) : ( " + px + ", " + py + " )");

                // 그려줄 위치를 nDistance 거리의 좌표와 해당 모서리의 사각형의 중심 좌표를 일치시켜준다.
                //iconX = (int) ((px + pt1.x) / 2.0 - iconWidth / 2.0);
                //iconY = (int) ((py + pt1.y) / 2.0 - iconHeight / 2.0);
                iconX = (int) ((px + pt1.x) / 2.0 - activityMapeditorBinding.roiCompleteLayout.getWidth() / 2.0);
                iconY = (int) ((py + pt1.y) / 2.0 - activityMapeditorBinding.roiCompleteLayout.getHeight() / 2.0);

                // view의 시작 좌표에 맞추어 보정해준다.
                activityMapeditorBinding.roiCompleteLayout.setX(iconX + location[0]);
                activityMapeditorBinding.roiCompleteLayout.setY(iconY + location[1]);
                activityMapeditorBinding.roiCompleteLayout.setVisibility(View.VISIBLE);

            } else {
                // 선택된 것이 없음.
                hideRoiCompleteToggleBar();
            }
        });

        // MapImageView에서 Rio(m_RoiCurObject)가 새로 생성되어 저장되기 전까지 수신
        mapViewer.setRoiCreateListener(() -> {
            // ROI 새롭게 만든 경우
            //Log.d(TAG, "setRoiCreateListener.onRoiCreate()");

            // 필요한 버튼을 선택된 ROI 객체에 Dash 역역에 보여준다.
            if (mapViewer.m_RoiCurObject != null) {
                //    [0]=====[1]
                //    ||      ||
                //    ||      ||
                //    [3]=====[2]

                // MapViewer의 시작위치를 가져온다.
                int[] location = new int[2];
                mapViewer.getLocationOnScreen(location);
                //int x0 = location[0]; // X 좌표
                //int y0 = location[1]; // Y 좌표
                //Log.d(TAG, "location : ("+x0+","+y0+")");

                //Log.d(TAG, "m_DashPoints.get(0) : " + mapViewer.m_RoiCurObject.m_DashPoints.get(0).x + ", " + mapViewer.m_RoiCurObject.m_DashPoints.get(0).y);
                //Log.d(TAG, "m_DashPoints.get(1) : " + mapViewer.m_RoiCurObject.m_DashPoints.get(1).x + ", " + mapViewer.m_RoiCurObject.m_DashPoints.get(1).y);
                //Log.d(TAG, "m_DashPoints.get(2) : " + mapViewer.m_RoiCurObject.m_DashPoints.get(2).x + ", " + mapViewer.m_RoiCurObject.m_DashPoints.get(2).y);
                //Log.d(TAG, "m_DashPoints.get(3) : " + mapViewer.m_RoiCurObject.m_DashPoints.get(3).x + ", " + mapViewer.m_RoiCurObject.m_DashPoints.get(3).y);

                Point pt0 = mapViewer.m_RoiCurObject.m_DashPoints.get(0);
                Point pt1 = mapViewer.m_RoiCurObject.m_DashPoints.get(1);
                Point pt2 = mapViewer.m_RoiCurObject.m_DashPoints.get(2);
                Point pt3 = mapViewer.m_RoiCurObject.m_DashPoints.get(3);


                // 선택된 객체 위치에 토글바 배치
                // 위치를 모서리 바깥에 위치하게 수정

                // 아이콘을 그려주는 위치는 해당 DashPoint에서 다음 DashPoint 의 각도를 계산하여
                // 45도 회전한 위치의 Point와 해당 DashPoint를 사각형의 중심 좌표에 아이콘의 중심이 위치하게 그려준다.
                int px, py;  // 45도 회전하여 nDistance 만큼 이동한 좌표
                int nDistance = mapViewer.roiButtonDistance;    // 아이콘 모서리와의 거리
                int iconX, iconY;   // 아이콘이 그려질 x,y 좌표
                //int iconWidth = 100;    // 아이콘 가로크기
                //int iconHeight = 100;   // 아이콘 높이
                double nAngle;  // 해당 모서리에서 다음 모시리와의 기울기

                // 두 점 간의 각도 계산
                nAngle = Math.atan2(pt1.y - pt0.y, pt1.x - pt0.x);    // 다음 DashPoint의 각도을 얻어서 45도 회전한 각도를 구한다.
                //Log.d(TAG, "nAngle : " + nAngle);

                // 각도에서 45도 위치로 이동한다.
                px = (int) (pt0.x - nDistance * (float) Math.cos(nAngle + (float) Math.PI / 4.0));
                py = (int) (pt0.y - nDistance * (float) Math.sin(nAngle + (float) Math.PI / 4.0));
                //Log.d(TAG, "(px1,py1) : ( " + px + ", " + py + " )");

                // 그려줄 위치를 nDistance 거리의 좌표와 해당 모서리의 사각형의 중심 좌표를 일치시켜준다.
                //iconX = (int) ((px + pt0.x) / 2.0 - iconWidth / 2.0);
                //iconY = (int) ((py + pt0.y) / 2.0 - iconHeight / 2.0);
                iconX = (int) ((px + pt0.x) / 2.0 - activityMapeditorBinding.roiDeleteLayout.getWidth() / 2.0);
                iconY = (int) ((py + pt0.y) / 2.0 - activityMapeditorBinding.roiDeleteLayout.getHeight() / 2.0);

                // view의 시작 좌표에 맞추어 보정해준다.
                activityMapeditorBinding.roiDeleteLayout.setX(iconX + location[0]);
                activityMapeditorBinding.roiDeleteLayout.setY(iconY + location[1]);
                activityMapeditorBinding.roiDeleteLayout.setVisibility(View.VISIBLE);

                nAngle = Math.atan2(pt2.y - pt1.y, pt2.x - pt1.x);    // 다음 DashPoint의 각도을 얻어서 45도 회전한 각도를 구한다.
                //Log.d(TAG, "nAngle : " + nAngle);
                px = (int) (pt1.x - nDistance * (float) Math.cos(nAngle + (float) Math.PI / 4.0));
                py = (int) (pt1.y - nDistance * (float) Math.sin(nAngle + (float) Math.PI / 4.0));
                //Log.d(TAG, "(px1,py1) : ( " + px + ", " + py + " )");

                // 그려줄 위치를 nDistance 거리의 좌표와 해당 모서리의 사각형의 중심 좌표를 일치시켜준다.
                //iconX = (int) ((px + pt1.x) / 2.0 - iconWidth / 2.0);
                //iconY = (int) ((py + pt1.y) / 2.0 - iconHeight / 2.0);
                iconX = (int) ((px + pt1.x) / 2.0 - activityMapeditorBinding.roiCompleteLayout.getWidth() / 2.0);
                iconY = (int) ((py + pt1.y) / 2.0 - activityMapeditorBinding.roiCompleteLayout.getHeight() / 2.0);

                // view의 시작 좌표에 맞추어 보정해준다.
                activityMapeditorBinding.roiCompleteLayout.setX(iconX + location[0]);
                activityMapeditorBinding.roiCompleteLayout.setY(iconY + location[1]);
                activityMapeditorBinding.roiCompleteLayout.setVisibility(View.VISIBLE);


            } else {
                // 선택된 것이 없음.
                hideRoiCompleteToggleBar();
            }
        });

        // MapImageView에서 Rio가 선택된 것의 변형, 이동시 수신
        mapViewer.setRoiChangedListener(indexSelected -> {
            // ROI 수정한 경우
            //Log.d(TAG, "onRoiChanged(" + indexSelected + ")");

            // 필요한 버튼을 선택된 ROI 객체에 Dash 역역에 보여준다.
            if ((indexSelected > -1) && (indexSelected < mapViewer.m_RoiObjects.size())) {
                //    [0]=====[1]
                //    ||      ||
                //    ||      ||
                //    [3]=====[2]

                // MapViewer의 시작위치를 가져온다.
                int[] location = new int[2];
                mapViewer.getLocationOnScreen(location);
                //int x0 = location[0]; // X 좌표
                //int y0 = location[1]; // Y 좌표
                //Log.d(TAG, "location : ("+x0+","+y0+")");

                //Log.d(TAG, "m_DashPoints.get(0) : " + mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(0).x + ", " + mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(0).y);
                //Log.d(TAG, "m_DashPoints.get(1) : " + mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(1).x + ", " + mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(1).y);
                //Log.d(TAG, "m_DashPoints.get(2) : " + mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(2).x + ", " + mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(2).y);
                //Log.d(TAG, "m_DashPoints.get(3) : " + mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(3).x + ", " + mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(3).y);

                Point pt0 = mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(0);
                Point pt1 = mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(1);
                Point pt2 = mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(2);
                Point pt3 = mapViewer.m_RoiObjects.get(indexSelected).m_DashPoints.get(3);


                // 선택된 객체 위치에 토글바 배치
                // 위치를 모서리 바깥에 위치하게 수정

                // 아이콘을 그려주는 위치는 해당 DashPoint에서 다음 DashPoint 의 각도를 계산하여
                // 45도 회전한 위치의 Point와 해당 DashPoint를 사각형의 중심 좌표에 아이콘의 중심이 위치하게 그려준다.
                int px, py;  // 45도 회전하여 nDistance 만큼 이동한 좌표
                int nDistance = mapViewer.roiButtonDistance;    // 아이콘 모서리와의 거리
                int iconX, iconY;   // 아이콘이 그려질 x,y 좌표
                double nAngle;  // 해당 모서리에서 다음 모시리와의 기울기

                // 두 점 간의 각도 계산
                nAngle = Math.atan2(pt1.y - pt0.y, pt1.x - pt0.x);    // 다음 DashPoint의 각도을 얻어서 45도 회전한 각도를 구한다.
                //Log.d(TAG, "nAngle : " + nAngle);

                // 각도에서 45도 위치로 이동한다.
                px = (int) (pt0.x - nDistance * (float) Math.cos(nAngle + (float) Math.PI / 4.0));
                py = (int) (pt0.y - nDistance * (float) Math.sin(nAngle + (float) Math.PI / 4.0));
                //Log.d(TAG, "(px1,py1) : ( " + px + ", " + py + " )");

                // 그려줄 위치를 nDistance 거리의 좌표와 해당 모서리의 사각형의 중심 좌표를 일치시켜준다.
                //iconX = (int) ((px + pt0.x) / 2.0 - iconWidth / 2.0);
                //iconY = (int) ((py + pt0.y) / 2.0 - iconHeight / 2.0);
                iconX = (int) ((px + pt0.x) / 2.0 - activityMapeditorBinding.roiDeleteLayout.getWidth() / 2.0);
                iconY = (int) ((py + pt0.y) / 2.0 - activityMapeditorBinding.roiDeleteLayout.getHeight() / 2.0);

                // view의 시작 좌표에 맞추어 보정해준다.
                activityMapeditorBinding.roiDeleteLayout.setX(iconX + location[0]);
                activityMapeditorBinding.roiDeleteLayout.setY(iconY + location[1]);
                activityMapeditorBinding.roiDeleteLayout.setVisibility(View.VISIBLE);

                nAngle = Math.atan2(pt2.y - pt1.y, pt2.x - pt1.x);    // 다음 DashPoint의 각도을 얻어서 45도 회전한 각도를 구한다.
                //Log.d(TAG, "nAngle : " + nAngle);
                px = (int) (pt1.x - nDistance * (float) Math.cos(nAngle + (float) Math.PI / 4.0));
                py = (int) (pt1.y - nDistance * (float) Math.sin(nAngle + (float) Math.PI / 4.0));
                //Log.d(TAG, "(px1,py1) : ( " + px + ", " + py + " )");

                // 그려줄 위치를 nDistance 거리의 좌표와 해당 모서리의 사각형의 중심 좌표를 일치시켜준다.
                //iconX = (int) ((px + pt0.x) / 2.0 - iconWidth / 2.0);
                //iconY = (int) ((py + pt0.y) / 2.0 - iconHeight / 2.0);
                iconX = (int) ((px + pt1.x) / 2.0 - activityMapeditorBinding.roiCompleteLayout.getWidth() / 2.0);
                iconY = (int) ((py + pt1.y) / 2.0 - activityMapeditorBinding.roiCompleteLayout.getHeight() / 2.0);

                // view의 시작 좌표에 맞추어 보정해준다.
                activityMapeditorBinding.roiCompleteLayout.setX(iconX + location[0]);
                activityMapeditorBinding.roiCompleteLayout.setY(iconY + location[1]);
                activityMapeditorBinding.roiCompleteLayout.setVisibility(View.VISIBLE);
            } else {
                // 선택된 것이 없음.
                hideRoiCompleteToggleBar();
            }

        });
    }

    // 토글바 숨김 함수
    private void hideRoiCompleteToggleBar() {
        activityMapeditorBinding.roiCompleteLayout.setVisibility(View.GONE);
        activityMapeditorBinding.roiDeleteLayout.setVisibility(View.GONE);
    }

    // 토글바 숨김 함수
    private void showRoiCompleteToggleBar() {
        activityMapeditorBinding.roiCompleteLayout.setVisibility(View.VISIBLE);
        activityMapeditorBinding.roiDeleteLayout.setVisibility(View.VISIBLE);
    }

    private void updateToggleButtonStatus(int viewId) {
        Drawable selectedBackground = AppCompatResources.getDrawable(this, R.drawable.rounded_button_white);

        if (viewId == activityMapeditorBinding.toggleBar.buttonAddObjectBackground.getId()) {
            activityMapeditorBinding.toggleBar.buttonAddObjectBackground.setBackground(selectedBackground);
            activityMapeditorBinding.toggleBar.buttonAddObject.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.add_black));

            activityMapeditorBinding.toggleBar.buttonPinRotateBackground.setBackground(null);
            activityMapeditorBinding.toggleBar.buttonPinRotate.setTextColor(getColor(R.color.white));

            activityMapeditorBinding.toggleBar.buttonPinMoveBackground.setBackground(null);
            activityMapeditorBinding.toggleBar.buttonPinMove.setTextColor(getColor(R.color.white));

            activityMapeditorBinding.toggleBar.buttonRenameBackground.setBackground(null);
            activityMapeditorBinding.toggleBar.buttonRename.setTextColor(getColor(R.color.white));

        } else if (viewId == activityMapeditorBinding.toggleBar.buttonPinRotateBackground.getId()) {
            activityMapeditorBinding.toggleBar.buttonAddObjectBackground.setBackground(null);
            activityMapeditorBinding.toggleBar.buttonAddObject.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.add_white));

            activityMapeditorBinding.toggleBar.buttonPinRotateBackground.setBackground(selectedBackground);
            activityMapeditorBinding.toggleBar.buttonPinRotate.setTextColor(getColor(R.color.black));

            activityMapeditorBinding.toggleBar.buttonPinMoveBackground.setBackground(null);
            activityMapeditorBinding.toggleBar.buttonPinMove.setTextColor(getColor(R.color.white));

            activityMapeditorBinding.toggleBar.buttonRenameBackground.setBackground(null);
            activityMapeditorBinding.toggleBar.buttonRename.setTextColor(getColor(R.color.white));

        } else if (viewId == activityMapeditorBinding.toggleBar.buttonPinMoveBackground.getId()) {
            activityMapeditorBinding.toggleBar.buttonAddObjectBackground.setBackground(null);
            activityMapeditorBinding.toggleBar.buttonAddObject.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.add_white));

            activityMapeditorBinding.toggleBar.buttonPinRotateBackground.setBackground(null);
            activityMapeditorBinding.toggleBar.buttonPinRotate.setTextColor(getColor(R.color.white));

            activityMapeditorBinding.toggleBar.buttonPinMoveBackground.setBackground(selectedBackground);
            activityMapeditorBinding.toggleBar.buttonPinMove.setTextColor(getColor(R.color.black));

            activityMapeditorBinding.toggleBar.buttonRenameBackground.setBackground(null);
            activityMapeditorBinding.toggleBar.buttonRename.setTextColor(getColor(R.color.white));

        } else if (viewId == activityMapeditorBinding.toggleBar.buttonRenameBackground.getId()) {
            activityMapeditorBinding.toggleBar.buttonAddObjectBackground.setBackground(null);
            activityMapeditorBinding.toggleBar.buttonAddObject.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.add_white));

            activityMapeditorBinding.toggleBar.buttonPinRotateBackground.setBackground(null);
            activityMapeditorBinding.toggleBar.buttonPinRotate.setTextColor(getColor(R.color.white));

            activityMapeditorBinding.toggleBar.buttonPinMoveBackground.setBackground(null);
            activityMapeditorBinding.toggleBar.buttonPinMove.setTextColor(getColor(R.color.white));

            activityMapeditorBinding.toggleBar.buttonRenameBackground.setBackground(selectedBackground);
            activityMapeditorBinding.toggleBar.buttonRename.setTextColor(getColor(R.color.black));
        } else {
            activityMapeditorBinding.toggleBar.buttonAddObjectBackground.setBackground(null);
            activityMapeditorBinding.toggleBar.buttonAddObject.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.add_white));

            activityMapeditorBinding.toggleBar.buttonPinRotateBackground.setBackground(null);
            activityMapeditorBinding.toggleBar.buttonPinRotate.setTextColor(getColor(R.color.white));

            activityMapeditorBinding.toggleBar.buttonPinMoveBackground.setBackground(null);
            activityMapeditorBinding.toggleBar.buttonPinMove.setTextColor(getColor(R.color.white));

            activityMapeditorBinding.toggleBar.buttonRenameBackground.setBackground(null);
            activityMapeditorBinding.toggleBar.buttonRename.setTextColor(getColor(R.color.white));
        }
    }

    // currentMode에 따라 텍스트 업데이트
    private void updateModeDescription() {
        switch (currentMode) {
            case MODE_SPACE_CREATION:
                activityMapeditorBinding.modeDescription.setText(R.string.text_press_button_create_space);
                break;
            case MODE_BLOCK_WALL:
                activityMapeditorBinding.modeDescription.setText(R.string.text_press_plus_block_wall);
                break;
            case MODE_BLOCK_AREA:
                activityMapeditorBinding.modeDescription.setText(R.string.text_press_plus_block_area);
                break;
            default:
                activityMapeditorBinding.modeDescription.setText(" "); // 다른 상태에서는 빈 문자열
                break;
        }
    }

    public void selectedFunc(int viewId) {
        if (viewId == R.id.button_space_creation) {
            applyBottomButtonStyle(activityMapeditorBinding.buttonSpaceCreation, true);
            applyBottomButtonStyle(activityMapeditorBinding.buttonBlockWall, false);
            applyBottomButtonStyle(activityMapeditorBinding.buttonBlockArea, false);


            mapViewer.setMenu("선택");
            mapViewer.CObject_UnSelect();
        }
        if (viewId == R.id.button_block_wall) {
            applyBottomButtonStyle(activityMapeditorBinding.buttonSpaceCreation, false);
            applyBottomButtonStyle(activityMapeditorBinding.buttonBlockWall, true);
            applyBottomButtonStyle(activityMapeditorBinding.buttonBlockArea, false);

            mapViewer.setMenu("선택");
            mapViewer.CObject_UnSelect();
        }
        if (viewId == R.id.button_block_area) {
            applyBottomButtonStyle(activityMapeditorBinding.buttonSpaceCreation, false);
            applyBottomButtonStyle(activityMapeditorBinding.buttonBlockWall, false);
            applyBottomButtonStyle(activityMapeditorBinding.buttonBlockArea, true);

            mapViewer.setMenu("선택");
            mapViewer.CObject_UnSelect();
        }
    }

    Boolean extendToggleBar = false;

    public void clickBackButton(boolean extendToggleBar) {
        if (currentMode == MODE_MAP_EXPLORATION) {
            Toast.makeText(this, "편집 모드를 선택해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (extendToggleBar) {
            activityMapeditorBinding.toggleBar.fabMainBack.setRotation(180f);
            activityMapeditorBinding.toggleBar.buttonAddObjectBackground.setVisibility(View.GONE);
            activityMapeditorBinding.toggleBar.buttonPinMoveBackground.setVisibility(View.GONE);
            activityMapeditorBinding.toggleBar.buttonPinRotateBackground.setVisibility(View.GONE);
            activityMapeditorBinding.toggleBar.buttonRenameBackground.setVisibility(View.GONE);
            activityMapeditorBinding.toggleBar.divider1.setVisibility(View.GONE);
            activityMapeditorBinding.toggleBar.divider2.setVisibility(View.GONE);
            activityMapeditorBinding.toggleBar.divider3.setVisibility(View.GONE);
            activityMapeditorBinding.toggleBar.divider4.setVisibility(View.GONE);
        } else {
            activityMapeditorBinding.toggleBar.fabMainBack.setRotation(0f);
            activityMapeditorBinding.toggleBar.buttonAddObjectBackground.setVisibility(View.VISIBLE);
            activityMapeditorBinding.toggleBar.buttonPinMoveBackground.setVisibility(View.VISIBLE);
            activityMapeditorBinding.toggleBar.buttonPinRotateBackground.setVisibility(View.VISIBLE);
            activityMapeditorBinding.toggleBar.buttonRenameBackground.setVisibility(View.VISIBLE);
            activityMapeditorBinding.toggleBar.divider1.setVisibility(View.VISIBLE);
            activityMapeditorBinding.toggleBar.divider2.setVisibility(View.VISIBLE);
            activityMapeditorBinding.toggleBar.divider3.setVisibility(View.VISIBLE);
            activityMapeditorBinding.toggleBar.divider4.setVisibility(View.VISIBLE);
        }
        extendToggleBar = !extendToggleBar;
    }

    public void setToggleBarByMode(int mode) {
        //백버튼 초기화
        clickBackButton(extendToggleBar);

        switch (mode) {
            case MODE_SPACE_CREATION:
                activityMapeditorBinding.toggleBar.buttonAddObjectBackground.setVisibility(View.VISIBLE);
                activityMapeditorBinding.toggleBar.buttonPinMoveBackground.setVisibility(View.VISIBLE);
                activityMapeditorBinding.toggleBar.buttonPinRotateBackground.setVisibility(View.VISIBLE);
                activityMapeditorBinding.toggleBar.buttonRenameBackground.setVisibility(View.VISIBLE);
                activityMapeditorBinding.toggleBar.divider1.setVisibility(View.VISIBLE);
                activityMapeditorBinding.toggleBar.divider2.setVisibility(View.VISIBLE);
                activityMapeditorBinding.toggleBar.divider3.setVisibility(View.VISIBLE);
                activityMapeditorBinding.toggleBar.divider4.setVisibility(View.VISIBLE);
                break;
            case MODE_BLOCK_WALL:
            case MODE_BLOCK_AREA:
                activityMapeditorBinding.toggleBar.buttonAddObjectBackground.setVisibility(View.VISIBLE);
                activityMapeditorBinding.toggleBar.buttonPinMoveBackground.setVisibility(View.GONE);
                activityMapeditorBinding.toggleBar.buttonPinRotateBackground.setVisibility(View.GONE);
                activityMapeditorBinding.toggleBar.buttonRenameBackground.setVisibility(View.GONE);
                activityMapeditorBinding.toggleBar.divider2.setVisibility(View.GONE);
                activityMapeditorBinding.toggleBar.divider3.setVisibility(View.GONE);
                activityMapeditorBinding.toggleBar.divider4.setVisibility(View.GONE);
                break;
        }
    }

    // 버튼 스타일 적용 메서드
    private void applyBottomButtonStyle(Button button, boolean isSelected) {
        if (isSelected) {
            button.setBackgroundResource(R.drawable.rounded_button_white);
            button.setTextColor(Color.BLACK);
        } else {
            button.setBackgroundResource(R.drawable.rounded_button);
            button.setTextColor(Color.WHITE);
        }
    }


    private void loadFile() {
        try {
            // File 객체 생성
            File file = new File(srcMapPgmFilePath);

            String path = file.getParent() + "/"; // 디렉터리 경로
            String filename = file.getName(); // 파일 이름
            String fileTitle = filename.substring(0, filename.lastIndexOf('.')); // 확장자를 제외한 파일 제목

            String path_rot = path + "rot/";
            File file_rot = new File(path_rot);
            // 폴더가 제대로 만들어졌는지 체크 ======
            if (!file_rot.mkdirs()) {

                Log.e("FILE", "Directory not created : " + path_rot);

            }
            Log.d("SKOnDeviceService", "Run library-rotate!");
            //com.caselab.forsk.MapOptimization.mapRotation(PATH_FILE_MAP_ORG, PATH_FILE_MAP_ROT, NAME_FILE_MAP_ORG);
            com.caselab.forsk.MapOptimization.mapRotation(path, path_rot, fileTitle);
            Log.d("SKOnDeviceService", "Library-rotate Finish!");

            String path_opt = path + "opt/";
            File file_opt = new File(path_opt);
            // 폴더가 제대로 만들어졌는지 체크 ======
            if (!file_opt.mkdirs()) {

                Log.e("FILE", "Directory not created : " + path_opt);

            }
            Log.d("SKOnDeviceService", "Run library-line opt!");
            com.caselab.forsk.MapOptimization.lineOptimization(path_rot, path_opt, fileTitle);
            Log.d("SKOnDeviceService", "Library-line Finish!");

            String strPgmFile = path_opt + fileTitle + ".pgm";

            lib_flag = true;
            srcMapPgmFilePath = strPgmFile;
            srcMapYamlFilePath = path_opt + fileTitle + ".yaml";

        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library not loaded or linked properly", e);
        } catch (ExceptionInInitializerError e) {
            Log.e(TAG, "Initialization error in native method", e);
        } catch (RuntimeException e) {
            Log.e(TAG, "Runtime exception occurred", e);
        } catch (Throwable e) {
            Log.e(TAG, "Unexpected error occurred", e);
        }
    }

    // 지도 이미지 불러오기
    private Bitmap loadPGM(String filePath) throws IOException {
        Log.d(TAG, "loadPGM(\"" + filePath + "\")");

        String[] paths = filePath.split("/");
        String fileName = paths[paths.length - 1];
        Log.d(TAG, "fileName : " + fileName);

        File file = new File(filePath);
        try (FileInputStream fis = new FileInputStream(file)) {
            // Basic PGM file decoding
            String magicNumber = readToken(fis);  // Reads "P5"
            if (!"P5".equals(magicNumber)) throw new IOException("Not a PGM file");

            int width = Integer.parseInt(readToken(fis));
            int height = Integer.parseInt(readToken(fis));
            int maxGray = Integer.parseInt(readToken(fis));

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int grayValue = fis.read();
                    // original PGM은 흰색:254, 0:검은색,else:회색
                    // rotate pgm은 흰색 255, Else:회색(220)
                    if ((grayValue == 254) || (grayValue == 255)) { //내부와 외걱선 옅은 회색 #969696
                        grayValue = 150;
                    } else if (grayValue == 0) {
                        grayValue = 17;
                    } else {    //외부(254)는 짙은 회색 #333333
                        grayValue = 51;
                    }
                    int color = Color.rgb(grayValue, grayValue, grayValue);
                    bitmap.setPixel(x, y, color);
                }
            }

            return bitmap;

        } catch (FileNotFoundException fe) {
            Log.e(TAG, "loadPGM FileNotFoundException: " + fe.getMessage());
            verifyStoragePermissions(this);
        }
        return null;

    }

    private static String readToken(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = is.read()) != -1) {
            if (b == ' ' || b == '\n') break;
            sb.append((char) b);
        }
        return sb.toString();
    }

    // 로봇에서 사용하는 좌표계
    private boolean loadYaml(String filePath) {
        // 내부 저장소에서 파일 스트림 열기
        try (InputStream inputStream = new FileInputStream(filePath)) {

            Yaml yaml = new Yaml();

            // YAML 파싱
            Map<String, Object> data = yaml.load(inputStream);

            // nResolution = (double) data.get("resolution");
            // nResolution 처리
            //Log.d(TAG, "resolution : " + nResolution);
            Object resolutionValue = data.get("resolution");
            if (resolutionValue instanceof Number) {
                nResolution = ((Number) resolutionValue).doubleValue();
                Log.d(TAG, "resolution : " + nResolution);
            } else {
                Log.d(TAG, "Resolution is not a valid number.");
            }

            //ArrayList<Double> origin = (ArrayList<Double>) data.get("origin");
            ArrayList<?> origin = (ArrayList<?>) data.get("origin");
            if (origin != null && origin.size() >= 3) {
                Object originX = origin.get(0);
                Object originY = origin.get(1);
                Object originAngle = origin.get(2);

                if (originX instanceof Number) {
                    origin_x = ((Number) originX).doubleValue();
                    Log.d(TAG, "origin_x : " + origin_x);
                } else {
                    Log.d(TAG, "origin_x is not a valid number.");
                }

                if (originY instanceof Number) {
                    origin_y = ((Number) originY).doubleValue();
                    Log.d(TAG, "origin_y : " + origin_y);
                } else {
                    Log.d(TAG, "origin_y is not a valid number.");
                }

                if (originAngle instanceof Number) {
                    origin_angle = ((Number) originAngle).doubleValue();
                    Log.d(TAG, "origin_angle : " + origin_angle);
                } else {
                    Log.d(TAG, "origin_angle is not a valid number.");
                }
            } else {
                Log.d(TAG, "Origin data is incomplete or invalid.");
            }

            // 241222 최적화 라이브러리 읽을 경우 추가.
            if (lib_flag) {
                // OpenCV 네이티브 라이브러리 로드
                try {
                    if (!OpenCVLoader.initDebug()) {
                        Log.e("OpenCV", "Initialization failed.");
                    } else {
                        Log.d("OpenCV", "Initialization succeeded.");
                    }
                } catch (UnsatisfiedLinkError e) {
                    Log.e("OpenCV", "Native library not found or incompatible ABI: " + e.toString());
                } catch (SecurityException e) {
                    Log.e("OpenCV", "Security exception while loading OpenCV: " + e.toString());
                } catch (RuntimeException e) {
                    Log.e("OpenCV", "Runtime exception during OpenCV initialization: " + e.toString());
                }
                //original_image_height = (int)data.get("original_image_height");
                Object originalImageHeightValue = data.get("original_image_height");
                if (originalImageHeightValue instanceof Number) {
                    original_image_height = ((Number) originalImageHeightValue).intValue();
                    Log.d(TAG, "original_image_height: " + original_image_height);
                } else {
                    Log.d(TAG, "original_image_height is not a valid number.");
                }

                ArrayList<ArrayList<?>> matrixList = (ArrayList<ArrayList<?>>) data.get("transformation_matrix");
                if (matrixList != null && !matrixList.isEmpty()) {
                    int rows = matrixList.size();
                    int cols = matrixList.get(0).size();

                    transformationMatrix = new Mat(rows, cols, CvType.CV_64F);

                    for (int i = 0; i < rows; i++) {
                        ArrayList<?> row = matrixList.get(i);
                        for (int j = 0; j < cols; j++) {
                            Object cellValue = row.get(j);
                            if (cellValue instanceof Number) {
                                transformationMatrix.put(i, j, ((Number) cellValue).doubleValue());
                                Log.d(TAG, "transformation_matrix row " + i + " , " + j + " : " + ((Number) cellValue).doubleValue());
                            } else {
                                Log.d(TAG, "Invalid value in transformation_matrix at (" + i + ", " + j + ").");
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "Transformation matrix data is invalid.");
                }

                Object rotatedAngleValue = data.get("rotated_angle");
                if (rotatedAngleValue instanceof Number) {
                    rotated_angle = ((Number) rotatedAngleValue).floatValue();
                    Log.d(TAG, "Converted to float: " + rotated_angle);
                } else {
                    Log.d(TAG, "rotated_angle is not a valid number.");
                }
                //rotated_angle = (float) (double) data.get("rotated_angle");
                //Log.d(TAG,"rotated angle: " + rotated_angle);
            }


            return true;
        } catch (IOException | NullPointerException e) {
            Log.e(TAG, "loadYaml Exception: " + e.getMessage());
            return false;
        }
    }

    // 설정값 저장
    // calculateCoordinate() 홤수는 화면에 보이는 지도 좌표를 로봇좌표로 변환하는 함수
    public boolean roi_saveToFile(String strPath, String strFileName) {

        int i, j;
        int image_width = mapViewer.GetBitmapWidth();
        int image_height = mapViewer.GetBitmapHeight();

        Log.d(TAG, "image width : " + image_width + " , image_height : " + image_height);
        int count_id = 0;
        int count_id_rect = 0;
        int count_id_line = 0;

        Date now = new Date();
        // 원하는 포맷으로 설정
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        // 날짜를 포맷팅
        String formattedDate = sdf.format(now);

        String strRoiJson;
        strRoiJson = "";
        strRoiJson += "{";
        strRoiJson += "\"uid\":\"OnDevice\",\"info\":{\"version\":\"1.0.0\",\"modified\":";
        strRoiJson += "\"" + formattedDate + "\"}";
        strRoiJson += ", \"room_list\":[";

        Log.d(TAG, "MapViewer.m_RoiObjects.size() : " + mapViewer.m_RoiObjects.size());

        for (i = 0; i < mapViewer.m_RoiObjects.size(); i++) {

            if (mapViewer.m_RoiObjects.get(i).roi_type.equals("roi_polygon")) {
                if (count_id > 0) {
                    strRoiJson += ", ";
                }

                count_id++; // 고유 id


                strRoiJson += "{";
                strRoiJson += "\"id\": \"" + (count_id) + "\"";
                strRoiJson += ", \"name\": \"" + mapViewer.m_RoiObjects.get(i).m_label + "\"";
                strRoiJson += ", \"color\":\"#47910f\", \"desc\":\"\"";
                strRoiJson += ", \"robot_path\":[";
                //strRoiJson += "{\"x\":-2.97,\"y\":7.15},{\"x\":-3.02,\"y\":7.1000004},{\"x\":-3.17,\"y\":7.1000004},{\"x\":-3.22,\"y\":7.05},{\"x\":-3.22,\"y\":6.9},{\"x\":-3.27,\"y\":6.8500004},{\"x\":-3.27,\"y\":4},{\"x\":-3.22,\"y\":3.95},{\"x\":-2.47,\"y\":3.95},{\"x\":-2.42,\"y\":4},{\"x\":-2.22,\"y\":4},{\"x\":-2.17,\"y\":4.05},{\"x\":-1.7199999,\"y\":4.05},{\"x\":-1.7199999,\"y\":4.1},{\"x\":-1.67,\"y\":4.15},{\"x\":-1.67,\"y\":4.25},{\"x\":-1.62,\"y\":4.3},{\"x\":-1.62,\"y\":4.35},{\"x\":-1.5699999,\"y\":4.4},{\"x\":-1.5699999,\"y\":4.4500003},{\"x\":-1.52,\"y\":4.5},{\"x\":-1.52,\"y\":4.6},{\"x\":-1.4699999,\"y\":4.65},{\"x\":-1.4699999,\"y\":4.7000003},{\"x\":-1.42,\"y\":4.75},{\"x\":-1.42,\"y\":4.8500004},{\"x\":-1.37,\"y\":4.9},{\"x\":-1.37,\"y\":4.9500003},{\"x\":-1.3199999,\"y\":5},{\"x\":-1.3199999,\"y\":5.05},{\"x\":-1.27,\"y\":5.1000004},{\"x\":-1.27,\"y\":5.2000003},{\"x\":-1.2199999,\"y\":5.25},{\"x\":-1.2199999,\"y\":5.3},{\"x\":-1.17,\"y\":5.3500004},{\"x\":-1.17,\"y\":5.4500003},{\"x\":-1.12,\"y\":5.5},{\"x\":-1.12,\"y\":5.55},{\"x\":-1.0699999,\"y\":5.6000004},{\"x\":-1.0699999,\"y\":5.65},{\"x\":-1.02,\"y\":5.7000003},{\"x\":-1.02,\"y\":5.8},{\"x\":-0.96999997,\"y\":5.8500004},{\"x\":-0.96999997,\"y\":5.9},{\"x\":-0.91999996,\"y\":5.9500003},{\"x\":-0.91999996,\"y\":6.05},{\"x\":-0.86999995,\"y\":6.1000004},{\"x\":-0.86999995,\"y\":6.15},{\"x\":-0.81999993,\"y\":6.2000003},{\"x\":-0.81999993,\"y\":6.3},{\"x\":-0.77,\"y\":6.3500004},{\"x\":-0.77,\"y\":6.4},{\"x\":-1.27,\"y\":6.4},{\"x\":-1.37,\"y\":6.5},{\"x\":-1.37,\"y\":6.8},{\"x\":-1.42,\"y\":6.8500004},{\"x\":-1.42,\"y\":7},{\"x\":-1.5699999,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15}";

                for (j = 0; j < mapViewer.m_RoiObjects.get(i).m_Points.size(); j++) {

                    if (j > 0) {
                        strRoiJson += ", ";
                    }

                    strRoiJson += "{";

                    double[] coordinates = calculateCoordinate(mapViewer.m_RoiObjects.get(i).m_Points.get(j).x, mapViewer.m_RoiObjects.get(i).m_Points.get(j).y, image_height);

                    double path_x = coordinates[0];
                    double path_y = coordinates[1];

                    strRoiJson += "\"x\":" + path_x;
                    strRoiJson += ", \"y\":" + path_y;

                    strRoiJson += "}";
                    // roi_rect
                    // roi_line
                }
                strRoiJson += "]";
                strRoiJson += ", \"image_path\":[";
                for (j = 0; j < mapViewer.m_RoiObjects.get(i).m_Points.size(); j++) {

                    if (j > 0) {
                        strRoiJson += ", ";
                    }

                    strRoiJson += "{";

                    strRoiJson += "\"x\":" + (int) (mapViewer.m_RoiObjects.get(i).m_Points.get(j).x);
                    strRoiJson += ", \"y\":" + (int) (mapViewer.m_RoiObjects.get(i).m_Points.get(j).y);

                    strRoiJson += "}";
                    // roi_rect
                    // roi_line
                }
                strRoiJson += "]";
                strRoiJson += ", \"robot_position\":{";

                double[] coordinates = calculateCoordinate(mapViewer.m_RoiObjects.get(i).m_MBR_center.x, mapViewer.m_RoiObjects.get(i).m_MBR_center.y, image_height);

                double xvw = coordinates[0];
                double yvh = coordinates[1];
                strRoiJson += "\"x\":" + xvw;
                strRoiJson += ", \"y\":" + yvh;
                double angle = mapViewer.m_RoiObjects.get(i).getAngle() - Math.toRadians(rotated_angle);

                angle = ((angle + Math.PI) % (2 * Math.PI)) - Math.PI;
                strRoiJson += ", \"theta\":" + angle;

                strRoiJson += "}";

                strRoiJson += ", \"image_position\":{";
                int xvw_image = 0; // polygon의 무게중심 x

                int yvh_image = 0; // polygon의 무게중심 x

                // Log.d(TAG, "xvw before : "+ xvw);
                xvw_image = mapViewer.m_RoiObjects.get(i).m_MBR_center.x;
                yvh_image = mapViewer.m_RoiObjects.get(i).m_MBR_center.y;

                // MapViewer.m_RoiObjects.get(i).m_MBR;
                //Log.d(TAG,"height: " + image_height +", origin_y: "+ origin_y + ", imagey: " + MapViewer.m_RoiObjects.get(i).m_Points.get(j).y + ", real_y: "+ (float)((image_height-MapViewer.m_RoiObjects.get(i).m_Points.get(j).y)*nResolution + origin_y));
                //Toast.makeText(getApplicationContext(), "X: " + (float)(xvw * nResolution + origin_x) +", Y: " + ((image_height - yvh) * nResolution + origin_y), Toast.LENGTH_SHORT).show();

                strRoiJson += "\"x\":" + (int) xvw_image;
                strRoiJson += ", \"y\":" + (int) yvh_image;
                strRoiJson += ", \"theta\":" + mapViewer.m_RoiObjects.get(i).getAngle();
                strRoiJson += ", \"is_set_theta\":" + mapViewer.m_RoiObjects.get(i).isSetTheta;
                strRoiJson += ", \"isAssign\":true";
                strRoiJson += "}";
                strRoiJson += "}";
            }

        }
        strRoiJson += "]";
        strRoiJson += ", \"block_area\":[";
        for (i = 0; i < mapViewer.m_RoiObjects.size(); i++) {
            if (mapViewer.m_RoiObjects.get(i).roi_type.equals("roi_rect")) {
                if (count_id_rect > 0) {
                    strRoiJson += ", ";
                }

                count_id_rect++; // 고유 id


                strRoiJson += "{";
                strRoiJson += "\"image_path\":[";
                strRoiJson += "{";

                //left top
                strRoiJson += "\"x\":" + (int) (mapViewer.m_RoiObjects.get(i).m_MBR.left);
                strRoiJson += ", \"y\":" + (int) (mapViewer.m_RoiObjects.get(i).m_MBR.bottom);
                strRoiJson += "}, {";

                // right top
                strRoiJson += "\"x\":" + (int) (mapViewer.m_RoiObjects.get(i).m_MBR.right);
                strRoiJson += ", \"y\":" + (int) (mapViewer.m_RoiObjects.get(i).m_MBR.bottom);
                strRoiJson += "}, {";

                // right bottom
                strRoiJson += "\"x\":" + (int) (mapViewer.m_RoiObjects.get(i).m_MBR.right);
                strRoiJson += ", \"y\":" + (int) (mapViewer.m_RoiObjects.get(i).m_MBR.top);
                strRoiJson += "}, {";

                // left bottom
                strRoiJson += "\"x\":" + (int) (mapViewer.m_RoiObjects.get(i).m_MBR.left);
                strRoiJson += ", \"y\":" + (int) (mapViewer.m_RoiObjects.get(i).m_MBR.top);

                strRoiJson += "}";

                strRoiJson += "]";
                strRoiJson += ", \"robot_path\":[";


                strRoiJson += "{";

                // left top
                double[] coordinates = calculateCoordinate(mapViewer.m_RoiObjects.get(i).m_MBR.left, mapViewer.m_RoiObjects.get(i).m_MBR.bottom, image_height);
                double area_x = coordinates[0];
                double area_y = coordinates[1];

                strRoiJson += "\"x\":" + area_x;
                strRoiJson += ", \"y\":" + area_y;
                strRoiJson += "}, {";

                // right top
                coordinates = calculateCoordinate(mapViewer.m_RoiObjects.get(i).m_MBR.right, mapViewer.m_RoiObjects.get(i).m_MBR.bottom, image_height);
                area_x = coordinates[0];
                area_y = coordinates[1];

                strRoiJson += "\"x\":" + area_x;
                strRoiJson += ", \"y\":" + area_y;
                strRoiJson += "}, {";

                // right bottom
                coordinates = calculateCoordinate(mapViewer.m_RoiObjects.get(i).m_MBR.right, mapViewer.m_RoiObjects.get(i).m_MBR.top, image_height);
                area_x = coordinates[0];
                area_y = coordinates[1];

                strRoiJson += "\"x\":" + area_x;
                strRoiJson += ", \"y\":" + area_y;
                strRoiJson += "}, {";

                // left bottom
                coordinates = calculateCoordinate(mapViewer.m_RoiObjects.get(i).m_MBR.left, mapViewer.m_RoiObjects.get(i).m_MBR.top, image_height);
                area_x = coordinates[0];
                area_y = coordinates[1];

                strRoiJson += "\"x\":" + area_x;
                strRoiJson += ", \"y\":" + area_y;
                strRoiJson += "}";

                strRoiJson += "]";
                strRoiJson += ", \"id\": \"a22fd01c-69b9-437f-b81e-bbf4955bd034" + (int) (Math.random() * 255) + (int) (Math.random() * 255) + "\"";
                strRoiJson += "}";
            }

        }
        strRoiJson += "]";

        strRoiJson += ", \"block_wall\":[";

        for (i = 0; i < mapViewer.m_RoiObjects.size(); i++) {

            if (mapViewer.m_RoiObjects.get(i).roi_type.equals("roi_line")) {
                if (count_id_line > 0) {
                    strRoiJson += ", ";
                }

                Log.d(TAG, "image_height: " + image_height + ", " + "image width: " + image_width);

                count_id_line++;
                CDrawObj roi = mapViewer.m_RoiObjects.get(i);

                strRoiJson += "{\"image_path\":[";

                // Add start point
                strRoiJson += "{\"x\":" + (int) roi.m_MBR.left + ", \"y\":" + (int) (roi.m_MBR.top) + "}, ";

                // Add end point
                strRoiJson += "{\"x\":" + (int) roi.m_MBR.right + ", \"y\":" + (int) (roi.m_MBR.bottom) + "}], ";

                strRoiJson += "\"robot_path\":[";

                // Convert image coordinates to robot coordinates
                double[] startCoordinates = calculateCoordinate(roi.m_MBR.left, roi.m_MBR.top, image_height);
                double[] endCoordinates = calculateCoordinate(roi.m_MBR.right, roi.m_MBR.bottom, image_height);

                // Add start robot path
                strRoiJson += "{\"x\":" + startCoordinates[0] + ", \"y\":" + startCoordinates[1] + "}, ";

                // Add end robot path
                strRoiJson += "{\"x\":" + endCoordinates[0] + ", \"y\":" + endCoordinates[1] + "}], ";

                // Add unique ID
                strRoiJson += "\"id\":\"d4f940b8-81cf-4b46-82d6-8f10f4e03038" + (int) (Math.random() * 255) + "\"}";
            }


        }
        strRoiJson += "]";

        strRoiJson += ", \"assign_info\":{}";
        strRoiJson += ", \"user_angle\":0";

        strRoiJson += "}";
        Log.d(TAG, strRoiJson);

        //권한 상태 체크 팝업 띄우기
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE}, 2);
            }
        }

        // 액션 바와 상태 바 숨기
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        // 파일 경로를 설정합니다.
        //File file = new File(downloadDir, strFileName);
        File file = new File(strPath, strFileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {

            Log.d(TAG, strPath + "/" + strFileName);

            fos.write(strRoiJson.getBytes());
            fos.flush();
            Thread.sleep(1000); // 파일쓰기가 완료될 까지 기다린다.
            fos.close();

            return true;

        } catch (FileNotFoundException fe) {
            Log.d(TAG, Objects.requireNonNull(fe.getLocalizedMessage()));
            Toast.makeText(getApplicationContext(), fe.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        } catch (InterruptedException | IOException ie) {
            Log.d(TAG, Objects.requireNonNull(ie.getLocalizedMessage()));
        }

        return false;
    }


    // 도형 설정없는 기본 json파일
    public boolean roi_saveToEmptyFile(String strPath, String strFileName) {

        int i, j;

        String strRoiJson;
        strRoiJson = "";
        strRoiJson += "{";
        strRoiJson += "\"uid\":\"Tybqxakqm2\",\"info\":{\"version\":\"1.0.0\",\"modified\":\"2024-11-13T20:47:41.739\"}";
        strRoiJson += ", \"room_list\":[";
        strRoiJson += "]";
        strRoiJson += ", \"block_area\":[";
        strRoiJson += "]";
        strRoiJson += ", \"block_wall\":[";
        strRoiJson += "]";
        strRoiJson += ", \"assign_info\":{}";
        strRoiJson += ", \"user_angle\":0";
        strRoiJson += "}";

        Log.d(TAG, strRoiJson);

        //권한 상태 체크 팝업 띄우기
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE}, 2);
        }

        // 액션 바와 상태 바 숨기
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        File file = new File(strPath, strFileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            Log.d(TAG, strPath + "/" + strFileName);
            fos.write(strRoiJson.getBytes());
            fos.flush();
            Thread.sleep(1000); // 파일쓰기가 완료될 까지 기다린다.
            fos.close();

            return true;

        } catch (IOException ie) {
            Log.d(TAG, Objects.requireNonNull(ie.getLocalizedMessage()));
            Toast.makeText(getApplicationContext(), ie.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return false;
    }

    // 지도 설정된 값을 불러온다.
    private synchronized boolean loadJson(String filePath) {
        Log.d(TAG, "Exist Json File");
        StringBuilder jsonStringBuilder = new StringBuilder();

        // 내부 저장소에서 파일 스트림 열기
        // InputStream 데이터를 문자열로 변환
        try (InputStream inputStream = new FileInputStream(filePath); InputStreamReader inputStreamReader = new InputStreamReader(inputStream); BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {

            String line;

            while ((line = bufferedReader.readLine()) != null) {
                jsonStringBuilder.append(line);
            }

            // JSON 문자열로 변환
            String jsonString = jsonStringBuilder.toString();

            // JSON 파싱
            JSONObject jsonObject = new JSONObject(jsonString);

            int x = 0;
            int y = 0;
            int left = 0, right = 0, bottom = 0, top = 0;
            // 1. room_list 데이터 읽기
            System.out.println("Room List:");
            JSONArray roomList = jsonObject.getJSONArray("room_list");
            for (int i = 0; i < roomList.length(); i++) {
                JSONObject room = roomList.getJSONObject(i);
                String id = room.getString("id");
                String name = room.getString("name");
                JSONArray imagePathArray = room.getJSONArray("image_path");
                JSONObject imagePosition = room.getJSONObject("image_position");
                boolean isSetTheta = imagePosition.getBoolean("is_set_theta");
                if (!isSetTheta) isSetTheta = false;
                int mbr_x = imagePosition.getInt("x");
                int mbr_y = imagePosition.getInt("y");
                Log.d(TAG, "  Room ID: " + id);
                Log.d(TAG, "  Name: " + name);
                Log.d(TAG, "  Image Path:");
                Log.d(TAG, "  ImagePosition: " + mbr_x + " " + mbr_y);
                for (int j = 0; j < imagePathArray.length(); j++) {
                    JSONObject point = imagePathArray.getJSONObject(j);
                    x = point.getInt("x");
                    y = point.getInt("y");
                    Log.d(TAG, "    Point: (" + x + ", " + y + ")");
                    if (j == 0) {
                        mapViewer.CObject_LoadObject("roi_polygon", new Point(x, y));
                    } else {
                        mapViewer.AddPoint_Polygon(new Point(x, y));
                    }
                    if (left > x) left = x;
                    if (right < x) right = x;
                    if (top > y) top = y;
                    if (bottom < y) bottom = y;

                }
                mapViewer.m_drawing = true;
                mapViewer.roi_AddObject();
                mapViewer.m_RoiCurObject.isSetTheta = isSetTheta;
                // 241222 seongwoong 현재 버그 있음 확인 필요
                //MapViewer.m_RoiCurObject.m_MBR = new Rect(left, top, right, bottom);
                mapViewer.m_RoiCurObject.m_MBR_center.x = mbr_x;
                mapViewer.m_RoiCurObject.m_MBR_center.y = mbr_y;
                mapViewer.SetLabel(name);
                if (lib_flag) {
                    float theta = (float) imagePosition.getDouble("theta");
                    mapViewer.m_RoiCurObject.setAngle(theta);
                }
            }


            // 2. block_area 데이터 읽기
            Log.d(TAG, "\nBlock Area:");
            JSONArray blockAreaArray = jsonObject.getJSONArray("block_area");
            for (int i = 0; i < blockAreaArray.length(); i++) {
                JSONObject block = blockAreaArray.getJSONObject(i);
                JSONArray imagePathArray = block.getJSONArray("image_path");

                Log.d(TAG, "  Block Area Image Path:");
                JSONObject point = imagePathArray.getJSONObject(0);
                x = point.getInt("x");
                y = point.getInt("y");
                Point pt1 = new Point(x, y);
                Log.d(TAG, "    Point: (" + x + ", " + y + ")");
                JSONObject point2 = imagePathArray.getJSONObject(2);
                x = point2.getInt("x");
                y = point2.getInt("y");
                Point pt2 = new Point(x, y);

                Log.d(TAG, "    Point: (" + x + ", " + y + ")");

                mapViewer.CObject_LoadObject("roi_rect", pt1);
                mapViewer.CObject_LoadRect(pt1, pt2);
                mapViewer.roi_AddObject();

            }

            // 3. block_wall 데이터 읽기
            Log.d(TAG, "\nBlock Wall:");
            JSONArray blockWallArray = jsonObject.getJSONArray("block_wall");
            for (int i = 0; i < blockWallArray.length(); i++) {
                JSONObject block = blockWallArray.getJSONObject(i);
                JSONArray imagePathArray = block.getJSONArray("image_path");

                Log.d(TAG, "  Block Wall Image Path:");
                JSONObject point = imagePathArray.getJSONObject(0);
                x = point.getInt("x");
                y = point.getInt("y");
                Point pt1 = new Point(x, y);

                Log.d(TAG, "    Point: (" + x + ", " + y + ")");

                JSONObject point2 = imagePathArray.getJSONObject(1);
                x = point2.getInt("x");
                y = point2.getInt("y");
                Point pt2 = new Point(x, y);

                Log.d(TAG, "    Point: (" + x + ", " + y + ")");

                mapViewer.CObject_LoadObject("roi_line", pt1);
                mapViewer.CObject_LoadRect(pt1, pt2);
                mapViewer.roi_AddObject();
            }

            int[] stationCoordinates = getStationPos(0, 0, original_image_height); // 원점 좌표에 스테이지가 있다.
            CDrawObj obj = new CDrawObj("roi_point", stationCoordinates[0], stationCoordinates[1], stationCoordinates[0], stationCoordinates[1]);
            obj.SetString("스테이션");
            obj.m_labelviewflag = true;

            mapViewer.m_StationObjects.add(obj);

            mapViewer.m_RoiCurIndex = -1;
            mapViewer.m_RoiCurObject = null;

            Log.d(TAG, "Read Json Success");
            return true;
        } catch (FileNotFoundException fe) {
            Log.e(TAG, "Read Json FileNotFoundException: " + filePath + " " + fe.getMessage());
            return false;
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Read Json JSON, IO Exception: " + e.getMessage());
            return false;
        } finally {
            // 자원이 자동으로 닫히는지 확인할 필요가 없지만 명시적으로 로깅 가능
            Log.d(TAG, "loadjson end");

        }
    }

    // 이미지 보정전 이미지 좌표로 변환
    public Point transformToRobotCoordinates(int image_x, int image_y) {
        // 1. 이미지 좌표를 3x1 행렬로 변환
        Mat pointMat = new Mat(3, 1, CvType.CV_64F);
        pointMat.put(0, 0, image_x); // transformed_pixel_x
        pointMat.put(1, 0, image_y); // transformed_pixel_y
        pointMat.put(2, 0, 1.0);           // Homogeneous coordinate

        // 2. 변환 행렬의 역행렬 계산
        Mat inverseTransformationMatrix = new Mat();
        Core.invert(transformationMatrix, inverseTransformationMatrix);

        // 3. 역행렬 적용
        Mat inverseTransformedPointMat = new Mat();
        Core.gemm(inverseTransformationMatrix, pointMat, 1, new Mat(), 0, inverseTransformedPointMat);

        // 4. 원래 이미지 좌표 추출
        // 241226 성웅 int의 오차가 얼마인지 확인 필요

        int original_pixel_x = (int) Math.round(inverseTransformedPointMat.get(0, 0)[0]);
        int original_pixel_y = (int) Math.round(inverseTransformedPointMat.get(1, 0)[0]);

        //Log.d(TAG, "check dobule to int inversion. dobule: " + inverseTransformedPointMat.get(0, 0)[0] + ",int:  " + original_pixel_x);
        // 5. 로봇 좌표로 변환
        //double robot_x = (original_pixel_x * nResolution) + origin_x;
        //double robot_y = (original_image_height - original_pixel_y) * nResolution + origin_y;

        return new Point(original_pixel_x, original_pixel_y);
    }

    // 지도 좌표를 로봇 좌표로 변환
    public double[] calculateCoordinate(int x, int y, int image_height) {
        // 계산 공식: (x * resolution + origin_x)
        double robot_x = 0.0;
        double robot_y = 0.0;

        if (lib_flag) {
            Point pt = transformToRobotCoordinates(x, y);
            robot_x = pt.x * nResolution + origin_x;
            robot_y = (original_image_height - pt.y) * nResolution + origin_y;
        } else {
            robot_x = x * nResolution + origin_x;
            robot_y = (image_height - y) * nResolution + origin_y;
        }
        return new double[]{robot_x, robot_y};
    }

    // 로봇 좌표를 현재 이미지 좌표로 변환
    // 스테이션(충전소)의 경우, (0, 0)은 무조건 있어야 함.
    public int[] getStationPos(double x, double y, int image_height) {
        Log.d(TAG, "getStationPos( " + x + ", " + y + ", " + image_height + " )");

        int station_x = 0;
        int station_y = 0;

        // 원본 이미지의 좌표를 구한다.
        int img_x = (int) ((x - origin_x) / nResolution);
        int img_y = (int) (original_image_height - (y - origin_y) / nResolution);

        // 영상처리 되어 이미지 크기나 회전이 있으면 적용한다.
        if (lib_flag) {
            // 1. 이미지 좌표를 3x1 행렬로 변환
            Mat pointMat = new Mat(3, 1, CvType.CV_64F);
            pointMat.put(0, 0, img_x); // origin_pixel_x
            pointMat.put(1, 0, img_y); // origin_pixel_y
            pointMat.put(2, 0, 1.0);           // Homogeneous coordinate

            // 2. 이미지 좌표를 구한다.
            Mat inverseTransformedPointMat = new Mat();
            Core.gemm(transformationMatrix, pointMat, 1, new Mat(), 0, inverseTransformedPointMat);

            station_x = (int) Math.round(inverseTransformedPointMat.get(0, 0)[0]); // transformed_pixel_x
            station_y = (int) Math.round(inverseTransformedPointMat.get(1, 0)[0]); // transformed_pixel_y

        } else {
            station_x = img_x;
            station_y = img_y;
        }

        Log.d(TAG, "station_position( : " + station_x + ", " + station_y + " )");

        return new int[]{station_x, station_y};
    }
}

