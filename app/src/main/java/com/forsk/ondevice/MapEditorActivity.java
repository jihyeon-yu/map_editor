package com.forsk.ondevice;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;

import com.caselab.forsk.MapOptimization;

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

    private static final String TAG = "MapEditorActivity";

    private static final String NAME_LIBRARY_CASELAB_OPT    =
//                                                              "mapoptimizationV2";
            "mapoptimization241217v11";

    static {
        try {
            System.loadLibrary(NAME_LIBRARY_CASELAB_OPT);
            Log.d("SKOnDeviceService", "SO library load success!");
        } catch(Exception e) {
            Log.d("SKOnDeviceService", "SO library load error : " + e.toString());
        }
    }
    /*
        PATH_FILE_MAP_ORG : 가공되지 않은 원본 pgm, yaml 파일이 존재하는 폴더
        PATH_FILE_MAP_ROT : so 라이브리를 이용해서 가공된 pgm, yaml 결과 파일일 만들어질 폴더
        NAME_FILE_MAP_ORG : 파일형식 확장자(pgm,yaml) 제외한 파일명
        PATH_FILE_MAP_OPT : 옵티마이징한 결과를 저장할 폴더명
        PATH_FILE_MAP_SEG : 세그멘트 결과를 저장할 폴더명
        참고 : NAME_FILE_MAP_ORG의 파일명으로 각각 다른 폴더에 결과물이 나오면 최종 결과물은 PATH_FILE_MAP_ROT 폴더에 저장됨
     */
    private static final String PATH_FILE_MAP_ORG           = "/sdcard/Download/";
    private static final String PATH_FILE_MAP_ROT           = "/sdcard/Download/map_test/rot/";
    private static final String NAME_FILE_MAP_ORG           = "office";//"5py";
    private static final String PATH_FILE_MAP_OPT           = "/sdcard/Download/map_test/opt/";
    private static final String PATH_FILE_MAP_SEG           = "/sdcard/Download/map_test/seg/";


    private boolean isFabOpen = false;

    private static final int PICK_PGM_FILE_REQUEST = 1;


    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    public static void verifyStoragePermissions(Activity activity) {
        int permission = ActivityCompat.checkSelfPermission(activity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));

            activity.startActivity(intent);
        }
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }


    private MapImageView MapViewer;

    String strMode = "Zoom";

    String srcMapPgmFilePath = "";
    String srcMapYamlFilePath = "";
    //String srcMappingFilePath = "";
    String destMappingFilePath = "";

    double nResolution = 0.0;
    double origin_x = 0.0;
    double origin_y = 0.0;
    double origin_angle = 0.0;

    // 모드 상수 정의
    private static final int MODE_MAP_EXPLORATION = 0;
    private static final int MODE_SPACE_CREATION = 1;
    private static final int MODE_BLOCK_WALL = 2;
    private static final int MODE_BLOCK_AREA = 3;
    private static final int MODE_EDIT_PIN = 4;

    // 현재 모드 변수
    private int currentMode = MODE_MAP_EXPLORATION;

    // 추가된 TextView 참조
    TextView modeDescription;

    private String PinName = "";

    private float scaleFactor = 1.0f;

    // 선택된 버튼 저장 변수
    Button selectedButton = null;

    private ConstraintLayout toggleBar;
    private ConstraintLayout toggleBar_CreateSpace;
    private boolean lib_flag = true;

    private Mat transformationMatrix = null;

    private float rotated_angle = 0f;
    int original_image_height = 0;

    // 삭제 토글바 관련 변수
    private View deleteToggleBar;
    private ImageView deleteButton, cancelButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        setTheme(R.style.Theme_OnDevice);

        super.onCreate(null);

//        super.onCreate(savedInstanceState);

        // 상태 바 제거
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);


        setContentView(R.layout.activity_mapeditor);

        // Mode-specific TextView 초기화
        modeDescription = findViewById(R.id.mode_description);

        // Toggle Bar 레이아웃 가져오기
        toggleBar = findViewById(R.id.toggle_bar);
        toggleBar_CreateSpace = findViewById(R.id.toggle_bar_createspace);



        // Toggle Bar 초기 상태 설정
        updateToggleBarVisibility();

        // Description 초기 상태 설정
        updateModeDescription();

        Button fabMain = findViewById(R.id.fabMain);
        View fabMainBack = findViewById(R.id.fabMainBack);
        View fabMainBackCS = findViewById(R.id.fabMainBackCS);


        // 맵 탐색 - 줌 인/아웃, 맵 이동
        View fabMoveMap = findViewById(R.id.fabMoveMap);

        // 공간생성,가상벽,금지구역,청저위치 - 객체 추가, 객체 이동, 선택, 제거, 이름 변경
        View fabAddObject = findViewById(R.id.fabAddObject);
        View fabMoveObject = findViewById(R.id.fabMoveObject);
        View fabSelectObject = findViewById(R.id.fabSelectObject);
        View fabDeleteObject = findViewById(R.id.fabDeleteObject);
        View fabMovePin = findViewById(R.id.fabMovePin);
        View fabRotatePin = findViewById(R.id.fabRotatePin);

        View fabAddObjectCS = findViewById(R.id.fabAddObjectCS);
        View fabMoveObjectCS = findViewById(R.id.fabMoveObjectCS);
        View fabSelectObjectCS = findViewById(R.id.fabSelectObjectCS);
        View fabDeleteObjectCS = findViewById(R.id.fabDeleteObjectCS);
        View fabRenameObjectCS = findViewById(R.id.fabRenameObjectCS);
        View fabMovePinCS = findViewById(R.id.fabMovePinCS);
        View fabRotatePinCS = findViewById(R.id.fabRotatePinCS);

        Button finshButton = findViewById(R.id.finish_button);

        Button cancleButton = findViewById(R.id.cancel_button);
        Button gobackButton = findViewById(R.id.goback_button);

        // 메뉴 클릭 시 배경 생성 버튼
        View fabAddObjectClicked = findViewById(R.id.fabAddObjectClicked);
        View fabSelectObjectClicked = findViewById(R.id.fabSelectObjectClicked);
        View fabMoveObjectClicked = findViewById(R.id.fabMoveObjectClicked);
        View fabMovePinClicked = findViewById(R.id.fabMovePinClicked);
        View fabRotatePinClicked = findViewById(R.id.fabRotatePinClicked);
        View fabDeleteObjectClicked = findViewById(R.id.fabDeleteObjectClicked);
        View fabRenameObjectClicked = findViewById(R.id.fabRenameObjectClicked);

        // 삭제 버튼 초기화 (onCreate 메서드에서)
        deleteToggleBar = findViewById(R.id.delete_toggle_bar);
        deleteButton = deleteToggleBar.findViewById(R.id.deleteButton);
        cancelButton = deleteToggleBar.findViewById(R.id.cancelButton);

        // 241217 jihyeon
        // cancle 버튼과 goback 버튼 구분하여 구현 필요
        // cancle: 종료 확인 팝업 노출 후 확인 선택 시 홈화면 이동
        // goback: 맵그리기 <자동/수동> 선택화면으로 복귀
        cancleButton.setOnClickListener(v -> {
            Log.d(TAG, "canclebutton.setOnClickListener(...)");
            try {
                String strFileName = "map_meta_sample.json";
                String strPath = "/sdcard/Download";
                File destFile = new File(strPath, strFileName);

                if (!destFile.isFile()) {
                    if (roi_saveToEmptyFile(strPath, strFileName))
                        Log.d(TAG, "Success create empty Json File");
                    else Log.d(TAG, "Fail create empty File");
                } else {
                    Log.d(TAG, "Already Exist Json FIle");
                }

                Log.d(TAG, "send broadcast... ");
                Intent intent = new Intent("sk.action.airbot.map.responseMapping");
                //intent.setPackage("com.sk.airbot.skmlauncher"); // 2024.12.04 이전
                intent.setPackage("com.sk.airbot.iotagent");
                intent.putExtra("destMappingFilePath", strPath + "/" + strFileName);
                //intent.putExtra("destMappingFilePath", strPath+File.separator+strFileName);
                intent.putExtra("resultCode", "MRC_000");

                sendBroadcast(intent);
                Log.d(TAG, "sent broadcast. ");

                Thread.sleep(1000);

                Log.d(TAG, "finish activity ");
                //System.exit(0);
                finish();
            } catch (InterruptedException ie) {
                Log.e(TAG, "Cancle Button InterruptedExtception: " + ie.getMessage());
            }

        });

        gobackButton.setOnClickListener(v -> {
            Log.d(TAG, "canclebutton.setOnClickListener(...)");
            try {
                String strFileName = "map_meta_sample.json";
                String strPath = "/sdcard/Download";
                File destFile = new File(strPath, strFileName);

                if (!destFile.isFile()) {
                    if (roi_saveToEmptyFile(strPath, strFileName))
                        Log.d(TAG, "Success create empty Json File");
                    else Log.d(TAG, "Fail create empty File");
                } else {
                    Log.d(TAG, "Already Exist Json FIle");
                }

                Log.d(TAG, "send broadcast... ");
                Intent intent = new Intent("sk.action.airbot.map.responseMapping");
                //intent.setPackage("com.sk.airbot.skmlauncher"); // 2024.12.04 이전
                intent.setPackage("com.sk.airbot.iotagent");
                intent.putExtra("destMappingFilePath", strPath + "/" + strFileName);
                //intent.putExtra("destMappingFilePath", strPath+File.separator+strFileName);
                intent.putExtra("resultCode", "MRC_000");

                sendBroadcast(intent);
                Log.d(TAG, "sent broadcast. ");

                Thread.sleep(1000);

                Log.d(TAG, "finish activity ");
                //System.exit(0);
                finish();
            } catch (InterruptedException ie) {
                Log.e(TAG, "goBack Button InterruptedExtception: " + ie.getMessage());
            }

        });

        finshButton.setOnClickListener(v -> {
            Log.d(TAG, "finshButton.setOnClickListener(...)");


            try {
                //String strFileName = "/sdcard/Download/map_meta_sample.json";
                //String strFileName = "/Download/new_mapping.json";
                String strFileName = "map_meta_sample.json";
                //String strPath = "/storage/emulated/0/Download";
                String strPath = "/sdcard/Download";

                // TODO 수정필요 srcMappingfilePath 경로로 확인해서 name,path구분

                //String strSDCardPath = Environment.getExternalStorageDirectory().getAbsolutePath();   // 외부 저장소의 절대 경로를 자동으로 가져와 주는 메서드
                Log.d(TAG, "try roi_saveToFile()");
                if (roi_saveToFile(strPath, strFileName)) {
                    Log.d(TAG, "send broadcast... ");
                    Intent intent = new Intent("sk.action.airbot.map.responseMapping");
                    //intent.setPackage("com.sk.airbot.skmlauncher"); // 2024.12.04 이전
                    intent.setPackage("com.sk.airbot.iotagent");
                    intent.putExtra("destMappingFilePath", strPath + "/" + strFileName);
                    //intent.putExtra("destMappingFilePath", strPath+File.separator+strFileName);
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
        });


        // 20241217 jihyeon
        // 공간 생성, 가상벽, 금지공간 버튼 분리
        Button buttonSpaceCreation = findViewById(R.id.button_space_creation);
        Button buttonBlockWall = findViewById(R.id.button_block_wall);
        Button buttonBlockArea = findViewById(R.id.button_block_area);

        buttonSpaceCreation.setOnClickListener(v -> {
            currentMode = MODE_SPACE_CREATION;
            MapViewer.SetMode("공간 생성");
            Log.d(TAG, "공간 생성 모드 활성화");
            updateButtonBackground(buttonSpaceCreation);
            updateToggleBarVisibility();
            updateModeDescription();
            hideDeleteToggleBar();
            fabAddObjectClicked.setVisibility(View.GONE);
            fabSelectObjectClicked.setVisibility(View.GONE);
            fabMoveObjectClicked.setVisibility(View.GONE);
            fabMovePinClicked.setVisibility(View.GONE);
            fabRotatePinClicked.setVisibility(View.GONE);
            fabDeleteObjectClicked.setVisibility(View.GONE);
            fabRenameObjectClicked.setVisibility(View.GONE);
        });

        buttonBlockWall.setOnClickListener(v -> {
            currentMode = MODE_BLOCK_WALL;
            MapViewer.SetMode("가상벽");
            Log.d(TAG, "가상벽 모드 활성화");
            updateButtonBackground(buttonBlockWall);
            updateToggleBarVisibility();
            updateModeDescription();
            hideDeleteToggleBar();
            fabAddObjectClicked.setVisibility(View.GONE);
            fabSelectObjectClicked.setVisibility(View.GONE);
            fabMoveObjectClicked.setVisibility(View.GONE);
            fabMovePinClicked.setVisibility(View.GONE);
            fabRotatePinClicked.setVisibility(View.GONE);
            fabDeleteObjectClicked.setVisibility(View.GONE);
            fabRenameObjectClicked.setVisibility(View.GONE);
        });

        buttonBlockArea.setOnClickListener(v -> {
            currentMode = MODE_BLOCK_AREA;
            MapViewer.SetMode("금지공간");
            Log.d(TAG, "금지공간 모드 활성화");
            updateButtonBackground(buttonBlockArea);
            updateToggleBarVisibility();
            updateModeDescription();
            hideDeleteToggleBar();
            fabAddObjectClicked.setVisibility(View.GONE);
            fabSelectObjectClicked.setVisibility(View.GONE);
            fabMoveObjectClicked.setVisibility(View.GONE);
            fabMovePinClicked.setVisibility(View.GONE);
            fabRotatePinClicked.setVisibility(View.GONE);
            fabDeleteObjectClicked.setVisibility(View.GONE);
            fabRenameObjectClicked.setVisibility(View.GONE);
        });


        // 열기 버튼 클릭 시 toggle_bar 보이기
        fabMain.setOnClickListener(v -> {
            if (currentMode == MODE_SPACE_CREATION) {
                toggleBar_CreateSpace.setVisibility(View.VISIBLE);   // toggle_bar 보이기
                fabMain.setVisibility(View.INVISIBLE);   // 열기 버튼 숨기기
                fabMainBackCS.setVisibility(View.VISIBLE); // 닫기 버튼 보이기
            } else if (currentMode == MODE_BLOCK_WALL || currentMode == MODE_BLOCK_AREA) {
                toggleBar.setVisibility(View.VISIBLE);   // toggle_bar 보이기
                fabMain.setVisibility(View.INVISIBLE);   // 열기 버튼 숨기기
                fabMainBack.setVisibility(View.VISIBLE); // 닫기 버튼 보이기
            }
        });

        // 닫기 버튼 클릭 시 toggle_bar 숨기기
        fabMainBack.setOnClickListener(v -> {
            toggleBar.setVisibility(View.GONE);      // toggle_bar 숨기기
            fabMain.setVisibility(View.VISIBLE);     // 열기 버튼 보이기
            fabMainBack.setVisibility(View.INVISIBLE); // 닫기 버튼 숨기기

            fabAddObjectClicked.setVisibility(View.GONE);
            fabSelectObjectClicked.setVisibility(View.GONE);
            fabMoveObjectClicked.setVisibility(View.GONE);
            fabMovePinClicked.setVisibility(View.GONE);
            fabRotatePinClicked.setVisibility(View.GONE);
            fabDeleteObjectClicked.setVisibility(View.GONE);
            fabRenameObjectClicked.setVisibility(View.GONE);
        });

        fabMainBackCS.setOnClickListener(v -> {
            toggleBar_CreateSpace.setVisibility(View.GONE);      // toggle_bar 숨기기
            fabMain.setVisibility(View.VISIBLE);     // 열기 버튼 보이기
            fabMainBackCS.setVisibility(View.INVISIBLE); // 닫기 버튼 숨기기

            fabAddObjectClicked.setVisibility(View.GONE);
            fabSelectObjectClicked.setVisibility(View.GONE);
            fabMoveObjectClicked.setVisibility(View.GONE);
            fabMovePinClicked.setVisibility(View.GONE);
            fabRotatePinClicked.setVisibility(View.GONE);
            fabDeleteObjectClicked.setVisibility(View.GONE);
            fabRenameObjectClicked.setVisibility(View.GONE);
        });

        fabMoveMap.setOnClickListener(v ->
        {
            fabAddObjectClicked.setVisibility(View.GONE);
            fabSelectObjectClicked.setVisibility(View.GONE);
            fabMoveObjectClicked.setVisibility(View.GONE);
            fabMovePinClicked.setVisibility(View.GONE);
            fabRotatePinClicked.setVisibility(View.GONE);
            fabDeleteObjectClicked.setVisibility(View.GONE);
            fabRenameObjectClicked.setVisibility(View.GONE);

            if (MapViewer.strMenu == "이동") {
                MapViewer.SetMenu("이동");
                //strMode = "Move";
            } else {
                MapViewer.SetMenu("이동");
                //strMode = "Move";
            }
        });

//        fabZoom.setOnClickListener(v -> {
//            MapViewer.SetMenu("줌");
//            strMode = "Zoom";
//        });

        fabAddObject.setOnClickListener(v -> {
            MapViewer.SetMenu("추가");
            fabAddObjectClicked.setVisibility(View.VISIBLE);
            fabSelectObjectClicked.setVisibility(View.GONE);
            fabMoveObjectClicked.setVisibility(View.GONE);
            fabMovePinClicked.setVisibility(View.GONE);
            fabRotatePinClicked.setVisibility(View.GONE);
            fabDeleteObjectClicked.setVisibility(View.GONE);
            fabRenameObjectClicked.setVisibility(View.GONE);
            hideDeleteToggleBar();
        });

        fabMoveObject.setOnClickListener(v -> {
            //  move Object
            MapViewer.SetMenu("수정");
            fabAddObjectClicked.setVisibility(View.GONE);
            fabSelectObjectClicked.setVisibility(View.GONE);
            fabMoveObjectClicked.setVisibility(View.VISIBLE);
            fabMovePinClicked.setVisibility(View.GONE);
            fabRotatePinClicked.setVisibility(View.GONE);
            fabDeleteObjectClicked.setVisibility(View.GONE);
            fabRenameObjectClicked.setVisibility(View.GONE);
            hideDeleteToggleBar();
        });
        fabSelectObject.setOnClickListener(v -> {
            //  Select Object
            MapViewer.SetMenu("선택");
            fabAddObjectClicked.setVisibility(View.GONE);
            fabSelectObjectClicked.setVisibility(View.VISIBLE);
            fabMoveObjectClicked.setVisibility(View.GONE);
            fabMovePinClicked.setVisibility(View.GONE);
            fabRotatePinClicked.setVisibility(View.GONE);
            fabDeleteObjectClicked.setVisibility(View.GONE);
            fabRenameObjectClicked.setVisibility(View.GONE);
            hideDeleteToggleBar();
        });
        fabDeleteObject.setOnClickListener(v -> {
            MapViewer.SetMenu("삭제");
            fabAddObjectClicked.setVisibility(View.GONE);
            fabSelectObjectClicked.setVisibility(View.GONE);
            fabMoveObjectClicked.setVisibility(View.GONE);
            fabMovePinClicked.setVisibility(View.GONE);
            fabRotatePinClicked.setVisibility(View.GONE);
            fabDeleteObjectClicked.setVisibility(View.VISIBLE);
            fabRenameObjectClicked.setVisibility(View.GONE);
            Log.d(TAG, "current index: " + MapViewer.getCurrentSelectedIndex());
            if (MapViewer.getCurrentSelectedIndex() != -1) {
                Pair<Integer, Integer> selectedObjectPosition = MapViewer.getSelectedObjectPosition();
                showDeleteToggleBar(selectedObjectPosition);
            }
        });
        fabMovePin.setOnClickListener(v -> {
            MapViewer.SetMenu("핀 이동");
            fabAddObjectClicked.setVisibility(View.GONE);
            fabSelectObjectClicked.setVisibility(View.GONE);
            fabMoveObjectClicked.setVisibility(View.GONE);
            fabMovePinClicked.setVisibility(View.VISIBLE);
            fabRotatePinClicked.setVisibility(View.GONE);
            fabDeleteObjectClicked.setVisibility(View.GONE);
            fabRenameObjectClicked.setVisibility(View.GONE);
            hideDeleteToggleBar();
        });
        fabRotatePin.setOnClickListener(v -> {
            MapViewer.SetMenu("핀 회전");
            fabAddObjectClicked.setVisibility(View.GONE);
            fabSelectObjectClicked.setVisibility(View.GONE);
            fabMoveObjectClicked.setVisibility(View.GONE);
            fabMovePinClicked.setVisibility(View.GONE);
            fabRotatePinClicked.setVisibility(View.VISIBLE);
            fabDeleteObjectClicked.setVisibility(View.GONE);
            fabRenameObjectClicked.setVisibility(View.GONE);
            hideDeleteToggleBar();
        });

        // 241217 jihyeon
        // 공간 생성 모드일 때 토글 버튼 설정
        fabAddObjectCS.setOnClickListener(v -> {
            MapViewer.SetMenu("추가");
            fabAddObjectClicked.setVisibility(View.VISIBLE);
            fabSelectObjectClicked.setVisibility(View.GONE);
            fabMoveObjectClicked.setVisibility(View.GONE);
            fabMovePinClicked.setVisibility(View.GONE);
            fabRotatePinClicked.setVisibility(View.GONE);
            fabDeleteObjectClicked.setVisibility(View.GONE);
            fabRenameObjectClicked.setVisibility(View.GONE);
            hideDeleteToggleBar();
        });

        fabMoveObjectCS.setOnClickListener(v -> {
            MapViewer.SetMenu("수정");
            fabAddObjectClicked.setVisibility(View.GONE);
            fabSelectObjectClicked.setVisibility(View.GONE);
            fabMoveObjectClicked.setVisibility(View.VISIBLE);
            fabMovePinClicked.setVisibility(View.GONE);
            fabRotatePinClicked.setVisibility(View.GONE);
            fabDeleteObjectClicked.setVisibility(View.GONE);
            fabRenameObjectClicked.setVisibility(View.GONE);
            hideDeleteToggleBar();
        });
        fabSelectObjectCS.setOnClickListener(v -> {
            MapViewer.SetMenu("선택");
            fabAddObjectClicked.setVisibility(View.GONE);
            fabSelectObjectClicked.setVisibility(View.VISIBLE);
            fabMoveObjectClicked.setVisibility(View.GONE);
            fabMovePinClicked.setVisibility(View.GONE);
            fabRotatePinClicked.setVisibility(View.GONE);
            fabDeleteObjectClicked.setVisibility(View.GONE);
            fabRenameObjectClicked.setVisibility(View.GONE);
            hideDeleteToggleBar();
        });
        fabDeleteObjectCS.setOnClickListener(v -> {
            MapViewer.SetMenu("삭제");
            fabAddObjectClicked.setVisibility(View.GONE);
            fabSelectObjectClicked.setVisibility(View.GONE);
            fabMoveObjectClicked.setVisibility(View.GONE);
            fabMovePinClicked.setVisibility(View.GONE);
            fabRotatePinClicked.setVisibility(View.GONE);
            fabDeleteObjectClicked.setVisibility(View.VISIBLE);
            fabRenameObjectClicked.setVisibility(View.GONE);
            Log.d(TAG, "current index: " + MapViewer.getCurrentSelectedIndex());
            if (MapViewer.getCurrentSelectedIndex() != -1) {
                Pair<Integer, Integer> selectedObjectPosition = MapViewer.getSelectedObjectPosition();
                showDeleteToggleBar(selectedObjectPosition);
            }
        });
        fabMovePinCS.setOnClickListener(v -> {
            MapViewer.SetMenu("핀 이동");
            fabAddObjectClicked.setVisibility(View.GONE);
            fabSelectObjectClicked.setVisibility(View.GONE);
            fabMoveObjectClicked.setVisibility(View.GONE);
            fabMovePinClicked.setVisibility(View.VISIBLE);
            fabRotatePinClicked.setVisibility(View.GONE);
            fabDeleteObjectClicked.setVisibility(View.GONE);
            fabRenameObjectClicked.setVisibility(View.GONE);
            hideDeleteToggleBar();
        });
        fabRotatePinCS.setOnClickListener(v -> {
            MapViewer.SetMenu("핀 회전");
            fabAddObjectClicked.setVisibility(View.GONE);
            fabSelectObjectClicked.setVisibility(View.GONE);
            fabMoveObjectClicked.setVisibility(View.GONE);
            fabMovePinClicked.setVisibility(View.GONE);
            fabRotatePinClicked.setVisibility(View.VISIBLE);
            fabDeleteObjectClicked.setVisibility(View.GONE);
            fabRenameObjectClicked.setVisibility(View.GONE);
            hideDeleteToggleBar();
        });

        fabRenameObjectCS.setOnClickListener(v -> {
            strMode = "None";
            String oldPinName = PinName;
            showCustomDialog(selectedText -> {
                if ((MapViewer.m_RoiCurObject != null)) {
                    MapViewer.SetLabel(PinName);
                }
            });
            fabAddObjectClicked.setVisibility(View.GONE);
            fabSelectObjectClicked.setVisibility(View.GONE);
            fabMoveObjectClicked.setVisibility(View.GONE);
            fabMovePinClicked.setVisibility(View.GONE);
            fabRotatePinClicked.setVisibility(View.GONE);
            fabDeleteObjectClicked.setVisibility(View.GONE);
            fabRenameObjectClicked.setVisibility(View.GONE);
            updateToggleBarVisibility();
            hideDeleteToggleBar();
        });


        // 휴지통 버튼 클릭 이벤트
        deleteButton.setOnClickListener(v -> {
            MapViewer.roi_RemoveObject();
            MapViewer.CObject_CurRoiCancelFunc();
            MapViewer.clearSelection(); // 선택 초기화 메서드
            hideDeleteToggleBar();
        });

        // X 버튼 클릭 이벤트
        cancelButton.setOnClickListener(v -> {
            MapViewer.clearSelection(); // 선택 초기화 메서드
            hideDeleteToggleBar();
        });

        MapViewer = findViewById(R.id.imageView);
        // 방법 1: ViewTreeObserver를 사용
        MapViewer.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // 가로와 세로 크기 얻기
                int width = MapViewer.getWidth();
                int height = MapViewer.getHeight();

                Log.d("ViewSize", "Width: " + width + ", Height: " + height);

                MapViewer.ScreenWidth = MapViewer.getWidth();
                MapViewer.ScreenHeight = MapViewer.getHeight();

                // 리스너 제거 (메모리 누수 방지)
                MapViewer.getViewTreeObserver().removeOnGlobalLayoutListener(this);

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

                        Log.e("FILE", "Directory not created : "+path_rot);

                    }
                    Log.d("SKOnDeviceService", "Run library-rotate!");
                    //com.caselab.forsk.MapOptimization.mapRotation(PATH_FILE_MAP_ORG, PATH_FILE_MAP_ROT, NAME_FILE_MAP_ORG);
                    com.caselab.forsk.MapOptimization.mapRotation(path, path_rot, fileTitle);
                    Log.d("SKOnDeviceService", "Library-rotate Finish!");

                    String path_opt = path + "opt/";
                    File file_opt = new File(path_opt);
                    // 폴더가 제대로 만들어졌는지 체크 ======
                    if (!file_opt.mkdirs()) {

                        Log.e("FILE", "Directory not created : "+path_opt);

                    }
                    Log.d("SKOnDeviceService", "Run library-line opt!");
                    //MapOptimization.lineOptimization(PATH_FILE_MAP_ROT, PATH_FILE_MAP_OPT, NAME_FILE_MAP_ORG);
                    com.caselab.forsk.MapOptimization.lineOptimization(path_rot, path_opt, fileTitle);
                    Log.d("SKOnDeviceService", "Library-line Finish!");

                    String strPgmFile = path_opt + fileTitle + ".pgm";

                    //Log.d(TAG, strPgmFile);
                    lib_flag = true;
                    srcMapPgmFilePath = strPgmFile;
                    srcMapYamlFilePath = path_opt + fileTitle + ".yaml";

                }  catch (UnsatisfiedLinkError e) {
                    Log.e(TAG, "Native library not loaded or linked properly", e);
                } catch (ExceptionInInitializerError e) {
                    Log.e(TAG, "Initialization error in native method", e);
                } catch (RuntimeException e) {
                    Log.e(TAG, "Runtime exception occurred", e);
                } catch (Throwable e) {
                    Log.e(TAG, "Unexpected error occurred", e);
                }

                try {
                    Bitmap bitmap = loadPGM(srcMapPgmFilePath);
                    if (bitmap != null) {
                        MapViewer.setBitmap(bitmap);

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
                /*
                for (int it = 0; it < MapViewer.m_RoiObjects.size(); it++) {
                    Log.d(TAG,"Type: "+MapViewer.m_RoiObjects.get(it).roi_type);
                    for (int j = 0; j < MapViewer.m_RoiObjects.get(it).m_Points.size(); j++) {
                        Log.d(TAG, "\"x\":" + (int) (MapViewer.m_RoiObjects.get(it).m_Points.get(j).x));
                        Log.d(TAG, ", \"y\":" + (int) (MapViewer.m_RoiObjects.get(it).m_Points.get(j).y));
                    }
                }
                */
            }
        });

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

    // 토글바 표시 함수
    void showDeleteToggleBar(Pair<Integer, Integer> position) {
        Log.d(TAG, "Toggle Bar Location: " + position.first + ", " + position.second);
        deleteToggleBar.setVisibility(View.VISIBLE);
        // 선택된 객체 위치에 토글바 배치
        deleteToggleBar.setX(position.first);
        deleteToggleBar.setY(position.second);
    }

    // 토글바 숨김 함수
    private void hideDeleteToggleBar() {
        deleteToggleBar.setVisibility(View.GONE);
    }

    private void updateToggleBarVisibility() {
        if (currentMode == MODE_SPACE_CREATION) {
            toggleBar_CreateSpace.setVisibility(View.VISIBLE);
            toggleBar.setVisibility(View.GONE);
        } else if (currentMode == MODE_BLOCK_WALL || currentMode == MODE_BLOCK_AREA) {
            toggleBar.setVisibility(View.VISIBLE);
            toggleBar_CreateSpace.setVisibility(View.GONE);
        } else {
            toggleBar.setVisibility(View.GONE);
            toggleBar_CreateSpace.setVisibility(View.GONE);
        }
    }

    // currentMode에 따라 텍스트 업데이트
    private void updateModeDescription() {
        switch (currentMode) {
            case MODE_SPACE_CREATION:
                modeDescription.setText("한 공간으로 묶고 싶은 영역을 3점 이상 Tap하여 지정해주세요.");
                break;
            case MODE_BLOCK_WALL:
                modeDescription.setText("Drag하여 진입 금지벽을 지정해주세요.");
                break;
            case MODE_BLOCK_AREA:
                modeDescription.setText("Drag하여 진입 금지 영역을 지정해주세요.");
                break;
            default:
                modeDescription.setText(""); // 다른 상태에서는 빈 문자열
                break;
        }
    }

    // 버튼 색상 변경 메소드
    private void updateButtonBackground(Button currentButton) {
        // 이전에 선택된 버튼이 있으면 배경을 원래 색상으로 변경
        if (selectedButton != null) {
            selectedButton.setBackgroundResource(R.drawable.rounded_button); // 원래 배경
            selectedButton.setTextColor(Color.WHITE);
        }
        // 현재 선택된 버튼은 흰색으로 변경
        currentButton.setBackgroundResource(R.drawable.rounded_button_white);
        currentButton.setTextColor(Color.BLACK);
        selectedButton = currentButton;
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
        String[][] layout = {
                {"거실", "게스트룸", "드레스룸", "발코니"}, // 첫 번째 행
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
            rowLayout.setLayoutParams(new RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.MATCH_PARENT,
                    RadioGroup.LayoutParams.WRAP_CONTENT
            ));
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
                    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1 // 균등 분배
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
                    space.setLayoutParams(new LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1 // 빈 공간도 균등 분배
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
                Log.d(TAG, "공간명이 선택되지 않음");
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

    private void toggleFabVisibility(View fabToHide, View fabToShow) {
        // 숨길 FAB를 GONE으로 설정
        fabToHide.setVisibility(View.GONE);

        // 보일 FAB를 VISIBLE로 설정
        fabToShow.setVisibility(View.VISIBLE);
    }

//    private void toggleFab(View fabMain, View... fabs) {
//        if (isFabOpen) {
//            // 메뉴를 닫을 때
//            for (int i = 0; i < fabs.length; i++) {
//                ObjectAnimator.ofFloat(fabs[i], "translationX", 0f).start();
//                fabs[i].setVisibility(View.GONE); // 버튼 숨기기
//            }
//            fabMain.setBackgroundResource(R.drawable.baseline_add_24); // "+" 아이콘으로 변경
//        } else {
//            // 메뉴를 열 때
//            for (int i = 0; i < fabs.length; i++) {
//                ObjectAnimator.ofFloat(fabs[i], "translationX", 240f + (150f * i)).start();
//                fabs[i].setVisibility(View.VISIBLE); // 버튼 보이기
//            }
//        }
//        isFabOpen = !isFabOpen;
//    }

    private Bitmap loadPGM(String filePath) throws IOException {
        Log.d(TAG, "loadPGM(\"" + filePath + "\")");

        String[] paths = filePath.split("/");
        String fileName = paths[paths.length - 1];
        Log.d(TAG, "fileName : " + fileName);

        File file = new File(filePath);
        //if (!file.exists()) {
        //    throw new IOException("File not found: " + filePath);
        //}
        try (FileInputStream fis = new FileInputStream(file)){
            //InputStream inputStream = new FileInputStream(filePath);
            //AssetManager assetManager = getAssets();
            //InputStream inputStream = assetManager.open(fileName);



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
                    } else if(grayValue == 0){
                        grayValue = 17;
                    }else {    //외부(254)는 짙은 회색 #333333
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

    private boolean loadYaml(String filePath) {

        // 내부 저장소에서 파일 스트림 열기
        try (InputStream inputStream = new FileInputStream(filePath)){

            Yaml yaml = new Yaml();

            // YAML 파싱
            Map<String, Object> data = yaml.load(inputStream);

            nResolution = (double) data.get("resolution");
            Log.d(TAG, "resolution : " + nResolution);

            ArrayList<Double> origin = (ArrayList<Double>) data.get("origin");

            Log.d(TAG, "origin.get(0) : " + origin.get(0));

            //origin_x = (double)Double.parseDouble(data.get(0).toString());
            origin_x = (double) origin.get(0);

            Log.d(TAG, "origin_x : " + origin_x);

            origin_y = (double) origin.get(1);
            origin_angle = (double) origin.get(2);
            Log.d(TAG, "origin_y : " + origin_y);
            Log.d(TAG, "origin_angle : " + origin_angle);

            // 241222 최적화 라이브러리 읽을 경우 추가.
            if (lib_flag) {
                // OpenCV 네이티브 라이브러리 로드
                try {
                    if (!OpenCVLoader.initDebug()) {
                        Log.e("OpenCV", "Initialization failed.");
                    } else {
                        Log.d("OpenCV", "Initialization succeeded.");
                    }
                } catch (Exception e) {
                    Log.e("OpenCV", "Exception while loading OpenCV", e);
                }
                original_image_height = (int)data.get("original_image_height");
                // 추가: transformation_matrix 읽기
                ArrayList<ArrayList<Double>> matrixList = (ArrayList<ArrayList<Double>>) data.get("transformation_matrix");
                for (int i = 0; i < matrixList.size(); i++) {
                    ArrayList<Double> row = matrixList.get(i);
                    Log.d(TAG, "transformation_matrix row " + i + " : " + row);
                }

                // 행과 열 크기 설정
                int rows = matrixList.size();
                int cols = matrixList.get(0).size();

                // OpenCV Mat 객체 생성
                transformationMatrix = new Mat(rows, cols, CvType.CV_64F);

                // 데이터 복사
                for (int i = 0; i < rows; i++) {
                    ArrayList<?> row = matrixList.get(i); // 데이터를 제네릭으로 읽음
                    for (int j = 0; j < cols; j++) {
                        Number value = (Number) row.get(j); // Number로 캐스팅
                        transformationMatrix.put(i, j, value.doubleValue()); // Double로 변환하여 Mat에 추가
                    }
                }
                rotated_angle = (float) (double) data.get("rotated_angle");
                Log.d(TAG,"rotated angle: " + rotated_angle);
            }

            return true;
        }
        catch (IOException | NullPointerException e) {
            Log.e(TAG, "loadYaml Exception: " + e.getMessage());
            return false;
        }
    }

    public boolean roi_saveToFile(String strPath, String strFileName) {

        int i, j;
        int image_width = MapViewer.GetBitmapWidth();
        int image_height = MapViewer.GetBitmapHeight();

        Log.d(TAG,"image width : " + image_width + " , image_height : " +image_height);
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

        Log.d(TAG, "MapViewer.m_RoiObjects.size() : " + MapViewer.m_RoiObjects.size());

        /* for test - hard cording
        strRoiJson += "{";
        strRoiJson += "\"id\": \""+ (count_id+1)+"\"";
        strRoiJson += ", \"name\": \"공간"+(count_id+1)+"\"";
        strRoiJson += ", \"color\":\"#47910f\", \"desc\":\"\"";
        strRoiJson += ", \"robot_path\":[";
        //strRoiJson += "{\"x\":-2.97,\"y\":7.15},{\"x\":-3.02,\"y\":7.1000004},{\"x\":-3.17,\"y\":7.1000004},{\"x\":-3.22,\"y\":7.05},{\"x\":-3.22,\"y\":6.9},{\"x\":-3.27,\"y\":6.8500004},{\"x\":-3.27,\"y\":4},{\"x\":-3.22,\"y\":3.95},{\"x\":-2.47,\"y\":3.95},{\"x\":-2.42,\"y\":4},{\"x\":-2.22,\"y\":4},{\"x\":-2.17,\"y\":4.05},{\"x\":-1.7199999,\"y\":4.05},{\"x\":-1.7199999,\"y\":4.1},{\"x\":-1.67,\"y\":4.15},{\"x\":-1.67,\"y\":4.25},{\"x\":-1.62,\"y\":4.3},{\"x\":-1.62,\"y\":4.35},{\"x\":-1.5699999,\"y\":4.4},{\"x\":-1.5699999,\"y\":4.4500003},{\"x\":-1.52,\"y\":4.5},{\"x\":-1.52,\"y\":4.6},{\"x\":-1.4699999,\"y\":4.65},{\"x\":-1.4699999,\"y\":4.7000003},{\"x\":-1.42,\"y\":4.75},{\"x\":-1.42,\"y\":4.8500004},{\"x\":-1.37,\"y\":4.9},{\"x\":-1.37,\"y\":4.9500003},{\"x\":-1.3199999,\"y\":5},{\"x\":-1.3199999,\"y\":5.05},{\"x\":-1.27,\"y\":5.1000004},{\"x\":-1.27,\"y\":5.2000003},{\"x\":-1.2199999,\"y\":5.25},{\"x\":-1.2199999,\"y\":5.3},{\"x\":-1.17,\"y\":5.3500004},{\"x\":-1.17,\"y\":5.4500003},{\"x\":-1.12,\"y\":5.5},{\"x\":-1.12,\"y\":5.55},{\"x\":-1.0699999,\"y\":5.6000004},{\"x\":-1.0699999,\"y\":5.65},{\"x\":-1.02,\"y\":5.7000003},{\"x\":-1.02,\"y\":5.8},{\"x\":-0.96999997,\"y\":5.8500004},{\"x\":-0.96999997,\"y\":5.9},{\"x\":-0.91999996,\"y\":5.9500003},{\"x\":-0.91999996,\"y\":6.05},{\"x\":-0.86999995,\"y\":6.1000004},{\"x\":-0.86999995,\"y\":6.15},{\"x\":-0.81999993,\"y\":6.2000003},{\"x\":-0.81999993,\"y\":6.3},{\"x\":-0.77,\"y\":6.3500004},{\"x\":-0.77,\"y\":6.4},{\"x\":-1.27,\"y\":6.4},{\"x\":-1.37,\"y\":6.5},{\"x\":-1.37,\"y\":6.8},{\"x\":-1.42,\"y\":6.8500004},{\"x\":-1.42,\"y\":7},{\"x\":-1.5699999,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15}";
        strRoiJson += "]";
        strRoiJson += ", \"image_path\":[]";
        strRoiJson += ", \"robot_position\":{";

        // MapViewer.m_RoiObjects.get(i).m_MBR;


        strRoiJson += "\"x\":" + 2.0;
        strRoiJson += ", \"y\":" + 2.0;

        strRoiJson += "}";

        strRoiJson += ", \"image_position\":{}";

        strRoiJson += "}";
        */
        for (i = 0; i < MapViewer.m_RoiObjects.size(); i++) {

            if (MapViewer.m_RoiObjects.get(i).roi_type.equals("roi_polygon")) {
                if (count_id > 0) {
                    strRoiJson += ", ";
                }

                count_id++; // 고유 id


                strRoiJson += "{";
                strRoiJson += "\"id\": \"" + (count_id) + "\"";
                strRoiJson += ", \"name\": \"" + MapViewer.m_RoiObjects.get(i).m_label + "\"";
                strRoiJson += ", \"color\":\"#47910f\", \"desc\":\"\"";
                strRoiJson += ", \"robot_path\":[";
                //strRoiJson += "{\"x\":-2.97,\"y\":7.15},{\"x\":-3.02,\"y\":7.1000004},{\"x\":-3.17,\"y\":7.1000004},{\"x\":-3.22,\"y\":7.05},{\"x\":-3.22,\"y\":6.9},{\"x\":-3.27,\"y\":6.8500004},{\"x\":-3.27,\"y\":4},{\"x\":-3.22,\"y\":3.95},{\"x\":-2.47,\"y\":3.95},{\"x\":-2.42,\"y\":4},{\"x\":-2.22,\"y\":4},{\"x\":-2.17,\"y\":4.05},{\"x\":-1.7199999,\"y\":4.05},{\"x\":-1.7199999,\"y\":4.1},{\"x\":-1.67,\"y\":4.15},{\"x\":-1.67,\"y\":4.25},{\"x\":-1.62,\"y\":4.3},{\"x\":-1.62,\"y\":4.35},{\"x\":-1.5699999,\"y\":4.4},{\"x\":-1.5699999,\"y\":4.4500003},{\"x\":-1.52,\"y\":4.5},{\"x\":-1.52,\"y\":4.6},{\"x\":-1.4699999,\"y\":4.65},{\"x\":-1.4699999,\"y\":4.7000003},{\"x\":-1.42,\"y\":4.75},{\"x\":-1.42,\"y\":4.8500004},{\"x\":-1.37,\"y\":4.9},{\"x\":-1.37,\"y\":4.9500003},{\"x\":-1.3199999,\"y\":5},{\"x\":-1.3199999,\"y\":5.05},{\"x\":-1.27,\"y\":5.1000004},{\"x\":-1.27,\"y\":5.2000003},{\"x\":-1.2199999,\"y\":5.25},{\"x\":-1.2199999,\"y\":5.3},{\"x\":-1.17,\"y\":5.3500004},{\"x\":-1.17,\"y\":5.4500003},{\"x\":-1.12,\"y\":5.5},{\"x\":-1.12,\"y\":5.55},{\"x\":-1.0699999,\"y\":5.6000004},{\"x\":-1.0699999,\"y\":5.65},{\"x\":-1.02,\"y\":5.7000003},{\"x\":-1.02,\"y\":5.8},{\"x\":-0.96999997,\"y\":5.8500004},{\"x\":-0.96999997,\"y\":5.9},{\"x\":-0.91999996,\"y\":5.9500003},{\"x\":-0.91999996,\"y\":6.05},{\"x\":-0.86999995,\"y\":6.1000004},{\"x\":-0.86999995,\"y\":6.15},{\"x\":-0.81999993,\"y\":6.2000003},{\"x\":-0.81999993,\"y\":6.3},{\"x\":-0.77,\"y\":6.3500004},{\"x\":-0.77,\"y\":6.4},{\"x\":-1.27,\"y\":6.4},{\"x\":-1.37,\"y\":6.5},{\"x\":-1.37,\"y\":6.8},{\"x\":-1.42,\"y\":6.8500004},{\"x\":-1.42,\"y\":7},{\"x\":-1.5699999,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15}";

                for (j = 0; j < MapViewer.m_RoiObjects.get(i).m_Points.size(); j++) {

                    if (j > 0) {
                        strRoiJson += ", ";
                    }

                    strRoiJson += "{";

                    double[] coordinates = calculateCoordinate(MapViewer.m_RoiObjects.get(i).m_Points.get(j).x , MapViewer.m_RoiObjects.get(i).m_Points.get(j).y, image_height);

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
                for (j = 0; j < MapViewer.m_RoiObjects.get(i).m_Points.size(); j++) {

                    if (j > 0) {
                        strRoiJson += ", ";
                    }

                    strRoiJson += "{";

                    strRoiJson += "\"x\":" + (int) (MapViewer.m_RoiObjects.get(i).m_Points.get(j).x);
                    strRoiJson += ", \"y\":" + (int) (MapViewer.m_RoiObjects.get(i).m_Points.get(j).y);

                    strRoiJson += "}";
                    // roi_rect
                    // roi_line
                }
                strRoiJson += "]";
                strRoiJson += ", \"robot_position\":{";

                // Log.d(TAG, "xvw before : "+ xvw);

                // MapViewer.m_RoiObjects.get(i).m_MBR;
//                Log.d(TAG, "xvw : " + xvw);
//                Log.d(TAG, "nResolution : " + nResolution);
//                Log.d(TAG, "origin_x : " + origin_x);
//                Log.d(TAG, "(xvw * nResolution + origin_x) : " + (xvw * nResolution + origin_x));

                //Log.d(TAG,"height: " + image_height +", origin_y: "+ origin_y + ", imagey: " + MapViewer.m_RoiObjects.get(i).m_Points.get(j).y + ", real_y: "+ (float)((image_height-MapViewer.m_RoiObjects.get(i).m_Points.get(j).y)*nResolution + origin_y));
                //Toast.makeText(getApplicationContext(), "X: " + (float)(xvw * nResolution + origin_x) +", Y: " + ((image_height - yvh) * nResolution + origin_y), Toast.LENGTH_SHORT).show();
                double[] coordinates = calculateCoordinate(MapViewer.m_RoiObjects.get(i).m_MBR_center.x, MapViewer.m_RoiObjects.get(i).m_MBR_center.y, image_height);

                double xvw = coordinates[0];
                double yvh = coordinates[1];
                strRoiJson += "\"x\":" + xvw;
                strRoiJson += ", \"y\":" + yvh;
                strRoiJson += ", \"theta\":" + (MapViewer.m_RoiObjects.get(i).getAngle() -  Math.toRadians(rotated_angle));

                strRoiJson += "}";

                strRoiJson += ", \"image_position\":{";
                int xvw_image = 0; // polygon의 무게중심 x

                int yvh_image = 0; // polygon의 무게중심 x

                // Log.d(TAG, "xvw before : "+ xvw);
                xvw_image = MapViewer.m_RoiObjects.get(i).m_MBR_center.x;
                yvh_image = MapViewer.m_RoiObjects.get(i).m_MBR_center.y;

                // MapViewer.m_RoiObjects.get(i).m_MBR;
                //Log.d(TAG,"height: " + image_height +", origin_y: "+ origin_y + ", imagey: " + MapViewer.m_RoiObjects.get(i).m_Points.get(j).y + ", real_y: "+ (float)((image_height-MapViewer.m_RoiObjects.get(i).m_Points.get(j).y)*nResolution + origin_y));
                //Toast.makeText(getApplicationContext(), "X: " + (float)(xvw * nResolution + origin_x) +", Y: " + ((image_height - yvh) * nResolution + origin_y), Toast.LENGTH_SHORT).show();

                strRoiJson += "\"x\":" + (int) xvw_image;
                strRoiJson += ", \"y\":" + (int) yvh_image;
                strRoiJson += ", \"theta\":" + MapViewer.m_RoiObjects.get(i).getAngle();
                strRoiJson += "}";
                strRoiJson += "}";
            }

        }
        strRoiJson += "]";
        strRoiJson += ", \"block_area\":[";
        for (i = 0; i < MapViewer.m_RoiObjects.size(); i++) {
            if (MapViewer.m_RoiObjects.get(i).roi_type.equals("roi_rect")) {
                if (count_id_rect > 0) {
                    strRoiJson += ", ";
                }

                count_id_rect++; // 고유 id


                strRoiJson += "{";
                strRoiJson += "\"image_path\":[";
                strRoiJson += "{";

                //left top
                strRoiJson += "\"x\":" + (int) (MapViewer.m_RoiObjects.get(i).m_MBR.left);
                strRoiJson += ", \"y\":" + (int) (MapViewer.m_RoiObjects.get(i).m_MBR.bottom);
                strRoiJson += "}, {";

                // right top
                strRoiJson += "\"x\":" + (int) (MapViewer.m_RoiObjects.get(i).m_MBR.right);
                strRoiJson += ", \"y\":" + (int) (MapViewer.m_RoiObjects.get(i).m_MBR.bottom);
                strRoiJson += "}, {";

                // right bottom
                strRoiJson += "\"x\":" + (int) (MapViewer.m_RoiObjects.get(i).m_MBR.right);
                strRoiJson += ", \"y\":" + (int) (MapViewer.m_RoiObjects.get(i).m_MBR.top);
                strRoiJson += "}, {";

                // left bottom
                strRoiJson += "\"x\":" + (int) (MapViewer.m_RoiObjects.get(i).m_MBR.left);
                strRoiJson += ", \"y\":" + (int) (MapViewer.m_RoiObjects.get(i).m_MBR.top);

                strRoiJson += "}";

                strRoiJson += "]";
                strRoiJson += ", \"robot_path\":[";


                strRoiJson += "{";

                // left top
                double[] coordinates = calculateCoordinate(MapViewer.m_RoiObjects.get(i).m_MBR.left, MapViewer.m_RoiObjects.get(i).m_MBR.bottom, image_height);
                double area_x = coordinates[0];
                double area_y = coordinates[1];

                strRoiJson += "\"x\":" + area_x;
                strRoiJson += ", \"y\":" + area_y;
                strRoiJson += "}, {";

                // right top
                coordinates = calculateCoordinate(MapViewer.m_RoiObjects.get(i).m_MBR.right, MapViewer.m_RoiObjects.get(i).m_MBR.bottom, image_height);
                area_x = coordinates[0];
                area_y = coordinates[1];

                strRoiJson += "\"x\":" + area_x;
                strRoiJson += ", \"y\":" + area_y;
                strRoiJson += "}, {";

                // right bottom
                coordinates = calculateCoordinate(MapViewer.m_RoiObjects.get(i).m_MBR.right, MapViewer.m_RoiObjects.get(i).m_MBR.top, image_height);
                area_x = coordinates[0];
                area_y = coordinates[1];

                strRoiJson += "\"x\":" + area_x;
                strRoiJson += ", \"y\":" + area_y;
                strRoiJson += "}, {";

                // left bottom
                coordinates = calculateCoordinate(MapViewer.m_RoiObjects.get(i).m_MBR.left, MapViewer.m_RoiObjects.get(i).m_MBR.top, image_height);
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

        for (i = 0; i < MapViewer.m_RoiObjects.size(); i++) {

            if (MapViewer.m_RoiObjects.get(i).roi_type.equals("roi_line")) {
                if (count_id_line > 0) {
                    strRoiJson += ", ";
                }

                count_id_line++; // 고유 id


                strRoiJson += "{";
                strRoiJson += "\"image_path\":[";
                strRoiJson += "{";

                //left bottom
                strRoiJson += "\"x\":" + (int) (MapViewer.m_RoiObjects.get(i).m_MBR.left);
                strRoiJson += ", \"y\":" + (int) (MapViewer.m_RoiObjects.get(i).m_MBR.top);

                // right top
                strRoiJson += "}, {\"x\":" + (int) (MapViewer.m_RoiObjects.get(i).m_MBR.right);
                strRoiJson += ", \"y\":" + (int) (MapViewer.m_RoiObjects.get(i).m_MBR.bottom);

                strRoiJson += "}";
                strRoiJson += "]";
                strRoiJson += ", \"robot_path\":[";


                strRoiJson += "{";

                //left bottom
                double[] coordinates = calculateCoordinate(MapViewer.m_RoiObjects.get(i).m_MBR.left, MapViewer.m_RoiObjects.get(i).m_MBR.top, image_height);
                double line_x = coordinates[0];
                double line_y = coordinates[1];

                strRoiJson += "\"x\":" + line_x;
                strRoiJson += ", \"y\":" + line_y;

                // right top
                coordinates = calculateCoordinate(MapViewer.m_RoiObjects.get(i).m_MBR.right, MapViewer.m_RoiObjects.get(i).m_MBR.bottom, image_height);
                line_x = coordinates[0];
                line_y = coordinates[1];

                strRoiJson += "}, {\"x\":" + line_x;
                strRoiJson += ", \"y\":" + line_y;

                strRoiJson += "}";

                strRoiJson += "]";
                strRoiJson += ", \"id\": \"d4f940b8-81cf-4b46-82d6-8f10f4e03038" + (int) (Math.random() * 255) + (int) (Math.random() * 255) + "\"";
                strRoiJson += "}";
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
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        // 파일 경로를 설정합니다.
        //File file = new File(downloadDir, strFileName);
        File file = new File(strPath, strFileName);
        try (FileOutputStream fos = new FileOutputStream(file)){
            //strPath += "/";

            //File directory = new File(strPath);
            //if (!directory.exists()) {
            //    boolean result = directory.mkdirs(); // 없으면 dir 경로 생성
            //    Log.i("prop", "!!!" + result);
            //    File file = new File(strPath,strFileName);
            //    file.createNewFile();
            //}

            // File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

            Log.d(TAG, strPath + "/" + strFileName);

            fos.write(strRoiJson.getBytes());
            fos.flush();
            Thread.sleep(1000); // 파일쓰기가 완료될 까지 기다린다.
            fos.close();


            /*
            FileOutputStream fos = new FileOutputStream(strFileName, true);
            DataOutputStream dos = new DataOutputStream(fos);

            dos.writeUTF(strRoiJson);
            dos.flush();
            dos.close();
            */


            return true;

        } catch (FileNotFoundException fe) {
            Log.d(TAG, Objects.requireNonNull(fe.getLocalizedMessage()));
            Toast.makeText(getApplicationContext(), fe.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        } catch (InterruptedException | IOException ie) {
            Log.d(TAG, Objects.requireNonNull(ie.getLocalizedMessage()));
        }

        return false;
    }

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
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        File file = new File(strPath, strFileName);
        try (FileOutputStream fos = new FileOutputStream(file)){

            Log.d(TAG, strPath + "/" + strFileName);
            // 파일 경로를 설정합니다.
            //File file = new File(downloadDir, strFileName);

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

    private synchronized boolean loadJson(String filePath) {

        Log.d(TAG, "Exist Json File");
        StringBuilder jsonStringBuilder = new StringBuilder();

        // 내부 저장소에서 파일 스트림 열기
        // InputStream 데이터를 문자열로 변환
        try (InputStream inputStream = new FileInputStream(filePath);
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))){

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
                        MapViewer.CObject_LoadObject("roi_polygon", new Point(x, y));
                    } else {
                        MapViewer.AddPoint_Polygon(new Point(x, y));
                    }
                    if (left > x)    left   = x;
                    if (right < x)   right  = x;
                    if (top > y)     top    = y;
                    if (bottom < y)  bottom = y;

                }
                MapViewer.m_drawing = true;
                MapViewer.roi_AddObject();
                // 241222 seongwoong 현재 버그 있음 확인 필요
                //MapViewer.m_RoiCurObject.m_MBR = new Rect(left, top, right, bottom);
                MapViewer.m_RoiCurObject.m_MBR_center.x = mbr_x;
                MapViewer.m_RoiCurObject.m_MBR_center.y = mbr_y;
                MapViewer.SetLabel(name);
                if (lib_flag) {
                    float theta = (float)imagePosition.getDouble("theta");
                    MapViewer.m_RoiCurObject.setAngle(theta);
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

                MapViewer.CObject_LoadObject("roi_rect", pt1);
                MapViewer.CObject_LoadRect(pt1, pt2);
                MapViewer.roi_AddObject();

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

                MapViewer.CObject_LoadObject("roi_line", pt1);
                MapViewer.CObject_LoadRect(pt1, pt2);
                MapViewer.roi_AddObject();
            }
            Log.d(TAG, "Read Json Success");
            return true;
        }  catch (FileNotFoundException fe) {
            Log.e(TAG, "Read Json FileNotFoundException: " + filePath + " " + fe.getMessage());
            return false;
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Read Json JSON, IO Exception: " + e.getMessage());
            return false;
        }

    }

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

    public double[] calculateCoordinate(int x, int y, int image_height) {
        // 계산 공식: (x * resolution + origin_x)
        double robot_x = 0.0;
        double robot_y = 0.0;

        if(lib_flag){
            Point pt = transformToRobotCoordinates(x,y);
            robot_x = pt.x * nResolution + origin_x;
            robot_y = (original_image_height - pt.y) * nResolution + origin_y;
        }
        else {
            robot_x = x * nResolution + origin_x;
            robot_y = (image_height - y) * nResolution + origin_y;
        }
        return new double[]{robot_x, robot_y};
    }


}

