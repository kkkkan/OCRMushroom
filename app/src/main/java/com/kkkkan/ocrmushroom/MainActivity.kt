package com.kkkkan.ocrmushroom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import com.googlecode.tesseract.android.TessBaseAPI
import com.kkkkan.ocrmushroom.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    companion object {
        // Smejiとやり取りするintentの仕様
        // https://simeji.me/blog/manuals/manuals_android/android_mushroom/make_mushroom/id=58
        val ACTION_INTERCEPT = "com.adamrocker.android.simeji.ACTION_INTERCEPT"
        val REPLACE_KEY = "replace_key"

        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        val TAG="kkkkkan"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        private fun createFile(baseFolder: File, format: String, extension: String) =
            File(baseFolder, SimpleDateFormat(format, Locale.US)
                .format(System.currentTimeMillis()) + extension)

        fun getOutputDirectory(context: Context): File {
            val appContext = context.applicationContext
            val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
                File(it, appContext.resources.getString(R.string.app_name)).apply { mkdirs() } }
            return if (mediaDir != null && mediaDir.exists())
                mediaDir else appContext.filesDir
        }
    }

    lateinit var binding: ActivityMainBinding
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    lateinit var outputDirectory : File
    lateinit var cameraExecutor : Executor

    private lateinit var imageCapture: ImageCapture

    // 定数
    private val REQUEST_CODE_PERMISSIONS = 101
    private val REQUIRED_PERMISSIONS = arrayOf<String>(Manifest.permission.CAMERA)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)

        val action = intent.action
        if (action != null && ACTION_INTERCEPT.equals(action)) {
            /* Simejiから呼出された時 */
            val result = "NEW_STRING"
            val data = Intent()
            data.putExtra(REPLACE_KEY, result)
            setResult(RESULT_OK, data)
            finish()
        }

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // パーミッションのチェック
        if (allPermissionsGranted()) {
            binding.cameraView.post({
                setUpCamera()
            })
        } else {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        outputDirectory= getOutputDirectory(this)
    }

    // パーミッション許可のリクエスト結果の取得
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
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

        binding.okButton.setOnClickListener {
            // Get a stable reference of the modifiable image capture use case
            imageCapture.let { imageCapture ->

                // Create output file to hold the image
                val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)

                // Setup image capture metadata
//                val metadata = Metadata.apply {
//
//                    // Mirror image when using the front camera
//                    isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
//                }

                // Create output options object which contains file + metadata
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
//                    .setMetadata(metadata)
                    .build()

                // Setup image capture listener which is triggered after photo has been taken
                imageCapture.takePicture(
                    outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                        override fun onError(exc: ImageCaptureException) {
                            Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                        }

                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                            Log.d(TAG, "Photo capture succeeded: $savedUri")

//                            // We can only change the foreground Drawable using API level 23+ API
//                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                                // Update the gallery thumbnail with latest picture taken
//                                setGalleryThumbnail(savedUri)
//                            }

                            // Implicit broadcasts will be ignored for devices running API level >= 24
                            // so if you only target API level 24+ you can remove this statement
//                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
//                                requireActivity().sendBroadcast(
//                                    Intent(android.hardware.Camera.ACTION_NEW_PICTURE, savedUri)
//                                )
//                            }

                            // If the folder selected is an external media directory, this is
                            // unnecessary but otherwise other apps will not be able to access our
                            // images unless we scan them using [MediaScannerConnection]
                            val mimeType = MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(savedUri.toFile().extension)
                            MediaScannerConnection.scanFile(
                                this@MainActivity,
                                arrayOf(savedUri.toFile().absolutePath),
                                arrayOf(mimeType)
                            ) { _, uri ->
                                Log.d(TAG, "Image capture scanned into media store: $uri")
                            }
                        }
                    })

                // We can only change the foreground Drawable using API level 23+ API
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//
//                    // Display flash animation to indicate that photo was captured
//                    container.postDelayed({
//                        container.foreground = ColorDrawable(Color.WHITE)
//                        container.postDelayed(
//                            { container.foreground = null }, ANIMATION_FAST_MILLIS)
//                    }, ANIMATION_SLOW_MILLIS)
//                }
            }

        }
    }



    private fun doOCR(bitmap: Bitmap) : String{
        val baseApi = TessBaseAPI()
        // initで言語データを読み込む
        baseApi.init(getFilesDir().toString(), "eng")
        // ギャラリーから読み込んだ画像をFile or Bitmap or byte[] or Pix形式に変換して渡してあげる
        baseApi.setImage(bitmap)
        // これだけで読み取ったテキストを取得できる
        val recognizedText = baseApi.utF8Text
        baseApi.end()
        return recognizedText
    }
}
