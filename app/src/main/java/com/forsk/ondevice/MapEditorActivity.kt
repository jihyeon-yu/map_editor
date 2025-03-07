package com.forsk.ondevice

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import com.caselab.forsk.MapOptimization
import com.forsk.ondevice.MapImageView.OnRoiChangedListener
import com.forsk.ondevice.MapImageView.OnRoiCreateListener
import com.forsk.ondevice.MapImageView.OnRoiSelectedListener
import com.forsk.ondevice.databinding.ActivityMapEditorBinding
import org.json.JSONException
import org.json.JSONObject
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.yaml.snakeyaml.Yaml
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Objects

// opencv 추가
class MapEditorActivity : Activity() {
    private val isFabOpen = false
    private lateinit var binding: ActivityMapEditorBinding
    //private lateinit var viewModel: MapEditorViewModel

    var strMode: String = "Zoom"

    var srcMapPgmFilePath: String? = ""
    var srcMapYamlFilePath: String? = ""

    //String srcMappingFilePath = "";
    var destMappingFilePath: String? = ""

    var nResolution: Double = 0.0
    var origin_x: Double = 0.0
    var origin_y: Double = 0.0
    var origin_angle: Double = 0.0

    // 현재 모드 변수
    private var currentMode = MODE_MAP_EXPLORATION

    // 추가된 TextView 참조

    private var PinName = ""

    private val scaleFactor = 1.0f

    // 선택된 버튼 저장 변수
    private var lib_flag = true

    private var transformationMatrix: Mat? = null

    private var rotated_angle = 0f
    var original_image_height: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_OnDevice)
        binding = ActivityMapEditorBinding.inflate(layoutInflater)
        super.onCreate(null)

        // 상태 바 제거
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContentView(binding.root)

        // Toggle Bar 초기 상태 설정
        updateToggleBarVisibility()

        // Description 초기 상태 설정
        updateModeDescription()

        binding.apply {
            cancelButton.setOnClickListener { v: View? ->
                Log.d(
                    TAG, "canclebutton.setOnClickListener(...)"
                )
                try {
                    val strFileName = "map_meta_sample.json"
                    val strPath = "/sdcard/Download"
                    val destFile = File(strPath, strFileName)

                    if (!destFile.isFile) {
                        if (roi_saveToEmptyFile(strPath, strFileName)) Log.d(
                            TAG, "Success create empty Json File"
                        )
                        else Log.d(
                            TAG, "Fail create empty File"
                        )
                    } else {
                        Log.d(TAG, "Already Exist Json FIle")
                    }

                    Log.d(TAG, "send broadcast... ")
                    val intent = Intent("sk.action.airbot.map.responseMapping")
                    intent.setPackage("com.sk.airbot.iotagent")
                    intent.putExtra("destMappingFilePath", "$strPath/$strFileName")
                    intent.putExtra("resultCode", "MRC_000")

                    sendBroadcast(intent)
                    Log.d(TAG, "sent broadcast. ")

                    Thread.sleep(1000)

                    Log.d(TAG, "finish activity ")
                    //System.exit(0);
                    finish()
                } catch (ie: InterruptedException) {
                    Log.e(
                        TAG, "Cancle Button InterruptedExtception: " + ie.message
                    )
                }
            }

            roiCompleteToggleBar.roiCompleteButton.setOnClickListener {
                // 완료 버튼이 클릭되면 roi 생성완료
                hideRoiCompleteToggleBar()
            }

            gobackButton.setOnClickListener { v: View? ->
                Log.d(
                    TAG, "canclebutton.setOnClickListener(...)"
                )
                try {
                    val strFileName = "map_meta_sample.json"
                    val strPath = "/sdcard/Download"
                    val destFile = File(strPath, strFileName)

                    if (!destFile.isFile) {
                        if (roi_saveToEmptyFile(strPath, strFileName)) Log.d(
                            TAG, "Success create empty Json File"
                        )
                        else Log.d(
                            TAG, "Fail create empty File"
                        )
                    } else {
                        Log.d(TAG, "Already Exist Json FIle")
                    }

                    Log.d(TAG, "send broadcast... ")
                    val intent = Intent("sk.action.airbot.map.responseMapping")
                    intent.setPackage("com.sk.airbot.iotagent")
                    intent.putExtra("destMappingFilePath", "$strPath/$strFileName")
                    intent.putExtra("resultCode", "MRC_000")

                    sendBroadcast(intent)
                    Log.d(TAG, "sent broadcast. ")

                    Thread.sleep(1000)

                    Log.d(TAG, "finish activity ")
                    //System.exit(0);
                    finish()
                } catch (ie: InterruptedException) {
                    Log.e(
                        TAG, "goBack Button InterruptedExtception: " + ie.message
                    )
                }
            }

            finishButton.setOnClickListener { v: View? ->
                Log.d(
                    TAG, "finshButton.setOnClickListener(...)"
                )
                try {
                    val strFileName = "map_meta_sample.json"
                    val strPath = "/sdcard/Download"

                    // TODO 수정필요 srcMappingfilePath 경로로 확인해서 name,path구분
                    Log.d(TAG, "try roi_saveToFile()")
                    if (roi_saveToFile(strPath, strFileName)) {
                        Log.d(TAG, "send broadcast... ")
                        val intent = Intent("sk.action.airbot.map.responseMapping")
                        intent.setPackage("com.sk.airbot.iotagent")
                        intent.putExtra("destMappingFilePath", "$strPath/$strFileName")
                        intent.putExtra("resultCode", "MRC_000")
                        sendBroadcast(intent)
                        Log.d(TAG, "sent broadcast. ")
                        Thread.sleep(1000)
                        Log.d(TAG, "finish activity ")
                        finish()
                    } else {
                        Toast.makeText(
                            applicationContext, "can not save JSON data !", Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (ie: InterruptedException) {
                    Log.e(TAG, "SendBroadcast Unexpected error: " + ie.message)
                }
            }


            buttonSpaceCreation.setOnClickListener { v: View? ->
                currentMode = MODE_SPACE_CREATION
                mapViewer.SetMode("공간 생성")
                Log.d(TAG, "공간 생성 모드 활성화")
                updateButtonBackground(buttonSpaceCreation)
                updateToggleBarVisibility()
                updateModeDescription()
                hideDeleteToggleBar()
                fabAddObjectClicked.visibility = View.GONE
                fabRotatePinClicked.visibility = View.GONE
                fabRenameObjectClicked.visibility = View.GONE
            }

            buttonBlockWall.setOnClickListener { v: View? ->
                currentMode = MODE_BLOCK_WALL
                mapViewer.SetMode("가상벽")
                Log.d(TAG, "가상벽 모드 활성화")
                updateButtonBackground(buttonBlockWall)
                updateToggleBarVisibility()
                updateModeDescription()
                hideDeleteToggleBar()
                fabAddObjectClicked.visibility = View.GONE
                fabRotatePinClicked.visibility = View.GONE
                fabRenameObjectClicked.visibility = View.GONE
            }

            buttonBlockArea.setOnClickListener { v: View? ->
                currentMode = MODE_BLOCK_AREA
                mapViewer.SetMode("금지공간")
                Log.d(TAG, "금지공간 모드 활성화")
                updateButtonBackground(buttonBlockArea)
                updateToggleBarVisibility()
                updateModeDescription()
                hideDeleteToggleBar()
                fabAddObjectClicked.visibility = View.GONE
                fabRotatePinClicked.visibility = View.GONE
                fabRenameObjectClicked.visibility = View.GONE
            }

            // 열기 버튼 클릭 시 toggle_bar 보이기
            fabMain.setOnClickListener { v: View? ->
                if (currentMode == MODE_SPACE_CREATION) {
                    toggleBarCreateSpace.root.visibility = View.VISIBLE // toggle_bar 보이기
                    fabMain.visibility = View.INVISIBLE // 열기 버튼 숨기기
                    toggleBarCreateSpace.fabMainBackCS.visibility = View.VISIBLE // 닫기 버튼 보이기
                } else if (currentMode == MODE_BLOCK_WALL || currentMode == MODE_BLOCK_AREA) {
                    toggleBar.root.visibility = View.VISIBLE // toggle_bar 보이기
                    fabMain.visibility = View.INVISIBLE // 열기 버튼 숨기기
                    toggleBar.fabMainBack.visibility = View.VISIBLE // 닫기 버튼 보이기
                }
            }

            // 닫기 버튼 클릭 시 toggle_bar 숨기기
            toggleBar.fabMainBack.setOnClickListener { v: View? ->
                toggleBar.root.visibility = View.GONE // toggle_bar 숨기기
                fabMain.visibility = View.VISIBLE // 열기 버튼 보이기
                toggleBar.fabMainBack.visibility = View.INVISIBLE // 닫기 버튼 숨기기
                fabAddObjectClicked.visibility = View.GONE
                fabRotatePinClicked.visibility = View.GONE
                fabRenameObjectClicked.visibility = View.GONE
            }

            toggleBarCreateSpace.fabMainBackCS.setOnClickListener { v: View? ->
                toggleBarCreateSpace.root.visibility = View.GONE // toggle_bar 숨기기
                fabMain.visibility = View.VISIBLE // 열기 버튼 보이기
                toggleBarCreateSpace.fabMainBackCS.visibility = View.INVISIBLE // 닫기 버튼 숨기기
                fabAddObjectClicked.visibility = View.GONE
                fabRotatePinClicked.visibility = View.GONE
                fabRenameObjectClicked.visibility = View.GONE
            }

            fabRotateMap.setOnClickListener { v: View? ->
                // 250104 skmg
                // Map 90도 회전 버튼
                mapViewer.setRotateMap90()
            }

            toggleBar.fabAddObject.setOnClickListener { v: View? ->
                mapViewer.SetMenu("추가")
                fabAddObjectClicked.visibility = View.VISIBLE
                fabRotatePinClicked.visibility = View.GONE
                fabRenameObjectClicked.visibility = View.GONE
                hideDeleteToggleBar()
            }

            toggleBarCreateSpace.fabRotatePinCS.setOnClickListener { v: View? ->
                mapViewer.SetMenu("핀 회전")
                fabAddObjectClicked.visibility = View.GONE
                fabRotatePinClicked.visibility = View.VISIBLE
                fabRenameObjectClicked.visibility = View.GONE
                hideDeleteToggleBar()
            }

            toggleBarCreateSpace.fabRenameObjectCS.setOnClickListener { v: View? ->
                if (mapViewer.currentSelectedIndex < 0) {
                    Toast.makeText(applicationContext, "선택된 공간이 없습니다.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                strMode = "None"

                showCustomDialog { _: String? ->
                    if ((mapViewer.m_RoiCurObject != null)) {
                        mapViewer.SetLabel(PinName)
                    }
                }

                fabAddObjectClicked.visibility = View.GONE
                fabRotatePinClicked.visibility = View.GONE
                fabRenameObjectClicked.visibility = View.GONE
                updateToggleBarVisibility()
                hideDeleteToggleBar()
            }

            // 휴지통 버튼 클릭 이벤트
            deleteToggleBar.deleteButton.setOnClickListener { v: View? ->
                mapViewer.roi_RemoveObject()
                mapViewer.CObject_CurRoiCancelFunc()
                mapViewer.clearSelection() // 선택 초기화 메서드
                hideDeleteToggleBar()
            }

            // X 버튼 클릭 이벤트
            cancelButton.setOnClickListener { v: View? ->
                mapViewer.clearSelection() // 선택 초기화 메서드
                hideDeleteToggleBar()
            }

            toggleBarCreateSpace.fabAddObjectCS.setOnClickListener { v: View? ->
                mapViewer.SetMenu("추가")
                fabAddObjectClicked.visibility = View.VISIBLE
                fabRotatePinClicked.visibility = View.GONE
                fabRenameObjectClicked.visibility = View.GONE
                hideDeleteToggleBar()
            }
        }

        // 241217 jihyeon
        // cancle 버튼과 goback 버튼 구분하여 구현 필요
        // cancle: 종료 확인 팝업 노출 후 확인 선택 시 홈화면 이동
        // goback: 맵그리기 <자동/수동> 선택화면으로 복귀

        hideRoiCompleteToggleBar() // 초기에는 안 보이는 것으로 설정

        // 20241217 jihyeon
        // 공간 생성, 가상벽, 금지공간 버튼 분리


        // 241217 jihyeon
        // 공간 생성 모드일 때 토글 버튼 설정

        val mapViewer = binding.mapViewer
        // 방법 1: ViewTreeObserver를 사용
        mapViewer.getViewTreeObserver().addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // 가로와 세로 크기 얻기
                val width = mapViewer.getWidth()
                val height = mapViewer.getHeight()

                Log.d("ViewSize", "Width: $width, Height: $height")

                mapViewer.ScreenWidth = mapViewer.getWidth()
                mapViewer.ScreenHeight = mapViewer.getHeight()

                // 리스너 제거 (메모리 누수 방지)
                mapViewer.getViewTreeObserver().removeOnGlobalLayoutListener(this)

                try {
                    // File 객체 생성

                    val file = File(srcMapPgmFilePath)


                    val path = file.parent + "/" // 디렉터리 경로
                    val filename = file.name // 파일 이름
                    val fileTitle =
                        filename.substring(0, filename.lastIndexOf('.')) // 확장자를 제외한 파일 제목

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

                    //Log.d(TAG, strPgmFile);
                    lib_flag = true
                    srcMapPgmFilePath = strPgmFile
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
                    val bitmap = loadPGM(srcMapPgmFilePath!!)
                    if (bitmap != null) {
                        mapViewer.setBitmap(bitmap)
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "IOException error occurred", e)
                }

                if (!loadYaml(srcMapYamlFilePath)) {
                    Log.d(
                        TAG, "con not read $srcMapYamlFilePath"
                    )
                }
                // 241218 성웅 맵 불러오기
                if (!loadJson(destMappingFilePath)) {
                    Log.d(
                        TAG, "con not read $destMappingFilePath"
                    )
                }
            }
        })

        // MapImageView에서 Rio가 선택되면 수신
        mapViewer.setRoiSelectedListener(object : OnRoiSelectedListener {
            override fun onRoiSelected(indexSelected: Int) {
                // ROI가 선택된 경우
                Log.d(TAG, "onRoiSelected($indexSelected)")

                // 필요한 버튼을 선택된 ROI 객체에 Dash 역역에 보여준다.
                if ((indexSelected > -1) && (indexSelected < mapViewer.m_RoiObjects.size)) {
                    //    [0]=====[1]
                    //    ||      ||
                    //    ||      ||
                    //    [3]=====[2]

                    val nLef = mapViewer.m_RoiObjects[indexSelected].m_DashPoints[0].x
                    val nTop = mapViewer.m_RoiObjects[indexSelected].m_DashPoints[0].y
                    val nRight = mapViewer.m_RoiObjects[indexSelected].m_DashPoints[2].x
                    val nBottom = mapViewer.m_RoiObjects[indexSelected].m_DashPoints[2].y

                    Log.d(TAG, "nLef : $nLef")
                    Log.d(TAG, "nTop : $nTop")
                    Log.d(TAG, "nRight : $nRight")
                    Log.d(TAG, "nBottom : $nBottom")

                    showRoiCompleteToggleBar(nLef, nTop)
                } else {
                    // 선택된 것이 없음.
                    hideRoiCompleteToggleBar()
                }
            }
        })

        // MapImageView에서 Rio(m_RoiCurObject)가 새로 생성되어 저장되기 전까지 수신
        mapViewer.setRoiCreateListener(object : OnRoiCreateListener {
            override fun onRoiCreate() {
                // ROI 새롭게 만든 경우
                Log.d(TAG, "setRoiCreateListener.onRoiCreate()")

                // 필요한 버튼을 선택된 ROI 객체에 Dash 역역에 보여준다.
                if (mapViewer.m_RoiCurObject != null) {
                    //    [0]=====[1]
                    //    ||      ||
                    //    ||      ||
                    //    [3]=====[2]

                    val nLef = mapViewer.m_RoiCurObject!!.m_DashPoints[0].x
                    val nTop = mapViewer.m_RoiCurObject!!.m_DashPoints[0].y
                    val nRight = mapViewer.m_RoiCurObject!!.m_DashPoints[2].x
                    val nBottom = mapViewer.m_RoiCurObject!!.m_DashPoints[2].y

                    Log.d(TAG, "nLef : $nLef")
                    Log.d(TAG, "nTop : $nTop")
                    Log.d(TAG, "nRight : $nRight")
                    Log.d(TAG, "nBottom : $nBottom")

                    showRoiCompleteToggleBar(nLef, nTop)
                } else {
                    hideRoiCompleteToggleBar()
                }
            }
        })

        // MapImageView에서 Rio가 선택된 것의 변형, 이동시 수신
        binding.mapViewer.setRoiChangedListener(object : OnRoiChangedListener {
            override fun onRoiChanged(indexSelected: Int) {
                // ROI 수정한 경우
                Log.d(TAG, "onRoiChanged($indexSelected)")

                // 필요한 버튼을 선택된 ROI 객체에 Dash 역역에 보여준다.
                if ((indexSelected > -1) && (indexSelected < mapViewer.m_RoiObjects.size)) {
                    //    [0]=====[1]
                    //    ||      ||
                    //    ||      ||
                    //    [3]=====[2]

                    val nLef = mapViewer.m_RoiObjects[indexSelected].m_DashPoints[0].x
                    val nTop = mapViewer.m_RoiObjects[indexSelected].m_DashPoints[0].y
                    val nRight = mapViewer.m_RoiObjects[indexSelected].m_DashPoints[2].x
                    val nBottom = mapViewer.m_RoiObjects[indexSelected].m_DashPoints[2].y

                    Log.d(TAG, "nLef : $nLef")
                    Log.d(TAG, "nTop : $nTop")
                    Log.d(TAG, "nRight : $nRight")
                    Log.d(TAG, "nBottom : $nBottom")
                }
            }
        })

        srcMapPgmFilePath =
            intent.getStringExtra("srcMapPgmFilePath") ?: "/sdcard/Download/office.pgm"
        srcMapYamlFilePath =
            intent.getStringExtra("srcMapYamlFilePath") ?: "/sdcard/Download/office.yaml"
        destMappingFilePath =
            intent.getStringExtra("destMappingFilePath") ?: "/sdcard/Download/map_meta_sample.json"
    }

    // 토글바 표시 함수
    fun showDeleteToggleBar(position: Pair<Int, Int>) {
        binding.deleteToggleBar.root.apply {
            Log.d(TAG, "Toggle Bar Location: " + position.first + ", " + position.second)
            visibility = View.VISIBLE
            // 선택된 객체 위치에 토글바 배치
            x = position.first.toFloat()
            y = position.second.toFloat()
        }
    }

    // 토글바 숨김 함수
    private fun hideDeleteToggleBar() {
        binding.deleteToggleBar.root.visibility = View.GONE
    }

    // 토글바 표시 함수
    fun showRoiCompleteToggleBar(x: Int, y: Int) {
        binding.apply {
            Log.d(TAG, "showRoiCompleteToggleBar($x, $y)")
            roiCompleteToggleBar.root.visibility = View.VISIBLE

            // MapViewer의 시작위치를 가져온다.
            val location = IntArray(2)
            mapViewer.getLocationOnScreen(location)

            // 선택된 객체 위치에 토글바 배치
            roiCompleteToggleBar.root.x = (x + location[0]).toFloat()
            roiCompleteToggleBar.root.y = (y + location[1]).toFloat()
        }
    }

    // 토글바 숨김 함수
    private fun hideRoiCompleteToggleBar() {
        binding.roiCompleteToggleBar.root.visibility = View.GONE
    }

    private fun updateToggleBarVisibility() {
        binding.apply {
            when (currentMode) {
                MODE_SPACE_CREATION -> {
                    toggleBarCreateSpace.root.visibility = View.VISIBLE
                    toggleBar.root.visibility = View.GONE
                }

                MODE_BLOCK_WALL, MODE_BLOCK_AREA -> {
                    toggleBar.root.visibility = View.VISIBLE
                    toggleBarCreateSpace.root.visibility = View.GONE
                }

                else -> {
                    toggleBar.root.visibility = View.GONE
                    toggleBarCreateSpace.root.visibility = View.GONE
                }
            }
        }
    }

    // currentMode에 따라 텍스트 업데이트
    private fun updateModeDescription() {
        binding.apply {
            when (currentMode) {
                MODE_SPACE_CREATION -> modeDescription.text = "한 공간으로 묶고 싶은 영역을 3점 이상 Tap하여 지정해주세요."
                MODE_BLOCK_WALL -> modeDescription.text = "Drag하여 진입 금지벽을 지정해주세요."
                MODE_BLOCK_AREA -> modeDescription.text = "Drag하여 진입 금지 영역을 지정해주세요."
                else -> modeDescription.text = "" // 다른 상태에서는 빈 문자열
            }
        }
    }

    // 버튼 색상 변경 메소드
    private fun updateButtonBackground(currentButton: Button) {
        val selectedBackground =
            AppCompatResources.getDrawable(this@MapEditorActivity, R.drawable.rounded_button_white)
        val unSelectedBackground =
            AppCompatResources.getDrawable(this@MapEditorActivity, R.drawable.rounded_button)
        binding.apply {
            val buttonList = listOf(buttonSpaceCreation, buttonBlockArea, buttonBlockWall)
            buttonList.forEach {
                if (it == currentButton) {
                    currentButton.background = selectedBackground // 원래 배경
                    currentButton.setTextColor(Color.BLACK)
                } else {
                    it.setTextColor(Color.WHITE)
                    it.background = unSelectedBackground
                }
            }
        }
    }

    fun interface DialogCallback {
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
        val selectedText = arrayOf<String?>(null)
        // Dynamically add rows
        for (row in layout) {
            // Create a horizontal LinearLayout for each row
            val rowLayout = LinearLayout(this)
            rowLayout.orientation = LinearLayout.HORIZONTAL
            rowLayout.layoutParams = RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.WRAP_CONTENT
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
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f // 균등 분배
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
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f // 빈 공간도 균등 분배
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
                PinName = selectedText[0] ?: ""
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
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
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
                            TAG, "origin_angle : $origin_angle"
                        )
                    } else {
                        Log.d(TAG, "origin_angle is not a valid number.")
                    }
                } else {
                    Log.d(TAG, "Origin data is incomplete or invalid.")
                }

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
                            "OpenCV", "Runtime exception during OpenCV initialization: $e"
                        )
                    }

                    val originalImageHeightValue = data["original_image_height"]
                    if (originalImageHeightValue is Number) {
                        original_image_height = originalImageHeightValue.toInt()
                        Log.d(
                            TAG, "original_image_height: $original_image_height"
                        )
                    } else {
                        Log.d(TAG, "original_image_height is not a valid number.")
                    }

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
                                        TAG, "Invalid value in transformation_matrix at ($i, $j)."
                                    )
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "Transformation matrix data is invalid.")
                    }

                    val rotatedAngleValue = data["rotated_angle"]
                    if (rotatedAngleValue is Number) {
                        rotated_angle = rotatedAngleValue.toFloat()
                        Log.d(
                            TAG, "Converted to float: $rotated_angle"
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
            TAG, "image width : $image_width , image_height : $image_height"
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

        Log.d(TAG, "MapViewer.m_RoiObjects.size() : " + mapViewer.m_RoiObjects.size)


        var i = 0
        while (i < mapViewer.m_RoiObjects.size) {
            if (mapViewer.m_RoiObjects[i].roi_type == "roi_polygon") {
                if (count_id > 0) {
                    strRoiJson += ", "
                }

                count_id++ // 고유 id


                strRoiJson += "{"
                strRoiJson += "\"id\": \"$count_id\""
                strRoiJson += ", \"name\": \"" + mapViewer.m_RoiObjects[i].m_label + "\""
                strRoiJson += ", \"color\":\"#47910f\", \"desc\":\"\""
                strRoiJson += ", \"robot_path\":["

                //strRoiJson += "{\"x\":-2.97,\"y\":7.15},{\"x\":-3.02,\"y\":7.1000004},{\"x\":-3.17,\"y\":7.1000004},{\"x\":-3.22,\"y\":7.05},{\"x\":-3.22,\"y\":6.9},{\"x\":-3.27,\"y\":6.8500004},{\"x\":-3.27,\"y\":4},{\"x\":-3.22,\"y\":3.95},{\"x\":-2.47,\"y\":3.95},{\"x\":-2.42,\"y\":4},{\"x\":-2.22,\"y\":4},{\"x\":-2.17,\"y\":4.05},{\"x\":-1.7199999,\"y\":4.05},{\"x\":-1.7199999,\"y\":4.1},{\"x\":-1.67,\"y\":4.15},{\"x\":-1.67,\"y\":4.25},{\"x\":-1.62,\"y\":4.3},{\"x\":-1.62,\"y\":4.35},{\"x\":-1.5699999,\"y\":4.4},{\"x\":-1.5699999,\"y\":4.4500003},{\"x\":-1.52,\"y\":4.5},{\"x\":-1.52,\"y\":4.6},{\"x\":-1.4699999,\"y\":4.65},{\"x\":-1.4699999,\"y\":4.7000003},{\"x\":-1.42,\"y\":4.75},{\"x\":-1.42,\"y\":4.8500004},{\"x\":-1.37,\"y\":4.9},{\"x\":-1.37,\"y\":4.9500003},{\"x\":-1.3199999,\"y\":5},{\"x\":-1.3199999,\"y\":5.05},{\"x\":-1.27,\"y\":5.1000004},{\"x\":-1.27,\"y\":5.2000003},{\"x\":-1.2199999,\"y\":5.25},{\"x\":-1.2199999,\"y\":5.3},{\"x\":-1.17,\"y\":5.3500004},{\"x\":-1.17,\"y\":5.4500003},{\"x\":-1.12,\"y\":5.5},{\"x\":-1.12,\"y\":5.55},{\"x\":-1.0699999,\"y\":5.6000004},{\"x\":-1.0699999,\"y\":5.65},{\"x\":-1.02,\"y\":5.7000003},{\"x\":-1.02,\"y\":5.8},{\"x\":-0.96999997,\"y\":5.8500004},{\"x\":-0.96999997,\"y\":5.9},{\"x\":-0.91999996,\"y\":5.9500003},{\"x\":-0.91999996,\"y\":6.05},{\"x\":-0.86999995,\"y\":6.1000004},{\"x\":-0.86999995,\"y\":6.15},{\"x\":-0.81999993,\"y\":6.2000003},{\"x\":-0.81999993,\"y\":6.3},{\"x\":-0.77,\"y\":6.3500004},{\"x\":-0.77,\"y\":6.4},{\"x\":-1.27,\"y\":6.4},{\"x\":-1.37,\"y\":6.5},{\"x\":-1.37,\"y\":6.8},{\"x\":-1.42,\"y\":6.8500004},{\"x\":-1.42,\"y\":7},{\"x\":-1.5699999,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15},{\"x\":-2.97,\"y\":7.15}";
                j = 0
                while (j < mapViewer.m_RoiObjects[i].m_Points.size) {
                    if (j > 0) {
                        strRoiJson += ", "
                    }

                    strRoiJson += "{"

                    val coordinates = calculateCoordinate(
                        mapViewer.m_RoiObjects[i].m_Points[j].x,
                        mapViewer.m_RoiObjects[i].m_Points[j].y,
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
                while (j < mapViewer.m_RoiObjects[i].m_Points.size) {
                    if (j > 0) {
                        strRoiJson += ", "
                    }

                    strRoiJson += "{"

                    strRoiJson += "\"x\":" + (mapViewer.m_RoiObjects[i].m_Points[j].x)
                    strRoiJson += ", \"y\":" + (mapViewer.m_RoiObjects[i].m_Points[j].y)

                    strRoiJson += "}"
                    j++
                }
                strRoiJson += "]"
                strRoiJson += ", \"robot_position\":{"

                val coordinates = calculateCoordinate(
                    mapViewer.m_RoiObjects[i].m_MBR_center.x,
                    mapViewer.m_RoiObjects[i].m_MBR_center.y,
                    image_height
                )

                val xvw = coordinates[0]
                val yvh = coordinates[1]
                strRoiJson += "\"x\":$xvw"
                strRoiJson += ", \"y\":$yvh"
                var angle =
                    mapViewer.m_RoiObjects[i].getAngle() - Math.toRadians(rotated_angle.toDouble())
                angle = ((angle + Math.PI) % (2 * Math.PI)) - Math.PI
                strRoiJson += ", \"theta\":$angle"

                strRoiJson += "}"

                strRoiJson += ", \"image_position\":{"
                var xvw_image = 0 // polygon의 무게중심 x

                var yvh_image = 0 // polygon의 무게중심 x

                // Log.d(TAG, "xvw before : "+ xvw);
                xvw_image = mapViewer.m_RoiObjects[i].m_MBR_center.x
                yvh_image = mapViewer.m_RoiObjects[i].m_MBR_center.y

                // MapViewer.m_RoiObjects.get(i).m_MBR;
                //Log.d(TAG,"height: " + image_height +", origin_y: "+ origin_y + ", imagey: " + MapViewer.m_RoiObjects.get(i).m_Points.get(j).y + ", real_y: "+ (float)((image_height-MapViewer.m_RoiObjects.get(i).m_Points.get(j).y)*nResolution + origin_y));
                //Toast.makeText(getApplicationContext(), "X: " + (float)(xvw * nResolution + origin_x) +", Y: " + ((image_height - yvh) * nResolution + origin_y), Toast.LENGTH_SHORT).show();
                strRoiJson += "\"x\":" + xvw_image
                strRoiJson += ", \"y\":" + yvh_image
                strRoiJson += ", \"theta\":" + mapViewer.m_RoiObjects[i].getAngle()
                strRoiJson += "}"
                strRoiJson += "}"
            }

            i++
        }
        strRoiJson += "]"
        strRoiJson += ", \"block_area\":["
        i = 0
        while (i < mapViewer.m_RoiObjects.size) {
            if (mapViewer.m_RoiObjects[i].roi_type == "roi_rect") {
                if (count_id_rect > 0) {
                    strRoiJson += ", "
                }

                count_id_rect++ // 고유 id


                strRoiJson += "{"
                strRoiJson += "\"image_path\":["
                strRoiJson += "{"

                //left top
                strRoiJson += "\"x\":" + (mapViewer.m_RoiObjects[i].m_MBR.left)
                strRoiJson += ", \"y\":" + (mapViewer.m_RoiObjects[i].m_MBR.bottom)
                strRoiJson += "}, {"

                // right top
                strRoiJson += "\"x\":" + (mapViewer.m_RoiObjects[i].m_MBR.right)
                strRoiJson += ", \"y\":" + (mapViewer.m_RoiObjects[i].m_MBR.bottom)
                strRoiJson += "}, {"

                // right bottom
                strRoiJson += "\"x\":" + (mapViewer.m_RoiObjects[i].m_MBR.right)
                strRoiJson += ", \"y\":" + (mapViewer.m_RoiObjects[i].m_MBR.top)
                strRoiJson += "}, {"

                // left bottom
                strRoiJson += "\"x\":" + (mapViewer.m_RoiObjects[i].m_MBR.left)
                strRoiJson += ", \"y\":" + (mapViewer.m_RoiObjects[i].m_MBR.top)

                strRoiJson += "}"

                strRoiJson += "]"
                strRoiJson += ", \"robot_path\":["


                strRoiJson += "{"

                // left top
                var coordinates = calculateCoordinate(
                    mapViewer.m_RoiObjects[i].m_MBR.left,
                    mapViewer.m_RoiObjects[i].m_MBR.bottom,
                    image_height
                )
                var area_x = coordinates[0]
                var area_y = coordinates[1]

                strRoiJson += "\"x\":$area_x"
                strRoiJson += ", \"y\":$area_y"
                strRoiJson += "}, {"

                // right top
                coordinates = calculateCoordinate(
                    mapViewer.m_RoiObjects[i].m_MBR.right,
                    mapViewer.m_RoiObjects[i].m_MBR.bottom,
                    image_height
                )
                area_x = coordinates[0]
                area_y = coordinates[1]

                strRoiJson += "\"x\":$area_x"
                strRoiJson += ", \"y\":$area_y"
                strRoiJson += "}, {"

                // right bottom
                coordinates = calculateCoordinate(
                    mapViewer.m_RoiObjects[i].m_MBR.right,
                    mapViewer.m_RoiObjects[i].m_MBR.top,
                    image_height
                )
                area_x = coordinates[0]
                area_y = coordinates[1]

                strRoiJson += "\"x\":$area_x"
                strRoiJson += ", \"y\":$area_y"
                strRoiJson += "}, {"

                // left bottom
                coordinates = calculateCoordinate(
                    mapViewer.m_RoiObjects[i].m_MBR.left,
                    mapViewer.m_RoiObjects[i].m_MBR.top,
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
        while (i < mapViewer.m_RoiObjects.size) {
            if (mapViewer.m_RoiObjects[i].roi_type == "roi_line") {
                if (count_id_line > 0) {
                    strRoiJson += ", "
                }

                Log.d(
                    TAG, "image_height: $image_height, image width: $image_width"
                )

                count_id_line++
                val roi = mapViewer.m_RoiObjects[i]

                strRoiJson += "{\"image_path\":["

                // Add start point
                strRoiJson += "{\"x\":" + roi.m_MBR.left + ", \"y\":" + (roi.m_MBR.top) + "}, "

                // Add end point
                strRoiJson += "{\"x\":" + roi.m_MBR.right + ", \"y\":" + (roi.m_MBR.bottom) + "}], "

                strRoiJson += "\"robot_path\":["

                // Convert image coordinates to robot coordinates
                val startCoordinates =
                    calculateCoordinate(roi.m_MBR.left, roi.m_MBR.top, image_height)
                val endCoordinates =
                    calculateCoordinate(roi.m_MBR.right, roi.m_MBR.bottom, image_height)

                // Add start robot path
                strRoiJson += "{\"x\":" + startCoordinates[0] + ", \"y\":" + startCoordinates[1] + "}, "

                // Add end robot path
                strRoiJson += "{\"x\":" + endCoordinates[0] + ", \"y\":" + endCoordinates[1] + "}], "

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

        val file = File(strPath, strFileName)
        try {
            FileOutputStream(file).use { fos ->
                Log.d(TAG, "$strPath/$strFileName")
                fos.write(strRoiJson.toByteArray())
                fos.flush()
                Thread.sleep(1000) // 파일쓰기가 완료될 까지 기다린다.
                fos.close()

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

    @Synchronized
    private fun loadJson(filePath: String?): Boolean {
        Log.d(TAG, "Exist Json File")
        val jsonStringBuilder = StringBuilder()

        // 내부 저장소에서 파일 스트림 열기
        // InputStream 데이터를 문자열로 변환
        try {
            val mapViewer = binding.mapViewer
            FileInputStream(filePath).use { inputStream ->
                InputStreamReader(inputStream).use { inputStreamReader ->
                    BufferedReader(inputStreamReader).use { bufferedReader ->
                        var line: String?
                        while ((bufferedReader.readLine().also { line = it }) != null) {
                            jsonStringBuilder.append(line)
                        }

                        // JSON 문자열로 변환
                        val jsonString = jsonStringBuilder.toString()

                        // JSON 파싱
                        val jsonObject = JSONObject(jsonString)

                        var x = 0
                        var y = 0
                        var left = 0
                        var right = 0
                        var bottom = 0
                        var top = 0
                        // 1. room_list 데이터 읽기
                        println("Room List:")
                        val roomList = jsonObject.getJSONArray("room_list")
                        for (i in 0 until roomList.length()) {
                            val room = roomList.getJSONObject(i)
                            val id = room.getString("id")
                            val name = room.getString("name")
                            val imagePathArray = room.getJSONArray("image_path")
                            val imagePosition = room.getJSONObject("image_position")
                            val mbr_x = imagePosition.getInt("x")
                            val mbr_y = imagePosition.getInt("y")
                            Log.d(TAG, "  Room ID: $id")
                            Log.d(TAG, "  Name: $name")
                            Log.d(TAG, "  Image Path:")
                            Log.d(
                                TAG, "  ImagePosition: $mbr_x $mbr_y"
                            )
                            for (j in 0 until imagePathArray.length()) {
                                val point = imagePathArray.getJSONObject(j)
                                x = point.getInt("x")
                                y = point.getInt("y")
                                Log.d(TAG, "    Point: ($x, $y)")
                                if (j == 0) {
                                    mapViewer.CObject_LoadObject("roi_polygon", Point(x, y))
                                } else {
                                    mapViewer.AddPoint_Polygon(Point(x, y))
                                }
                                if (left > x) left = x
                                if (right < x) right = x
                                if (top > y) top = y
                                if (bottom < y) bottom = y
                            }
                            mapViewer.m_drawing = true
                            mapViewer.roi_AddObject()
                            // 241222 seongwoong 현재 버그 있음 확인 필요
                            //MapViewer.m_RoiCurObject.m_MBR = new Rect(left, top, right, bottom);
                            mapViewer.m_RoiCurObject!!.m_MBR_center.x = mbr_x
                            mapViewer.m_RoiCurObject!!.m_MBR_center.y = mbr_y
                            mapViewer.SetLabel(name)
                            if (lib_flag) {
                                val theta = imagePosition.getDouble("theta").toFloat()
                                mapViewer.m_RoiCurObject!!.setAngle(theta)
                            }
                        }


                        // 2. block_area 데이터 읽기
                        Log.d(TAG, "\nBlock Area:")
                        val blockAreaArray = jsonObject.getJSONArray("block_area")
                        for (i in 0 until blockAreaArray.length()) {
                            val block = blockAreaArray.getJSONObject(i)
                            val imagePathArray = block.getJSONArray("image_path")

                            Log.d(TAG, "  Block Area Image Path:")
                            val point = imagePathArray.getJSONObject(0)
                            x = point.getInt("x")
                            y = point.getInt("y")
                            val pt1 = Point(x, y)
                            Log.d(TAG, "    Point: ($x, $y)")
                            val point2 = imagePathArray.getJSONObject(2)
                            x = point2.getInt("x")
                            y = point2.getInt("y")
                            val pt2 = Point(x, y)

                            Log.d(TAG, "    Point: ($x, $y)")

                            mapViewer.CObject_LoadObject("roi_rect", pt1)
                            mapViewer.CObject_LoadRect(pt1, pt2)
                            mapViewer.roi_AddObject()
                        }

                        // 3. block_wall 데이터 읽기
                        Log.d(TAG, "\nBlock Wall:")
                        val blockWallArray = jsonObject.getJSONArray("block_wall")
                        for (i in 0 until blockWallArray.length()) {
                            val block = blockWallArray.getJSONObject(i)
                            val imagePathArray = block.getJSONArray("image_path")

                            Log.d(TAG, "  Block Wall Image Path:")
                            val point = imagePathArray.getJSONObject(0)
                            x = point.getInt("x")
                            y = point.getInt("y")
                            val pt1 = Point(x, y)

                            Log.d(TAG, "    Point: ($x, $y)")

                            val point2 = imagePathArray.getJSONObject(1)
                            x = point2.getInt("x")
                            y = point2.getInt("y")
                            val pt2 = Point(x, y)

                            Log.d(TAG, "    Point: ($x, $y)")

                            mapViewer.CObject_LoadObject("roi_line", pt1)
                            mapViewer.CObject_LoadRect(pt1, pt2)
                            mapViewer.roi_AddObject()
                        }

                        mapViewer.currentSelectedIndex = -1
                        mapViewer.m_RoiCurObject = null
                        Log.d(TAG, "Read Json Success")
                        return true
                    }
                }
            }
        } catch (fe: FileNotFoundException) {
            Log.e(TAG, "Read Json FileNotFoundException: " + filePath + " " + fe.message)
            return false
        } catch (e: JSONException) {
            Log.e(TAG, "Read Json JSON, IO Exception: " + e.message)
            return false
        } catch (e: IOException) {
            Log.e(TAG, "Read Json JSON, IO Exception: " + e.message)
            return false
        } finally {
            // 자원이 자동으로 닫히는지 확인할 필요가 없지만 명시적으로 로깅 가능
            Log.d(TAG, "loadjson end")
        }
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
            inverseTransformationMatrix, pointMat, 1.0, Mat(), 0.0, inverseTransformedPointMat
        )

        // 4. 원래 이미지 좌표 추출
        // 241226 성웅 int의 오차가 얼마인지 확인 필요
        val original_pixel_x = Math.round(inverseTransformedPointMat[0, 0][0]).toInt()
        val original_pixel_y = Math.round(inverseTransformedPointMat[1, 0][0]).toInt()
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


    companion object {
        private const val TAG = "MapEditorActivity"

        private const val NAME_LIBRARY_CASELAB_OPT =
            //                                                              "mapoptimizationV2";
            "mapoptimization241217v11"

        init {
            try {
                System.loadLibrary(NAME_LIBRARY_CASELAB_OPT)
                Log.d("SKOnDeviceService", "SO library load success!")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(
                    "SKOnDeviceService", "SO library load error (UnsatisfiedLinkError): $e"
                )
            } catch (e: SecurityException) {
                Log.e(
                    "SKOnDeviceService", "SO library load error (SecurityException): $e"
                )
            }
        }

        private const val REQUEST_EXTERNAL_STORAGE = 1
        private val PERMISSIONS_STORAGE = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        fun verifyStoragePermissions(activity: Activity) {
            val permission = ActivityCompat.checkSelfPermission(
                activity, Manifest.permission.WRITE_EXTERNAL_STORAGE
            )

            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.setData(Uri.parse("package:" + activity.packageName))

            activity.startActivity(intent)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    activity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE
                )
            }
        }


        // 모드 상수 정의
        private const val MODE_MAP_EXPLORATION = 0
        private const val MODE_SPACE_CREATION = 1
        private const val MODE_BLOCK_WALL = 2
        private const val MODE_BLOCK_AREA = 3
        private const val MODE_EDIT_PIN = 4

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
    }
}

