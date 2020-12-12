package com.kkkkan.ocrmushroom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
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
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
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
        val TAG = "kkkkkanMainAct"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        private fun createFile(baseFolder: File, format: String, extension: String) =
            File(
                baseFolder, SimpleDateFormat(format, Locale.US)
                    .format(System.currentTimeMillis()) + extension
            )

        fun getOutputDirectory(context: Context): File {
            val appContext = context.applicationContext
            val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
                File(it, appContext.resources.getString(R.string.app_name)).apply { mkdirs() }
            }
            return if (mediaDir != null && mediaDir.exists())
                mediaDir else appContext.filesDir
        }

        private val TESS_DATA_DIR = "tessdata" + File.separator
        private val TESS_TRAINED_DATA = arrayListOf("eng.traineddata")
        private fun copyFiles(context: Context) {
            try {
                TESS_TRAINED_DATA.forEach {
                    val filePath = context.filesDir.toString() + File.separator + TESS_DATA_DIR + it

                    Log.d(TAG,"filepath "+filePath)
                    // assets以下をinputStreamでopenしてbaseApi.initで読み込める領域にコピー
                    val f0 = File(context.filesDir.toString() + File.separator + TESS_DATA_DIR)
                    if (!f0.exists()){
                        f0.mkdirs()
                    }
                    val f = File(filePath)
                    if (!f.exists()){
                        f.createNewFile()
                    }

                    context.resources.assets.open(TESS_DATA_DIR + it).use {inputStream ->
                        FileOutputStream(f).use { outStream ->
                            val buffer = ByteArray(1024)
                            var read = inputStream.read(buffer)
                            while (read != -1) {
                                outStream.write(buffer, 0, read)
                                read = inputStream.read(buffer)
                            }
                            outStream.flush()
                        }
                    }

                    val file = File(filePath)
                    if (!file.exists()) throw FileNotFoundException()
                }
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                Log.d(TAG,"FileNotFoundException"+e.localizedMessage)
            } catch (e: IOException) {
                e.printStackTrace()
                Log.d(TAG,"IOException"+e.localizedMessage)
            }
        }
    }

    lateinit var binding: ActivityMainBinding
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    lateinit var outputDirectory: File
    lateinit var cameraExecutor: Executor

    private lateinit var imageCapture: ImageCapture

    // 定数
    private val REQUEST_CODE_PERMISSIONS = 101
    private val REQUIRED_PERMISSIONS = arrayOf<String>(Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE)


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
        copyFiles(this)
        outputDirectory = getOutputDirectory(applicationContext)

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
                                binding.resultView.text = doOCR(uri) ?: "空"
                            }
                        }
                    })
            }

        }
    }


    private fun doOCR(jpgUri: Uri): String {
        val baseApi = TessBaseAPI()
        // initで言語データを読み込む
        baseApi.init(filesDir.path, "eng")
        // ギャラリーから読み込んだ画像をFile or Bitmap or byte[] or Pix形式に変換して渡してあげる
        val bitmap =   MediaStore.Images.Media.getBitmap(this.getContentResolver(), jpgUri);
        baseApi.setImage(bitmap)
        // これだけで読み取ったテキストを取得できる
        val recognizedText = baseApi.utF8Text
        baseApi.end()
        return recognizedText
    }



}
