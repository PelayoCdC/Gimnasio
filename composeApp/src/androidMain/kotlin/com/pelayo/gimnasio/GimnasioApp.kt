package com.pelayo.gimnasio

import android.app.Application
import com.pelayo.gimnasio.di.initKoin
import org.koin.android.ext.koin.androidContext

class GimnasioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@GimnasioApp)
        }
    }
}
