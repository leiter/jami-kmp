package net.jami.android

import android.app.Application
import net.jami.di.initKoin
import org.koin.android.ext.koin.androidContext

class JamiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@JamiApplication)
        }
    }
}
