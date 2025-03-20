package com.forsk.ondevice

import android.app.*
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.caselab.forsk.MapOptimization
import java.io.File

class FileProcessingService : Service() {
    var basePath: String = Environment.getExternalStorageDirectory().path + "/Download/"
    var fileNameBase: String = "office"
    var jsonFileName: String = "map_meta_sample.json"

    var pgmFileName: String = "$fileNameBase.pgm"
    var yamlFileName: String = "$fileNameBase.yaml"
    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fileUri: Uri? = intent?.data  // 인텐트에서 파일 URI 가져오기
        fileUri?.let { processFile(it) }

        stopSelf()  // 작업 완료 후 서비스 종료
        return START_NOT_STICKY
    }

    private fun processFile(fileUri: Uri) {
        Log.d("FileProcessingService", "Processing file: $fileUri")
        val NAME_LIBRARY_CASELAB_OPT = "mapoptimization"
        try {
            val fileDescriptor = contentResolver.openFileDescriptor(fileUri, "r")?.fileDescriptor
            if (fileDescriptor != null) {
                // 네이티브 라이브러리 실행
                System.loadLibrary(NAME_LIBRARY_CASELAB_OPT)
                loadFile()
            }
        } catch (e: Exception) {
            Log.e("FileProcessingService", "파일 처리 중 오류 발생", e)
        }
    }

    var srcMapPgmFilePath = basePath + pgmFileName
    var srcMapYamlFilePath = basePath + yamlFileName
    val destMappingFilePath = ""
    private fun loadFile() {
        val TAG = ""
        try {
            // File 객체 생성
            val file: File = File(srcMapPgmFilePath)

            val path = file.parent + "/" // 디렉터리 경로
            val filename = file.name // 파일 이름
            val fileTitle = filename.substring(0, filename.lastIndexOf('.')) // 확장자를 제외한 파일 제목

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
            MapOptimization.lineOptimization(path_rot, path_opt, fileTitle)
            Log.d("SKOnDeviceService", "Library-line Finish!")

            val strPgmFile = "$path_opt$fileTitle.pgm"

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
    }


    private fun startForegroundService() {
        val channelId = "file_processing_service"
        val channel =
            NotificationChannel(channelId, "File Processing", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("파일 처리 중")
            .setContentText("네이티브 라이브러리를 실행 중입니다.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
