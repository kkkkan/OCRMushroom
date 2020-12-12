package com.kkkkan.ocrmushroom

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent



class MainActivity : AppCompatActivity() {
    companion object{
        // Smejiとやり取りするintentの仕様
        // https://simeji.me/blog/manuals/manuals_android/android_mushroom/make_mushroom/id=58
        val ACTION_INTERCEPT="com.adamrocker.android.simeji.ACTION_INTERCEPT"
        val REPLACE_KEY="replace_key"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        val it = intent
        val action = it.action
        if (action != null && ACTION_INTERCEPT.equals(action)) {
            /* Simejiから呼出された時 */
            val result = "NEW_STRING"
            val data = Intent()
            data.putExtra(REPLACE_KEY, result)
            setResult(RESULT_OK, data)
            finish()
        }
    }
}
