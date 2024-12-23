package com.forsk.ondevice;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash_screen);

        // 2초 후 메인 액티비티로 이동
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MapEditorActivity.class);
            startActivity(intent);
            finish();
        }, 1000);
    }
}
