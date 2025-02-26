package com.forsk.ondevice

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Point
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forsk.ondevice.CDrawObj.Companion.ROI_TYPE_LINE
import com.forsk.ondevice.CDrawObj.Companion.ROI_TYPE_POLYGON
import com.forsk.ondevice.CDrawObj.Companion.ROI_TYPE_RECT
import com.forsk.ondevice.CommonUtil.debugLog
import com.forsk.ondevice.domain.BlockArea
import com.forsk.ondevice.domain.BlockWall
import com.forsk.ondevice.domain.MapMapping
import com.forsk.ondevice.domain.Room
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import java.io.IOException
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Objects

class MapEditorViewModel(application: Application) : AndroidViewModel(application = application) {
    val TAG = "loadJson"

    suspend fun loadJson(mapViewer: MapImageView, filePath: String?, libFlag: Boolean): Boolean {
        Log.d(TAG, "Exist Json File")

        return try {
            withContext(IO) {
                val jsonString = FileInputStream(filePath).use { inputStream ->
                    InputStreamReader(inputStream).use { inputStreamReader ->
                        BufferedReader(inputStreamReader).use { it.readText() }
                    }
                }

                val jsonObject = Gson().fromJson(jsonString, MapMapping::class.java)
                parseRoomList(jsonObject.room_list, mapViewer, libFlag)
                parseBlockArea(jsonObject.block_area, mapViewer)
                parseBlockWall(jsonObject.block_wall, mapViewer)

                mapViewer.currentSelectedIndex = -1
                mapViewer.roiCurObject = null
                Log.d(TAG, "Read Json Success")
                true
            }
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "Read Json FileNotFoundException: $filePath ${e.message}")
            false
        } catch (e: JSONException) {
            Log.e(TAG, "Read Json JSONException: ${e.message}")
            false
        } catch (e: IOException) {
            Log.e(TAG, "Read Json IOException: ${e.message}")
            false
        } finally {
            Log.d(TAG, "loadJson end")
        }
    }

    private fun parseRoomList(roomList: List<Room>, mapViewer: MapImageView, libFlag: Boolean) {
        Log.d(TAG, "Room List:")

        roomList.forEach {
            val name = it.name
            val imagePathArray = it.image_path
            val imagePosition = it.image_position
            val x = it.image_position.x
            val y = it.image_position.y

            Log.d(TAG, "  Image Position: $x, $y")

            var left = Int.MAX_VALUE
            var right = Int.MIN_VALUE
            var top = Int.MAX_VALUE
            var bottom = Int.MIN_VALUE

            imagePathArray.forEachIndexed { index, imagePath ->
                if (index == 0) {
                    mapViewer.loadObject(ROI_TYPE_POLYGON, Point(imagePath.x, imagePath.y))
                } else {
                    mapViewer.addPointPolygon(Point(imagePath.x, imagePath.y))
                }

                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
            }

            mapViewer.drawing = true
            mapViewer.addROIObject()
            mapViewer.roiCurObject?.mMBRCenter = Point(x, y)
            mapViewer.setLabel(name)

            if (libFlag) {
                mapViewer.roiCurObject?.angle = imagePosition.theta.toFloat()
            }
        }
    }

    private fun parseBlockArea(blockAreaList: List<BlockArea>, mapViewer: MapImageView) {
        Log.d(TAG, "\nBlock Area:")

        blockAreaList.forEach {
            val imagePathArray = it.image_path
            val pt1 = Point(imagePathArray[0].x, imagePathArray[0].y)
            val pt2 = Point(imagePathArray[2].x, imagePathArray[2].y)

            Log.d(TAG, "  Block Area Points: $pt1, $pt2")
            mapViewer.loadObject(ROI_TYPE_RECT, pt1)
            mapViewer.loadRect(pt1, pt2)
            mapViewer.addROIObject()
        }
    }

    private fun parseBlockWall(blockWall: List<BlockWall>, mapViewer: MapImageView) {
        Log.d(TAG, "\nBlock Wall:")
        blockWall.forEach {
            val imagePathArray = it.image_path

            val pt1 = Point(imagePathArray[0].x, imagePathArray[0].y)
            val pt2 = Point(imagePathArray[1].x, imagePathArray[1].y)

            Log.d(TAG, "  Block Wall Points: $pt1, $pt2")
            mapViewer.loadObject(ROI_TYPE_LINE, pt1)
            mapViewer.loadRect(pt1, pt2)
            mapViewer.addROIObject()
        }
    }

    @Throws(IOException::class)
    fun loadPNG(filePath: String): Bitmap {
        val TAG = ""
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
                val newGray = when (r) {
                    255 -> 150 // 흰색
                    0 -> 0 // 검은색
                    else -> 51 // 회색
                }

                // 새 픽셀 값
                val newPixel = Color.rgb(newGray, newGray, newGray)
                bitmap.setPixel(x, y, newPixel)
            }
        }

        return bitmap
    }

    fun roiSaveToEmptyFile(strPath: String, strFileName: String) {
        val dateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
        val formattedDate = dateTime.format(formatter)

        var strRoiJson = ""
        strRoiJson += "{"
        strRoiJson += "\"uid\":\"Tybqxakqm2\",\"info\":{\"version\":\"1.0.0\",\"modified\":\"${formattedDate}\"}"
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
        val file = File(strPath, strFileName)
        try {
            debugLog(TAG, "$strPath/$strFileName")
            viewModelScope.launch(IO) {
                withContext(IO) {
                    file.writeText(strRoiJson)
                }
            }
        } catch (ie: IOException) {
            Log.d(TAG, Objects.requireNonNull(ie.localizedMessage))
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
    }

    private var transformationMatrix: Mat? = null
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
        return Point(original_pixel_x, original_pixel_y)
    }

    var nResolution: Double = 0.0
    var originX: Double = 0.0
    var originY: Double = 0.0
    var originAngle: Double = 0.0
    var lib_flag = true
    fun loadYaml(filePath: String?): Boolean {
        // 내부 저장소에서 파일 스트림 열기
        try {
            FileInputStream(filePath).use { inputStream ->
                val yaml = Yaml()
                // YAML 파싱
                val data = yaml.load<Map<String, Any>>(inputStream)

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
                        this.originX = originX.toDouble()
                        Log.d(TAG, "origin_x : ${this.originX}")
                    } else {
                        Log.d(TAG, "origin_x is not a valid number.")
                    }

                    if (originY is Number) {
                        this.originY = originY.toDouble()
                        Log.d(TAG, "origin_y : ${this.originY}")
                    } else {
                        Log.d(TAG, "origin_y is not a valid number.")
                    }

                    if (originAngle is Number) {
                        this.originAngle = originAngle.toDouble()
                        Log.d(
                            TAG,
                            "origin_angle : ${this.originAngle}"
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
                        if (!OpenCVLoader.initLocal()) {
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

    var rotated_angle = 0f
    var original_image_height: Int = 0

    fun calculateCoordinate(x: Int, y: Int, image_height: Int): DoubleArray {
        // 계산 공식: (x * resolution + origin_x)
        var robot_x = 0.0
        var robot_y = 0.0

        if (lib_flag) {
            val pt = transformToRobotCoordinates(x, y)
            robot_x = pt.x * nResolution + originX
            robot_y = (original_image_height - pt.y) * nResolution + originY
        } else {
            robot_x = x * nResolution + originX
            robot_y = (image_height - y) * nResolution + originY
        }
        return doubleArrayOf(robot_x, robot_y)
    }
}