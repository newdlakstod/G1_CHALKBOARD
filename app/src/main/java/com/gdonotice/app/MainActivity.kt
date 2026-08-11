package com.gdonotice.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.TextView

/** Zero-base shell. Screens and design assets are intentionally rebuilt from the reference. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(247, 240, 227)
        window.navigationBarColor = Color.rgb(247, 240, 227)
        setContentView(TextView(this).apply {
            text = "G1 SKETCHBOOK"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(32, 32, 32))
            setBackgroundColor(Color.rgb(247, 240, 227))
        })
    }
}
