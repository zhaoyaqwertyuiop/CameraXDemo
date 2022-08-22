package com.zy.camerax

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

class ImageDialog(context: Context) : Dialog(context) {

    init {
        setContentView(R.layout.dialog_image)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT) // 去除系统dialog自带的margin

        setCanceledOnTouchOutside(true)
        findViewById<View>(R.id.imagell).setOnClickListener {
            dismiss()
        }
    }

    fun setBitmap(bitmap: Bitmap): ImageDialog {
        findViewById<ImageView>(R.id.image).setImageBitmap(bitmap)
        return this
    }

}