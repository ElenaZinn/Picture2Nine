package com.example.cutthepicture

import android.app.Application
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.request.RequestOptions

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 提升大图处理性能
        Glide.init(this, GlideBuilder().setDefaultRequestOptions(
            RequestOptions()
                .format(DecodeFormat.PREFER_RGB_565)
        ))
    }
}
