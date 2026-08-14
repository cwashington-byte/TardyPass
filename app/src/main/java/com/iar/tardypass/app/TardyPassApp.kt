package com.iar.tardypass.app

import android.app.Application
import com.iar.tardypass.data.local.AppDatabase
import com.iar.tardypass.data.repo.PassRepository
import com.iar.tardypass.data.repo.SettingsRepository
import com.iar.tardypass.data.repo.StudentRepository
import com.iar.tardypass.printing.PrinterManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual DI — the whole graph is ~7 objects; Hilt isn't worth the startup cost here. */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = AppDatabase.build(app)
    val settings = SettingsRepository(app)
    val students = StudentRepository(db.studentDao())
    val passes = PassRepository(db.passDao())
    val printer = PrinterManager(app, settings, appScope)
}

class TardyPassApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.printer.startProbe()
    }
}
