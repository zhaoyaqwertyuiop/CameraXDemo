package com.zy.camerax;

import android.app.Application;

import androidx.core.content.FileProvider;

public class CameraXFileProvider extends FileProvider{

    @Override
    public boolean onCreate() {
        //noinspection ConstantConditions
        CameraXApp.INSTANCE.initApp((Application) getContext().getApplicationContext());
        YuvUtils.init(getContext());
        return true;
    }

}