package com.zy.camerax

import android.app.Application

/**
 * @description：
 * @author: zhaoya
 * @create：2022/8/19 0019 16:01
 */
object CameraXApp {
    lateinit var application: Application
        private set

    fun initApp(application: Application) {
        if (!this::application.isInitialized) {
            this.application = application
        }
    }
}