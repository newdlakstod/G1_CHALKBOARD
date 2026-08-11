package com.gdonotice.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(247, 240, 227)
        window.navigationBarColor = Color.rgb(247, 240, 227)
        setContentView(ReferenceSketchbookView(this))
    }
}
