package com.kyuusanq3.aabooter

import android.app.Activity
import android.os.Bundle
import android.widget.Button

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.start_button).setOnClickListener {
            HeadUnitServerStarter.start(applicationContext)
        }
    }
}
