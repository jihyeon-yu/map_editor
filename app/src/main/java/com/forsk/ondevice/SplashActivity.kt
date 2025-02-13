package com.forsk.ondevice

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash_screen)

        // 2초 후 메인 액티비티로 이동
        Handler().postDelayed({
            val intent =
                Intent(this@SplashActivity, MapEditorActivity::class.java)
            startActivity(intent)
            finish()
        }, 1000)
    }
}
