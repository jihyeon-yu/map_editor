package com.forsk.ondevice

import android.app.Application
import android.graphics.Point
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.forsk.ondevice.CDrawObj.Companion.ROI_TYPE_LINE
import com.forsk.ondevice.CDrawObj.Companion.ROI_TYPE_POLYGON
import com.forsk.ondevice.CDrawObj.Companion.ROI_TYPE_RECT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader

class MapEditorViewModel(application: Application) : AndroidViewModel(application = application) {
    suspend fun loadJson(mapViewer: MapImageView, filePath: String?, lib_flag: Boolean): Boolean {
        val TAG = "loadJson"

        Log.d(TAG, "Exist Json File")
        val jsonStringBuilder = StringBuilder()

        // 내부 저장소에서 파일 스트림 열기
        // InputStream 데이터를 문자열로 변환
        try {
            withContext(Dispatchers.IO) {
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
                                    TAG,
                                    "  ImagePosition: $mbr_x $mbr_y"
                                )
                                for (j in 0 until imagePathArray.length()) {
                                    val point = imagePathArray.getJSONObject(j)
                                    x = point.getInt("x")
                                    y = point.getInt("y")
                                    Log.d(TAG, "    Point: ($x, $y)")
                                    if (j == 0) {
                                        mapViewer.loadObject(ROI_TYPE_POLYGON, Point(x, y))
                                    } else {
                                        mapViewer.addPointPolygon(Point(x, y))
                                    }
                                    if (left > x) left = x
                                    if (right < x) right = x
                                    if (top > y) top = y
                                    if (bottom < y) bottom = y
                                }
                                mapViewer.drawing = true
                                mapViewer.roiAddObject()
                                // 241222 seongwoong 현재 버그 있음 확인 필요
                                //MapViewer.m_RoiCurObject.m_MBR = new Rect(left, top, right, bottom);
                                mapViewer.roiCurObject!!.mMBRCenter.x = mbr_x
                                mapViewer.roiCurObject!!.mMBRCenter.y = mbr_y
                                mapViewer.SetLabel(name)
                                if (lib_flag) {
                                    val theta = imagePosition.getDouble("theta").toFloat()
                                    mapViewer.roiCurObject?.angle = theta
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

                                mapViewer.loadObject(ROI_TYPE_RECT, pt1)
                                mapViewer.loadRect(pt1, pt2)
                                mapViewer.roiAddObject()
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

                                mapViewer.loadObject(ROI_TYPE_LINE, pt1)
                                mapViewer.loadRect(pt1, pt2)
                                mapViewer.roiAddObject()
                            }

                            mapViewer?.currentSelectedIndex = -1
                            mapViewer.roiCurObject = null
                            Log.d(TAG, "Read Json Success")
                            return@withContext true
                        }
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
        return true
    }
}