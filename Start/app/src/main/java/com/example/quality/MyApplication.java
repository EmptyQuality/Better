package com.example.quality;

import android.app.Application;

import com.example.quality.util.LogUtil;

public class MyApplication extends Application {
    @Override
    public void onCreate(){
        super.onCreate();
        LogUtil.init("Lifecycle",true);
    }
}
