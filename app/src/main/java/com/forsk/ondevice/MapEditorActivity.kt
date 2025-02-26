package com.forsk.ondevice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.Pair
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.caselab.forsk.MapOptimization
import com.forsk.ondevice.CDrawObj.Companion.ROI_TYPE_LINE
import com.forsk.ondevice.CDrawObj.Companion.ROI_TYPE_POLYGON
import com.forsk.ondevice.CDrawObj.Companion.ROI_TYPE_RECT
import com.forsk.ondevice.CommonUtil.debugLog
import com.forsk.ondevice.CommonUtil.warnLog
import com.forsk.ondevice.databinding.ActivityMapEditorBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// opencv 추가
class MapEditorActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MapEditorActivity"
        private const val NAME_LIBRARY_CASELAB_OPT = "mapoptimization_arm_v2_240117"
        private const val PACKAGE_NAME = "com.sk.airbot.iotagent"
        private const val ACTION_NAME = "sk.action.airbot.map.responseMapping"
        private const val ACTION_FILE_PATH = "destMappingFilePath"
        private const val ACTION_STATUS_NAME = "appStatus"
        private const val ACTION_RESULT_CODE = "resultCode"
        private const val ACTION_VALUE = "MRC_000"
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
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

    init {
        loadMapLibrary()
    }

    //TODO : 하드코딩 수정할 것
    private val strFileName = "map_meta_sample.json"
    private val strPath = "/sdcard/Download"

    private lateinit var binding: ActivityMapEditorBinding
    private lateinit var viewModel: MapEditorViewModel

    private var strMode: String = "Zoom"
    private var srcMapPgmFilePath: String = ""
    private var srcMapPngFilePath: String = ""
    private var srcMapYamlFilePath: String = ""
    private var destMappingFilePath: String = ""


    // 현재 모드 변수
    private var currentMode = MODE_MAP_EXPLORATION

    private var pinName = ""

    // 선택된 버튼 저장 변수
    var selectedButton: TextView? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_OnDevice)

        binding = ActivityMapEditorBinding.inflate(layoutInflater)
        viewModel = ViewModelProvider(this)[MapEditorViewModel::class.java]
        verifyStoragePermissions(this@MapEditorActivity)

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
            //fabMain.setOnClickListener { fabMainClickEvent() }

            toggleBar.apply {
                fabMain.setOnClickListener { fabMainBackClickEvent() }
                fabAddObject.setOnClickListener { fabAddObjectClickEvent() }
                fabMoveObject.setOnClickListener { fabMoveObjectClickEvent() }//  move Object
                fabSelectObject.setOnClickListener { fabSelectObjectClickEvent() }//  Select Object
                fabDeleteObject.setOnClickListener { toggleBarCreateSpaceClickEvent() }
                fabMovePin.setOnClickListener { fabMovePinCSClickEvent() }
                fabRotatePin.setOnClickListener { fabRotatePinCSClickEvent() }
                fabRenameObject.setOnClickListener { fabRenameObjectCSClickEvent() }
            }

            // 휴지통 버튼 클릭 이벤트
            deleteToggleBar.apply {
                deleteButton.setOnClickListener { deleteButtonClickEvent() }
                // X 버튼 클릭 이벤트
                cancelButton.setOnClickListener { v: View? ->
                    mapViewer.clearSelection() // 선택 초기화 메서드
                    hideDeleteToggleBar()
                }
            }

            mapViewer.apply {
                viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        processMapFiles()
                        loadMapBitmap()
                        loadYamlFile()
                        loadJsonMap()
                        viewTreeObserver.removeOnGlobalLayoutListener(this) // 리스너 제거 (메모리 누수 방지)
                    }
                })
            }
        }
    }

    private fun processMapFiles() {
        val file = File(srcMapPgmFilePath)
        val path = file.parent + "/"
        val fileTitle = file.name.substringBeforeLast('.') // 확장자 제외 파일명

        val pathRot = "${path}rot/"
        val pathOpt = "${path}opt/"

        createDirectoryIfNeeded(pathRot)
        createDirectoryIfNeeded(pathOpt)

        try {
            Log.d("SKOnDeviceService", "Run library-rotate!")
            MapOptimization.mapRotation(path, pathRot, fileTitle)
            Log.d("SKOnDeviceService", "Library-rotate Finish!")

            Log.d("SKOnDeviceService", "Run library-line opt!")
            MapOptimization.lineOptimization(pathRot, pathOpt, fileTitle)
            Log.d("SKOnDeviceService", "Library-line Finish!")

            srcMapPgmFilePath = "$pathOpt$fileTitle.pgm"
            srcMapPngFilePath = "$pathOpt$fileTitle.png"
            srcMapYamlFilePath = "$pathOpt$fileTitle.yaml"

            viewModel.lib_flag = true
        } catch (e: Throwable) {
            Log.e(TAG, "Error processing map files", e)
        }
    }

    private fun createDirectoryIfNeeded(path: String) {
        val directory = File(path)
        if (!directory.exists() && !directory.mkdirs()) {
            Log.e("FILE", "Directory not created: $path")
        }
    }

    private fun loadMapBitmap() {
        try {
            val bitmap = viewModel.loadPNG(srcMapPngFilePath)
            bitmap.let { binding.mapViewer.setBitmap(it) }
        } catch (e: IOException) {
            Log.e(TAG, "IOException while loading bitmap", e)
        }
    }

    private fun loadYamlFile() {
        if (!viewModel.loadYaml(srcMapYamlFilePath)) {
            Log.d(TAG, "Cannot read $srcMapYamlFilePath")
        }
    }

    private fun loadJsonMap() {
        lifecycleScope.launch(IO) {
            if (!viewModel.loadJson(binding.mapViewer, destMappingFilePath, viewModel.lib_flag)) {
                Log.d(TAG, "Cannot read $destMappingFilePath")
            }
        }
    }

    private fun deleteButtonClickEvent() {
        binding.apply {
            mapViewer.removeObject()
            mapViewer.cancelCurObject()
            mapViewer.clearSelection() // 선택 초기화 메서드
        }
        hideDeleteToggleBar()
    }

    private fun setDefaultButtonBackground() {
        binding.toggleBar.apply {
            val defaultBackground =
                AppCompatResources.getDrawable(this@MapEditorActivity, R.color.button_default)
            fabAddObject.background = defaultBackground
            fabSelectObject.background = defaultBackground
            fabMoveObject.background = defaultBackground
            fabMovePin.background = defaultBackground
            fabRotatePin.background = defaultBackground
            fabDeleteObject.background = defaultBackground
            fabRenameObject.background = defaultBackground
        }
    }

    private fun setSelectedButtonBackground(id: Int) {
        setDefaultButtonBackground()
        binding.toggleBar.apply {
            val selectedButtonBackground =
                AppCompatResources.getDrawable(this@MapEditorActivity, R.drawable.rounded_button_white)
            when (id) {
                fabAddObject.id -> fabAddObject.background = selectedButtonBackground
                fabSelectObject.id -> fabSelectObject.background = selectedButtonBackground
                fabMoveObject.id -> fabMoveObject.background = selectedButtonBackground
                fabMovePin.id -> fabMovePin.background = selectedButtonBackground
                fabRotatePin.id -> fabRotatePin.background = selectedButtonBackground
                fabDeleteObject.id -> fabDeleteObject.background = selectedButtonBackground
                fabRenameObject.id -> fabRenameObject.background = selectedButtonBackground
            }
        }
    }

    private fun fabRenameObjectCSClickEvent() {
        strMode = "None"
        val oldPinName = pinName
        binding.apply {
            showCustomDialog(object : DialogCallback {
                override fun onConfirm(selectedText: String?) {
                    if ((mapViewer.roiCurObject != null)) {
                        mapViewer.setLabel(pinName)
                    }
                }
            })

            setDefaultButtonBackground()
        }

        updateToggleBarVisibility()
        hideDeleteToggleBar()
    }

    private fun fabRotatePinCSClickEvent() {
        binding.apply {
            mapViewer.setMenu("핀 회전")
            setSelectedButtonBackground(toggleBar.fabRotatePin.id)
            hideDeleteToggleBar()
        }
    }

    private fun fabMovePinCSClickEvent() {
        binding.apply {
            mapViewer.setMenu("핀 이동")
            setSelectedButtonBackground(toggleBar.fabMovePin.id)
            hideDeleteToggleBar()
        }
    }

    private fun toggleBarCreateSpaceClickEvent() {
        binding.apply {
            mapViewer.setMenu("삭제")
            setSelectedButtonBackground(toggleBar.fabDeleteObject.id)
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
            mapViewer.setMenu("선택")
            setSelectedButtonBackground(toggleBar.fabSelectObject.id)
            hideDeleteToggleBar()
        }
    }

    private fun fabMoveObjectClickEvent() {
        binding.apply {
            mapViewer.setMenu("수정")
            setSelectedButtonBackground(toggleBar.fabMoveObject.id)
            hideDeleteToggleBar()
        }
    }

    private fun fabAddObjectClickEvent() {
        binding.apply {
            mapViewer.setMenu("추가")
            setSelectedButtonBackground(toggleBar.fabAddObject.id)
            hideDeleteToggleBar()
        }
    }

    private fun fabMoveMapClickEvent() {
        binding.apply {
            if (mapViewer.strMenu === "이동") {
                mapViewer.setMenu("이동")
                //strMode = "Move";
            } else {
                mapViewer.setMenu("이동")
                //strMode = "Move";
            }
        }
    }

    private fun fabMainBackClickEvent() {
        binding.apply {
            toggleBar.apply {
                if (layoutToggleButton.visibility == View.VISIBLE) {
                    fabMain.setImageDrawable(
                        AppCompatResources.getDrawable(
                            this@MapEditorActivity,
                            R.drawable.baseline_arrow_forward_ios_24
                        )
                    )
                    layoutToggleButton.visibility = View.GONE
                } else {
                    fabMain.setImageDrawable(
                        AppCompatResources.getDrawable(
                            this@MapEditorActivity,
                            R.drawable.baseline_arrow_back_ios_new_24
                        )
                    )
                    layoutToggleButton.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun blockAreaButtonClickEvent() {
        binding.apply {
            currentMode = MODE_BLOCK_AREA
            mapViewer.setMode("금지공간")
            Log.d(TAG, "금지공간 모드 활성화")
            updateButtonBackground(buttonBlockArea)
            updateToggleBarVisibility()
            updateModeDescription()
            hideDeleteToggleBar()
            setDefaultButtonBackground()
        }
    }

    private fun blockWallClickEvent() {
        binding.apply {
            currentMode = MODE_BLOCK_WALL
            mapViewer.setMode("가상벽")
            Log.d(TAG, "가상벽 모드 활성화")
            updateButtonBackground(buttonBlockWall)
            updateToggleBarVisibility()
            updateModeDescription()
            hideDeleteToggleBar()
            setDefaultButtonBackground()
        }
    }

    private fun spaceCreationButtonClickFunc() {
        binding.apply {
            currentMode = MODE_SPACE_CREATION
            mapViewer.setMode("공간 생성")
            Log.d(TAG, "공간 생성 모드 활성화")
            updateButtonBackground(spaceCreationButton)
            updateToggleBarVisibility()
            updateModeDescription()
            hideDeleteToggleBar()
            setDefaultButtonBackground()
        }
    }

    private fun finishButtonClickFunc() {
        debugLog(TAG, "finishButton.setOnClickListener(...)")
        Log.d(TAG, "try roi_saveToFile()")
        roiSaveToFile(
            strPath = strPath,
            strFileName = strFileName,
            isSetTheta = true
        )
    }

    private fun sendFinishBroadcast(strPath: String, strFileName: String) {
        lifecycleScope.launch {
            withContext(Main) {
                Log.d(TAG, "send broadcast... ")
                Intent(ACTION_NAME).apply {
                    setPackage(PACKAGE_NAME)
                    putExtra(ACTION_FILE_PATH, "$strPath/$strFileName")
                    putExtra(ACTION_RESULT_CODE, ACTION_VALUE)
                    putExtra(ACTION_STATUS_NAME, ACTION_STOP)
                    sendBroadcast(this)
                    Log.d(TAG, "sent broadcast. ")
                }
                finish()
                Log.d(TAG, "finish activity ")
            }
        }
    }

    private fun goBackButtonClickFunc() {
        Log.d(TAG, "cancelButton.setOnClickListener(...)")
        val destFile = File(strPath, strFileName)
        if (!destFile.isFile) {
            viewModel.roiSaveToEmptyFile(strPath, strFileName)
        } else {
            Log.d(TAG, "Already Exist Json FIle")
        }
        sendCancelBroadcast(strPath, strFileName)
    }

    private fun sendCancelBroadcast(strPath: String, strFileName: String) {
        Log.d(TAG, "send broadcast... ")
        lifecycleScope.launch {
            withContext(Main) {
                Intent(ACTION_NAME).apply {
                    setPackage(PACKAGE_NAME)
                    putExtra(ACTION_FILE_PATH, "$strPath/$strFileName")
                    putExtra(ACTION_RESULT_CODE, ACTION_VALUE)
                    putExtra(ACTION_STATUS_NAME, "stop")
                    sendBroadcast(this)
                    Log.d(TAG, "sent broadcast. ")
                }

                finish()
                Log.d(TAG, "finish activity ")
            }
        }
    }

    private fun cancelButtonClickFunc() {
        Log.d(
            TAG,
            "cancelbutton.setOnClickListener(...)"
        )

        val destFile = File(strPath, strFileName)
        if (!destFile.isFile) {
            viewModel.roiSaveToEmptyFile(strPath, strFileName)
        } else {
            Log.d(TAG, "Already Exist Json FIle")
        }
        sendCancelBroadcast(strPath, strFileName)
    }

    private fun sendBroadcast() {
        Log.d(TAG, "send broadcast appStatus start... ")

        Intent(ACTION_NAME).apply {
            setPackage(PACKAGE_NAME)
            putExtra(ACTION_STATUS_NAME, ACTION_START)
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
            when (currentMode) {
                MODE_SPACE_CREATION, MODE_BLOCK_WALL, MODE_BLOCK_AREA -> {
                    toggleBar.root.visibility = View.VISIBLE
                }

                else -> toggleBar.root.visibility = View.GONE
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
//        val dialogBinding = DialogScrollableWithButtonsBinding.inflate(layoutInflater)
//        val dialogView = dialogBinding.root
//        val builder = AlertDialog.Builder(this, R.style.CustomDialogTheme)
//        builder.setView(dialogView)
//
//        val dialog = builder.create()
//
//        dialogBinding.radioGroup
//        // Find views
//
//        // Layout for items
//        val layout = arrayOf(
//            arrayOf("거실", "게스트룸", "드레스룸", "발코니"),  // 첫 번째 행
//            arrayOf("복도", "서재", "아이방1", "아이방2"),  // 두 번째 행
//            arrayOf("안방", "욕실1", "욕실2", "주방"),  // 세 번째 행
//            arrayOf("침실", "현관", "", "") // 네 번째 행
//        )
//
//        // List to manage all RadioButtons
//        val allRadioButtons = ArrayList<RadioButton>()
//        // 선택된 라디오 버튼의 텍스트를 추적하기 위한 변수
//        val selectedText = arrayOf<String>("")
//        // Dynamically add rows
//        for (row in layout) {
//            // Create a horizontal LinearLayout for each row
//            val rowLayout = LinearLayout(this)
//            rowLayout.orientation = LinearLayout.HORIZONTAL
//            rowLayout.layoutParams = RadioGroup.LayoutParams(
//                RadioGroup.LayoutParams.MATCH_PARENT,
//                RadioGroup.LayoutParams.WRAP_CONTENT
//            )
//            var idCounter = 0 // 고유 ID 생성기
//            // Add items to the row
//            for (item in row) {
//                if (!item.isEmpty()) {
//                    // Create and style the RadioButton
//                    val radioButton = RadioButton(this)
//                    radioButton.text = item
//                    radioButton.id = idCounter++
//                    radioButton.setTextColor(resources.getColor(android.R.color.white))
//                    radioButton.textSize = 20f
//
//                    // Set LayoutParams with margins
//                    val layoutParams = LinearLayout.LayoutParams(
//                        0,
//                        LinearLayout.LayoutParams.WRAP_CONTENT,
//                        1f // 균등 분배
//                    )
//                    layoutParams.setMargins(10, 20, 10, 20) // 위아래 마진 추가
//                    radioButton.layoutParams = layoutParams
//
//                    radioButton.gravity = Gravity.LEFT // 왼쪽 정렬
//
//                    // Add to the list
//                    allRadioButtons.add(radioButton)
//
//                    // Add click listener to uncheck others
//                    radioButton.setOnClickListener { v: View ->
//                        for (rb in allRadioButtons) {
//                            if (rb !== v) {
//                                rb.isChecked = false // 다른 버튼은 해제
//                            } else selectedText[0] = item
//                        }
//                    }
//
//                    // Add RadioButton to the row
//                    rowLayout.addView(radioButton)
//                } else {
//                    // Add empty space for empty slots
//                    val space = View(this)
//                    space.layoutParams = LinearLayout.LayoutParams(
//                        0,
//                        LinearLayout.LayoutParams.WRAP_CONTENT,
//                        1f // 빈 공간도 균등 분배
//                    )
//                    rowLayout.addView(space)
//                }
//            }
//
//            // Add the row to the RadioGroup
//            dialogBinding.radioGroup.addView(rowLayout)
//        }
//
//        // Set button listeners
//        dialogBinding.renamePinCancelButton.setOnClickListener { v: View? -> dialog.dismiss() }
//        dialogBinding.renamePinConfirmButton.setOnClickListener { v: View? ->
//            pinName = selectedText[0]
//            callback.onConfirm(selectedText[0])
//            dialog.dismiss()
//        }
//
//        // Show the dialog
//        dialog.show()
//
//        // Adjust dialog size
//        val window = dialog.window
//        if (window != null) {
//            window.setLayout(
//                ViewGroup.LayoutParams.MATCH_PARENT,
//                ViewGroup.LayoutParams.WRAP_CONTENT
//            )
//            val params = window.attributes
//            params.width = WindowManager.LayoutParams.MATCH_PARENT // 너비
//            params.height = WindowManager.LayoutParams.WRAP_CONTENT // 높이
//            params.horizontalMargin = 0.05f // 좌우 마진 (화면 비율로 계산)
//            params.verticalMargin = 0.1f // 상하 마진
//            window.attributes = params
//        }
    }


    override fun onResume() {
        super.onResume()

        setUI()

        sendBroadcast()

        srcMapPgmFilePath =
            intent.getStringExtra("srcMapPgmFilePath") ?: "/sdcard/Download/office.pgm"
        srcMapYamlFilePath =
            intent.getStringExtra("srcMapYamlFilePath") ?: "/sdcard/Download/office.yaml"
        destMappingFilePath =
            intent.getStringExtra(ACTION_FILE_PATH) ?: "/sdcard/Download/map_meta_sample.json"
    }


    fun roiSaveToFile(strPath: String, strFileName: String, isSetTheta: Boolean) {
        val mapViewer = binding.mapViewer
        var j: Int
        val imageWidth = mapViewer.getBitmapWidth()
        val image_height = mapViewer.getBitmapHeight()

        Log.d(
            TAG,
            "image width : $imageWidth , image_height : $image_height"
        )
        var countId = 0
        var countIdRect = 0
        var countIdLine = 0

        val now = Date()

        // 원하는 포맷으로 설정
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())

        // 날짜를 포맷팅
        val formattedDate = sdf.format(now)
        var strRoiJson = ""
        strRoiJson += "{"
        strRoiJson += "\"uid\":\"OnDevice\",\"info\":{\"version\":\"1.0.0\",\"modified\":"
        strRoiJson += "\"$formattedDate\"}"
        strRoiJson += ", \"room_list\":["

        Log.d(TAG, "MapViewer.m_RoiObjects.size() : " + mapViewer.roiObjects.size)

        var i = 0
        while (i < mapViewer.roiObjects.size) {
            when (mapViewer.roiObjects[i].roiType) {
                ROI_TYPE_POLYGON -> {
                    if (countId > 0) {
                        strRoiJson += ", "
                    }

                    countId++ // 고유 id

                    strRoiJson += "{"
                    strRoiJson += "\"id\": \"$countId\""
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

                        val coordinates = viewModel.calculateCoordinate(
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

                    val coordinates = viewModel.calculateCoordinate(
                        mapViewer.roiObjects[i].mMBRCenter.x,
                        mapViewer.roiObjects[i].mMBRCenter.y,
                        image_height
                    )

                    val xvw = coordinates[0]
                    val yvh = coordinates[1]
                    strRoiJson += "\"is_set_theta\":$isSetTheta, "
                    strRoiJson += "\"x\":$xvw"
                    strRoiJson += ", \"y\":$yvh"
                    var angle =
                        mapViewer.roiObjects[i].angle - Math.toRadians(viewModel.rotated_angle.toDouble())

                    angle = ((angle + Math.PI) % (2 * Math.PI)) - Math.PI
                    strRoiJson += ", \"theta\":$angle"

                    strRoiJson += "}"

                    strRoiJson += ", \"image_position\":{"

                    val xvwImage = mapViewer.roiObjects[i].mMBRCenter.x // polygon의 무게중심 x
                    val yvhImage = mapViewer.roiObjects[i].mMBRCenter.y // polygon의 무게중심 y

                    strRoiJson += "\"x\":" + xvwImage
                    strRoiJson += ", \"y\":" + yvhImage
                    strRoiJson += ", \"theta\":" + mapViewer.roiObjects[i].angle
                    strRoiJson += "}"
                    strRoiJson += "}"
                }
            }

            i++
        }
        strRoiJson += "]"
        strRoiJson += ", \"block_area\":["
        i = 0
        while (i < mapViewer.roiObjects.size) {
            if (mapViewer.roiObjects[i].roiType == ROI_TYPE_RECT) {
                if (countIdRect > 0) {
                    strRoiJson += ", "
                }

                countIdRect++ // 고유 id


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
                var coordinates = viewModel.calculateCoordinate(
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
                coordinates = viewModel.calculateCoordinate(
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
                coordinates = viewModel.calculateCoordinate(
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
                coordinates = viewModel.calculateCoordinate(
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
                if (countIdLine > 0) {
                    strRoiJson += ", "
                }

                Log.d(
                    TAG,
                    "image_height: $image_height, image width: $imageWidth"
                )

                countIdLine++
                val roi = mapViewer.roiObjects[i]
                strRoiJson += "{\"image_path\":["

                // Add start point
                strRoiJson += "{\"x\":" + roi.mMBR.left + ", \"y\":" + (roi.mMBR.top) + "}, "

                // Add end point
                strRoiJson += "{\"x\":" + roi.mMBR.right + ", \"y\":" + (roi.mMBR.bottom) + "}], "
                strRoiJson += "\"robot_path\":["

                // Convert image coordinates to robot coordinates
                val startCoordinates =
                    viewModel.calculateCoordinate(roi.mMBR.left, roi.mMBR.top, image_height)
                val endCoordinates =
                    viewModel.calculateCoordinate(roi.mMBR.right, roi.mMBR.bottom, image_height)

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

        // 파일 경로를 설정합니다.
        val file = File(strPath, strFileName)

        lifecycleScope.launch(IO) {
            try {
                file.writeText(strRoiJson)
                sendFinishBroadcast(strPath, strFileName)
            } catch (t: Throwable) {
                warnLog(message = "Fail to save Json")
                t.printStackTrace()
            }
        }
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

    private fun verifyStoragePermissions(activity: Activity) {
        val permission = ActivityCompat.checkSelfPermission(
            activity,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.setData(Uri.parse("package:" + activity.packageName))

        activity.startActivity(intent)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                PERMISSIONS_STORAGE,
                REQUEST_EXTERNAL_STORAGE
            )
        }
    }
}