package com.travelia

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class StartAppActivity : AppCompatActivity() {

    private val SPLASH_DELAY: Long = 1500

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_startapp)

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, OptionToEnterActivity::class.java)
            startActivity(intent)
            finish()
        }, SPLASH_DELAY)
    }
}