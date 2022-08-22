package com.zy.example.camerax

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.window.WindowManager
import com.zy.camerax.CameraXUtil
import com.zy.camerax.ImageDialog
import com.zy.camerax.YuvUtils
import com.zy.example.camerax.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    val TAG = "MainActivity"

    private lateinit var mBinding: ActivityMainBinding

    private var getBitmap = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)


        val cameraXUtil = CameraXUtil()
        cameraXUtil.init(this, mBinding.preview) {
            if (getBitmap) {
                getBitmap = false

                val bitmap = CameraXUtil.imageToBitmap(it)
                bitmap?.let {
                    lifecycleScope.launch(Dispatchers.Main) {
                        ImageDialog(this@MainActivity).setBitmap(bitmap).show()
                    }
                }
            }
        }

        mBinding.takepickture.setOnClickListener {
            val file = File(getExternalFilesDir(null), "test.png")
            cameraXUtil.takePickture(file) {
                if (it) {
                    Toast.makeText(this, "拍照成功：$file", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "拍照失败：", Toast.LENGTH_LONG).show()
                }
            }
        }

        mBinding.getBitmap.setOnClickListener {
            getBitmap = true
        }
    }

    private val PERMISSIONS_REQUEST_CODE = 10
    private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA)

    @SuppressLint("NewApi")
    override fun onResume() {
        super.onResume()
        if (!hasPermissions(this)) {
            // Request camera-related permissions
            requestPermissions(PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE)
        }
    }

    /** Convenience method used to check if all permissions required by this app are granted */
    fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}