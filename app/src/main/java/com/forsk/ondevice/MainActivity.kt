package com.forsk.ondevice

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import com.forsk.ondevice.ui.theme.OnDeviceTheme
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity() {
    val TAG = ""
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



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OnDeviceTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val bitmap = loadPNG("/sdcard/Download/opt/office.pgm")
                    MapEditor(bitmap.asImageBitmap(), Modifier)
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    OnDeviceTheme {
        Greeting("Android")
    }
}