package com.forsk.ondevice

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.Pair
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.caselab.forsk.MapOptimization
import com.forsk.ondevice.CDrawObj.Companion.ROI_TYPE_LINE
import com.forsk.ondevice.CDrawObj.Companion.ROI_TYPE_POLYGON
import com.forsk.ondevice.CDrawObj.Companion.ROI_TYPE_RECT
import com.forsk.ondevice.databinding.ActivityMapeditorBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Objects

// opencv 추가
class MapEditorActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MapEditorActivity"
        private const val NAME_LIBRARY_CASELAB_OPT = "mapoptimization_arm_v2_240117"

        /*
        PATH_FILE_MAP_ORG : 가공되지 않은 원본 pgm, yaml 파일이 존재하는 폴더
        PATH_FILE_MAP_ROT : so 라이브리를 이용해서 가공된 pgm, yaml 결과 파일일 만들어질 폴더
        NAME_FILE_MAP_ORG : 파일형식 확장자(pgm,yaml) 제외한 파일명
        PATH_FILE_MAP_OPT : 옵티마이징한 결과를 저장할 폴더명
        PATH_FILE_MAP_SEG : 세그멘트 결과를 저장할 폴더명
        참고 : NAME_FILE_MAP_ORG의 파일명으로 각각 다른 폴더에 결과물이 나오면 최종 결과물은 PATH_FILE_MAP_ROT 폴더에 저장됨
     */
        private const val PATH_FILE_MAP_ORG = "/sdcard/Download/"
        private const val PATH_FILE_MAP_ROT = "/sdcard/Download/map_test/rot/"
        private const val NAME_FILE_MAP_ORG = "office" //"5py";
        private const val PATH_FILE_MAP_OPT = "/sdcard/Download/map_test/opt/"
        private const val PATH_FILE_MAP_SEG = "/sdcard/Download/map_test/seg/"

        private const val PICK_PGM_FILE_REQUEST = 1

        private const val REQUEST_EXTERNAL_STORAGE = 1
        private val PERMISSIONS_STORAGE = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        // 모드 상수 정의
        private const val MODE_MAP_EXPLORATION = 0
        private const val MODE_SPACE_CREATION = 1
        private const val MODE_BLOCK_WALL = 2
        private const val MODE_BLOCK_AREA = 3
        private const val MODE_EDIT_PIN = 4
    }

    private var _binding: ActivityMapeditorBinding? = null
    private val binding get() = _binding!!

    private var _viewModel: MapEditorViewModel? = null
    private val viewModel get() = _viewModel!!

    private val isFabOpen = false

    var strMode: String = "Zoom"
    var srcMapPgmFilePath: String? = ""
    var srcMapPngFilePath: String = ""
    var srcMapYamlFilePath: String? = ""
    var destMappingFilePath: String? = ""

    var nResolution: Double = 0.0
    var origin_x: Double = 0.0
    var origin_y: Double = 0.0
    var origin_angle: Double = 0.0

    // 현재 모드 변수
    private var currentMode = MODE_MAP_EXPLORATION

    private var PinName = ""

    private val scaleFactor = 1.0f

    // 선택된 버튼 저장 변수
    var selectedButton: TextView? = null
    private var lib_flag = true

    private var transformationMatrix: Mat? = null

    private var rotated_angle = 0f
    var original_image_height: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_OnDevice)

        _binding = ActivityMapeditorBinding.inflate(layoutInflater)
        _viewModel = ViewModelProvider(this)[MapEditorViewModel::class.java]

        setContentView(binding.root)
    }

    private fun setUI() {
        // 상태 바 제거
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        binding.apply {
            // Toggle Bar 초기 상태 설정
            updateToggleBarVisibility()

            // Description 초기 상태 설정
            updateModeDescription()

            // 241217 jihyeon
            // cancle 버튼과 goback 버튼 구분하여 구현 필요
            // cancle: 종료 확인 팝업 노출 후 확인 선택 시 홈화면 이동
            // goback: 맵그리기 <자동/수동> 선택화면으로 복귀

            cancelButton.setOnClickListener { cancelButtonClickFunc() }
            gobackButton.setOnClickListener { goBackButtonClickFunc() }
            finishButton.setOnClickListener { finishButtonClickFunc() }
            spaceCreationButton.setOnClickListener { spaceCreationButtonClickFunc() }
            buttonBlockWall.setOnClickListener { blockWallClickEvent() }
            buttonBlockArea.setOnClickListener { blockAreaButtonClickEvent() }
            // 열기 버튼 클릭 시 toggle_bar 보이기
            fabMain.setOnClickListener { fabMainClickEvent() }
            // 닫기 버튼 클릭 시 toggle_bar 숨기기
            toggleBar.fabMainBack.setOnClickListener { fabMainBackClickEvent() }
            toggleBarCreatespace.fabMainBackCS.setOnClickListener { fabMainBackClickEvent() }
            fabMoveMap.setOnClickListener { fabMoveMapClickEvent() }

            //        fabZoom.setOnClickListener(v -> {
//            MapViewer.SetMenu("줌");
//            strMode = "Zoom";
//        });

            toggleBar.apply {
                fabAddObject.setOnClickListener { fabAddObjectClickEvent() }
                fabMoveObject.setOnClickListener { fabMoveObjectClickEvent() }//  move Object
                fabSelectObject.setOnClickListener { fabSelectObjectClickEvent() }//  Select Object
                fabDeleteObject.setOnClickListener { toggleBarCreateSpaceClickEvent() }
                fabMovePin.setOnClickListener { fabMovePinCSClickEvent() }
                fabRotatePin.setOnClickListener { fabRotatePinCSClickEvent() }
            }

            // 241217 jihyeon
            // 공간 생성 모드일 때 토글 버튼 설정
            toggleBarCreatespace.apply {
                fabAddObjectCS.setOnClickListener { fabAddObjectClickEvent() }
                fabMoveObjectCS.setOnClickListener { fabMoveObjectClickEvent() }
                fabSelectObjectCS.setOnClickListener { fabSelectObjectClickEvent() }
                fabDeleteObjectCS.setOnClickListener { toggleBarCreateSpaceClickEvent() }
                fabMovePinCS.setOnClickListener { fabMovePinCSClickEvent() }
                fabRotatePinCS.setOnClickListener { fabRotatePinCSClickEvent() }
                fabRenameObjectCS.setOnClickListener { fabRenameObjectCSClickEvent() }
            }

            // 휴지통 버튼 클릭 이벤트
            deleteToggleBar.deleteButton.setOnClickListener { deleteButtonClickEvent() }

            // X 버튼 클릭 이벤트
            this.cancelButton.setOnClickListener { v: View? ->
                mapViewer.clearSelection() // 선택 초기화 메서드
                hideDeleteToggleBar()
            }

            // 방법 1: ViewTreeObserver를 사용
            binding.mapViewer.apply {
                getViewTreeObserver()?.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        // 가로와 세로 크기 얻기
                        Log.d("ViewSize", "Width: $width, Height: $height")

                        screenWidth = width
                        screenHeight = height

                        // 리스너 제거 (메모리 누수 방지)
                        getViewTreeObserver().removeOnGlobalLayoutListener(this)

                        try {
                            // File 객체 생성
                            val file = File(srcMapPgmFilePath)

                            val path = file.parent + "/" // 디렉터리 경로
                            val filename = file.name // 파일 이름
                            val fileTitle =
                                filename.substring(
                                    0,
                                    filename.lastIndexOf('.')
                                ) // 확장자를 제외한 파일 제목

                            val path_rot = path + "rot/"
                            val file_rot = File(path_rot)
                            // 폴더가 제대로 만들어졌는지 체크 ======
                            if (!file_rot.mkdirs()) {
                                Log.e("FILE", "Directory not created : $path_rot")
                            }
                            Log.d("SKOnDeviceService", "Run library-rotate!")
                            //com.caselab.forsk.MapOptimization.mapRotation(PATH_FILE_MAP_ORG, PATH_FILE_MAP_ROT, NAME_FILE_MAP_ORG);
                            MapOptimization.mapRotation(path, path_rot, fileTitle)
                            Log.d("SKOnDeviceService", "Library-rotate Finish!")

                            val path_opt = path + "opt/"
                            val file_opt = File(path_opt)
                            // 폴더가 제대로 만들어졌는지 체크 ======
                            if (!file_opt.mkdirs()) {
                                Log.e("FILE", "Directory not created : $path_opt")
                            }
                            Log.d("SKOnDeviceService", "Run library-line opt!")
                            //MapOptimization.lineOptimization(PATH_FILE_MAP_ROT, PATH_FILE_MAP_OPT, NAME_FILE_MAP_ORG);
                            MapOptimization.lineOptimization(path_rot, path_opt, fileTitle)
                            Log.d("SKOnDeviceService", "Library-line Finish!")

                            val strPgmFile = "$path_opt$fileTitle.pgm"
                            val strPngFile = "$path_opt$fileTitle.png"

                            //Log.d(TAG, strPgmFile);
                            lib_flag = true
                            srcMapPgmFilePath = strPgmFile
                            srcMapPngFilePath = strPngFile
                            srcMapYamlFilePath = "$path_opt$fileTitle.yaml"
                        } catch (e: UnsatisfiedLinkError) {
                            Log.e(TAG, "Native library not loaded or linked properly", e)
                        } catch (e: ExceptionInInitializerError) {
                            Log.e(TAG, "Initialization error in native method", e)
                        } catch (e: RuntimeException) {
                            Log.e(TAG, "Runtime exception occurred", e)
                        } catch (e: Throwable) {
                            Log.e(TAG, "Unexpected error occurred", e)
                        }

                        try {
                            //v2_240117.so
                            val bitmap = loadPNG(srcMapPngFilePath)
                            // 241217v11.so
                            // Bitmap bitmap = loadPGM(srcMapPgmFilePath);
                            if (bitmap != null) {
                                setBitmap(bitmap)
                            }
                        } catch (e: IOException) {
                            Log.e(TAG, "IOException error occurred", e)
                        }

                        if (!loadYaml(srcMapYamlFilePath)) {
                            Log.d(
                                TAG,
                                "con not read $srcMapYamlFilePath"
                            )
                        }
                        // 241218 성웅 맵 불러오기
                        lifecycleScope.launch(IO) {
                            if (!viewModel.loadJson(
                                    mapViewer = mapViewer,
                                    filePath = destMappingFilePath,
                                    lib_flag = lib_flag
                                )
                            ) {
                                Log.d(
                                    TAG,
                                    "con not read $destMappingFilePath"
                                )
                            }
                        }
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

                })
            }
        }
    }

    private fun deleteButtonClickEvent() {
        binding.apply {
            mapViewer.roi_RemoveObject()
            mapViewer.cancelCurObject()
            mapViewer.clearSelection() // 선택 초기화 메서드
        }
        hideDeleteToggleBar()
    }

    private fun fabRenameObjectCSClickEvent() {
        strMode = "None"
        val oldPinName = PinName
        binding.apply {
            showCustomDialog(object : DialogCallback {
                override fun onConfirm(selectedText: String?) {
                    if ((mapViewer.roiCurObject != null)) {
                        mapViewer.SetLabel(PinName)
                    }
                }
            })

            fabAddObjectClicked.visibility = View.GONE
            fabSelectObjectClicked.visibility = View.GONE
            fabMoveObjectClicked.visibility = View.GONE
            fabMovePinClicked.visibility = View.GONE
            fabRotatePinClicked.visibility = View.GONE
            fabDeleteObjectClicked.visibility = View.GONE
            fabRenameObjectClicked.visibility = View.GONE
        }

        updateToggleBarVisibility()
        hideDeleteToggleBar()
    }

    private fun fabRotatePinCSClickEvent() {
        binding.apply {
            mapViewer.SetMenu("핀 회전")
            fabAddObjectClicked.visibility = View.GONE
            fabSelectObjectClicked.visibility = View.GONE
            fabMoveObjectClicked.visibility = View.GONE
            fabMovePinClicked.visibility = View.GONE
            fabRotatePinClicked.visibility = View.VISIBLE
            fabDeleteObjectClicked.visibility = View.GONE
            fabRenameObjectClicked.visibility = View.GONE
            hideDeleteToggleBar()
        }
    }

    private fun fabMovePinCSClickEvent() {
        binding.apply {
            mapViewer.SetMenu("핀 이동")
            fabAddObjectClicked.visibility = View.GONE
            fabSelectObjectClicked.visibility = View.GONE
            fabMoveObjectClicked.visibility = View.GONE
            fabMovePinClicked.visibility = View.VISIBLE
            fabRotatePinClicked.visibility = View.GONE
            fabDeleteObjectClicked.visibility = View.GONE
            fabRenameObjectClicked.visibility = View.GONE
            hideDeleteToggleBar()
        }
    }

    private fun toggleBarCreateSpaceClickEvent() {
        binding.apply {
            mapViewer.SetMenu("삭제")
            fabAddObjectClicked.visibility = View.GONE
            fabSelectObjectClicked.visibility = View.GONE
            fabMoveObjectClicked.visibility = View.GONE
            fabMovePinClicked.visibility = View.GONE
            fabRotatePinClicked.visibility = View.GONE
            fabDeleteObjectClicked.visibility = View.VISIBLE
            fabRenameObjectClicked.visibility = View.GONE
            Log.d(
                TAG,
                "current index: " + mapViewer.currentSelectedIndex
            )
            if (mapViewer.currentSelectedIndex != -1) {
                val selectedObjectPosition = mapViewer.selectedObjectPosition
                showDeleteToggleBar(selectedObjectPosition!!)
            }
        }
    }

    private fun fabSelectObjectClickEvent() {
        binding.apply {
            mapViewer.SetMenu("선택")
            fabAddObjectClicked.visibility = View.GONE
            fabSelectObjectClicked.visibility = View.VISIBLE
            fabMoveObjectClicked.visibility = View.GONE
            fabMovePinClicked.visibility = View.GONE
            fabRotatePinClicked.visibility = View.GONE
            fabDeleteObjectClicked.visibility = View.GONE
            fabRenameObjectClicked.visibility = View.GONE
            hideDeleteToggleBar()
        }
    }

    private fun fabMoveObjectClickEvent() {
        binding.apply {
            mapViewer.SetMenu("수정")
            fabAddObjectClicked.visibility = View.GONE
            fabSelectObjectClicked.visibility = View.GONE
            fabMoveObjectClicked.visibility = View.VISIBLE
            fabMovePinClicked.visibility = View.GONE
            fabRotatePinClicked.visibility = View.GONE
            fabDeleteObjectClicked.visibility = View.GONE
            fabRenameObjectClicked.visibility = View.GONE
            hideDeleteToggleBar()
        }
    }

    private fun fabAddObjectClickEvent() {
        binding.apply {
            mapViewer.SetMenu("추가")
            fabAddObjectClicked.visibility = View.VISIBLE
            fabSelectObjectClicked.visibility = View.GONE
            fabMoveObjectClicked.visibility = View.GONE
            fabMovePinClicked.visibility = View.GONE
            fabRotatePinClicked.visibility = View.GONE
            fabDeleteObjectClicked.visibility = View.GONE
            fabRenameObjectClicked.visibility = View.GONE
            hideDeleteToggleBar()
        }
    }

    private fun fabMoveMapClickEvent() {
        binding.apply {
            fabAddObjectClicked.visibility = View.GONE
            fabSelectObjectClicked.visibility = View.GONE
            fabMoveObjectClicked.visibility = View.GONE
            fabMovePinClicked.visibility = View.GONE
            fabRotatePinClicked.visibility = View.GONE
            fabDeleteObjectClicked.visibility = View.GONE
            fabRenameObjectClicked.visibility = View.GONE
            if (mapViewer.strMenu === "이동") {
                mapViewer.SetMenu("이동")
                //strMode = "Move";
            } else {
                mapViewer.SetMenu("이동")
                //strMode = "Move";
            }
        }
    }

    private fun fabMainBackClickEvent() {
        binding.apply {
            toggleBar.root.visibility = View.GONE // toggle_bar 숨기기
            fabMain.visibility = View.VISIBLE // 열기 버튼 보이기
            toggleBar.fabMainBack.visibility = View.INVISIBLE // 닫기 버튼 숨기기
            fabAddObjectClicked.visibility = View.GONE
            fabSelectObjectClicked.visibility = View.GONE
            fabMoveObjectClicked.visibility = View.GONE
            fabMovePinClicked.visibility = View.GONE
            fabRotatePinClicked.visibility = View.GONE
            fabDeleteObjectClicked.visibility = View.GONE
            fabRenameObjectClicked.visibility = View.GONE
        }
    }

    private fun fabMainClickEvent() {
        binding.apply {
            if (currentMode == MODE_SPACE_CREATION) {
                toggleBarCreatespace.root.visibility = View.VISIBLE // toggle_bar 보이기
                fabMain.visibility = View.INVISIBLE // 열기 버튼 숨기기
                toggleBarCreatespace.fabMainBackCS.visibility = View.VISIBLE // 닫기 버튼 보이기
            } else if (currentMode == MODE_BLOCK_WALL || currentMode == MODE_BLOCK_AREA) {
                toggleBar.root.visibility = View.VISIBLE // toggle_bar 보이기
                fabMain.visibility = View.INVISIBLE // 열기 버튼 숨기기
                toggleBar.fabMainBack.visibility = View.VISIBLE // 닫기 버튼 보이기
            }
        }
    }

    private fun blockAreaButtonClickEvent() {
        binding.apply {
            currentMode = MODE_BLOCK_AREA
            mapViewer.SetMode("금지공간")
            Log.d(TAG, "금지공간 모드 활성화")
            updateButtonBackground(buttonBlockArea)
            updateToggleBarVisibility()
            updateModeDescription()
            hideDeleteToggleBar()
            fabAddObjectClicked.visibility = View.GONE
            fabSelectObjectClicked.visibility = View.GONE
            fabMoveObjectClicked.visibility = View.GONE
            fabMovePinClicked.visibility = View.GONE
            fabRotatePinClicked.visibility = View.GONE
            fabDeleteObjectClicked.visibility = View.GONE
            fabRenameObjectClicked.visibility = View.GONE
        }
    }

    private fun blockWallClickEvent() {
        binding.apply {
            currentMode = MODE_BLOCK_WALL
            mapViewer.SetMode("가상벽")
            Log.d(TAG, "가상벽 모드 활성화")
            updateButtonBackground(buttonBlockWall)
            updateToggleBarVisibility()
            updateModeDescription()
            hideDeleteToggleBar()
            fabAddObjectClicked.visibility = View.GONE
            fabSelectObjectClicked.visibility = View.GONE
            fabMoveObjectClicked.visibility = View.GONE
            fabMovePinClicked.visibility = View.GONE
            fabRotatePinClicked.visibility = View.GONE
            fabDeleteObjectClicked.visibility = View.GONE
            fabRenameObjectClicked.visibility = View.GONE
        }
    }

    private fun spaceCreationButtonClickFunc() {
        binding.apply {
            currentMode = MODE_SPACE_CREATION
            mapViewer.SetMode("공간 생성")
            Log.d(TAG, "공간 생성 모드 활성화")
            updateButtonBackground(spaceCreationButton)
            updateToggleBarVisibility()
            updateModeDescription()
            hideDeleteToggleBar()
            fabAddObjectClicked.visibility = View.GONE
            fabSelectObjectClicked.visibility = View.GONE
            fabMoveObjectClicked.visibility = View.GONE
            fabMovePinClicked.visibility = View.GONE
            fabRotatePinClicked.visibility = View.GONE
            fabDeleteObjectClicked.visibility = View.GONE
            fabRenameObjectClicked.visibility = View.GONE
        }
    }

    private fun finishButtonClickFunc() {
        Log.d(
            TAG,
            "finshButton.setOnClickListener(...)"
        )
        try {
            val strFileName = "map_meta_sample.json"
            val strPath = "/sdcard/Download"

            // TODO 수정필요 srcMappingfilePath 경로로 확인해서 name, path구분

            //String strSDCardPath = Environment.getExternalStorageDirectory().getAbsolutePath();   // 외부 저장소의 절대 경로를 자동으로 가져와 주는 메서드
            Log.d(TAG, "try roi_saveToFile()")
            if (roi_saveToFile(strPath, strFileName)) {
                sendFinishBroadcast(strPath, strFileName)
            } else {
                Toast.makeText(
                    applicationContext,
                    "can not save JSON data !",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (ie: InterruptedException) {
            Log.e(
                TAG,
                "SendBroadcast Unexpected error: " + ie.message
            )
        }
    }

    private fun sendFinishBroadcast(strPath: String, strFileName: String) {
        lifecycleScope.launch {
            launch {
                Log.d(TAG, "send broadcast... ")
                val intent = Intent("sk.action.airbot.map.responseMapping").apply {
                    setPackage("com.sk.airbot.iotagent")
                    putExtra("destMappingFilePath", "$strPath/$strFileName")
                    putExtra("resultCode", "MRC_000")
                    putExtra("appStatus", "stop")
                }

                sendBroadcast(intent)
                Log.d(TAG, "sent broadcast. ")
            }.join()
            launch {
                Log.d(TAG, "finish activity ")
                finish()
            }
        }
    }

    private fun goBackButtonClickFunc() {
        Log.d(
            TAG,
            "canclebutton.setOnClickListener(...)"
        )
        try {
            val strFileName = "map_meta_sample.json"
            val strPath = "/sdcard/Download"
            val destFile = File(strPath, strFileName)

            if (!destFile.isFile) {
                if (roi_saveToEmptyFile(strPath, strFileName)) Log.d(
                    TAG,
                    "Success create empty Json File"
                )
                else Log.d(
                    TAG,
                    "Fail create empty File"
                )
            } else {
                Log.d(TAG, "Already Exist Json FIle")
            }
            sendCancelBroadcast(strPath, strFileName)
        } catch (ie: InterruptedException) {
            Log.e(
                TAG,
                "goBack Button InterruptedExtception: " + ie.message
            )
        }
    }

    private fun sendCancelBroadcast(strPath: String, strFileName: String) {
        Log.d(TAG, "send broadcast... ")
        lifecycleScope.launch {
            launch {
                val intent = Intent("sk.action.airbot.map.responseMapping")
                intent.setPackage("com.sk.airbot.iotagent")
                intent.putExtra("destMappingFilePath", "$strPath/$strFileName")
                intent.putExtra("resultCode", "MRC_000")
                intent.putExtra("appStatus", "stop")
                sendBroadcast(intent)
                Log.d(TAG, "sent broadcast. ")
            }.join()

            launch {
                Log.d(TAG, "finish activity ")
                finish()
            }
        }
    }

    private fun cancelButtonClickFunc() {
        Log.d(
            TAG,
            "canclebutton.setOnClickListener(...)"
        )
        try {
            val strFileName = "map_meta_sample.json"
            val strPath = "/sdcard/Download"
            val destFile = File(strPath, strFileName)

            if (!destFile.isFile) {
                if (roi_saveToEmptyFile(strPath, strFileName)) Log.d(
                    TAG,
                    "Success create empty Json File"
                )
                else Log.d(
                    TAG,
                    "Fail create empty File"
                )
            } else {
                Log.d(TAG, "Already Exist Json FIle")
            }

            sendCancelBroadcast(strPath, strFileName)

        } catch (ie: InterruptedException) {
            Log.e(
                TAG,
                "Cancel Button InterruptedExtception: " + ie.message
            )
        }
    }

    private fun sendBroadcast() {
        Log.d(TAG, "send broadcast appStatus start... ")

        Intent("sk.action.airbot.map.responseMapping").apply {
            setPackage("com.sk.airbot.iotagent")
            putExtra("appStatus", "start")
            sendBroadcast(this)
        }

        Log.d(TAG, "sent broadcast. ")
    }

    // 토글바 표시 함수
    fun showDeleteToggleBar(position: Pair<Int?, Int?>) {
        binding.apply {
            Log.d(TAG, "Toggle Bar Location: " + position.first + ", " + position.second)
            deleteToggleBar.root.visibility = View.VISIBLE
            // 선택된 객체 위치에 토글바 배치
            deleteToggleBar.root.x = position.first!!.toFloat()
            deleteToggleBar.root.y = position.second!!.toFloat()
        }
    }

    // 토글바 숨김 함수
    private fun hideDeleteToggleBar() {
        binding.deleteToggleBar.root.visibility = View.GONE
    }

    private fun updateToggleBarVisibility() {
        binding.apply {
            if (currentMode == MODE_SPACE_CREATION) {
                toggleBarCreatespace.root.visibility = View.VISIBLE
                toggleBar.root.visibility = View.GONE
            } else if (currentMode == MODE_BLOCK_WALL || currentMode == MODE_BLOCK_AREA) {
                toggleBar.root.visibility = View.VISIBLE
                toggleBarCreatespace.root.visibility = View.GONE
            } else {
                toggleBar.root.visibility = View.GONE
                toggleBarCreatespace.root.visibility = View.GONE
            }
        }
    }

    // currentMode에 따라 텍스트 업데이트
    private fun updateModeDescription() {
        binding.apply {
            modeDescription.text = when (currentMode) {
                MODE_SPACE_CREATION -> "한 공간으로 묶고 싶은 영역을 3점 이상 Tap하여 지정해주세요."
                MODE_BLOCK_WALL -> "Drag하여 진입 금지벽을 지정해주세요."
                MODE_BLOCK_AREA -> "Drag하여 진입 금지 영역을 지정해주세요."
                else -> "맵 내에 영역을 묶어 공간을 나눠주세요" // 다른 상태에서는 빈 문자열
            }
        }
    }

    // 버튼 색상 변경 메소드
    private fun updateButtonBackground(currentButton: TextView) {
        binding.apply {
            // 이전에 선택된 버튼이 있으면 배경을 원래 색상으로 변경
            if (selectedButton != null) {
                selectedButton!!.setBackgroundResource(R.drawable.rounded_button) // 원래 배경
                selectedButton!!.setTextColor(Color.WHITE)
            }
            // 현재 선택된 버튼은 흰색으로 변경
            currentButton.setBackgroundResource(R.drawable.rounded_button_white)
            currentButton.setTextColor(Color.BLACK)
            selectedButton = currentButton
        }
    }

    interface DialogCallback {
        fun onConfirm(selectedText: String?) // 선택된 텍스트를 반환
    }

    private fun showCustomDialog(callback: DialogCallback) {
        // Inflate the custom layout
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_scrollable_with_buttons, null)

        val builder = AlertDialog.Builder(this, R.style.CustomDialogTheme)
        builder.setView(dialogView)

        val dialog = builder.create()

        // Find views
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radio_group)
        val cancelButton = dialogView.findViewById<Button>(R.id.rename_pin_cancel_button)
        val confirmButton = dialogView.findViewById<Button>(R.id.rename_pin_confirm_button)

        // Layout for items
        val layout = arrayOf(
            arrayOf("거실", "게스트룸", "드레스룸", "발코니"),  // 첫 번째 행
            arrayOf("복도", "서재", "아이방1", "아이방2"),  // 두 번째 행
            arrayOf("안방", "욕실1", "욕실2", "주방"),  // 세 번째 행
            arrayOf("침실", "현관", "", "") // 네 번째 행
        )

        // List to manage all RadioButtons
        val allRadioButtons = ArrayList<RadioButton>()
        // 선택된 라디오 버튼의 텍스트를 추적하기 위한 변수
        val selectedText = arrayOf<String>("")
        // Dynamically add rows
        for (row in layout) {
            // Create a horizontal LinearLayout for each row
            val rowLayout = LinearLayout(this)
            rowLayout.orientation = LinearLayout.HORIZONTAL
            rowLayout.layoutParams = RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.MATCH_PARENT,
                RadioGroup.LayoutParams.WRAP_CONTENT
            )
            var idCounter = 0 // 고유 ID 생성기
            // Add items to the row
            for (item in row) {
                if (!item.isEmpty()) {
                    // Create and style the RadioButton
                    val radioButton = RadioButton(this)
                    radioButton.text = item
                    radioButton.id = idCounter++
                    radioButton.setTextColor(resources.getColor(android.R.color.white))
                    radioButton.textSize = 20f

                    // Set LayoutParams with margins
                    val layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f // 균등 분배
                    )
                    layoutParams.setMargins(10, 20, 10, 20) // 위아래 마진 추가
                    radioButton.layoutParams = layoutParams

                    radioButton.gravity = Gravity.LEFT // 왼쪽 정렬

                    // Add to the list
                    allRadioButtons.add(radioButton)

                    // Add click listener to uncheck others
                    radioButton.setOnClickListener { v: View ->
                        for (rb in allRadioButtons) {
                            if (rb !== v) {
                                rb.isChecked = false // 다른 버튼은 해제
                            } else selectedText[0] = item
                        }
                    }

                    // Add RadioButton to the row
                    rowLayout.addView(radioButton)
                } else {
                    // Add empty space for empty slots
                    val space = View(this)
                    space.layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f // 빈 공간도 균등 분배
                    )
                    rowLayout.addView(space)
                }
            }

            // Add the row to the RadioGroup
            radioGroup.addView(rowLayout)
        }

        // Set button listeners
        cancelButton.setOnClickListener { v: View? -> dialog.dismiss() }

        confirmButton.setOnClickListener { v: View? ->
            if (selectedText[0] != null) {
                PinName = selectedText[0]
                callback.onConfirm(selectedText[0])
                dialog.dismiss()
            } else {
                Log.d(TAG, "공간명이 선택되지 않음")
                dialog.dismiss()
            }
        }

        // Show the dialog
        dialog.show()

        // Adjust dialog size
        val window = dialog.window
        if (window != null) {
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val params = window.attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT // 너비
            params.height = WindowManager.LayoutParams.WRAP_CONTENT // 높이
            params.horizontalMargin = 0.05f // 좌우 마진 (화면 비율로 계산)
            params.verticalMargin = 0.1f // 상하 마진
            window.attributes = params
        }
    }

    private fun toggleFabVisibility(fabToHide: View, fabToShow: View) {
        // 숨길 FAB를 GONE으로 설정
        fabToHide.visibility = View.GONE

        // 보일 FAB를 VISIBLE로 설정
        fabToShow.visibility = View.VISIBLE
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
    @Throws(IOException::class)
    private fun loadPGM(filePath: String): Bitmap? {
        Log.d(TAG, "loadPGM(\"$filePath\")")

        val paths = filePath.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val fileName = paths[paths.size - 1]
        Log.d(TAG, "fileName : $fileName")

        val file = File(filePath)
        //if (!file.exists()) {
        //    throw new IOException("File not found: " + filePath);
        //}
        try {
            FileInputStream(file).use { fis ->
                //InputStream inputStream = new FileInputStream(filePath);
                //AssetManager assetManager = getAssets();
                //InputStream inputStream = assetManager.open(fileName);


                // Basic PGM file decoding
                val magicNumber = readToken(fis) // Reads "P5"
                if ("P5" != magicNumber) throw IOException("Not a PGM file")

                val width = readToken(fis).toInt()
                val height = readToken(fis).toInt()
                val maxGray = readToken(fis).toInt()

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                for (y in 0 until height) {
                    for (x in 0 until width) {
                        var grayValue = fis.read()
                        // original PGM은 흰색:254, 0:검은색,else:회색
                        // rotate pgm은 흰색 255, Else:회색(220)
                        grayValue =
                            if ((grayValue == 254) || (grayValue == 255)) { //내부와 외걱선 옅은 회색 #969696
                                150
                            } else if (grayValue == 0) {
                                17
                            } else {    //외부(254)는 짙은 회색 #333333
                                51
                            }
                        val color = Color.rgb(grayValue, grayValue, grayValue)
                        bitmap.setPixel(x, y, color)
                    }
                }
                return bitmap
            }
        } catch (fe: FileNotFoundException) {
            Log.e(TAG, "loadPGM FileNotFoundException: " + fe.message)
            verifyStoragePermissions(this)
        }
        return null
    }

    @Throws(IOException::class)
    private fun loadPNG(filePath: String): Bitmap {
        Log.d(TAG, "loadPNG(\"$filePath\")")

        // 파일 이름 로깅(필수는 아님)
        val paths = filePath.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val fileName = paths[paths.size - 1]
        Log.d(TAG, "fileName : $fileName")

        val file = File(filePath)
        if (!file.exists()) {
            throw IOException("File not found: $filePath")
        }

        // PNG 디코딩
        val originalBitmap = BitmapFactory.decodeFile(filePath)
            ?: throw IOException("Failed to decode PNG file: $filePath")

        val width = originalBitmap.width
        val height = originalBitmap.height

        // 결과를 담을 Bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // 각 픽셀을 순회하며 R 값만 확인
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = originalBitmap.getPixel(x, y)

                // R 값 추출
                val r = Color.red(pixel)
                // r 값 기준으로 흰색/검은색/회색 매핑
                var newGray = if (r == 255) {
                    // 흰색
                    150
                } else if (r == 0) {
                    // 검은색
                    0
                } else {
                    // 회색
                    51
                }

                // 새 픽셀 값
                val newPixel = Color.rgb(newGray, newGray, newGray)
                bitmap.setPixel(x, y, newPixel)
            }
        }

        return bitmap
    }

    protected override fun onResume() {
        super.onResume()

        setUI()

        sendBroadcast()

//        // 추가된 TextView 참조
//        var modeDescription: TextView? = null

//        val fabMain = findViewById<Button>(R.id.fabMain)
//        val fabMainBack = findViewById<View>(R.id.fabMainBack)
//        val fabMainBackCS = findViewById<View>(R.id.fabMainBackCS)

        // 맵 탐색 - 줌 인/아웃, 맵 이동
        //val fabMoveMap = findViewById<View>(R.id.fabMoveMap)

        // 공간생성,가상벽,금지구역,청저위치 - 객체 추가, 객체 이동, 선택, 제거, 이름 변경
//        val fabAddObject = findViewById<View>(R.id.fabAddObject)
//        val fabMoveObject = findViewById<View>(R.id.fabMoveObject)
//        val fabSelectObject = findViewById<View>(R.id.fabSelectObject)
//        val fabDeleteObject = findViewById<View>(R.id.fabDeleteObject)
//        val fabMovePin = findViewById<View>(R.id.fabMovePin)
//        val fabRotatePin = findViewById<View>(R.id.fabRotatePin)

//        val fabAddObjectCS = findViewById<View>(R.id.fabAddObjectCS)
//        val fabMoveObjectCS = findViewById<View>(R.id.fabMoveObjectCS)
//        val fabSelectObjectCS = findViewById<View>(R.id.fabSelectObjectCS)
//        val fabDeleteObjectCS = findViewById<View>(R.id.fabDeleteObjectCS)
//        val fabRenameObjectCS = findViewById<View>(R.id.fabRenameObjectCS)
//        val fabMovePinCS = findViewById<View>(R.id.fabMovePinCS)
//        val fabRotatePinCS = findViewById<View>(R.id.fabRotatePinCS)

//        val finishButton = findViewById<Button>(R.id.finish_button)
//        val cancelButton = findViewById<Button>(R.id.cancel_button)
//        val goBackButton = findViewById<Button>(R.id.goback_button)

        // 메뉴 클릭 시 배경 생성 버튼
//        val fabAddObjectClicked = findViewById<View>(R.id.fabAddObjectClicked)
//        val fabSelectObjectClicked = findViewById<View>(R.id.fabSelectObjectClicked)
//        val fabMoveObjectClicked = findViewById<View>(R.id.fabMoveObjectClicked)
//        val fabMovePinClicked = findViewById<View>(R.id.fabMovePinClicked)
//        val fabRotatePinClicked = findViewById<View>(R.id.fabRotatePinClicked)
//        val fabDeleteObjectClicked = findViewById<View>(R.id.fabDeleteObjectClicked)
//        val fabRenameObjectClicked = findViewById<View>(R.id.fabRenameObjectClicked)

        // 삭제 버튼 초기화 (onCreate 메서드에서)
//        deleteToggleBar = findViewById(R.id.delete_toggle_bar)
//        deleteButton = deleteToggleBar?.findViewById(R.id.deleteButton)
//        cancelButton = deleteToggleBar?.findViewById(R.id.cancelButton)


        // 20241217 jihyeon
        // 공간 생성, 가상벽, 금지공간 버튼 분리
//        val buttonSpaceCreation = findViewById<Button>(R.id.button_space_creation)
//        val buttonBlockWall = findViewById<Button>(R.id.button_block_wall)
//        val buttonBlockArea = findViewById<Button>(R.id.button_block_area)

        // Mode-specific TextView 초기화
        //   modeDescription = findViewById(R.id.mode_description)

        // Toggle Bar 레이아웃 가져오기
//        toggleBar = findViewById(R.id.toggle_bar)
//        toggleBar_CreateSpace = findViewById(R.id.toggle_bar_createspace)

        srcMapPgmFilePath = intent.getStringExtra("srcMapPgmFilePath")
        if (srcMapPgmFilePath == null) {
            //srcMapPgmFilePath = "/storage/emulated/0/Download/caffe_map.pgm";   // for test
            srcMapPgmFilePath = "/sdcard/Download/office.pgm" // for test
        }
        srcMapYamlFilePath = intent.getStringExtra("srcMapYamlFilePath")
        if (srcMapYamlFilePath == null) {
            //srcMapYamlFilePath = "/storage/emulated/0/Download/caffe_map.yaml";   // for test
            //srcMapYamlFilePath = "/storage/emulated/0/Download/office.yaml";   // for test
            srcMapYamlFilePath = "/sdcard/Download/office.yaml"
        }
        destMappingFilePath = intent.getStringExtra("destMappingFilePath")
        if (destMappingFilePath == null) {
            //srcMappingFilePath = "/storage/emulated/0/Download/map_meta_sample.json";   // for test
            destMappingFilePath = "/sdcard/Download/map_meta_sample.json" // for test
        }
    }

    private fun loadYaml(filePath: String?): Boolean {
        // 내부 저장소에서 파일 스트림 열기

        try {
            FileInputStream(filePath).use { inputStream ->
                val yaml = Yaml()
                // YAML 파싱
                val data = yaml.load<Map<String, Any>>(inputStream)

                // nResolution = (double) data.get("resolution");
                // nResolution 처리
                //Log.d(TAG, "resolution : " + nResolution);
                val resolutionValue = data["resolution"]
                if (resolutionValue is Number) {
                    nResolution = resolutionValue.toDouble()
                    Log.d(TAG, "resolution : $nResolution")
                } else {
                    Log.d(TAG, "Resolution is not a valid number.")
                }

                //ArrayList<Double> origin = (ArrayList<Double>) data.get("origin");
                val origin = data["origin"] as ArrayList<*>?
                if (origin != null && origin.size >= 3) {
                    val originX = origin[0]
                    val originY = origin[1]
                    val originAngle = origin[2]

                    if (originX is Number) {
                        origin_x = originX.toDouble()
                        Log.d(TAG, "origin_x : $origin_x")
                    } else {
                        Log.d(TAG, "origin_x is not a valid number.")
                    }

                    if (originY is Number) {
                        origin_y = originY.toDouble()
                        Log.d(TAG, "origin_y : $origin_y")
                    } else {
                        Log.d(TAG, "origin_y is not a valid number.")
                    }

                    if (originAngle is Number) {
                        origin_angle = originAngle.toDouble()
                        Log.d(
                            TAG,
                            "origin_angle : $origin_angle"
                        )
                    } else {
                        Log.d(TAG, "origin_angle is not a valid number.")
                    }
                } else {
                    Log.d(TAG, "Origin data is incomplete or invalid.")
                }

                //            Log.d(TAG, "origin.get(0) : " + origin.get(0));
//
//            //origin_x = (double)Double.parseDouble(data.get(0).toString());
//            origin_x = (double) origin.get(0);
//
//            Log.d(TAG, "origin_x : " + origin_x);
//
//            origin_y = (double) origin.get(1);
//            origin_angle = (double) origin.get(2);
//            Log.d(TAG, "origin_y : " + origin_y);
//            Log.d(TAG, "origin_angle : " + origin_angle);

                // 241222 최적화 라이브러리 읽을 경우 추가.
                if (lib_flag) {
                    // OpenCV 네이티브 라이브러리 로드
                    try {
                        if (!OpenCVLoader.initDebug()) {
                            Log.e("OpenCV", "Initialization failed.")
                        } else {
                            Log.d("OpenCV", "Initialization succeeded.")
                        }
                    } catch (e: UnsatisfiedLinkError) {
                        Log.e("OpenCV", "Native library not found or incompatible ABI: $e")
                    } catch (e: SecurityException) {
                        Log.e("OpenCV", "Security exception while loading OpenCV: $e")
                    } catch (e: RuntimeException) {
                        Log.e(
                            "OpenCV",
                            "Runtime exception during OpenCV initialization: $e"
                        )
                    }
                    //original_image_height = (int)data.get("original_image_height");
                    val originalImageHeightValue = data["original_image_height"]
                    if (originalImageHeightValue is Number) {
                        original_image_height = originalImageHeightValue.toInt()
                        Log.d(
                            TAG,
                            "original_image_height: $original_image_height"
                        )
                    } else {
                        Log.d(TAG, "original_image_height is not a valid number.")
                    }

                    // 추가: transformation_matrix 읽기
                    //ArrayList<ArrayList<Double>> matrixList = (ArrayList<ArrayList<Double>>) data.get("transformation_matrix");
                    //for (int i = 0; i < matrixList.size(); i++) {
                    //    ArrayList<Double> row = matrixList.get(i);
                    //    Log.d(TAG, "transformation_matrix row " + i + " : " + row);
                    //}
                    val matrixList = data["transformation_matrix"] as ArrayList<ArrayList<*>>?
                    if (matrixList != null && !matrixList.isEmpty()) {
                        val rows = matrixList.size
                        val cols = matrixList[0].size

                        transformationMatrix = Mat(rows, cols, CvType.CV_64F)

                        for (i in 0 until rows) {
                            val row = matrixList[i]
                            for (j in 0 until cols) {
                                val cellValue = row[j]
                                if (cellValue is Number) {
                                    transformationMatrix!!.put(i, j, cellValue.toDouble())
                                    Log.d(
                                        TAG,
                                        "transformation_matrix row " + i + " , " + j + " : " + cellValue.toDouble()
                                    )
                                } else {
                                    Log.d(
                                        TAG,
                                        "Invalid value in transformation_matrix at ($i, $j)."
                                    )
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "Transformation matrix data is invalid.")
                    }

                    // 행과 열 크기 설정
                    //int rows = matrixList.size();
                    //int cols = matrixList.get(0).size();

                    // OpenCV Mat 객체 생성
                    //transformationMatrix = new Mat(rows, cols, CvType.CV_64F);

                    // 데이터 복사
//                for (int i = 0; i < rows; i++) {
//                    ArrayList<?> row = matrixList.get(i); // 데이터를 제네릭으로 읽음
//                    for (int j = 0; j < cols; j++) {
//                        Number value = (Number) row.get(j); // Number로 캐스팅
//                        transformationMatrix.put(i, j, value.doubleValue()); // Double로 변환하여 Mat에 추가
//                    }
//                }
                    val rotatedAngleValue = data["rotated_angle"]
                    if (rotatedAngleValue is Number) {
                        rotated_angle = rotatedAngleValue.toFloat()
                        Log.d(
                            TAG,
                            "Converted to float: $rotated_angle"
                        )
                    } else {
                        Log.d(TAG, "rotated_angle is not a valid number.")
                    }
                    //rotated_angle = (float) (double) data.get("rotated_angle");
                    //Log.d(TAG,"rotated angle: " + rotated_angle);
                }
                return true
            }
        } catch (e: IOException) {
            Log.e(TAG, "loadYaml Exception: " + e.message)
            return false
        } catch (e: NullPointerException) {
            Log.e(TAG, "loadYaml Exception: " + e.message)
            return false
        }
    }

    fun roi_saveToFile(strPath: String, strFileName: String): Boolean {
        val mapViewer = binding.mapViewer
        var j: Int
        val image_width = mapViewer.GetBitmapWidth()
        val image_height = mapViewer.GetBitmapHeight()

        Log.d(
            TAG,
            "image width : $image_width , image_height : $image_height"
        )
        var count_id = 0
        var count_id_rect = 0
        var count_id_line = 0

        val now = Date()
        // 원하는 포맷으로 설정
        @SuppressLint("SimpleDateFormat") val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
        // 날짜를 포맷팅
        val formattedDate = sdf.format(now)
        var strRoiJson = ""
        strRoiJson += "{"
        strRoiJson += "\"uid\":\"OnDevice\",\"info\":{\"version\":\"1.0.0\",\"modified\":"
        strRoiJson += "\"$formattedDate\"}"
        strRoiJson += ", \"room_list\":["

        Log.d(TAG, "MapViewer.m_RoiObjects.size() : " + mapViewer.roiObjects.size)

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
        var i = 0
        while (i < mapViewer.roiObjects.size) {
            if (mapViewer.roiObjects[i].roiType == ROI_TYPE_POLYGON) {
                if (count_id > 0) {
                    strRoiJson += ", "
                }

                count_id++ // 고유 id


                strRoiJson += "{"
                strRoiJson += "\"id\": \"$count_id\""
                strRoiJson += ", \"name\": \"" + mapViewer.roiObjects[i].label + "\""
                strRoiJson += ", \"color\":\"#47910f\", \"desc\":\"\""
                strRoiJson += ", \"robot_path\":["

                //strRoiJson += "{\"x\":-2.97,\"y\":7.15},{\"x\":-3.02,\"y\":7.1000004},{\"x\":-3.17,\"y\":7.1000004},{\"x\":-3.22,\"y\":7.05},{\"x\":-3.22,\"y\":6.9},{\"x\":-3.27,\"y\":6.8500004},{\"x\":-3.27,\"y\":4},{\"x\":-3.22,\"y\":3.95},{\"x\":-2.47,\"y\":3.95},{\"x\":-2.42,\"y\":4},{\"x\":-2.22,\"y\":4},{\"x\":-2.17,\"y\":4.05},{\"x\":-1.7199999,\"y\":4.05},{\"x\":-1.7199999,\"y\":4.1},{\"x\":-1.67,\"y\":4.15},{\"x\":-1.67,\"y\":4.25},{\"x\":-1.62,\"y\":4.3},{\"x\":-1.62,\"y\":4.35},{\"x\":-1.5699999,\"y\":4.4},{\"x\":-1.5699999,\"y\":4.4500003},{\"x\":-1.52,\"y\":4.5},{\"x\":-1.52,\"y\":4.6},{\"x\":-1.4699999,\"y\":4.65},{\"x\":-1.4699999,\"y\":4.7000003},{\"x\":-1.42,\"y\":4.75},{\"x\":-1.42,\"y\":4.8500004},{\"x\":-1.37,\"y\":4.9},{\"x\":-1.37,\"y\":4.9500003},{\"x\":-1.3199999,\"y\":5},{\"x\":-1.3199999,\"y\":5.05},{\"x\":-1.27,\"y\":5.1000004},{\"x\":-1.27,\"y\":5.2000003},{\"x\":-1.2199999,\"y\":5.25},{\"x\":-1.2199999,\"y\":5.3},{\"x\":-1.17,\"y\":5.3500004},{\"x\":-1.17,\"y\":5.4500003},{\"x\":-1.12,\"y\":5.5},{\"x\":-1.12,\"y\":5.55},{\"x\":-1.0699999,\"y\":5.6000004},{\"x\":-1.0699999,\"y\":5.65},{\"x\":-1.02,\"y\":5.7000003},{\"x\":-1.02,\"y\":5.8},{\"x\":-0.96999997,\"y\":5.8500004},{\"x\":-0.96999997,\"y\":5.9},{\"x\":-0.91999996,\"y\":5.9500003},{\"x\":-0.91999996,\"y\":6.05},{\"x\":-0.86999995,\"y\":6.1000004},{\"x\":-0.86999995,\"y\":6.15},{\"x\":-0.81999993,\"y\":6.2000003},{\"x\":-0.81999993,\"y\":6.3},{\"x\":-0.77,\"y\":6.3500004},{\"x\":-0.77,\"y\":6.4},{\"x\":-1.27,\"y\":6.4},{\"x\":-1.37,\"y\":6.5},{\"x\":-1.37,\"y\":6.8},{\"x\":-1.42,\"y\":6.8500004},{\"x\":-1.42,\"y\":7},{\"x\":-1.5699999,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15}";
                j = 0
                while (j < mapViewer.roiObjects[i].mPoints.size) {
                    if (j > 0) {
                        strRoiJson += ", "
                    }

                    strRoiJson += "{"

                    val coordinates = calculateCoordinate(
                        mapViewer.roiObjects[i].mPoints[j]!!.x,
                        mapViewer.roiObjects[i].mPoints[j]!!.y,
                        image_height
                    )

                    val path_x = coordinates[0]
                    val path_y = coordinates[1]

                    strRoiJson += "\"x\":$path_x"
                    strRoiJson += ", \"y\":$path_y"

                    strRoiJson += "}"
                    j++
                }
                strRoiJson += "]"
                strRoiJson += ", \"image_path\":["
                j = 0
                while (j < mapViewer.roiObjects[i].mPoints.size) {
                    if (j > 0) {
                        strRoiJson += ", "
                    }

                    strRoiJson += "{"

                    strRoiJson += "\"x\":" + (mapViewer.roiObjects[i].mPoints[j]!!.x)
                    strRoiJson += ", \"y\":" + (mapViewer.roiObjects[i].mPoints[j]!!.y)

                    strRoiJson += "}"
                    j++
                }
                strRoiJson += "]"
                strRoiJson += ", \"robot_position\":{"

                // Log.d(TAG, "xvw before : "+ xvw);

                // MapViewer.m_RoiObjects.get(i).m_MBR;
//                Log.d(TAG, "xvw : " + xvw);
//                Log.d(TAG, "nResolution : " + nResolution);
//                Log.d(TAG, "origin_x : " + origin_x);
//                Log.d(TAG, "(xvw * nResolution + origin_x) : " + (xvw * nResolution + origin_x));

                //Log.d(TAG,"height: " + image_height +", origin_y: "+ origin_y + ", imagey: " + MapViewer.m_RoiObjects.get(i).m_Points.get(j).y + ", real_y: "+ (float)((image_height-MapViewer.m_RoiObjects.get(i).m_Points.get(j).y)*nResolution + origin_y));
                //Toast.makeText(getApplicationContext(), "X: " + (float)(xvw * nResolution + origin_x) +", Y: " + ((image_height - yvh) * nResolution + origin_y), Toast.LENGTH_SHORT).show();
                val coordinates = calculateCoordinate(
                    mapViewer.roiObjects[i].mMBRCenter.x,
                    mapViewer.roiObjects[i].mMBRCenter.y,
                    image_height
                )

                val xvw = coordinates[0]
                val yvh = coordinates[1]
                strRoiJson += "\"x\":$xvw"
                strRoiJson += ", \"y\":$yvh"
                var angle =
                    mapViewer.roiObjects[i].angle - Math.toRadians(rotated_angle.toDouble())

                // -pi ~ +pi 범위 검사
//                if(angle > Math.PI)
//                {
//                    angle = -(2*Math.PI - angle);
//                }else if (angle < - Math.PI){
//                    angle = Math.PI*2 + angle;
//                }
                angle = ((angle + Math.PI) % (2 * Math.PI)) - Math.PI
                strRoiJson += ", \"theta\":$angle"

                strRoiJson += "}"

                strRoiJson += ", \"image_position\":{"
                var xvw_image = 0 // polygon의 무게중심 x

                var yvh_image = 0 // polygon의 무게중심 x

                // Log.d(TAG, "xvw before : "+ xvw);
                xvw_image = mapViewer.roiObjects[i].mMBRCenter.x
                yvh_image = mapViewer.roiObjects[i].mMBRCenter.y

                // MapViewer.m_RoiObjects.get(i).m_MBR;
                //Log.d(TAG,"height: " + image_height +", origin_y: "+ origin_y + ", imagey: " + MapViewer.m_RoiObjects.get(i).m_Points.get(j).y + ", real_y: "+ (float)((image_height-MapViewer.m_RoiObjects.get(i).m_Points.get(j).y)*nResolution + origin_y));
                //Toast.makeText(getApplicationContext(), "X: " + (float)(xvw * nResolution + origin_x) +", Y: " + ((image_height - yvh) * nResolution + origin_y), Toast.LENGTH_SHORT).show();
                strRoiJson += "\"x\":" + xvw_image
                strRoiJson += ", \"y\":" + yvh_image
                strRoiJson += ", \"theta\":" + mapViewer.roiObjects[i].angle
                strRoiJson += "}"
                strRoiJson += "}"
            }

            i++
        }
        strRoiJson += "]"
        strRoiJson += ", \"block_area\":["
        i = 0
        while (i < mapViewer.roiObjects.size) {
            if (mapViewer.roiObjects[i].roiType == ROI_TYPE_RECT) {
                if (count_id_rect > 0) {
                    strRoiJson += ", "
                }

                count_id_rect++ // 고유 id


                strRoiJson += "{"
                strRoiJson += "\"image_path\":["
                strRoiJson += "{"

                //left top
                strRoiJson += "\"x\":" + (mapViewer.roiObjects[i].mMBR.left)
                strRoiJson += ", \"y\":" + (mapViewer.roiObjects[i].mMBR.bottom)
                strRoiJson += "}, {"

                // right top
                strRoiJson += "\"x\":" + (mapViewer.roiObjects[i].mMBR.right)
                strRoiJson += ", \"y\":" + (mapViewer.roiObjects[i].mMBR.bottom)
                strRoiJson += "}, {"

                // right bottom
                strRoiJson += "\"x\":" + (mapViewer.roiObjects[i].mMBR.right)
                strRoiJson += ", \"y\":" + (mapViewer.roiObjects[i].mMBR.top)
                strRoiJson += "}, {"

                // left bottom
                strRoiJson += "\"x\":" + (mapViewer.roiObjects[i].mMBR.left)
                strRoiJson += ", \"y\":" + (mapViewer.roiObjects[i].mMBR.top)

                strRoiJson += "}"

                strRoiJson += "]"
                strRoiJson += ", \"robot_path\":["


                strRoiJson += "{"

                // left top
                var coordinates = calculateCoordinate(
                    mapViewer.roiObjects[i].mMBR.left,
                    mapViewer.roiObjects[i].mMBR.bottom,
                    image_height
                )
                var area_x = coordinates[0]
                var area_y = coordinates[1]

                strRoiJson += "\"x\":$area_x"
                strRoiJson += ", \"y\":$area_y"
                strRoiJson += "}, {"

                // right top
                coordinates = calculateCoordinate(
                    mapViewer.roiObjects[i].mMBR.right,
                    mapViewer.roiObjects[i].mMBR.bottom,
                    image_height
                )
                area_x = coordinates[0]
                area_y = coordinates[1]

                strRoiJson += "\"x\":$area_x"
                strRoiJson += ", \"y\":$area_y"
                strRoiJson += "}, {"

                // right bottom
                coordinates = calculateCoordinate(
                    mapViewer.roiObjects[i].mMBR.right,
                    mapViewer.roiObjects[i].mMBR.top,
                    image_height
                )
                area_x = coordinates[0]
                area_y = coordinates[1]

                strRoiJson += "\"x\":$area_x"
                strRoiJson += ", \"y\":$area_y"
                strRoiJson += "}, {"

                // left bottom
                coordinates = calculateCoordinate(
                    mapViewer.roiObjects[i].mMBR.left,
                    mapViewer.roiObjects[i].mMBR.top,
                    image_height
                )
                area_x = coordinates[0]
                area_y = coordinates[1]

                strRoiJson += "\"x\":$area_x"
                strRoiJson += ", \"y\":$area_y"
                strRoiJson += "}"

                strRoiJson += "]"
                strRoiJson += ", \"id\": \"a22fd01c-69b9-437f-b81e-bbf4955bd034" + (Math.random() * 255).toInt() + (Math.random() * 255).toInt() + "\""
                strRoiJson += "}"
            }

            i++
        }
        strRoiJson += "]"

        strRoiJson += ", \"block_wall\":["

        i = 0
        while (i < mapViewer.roiObjects.size) {
            if (mapViewer.roiObjects[i].roiType == ROI_TYPE_LINE) {
                if (count_id_line > 0) {
                    strRoiJson += ", "
                }

                Log.d(
                    TAG,
                    "image_height: $image_height, image width: $image_width"
                )

                count_id_line++
                val roi = mapViewer.roiObjects[i]

                strRoiJson += "{\"image_path\":["

                // Add start point
                strRoiJson += "{\"x\":" + roi.mMBR.left +
                        ", \"y\":" + (roi.mMBR.top) + "}, "

                // Add end point
                strRoiJson += "{\"x\":" + roi.mMBR.right +
                        ", \"y\":" + (roi.mMBR.bottom) + "}], "

                strRoiJson += "\"robot_path\":["

                // Convert image coordinates to robot coordinates
                val startCoordinates =
                    calculateCoordinate(roi.mMBR.left, roi.mMBR.top, image_height)
                val endCoordinates =
                    calculateCoordinate(roi.mMBR.right, roi.mMBR.bottom, image_height)

                // Add start robot path
                strRoiJson += "{\"x\":" + startCoordinates[0] +
                        ", \"y\":" + startCoordinates[1] + "}, "

                // Add end robot path
                strRoiJson += "{\"x\":" + endCoordinates[0] +
                        ", \"y\":" + endCoordinates[1] + "}], "

                // Add unique ID
                strRoiJson += "\"id\":\"d4f940b8-81cf-4b46-82d6-8f10f4e03038" + (Math.random() * 255).toInt() + "\"}"
            }


            i++
        }
        strRoiJson += "]"

        strRoiJson += ", \"assign_info\":{}"
        strRoiJson += ", \"user_angle\":0"

        strRoiJson += "}"
        Log.d(TAG, strRoiJson)

        //권한 상태 체크 팝업 띄우기
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ), 2
                )
            }
        }

        // 액션 바와 상태 바 숨기
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
        // 파일 경로를 설정합니다.
        //File file = new File(downloadDir, strFileName);
        val file = File(strPath, strFileName)
        try {
            FileOutputStream(file).use { fos ->
                //strPath += "/";
                //File directory = new File(strPath);
                //if (!directory.exists()) {
                //    boolean result = directory.mkdirs(); // 없으면 dir 경로 생성
                //    Log.i("prop", "!!!" + result);
                //    File file = new File(strPath,strFileName);
                //    file.createNewFile();
                //}

                // File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                Log.d(TAG, "$strPath/$strFileName")

                fos.write(strRoiJson.toByteArray())
                fos.flush()
                Thread.sleep(1000) // 파일쓰기가 완료될 까지 기다린다.
                fos.close()


                /*
            FileOutputStream fos = new FileOutputStream(strFileName, true);
            DataOutputStream dos = new DataOutputStream(fos);

            dos.writeUTF(strRoiJson);
            dos.flush();
            dos.close();
            */
                return true
            }
        } catch (fe: FileNotFoundException) {
            Log.d(TAG, Objects.requireNonNull(fe.localizedMessage))
            Toast.makeText(applicationContext, fe.localizedMessage, Toast.LENGTH_SHORT).show()
        } catch (ie: InterruptedException) {
            Log.d(TAG, Objects.requireNonNull(ie.localizedMessage))
        } catch (ie: IOException) {
            Log.d(TAG, Objects.requireNonNull(ie.localizedMessage))
        }

        return false
    }


    fun roi_saveToEmptyFile(strPath: String, strFileName: String): Boolean {
        var i: Int
        var j: Int
        var strRoiJson = ""
        strRoiJson += "{"
        strRoiJson += "\"uid\":\"Tybqxakqm2\",\"info\":{\"version\":\"1.0.0\",\"modified\":\"2024-11-13T20:47:41.739\"}"
        strRoiJson += ", \"room_list\":["
        strRoiJson += "]"
        strRoiJson += ", \"block_area\":["
        strRoiJson += "]"
        strRoiJson += ", \"block_wall\":["
        strRoiJson += "]"
        strRoiJson += ", \"assign_info\":{}"
        strRoiJson += ", \"user_angle\":0"
        strRoiJson += "}"

        Log.d(TAG, strRoiJson)

        //권한 상태 체크 팝업 띄우기
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ), 2
            )
        }

        // 액션 바와 상태 바 숨기
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }

        val file = File(strPath, strFileName)
        try {
            FileOutputStream(file).use { fos ->
                Log.d(TAG, "$strPath/$strFileName")
                // 파일 경로를 설정합니다.
                //File file = new File(downloadDir, strFileName);
                fos.write(strRoiJson.toByteArray())
                fos.flush()
                Thread.sleep(1000) // 파일쓰기가 완료될 까지 기다린다.
                fos.close()
                return true
            }
        } catch (ie: IOException) {
            Log.d(TAG, Objects.requireNonNull(ie.localizedMessage))
            Toast.makeText(applicationContext, ie.localizedMessage, Toast.LENGTH_SHORT).show()
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }

        return false
    }


    fun transformToRobotCoordinates(image_x: Int, image_y: Int): Point {
        // 1. 이미지 좌표를 3x1 행렬로 변환

        val pointMat = Mat(3, 1, CvType.CV_64F)
        pointMat.put(0, 0, image_x.toDouble()) // transformed_pixel_x
        pointMat.put(1, 0, image_y.toDouble()) // transformed_pixel_y
        pointMat.put(2, 0, 1.0) // Homogeneous coordinate

        // 2. 변환 행렬의 역행렬 계산
        val inverseTransformationMatrix = Mat()
        Core.invert(transformationMatrix, inverseTransformationMatrix)

        // 3. 역행렬 적용
        val inverseTransformedPointMat = Mat()
        Core.gemm(
            inverseTransformationMatrix,
            pointMat,
            1.0,
            Mat(),
            0.0,
            inverseTransformedPointMat
        )

        // 4. 원래 이미지 좌표 추출
        // 241226 성웅 int의 오차가 얼마인지 확인 필요
        val original_pixel_x = Math.round(inverseTransformedPointMat[0, 0][0]).toInt()
        val original_pixel_y = Math.round(inverseTransformedPointMat[1, 0][0]).toInt()

        //Log.d(TAG, "check dobule to int inversion. dobule: " + inverseTransformedPointMat.get(0, 0)[0] + ",int:  " + original_pixel_x);

        // 5. 로봇 좌표로 변환
        //double robot_x = (original_pixel_x * nResolution) + origin_x;
        //double robot_y = (original_image_height - original_pixel_y) * nResolution + origin_y;
        return Point(original_pixel_x, original_pixel_y)
    }

    fun calculateCoordinate(x: Int, y: Int, image_height: Int): DoubleArray {
        // 계산 공식: (x * resolution + origin_x)
        var robot_x = 0.0
        var robot_y = 0.0

        if (lib_flag) {
            val pt = transformToRobotCoordinates(x, y)
            robot_x = pt.x * nResolution + origin_x
            robot_y = (original_image_height - pt.y) * nResolution + origin_y
        } else {
            robot_x = x * nResolution + origin_x
            robot_y = (image_height - y) * nResolution + origin_y
        }
        return doubleArrayOf(robot_x, robot_y)
    }

    init {
        loadMapLibrary()
    }

    private fun loadMapLibrary() {
        try {
            System.loadLibrary(NAME_LIBRARY_CASELAB_OPT)
            Log.d("SKOnDeviceService", "SO library load success!")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(
                "SKOnDeviceService",
                "SO library load error (UnsatisfiedLinkError): $e"
            )
        } catch (e: SecurityException) {
            Log.e(
                "SKOnDeviceService",
                "SO library load error (SecurityException): $e"
            )
        }
    }

    @Throws(IOException::class)
    private fun readToken(`is`: InputStream): String {
        val sb = StringBuilder()
        var b: Int
        while ((`is`.read().also { b = it }) != -1) {
            if (b == ' '.code || b == '\n'.code) break
            sb.append(b.toChar())
        }
        return sb.toString()
    }

    fun verifyStoragePermissions(activity: Activity) {
        val permission = ActivityCompat.checkSelfPermission(
            activity,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.setData(Uri.parse("package:" + activity.packageName))

            activity.startActivity(intent)
        }
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                PERMISSIONS_STORAGE,
                REQUEST_EXTERNAL_STORAGE
            )
        }
    }
}

