package shifa.doctor

import android.app.Application
import shifa.doctor.data.storage.initSecureSettings
import shifa.doctor.utils.appContext

class ShifaDoctorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initSecureSettings(this) // before anything reads the token
        appContext = this
    }
}
