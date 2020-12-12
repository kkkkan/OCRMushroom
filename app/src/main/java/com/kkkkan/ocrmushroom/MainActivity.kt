package com.kkkkan.ocrmushroom

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import com.kkkkan.ocrmushroom.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {
    companion object {
        // Smejiとやり取りするintentの仕様
        // https://simeji.me/blog/manuals/manuals_android/android_mushroom/make_mushroom/id=58
        val ACTION_INTERCEPT = "com.adamrocker.android.simeji.ACTION_INTERCEPT"
        val REPLACE_KEY = "replace_key"
    }

    lateinit var binding: ActivityMainBinding
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null

    private lateinit var imageCapture: ImageCapture

    // 定数
    private val REQUEST_CODE_PERMISSIONS = 101
    private val REQUIRED_PERMISSIONS = arrayOf<String>(Manifest.permission.CAMERA)

    val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun surfaceDestroyed(p0: SurfaceHolder?) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun surfaceCreated(p0: SurfaceHolder?) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)

//        binding.cameraView.holder.addCallback(surfaceCallback)
        val action = intent.action
        if (action != null && ACTION_INTERCEPT.equals(action)) {
            /* Simejiから呼出された時 */
            val result = "NEW_STRING"
            val data = Intent()
            data.putExtra(REPLACE_KEY, result)
            setResult(RESULT_OK, data)
            finish()
        }

        // パーミッションのチェック
        if (allPermissionsGranted()) {
            binding.cameraView.post({
                //                startCamera()
                setUpCamera()
            })
        } else {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }


    // パーミッション許可のリクエスト結果の取得
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
//                startCamera()
                setUpCamera()
            } else {
                Toast.makeText(
                    this, "ユーザーから権限が許可されていません。",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    // 全てのパーミッション許可
    private fun allPermissionsGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }


    /** Initialize CameraX, and prepare to bind the camera use cases  */
    private fun setUpCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Camera provider is now guaranteed to be available
            val cameraProvider = cameraProviderFuture.get()

            // Set up the preview use case to display camera preview.
            val preview = Preview.Builder().build()

            // Set up the capture use case to allow users to take photos.
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // Choose the camera by requiring a lens facing
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            // Attach use cases to the camera with the same lifecycle owner
            val camera = cameraProvider.bindToLifecycle(
                this as LifecycleOwner, cameraSelector, preview, imageCapture
            )

            // Connect the preview use case to the previewView
            preview.setSurfaceProvider(
                binding.cameraView.surfaceProvider
            )
        }, ContextCompat.getMainExecutor(this))
    }


}
