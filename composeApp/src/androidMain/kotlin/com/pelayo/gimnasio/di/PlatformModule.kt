package com.pelayo.gimnasio.di

import com.pelayo.gimnasio.platform.BarcodeScanner
import com.pelayo.gimnasio.platform.DatabaseDriverFactory
import com.pelayo.gimnasio.platform.FileTransfer
import com.pelayo.gimnasio.platform.ReminderScheduler
import com.pelayo.gimnasio.platform.RestTimerNotifier
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { DatabaseDriverFactory(androidContext()) }
    single { RestTimerNotifier(androidContext()) }
    single { FileTransfer(androidContext()) }
    single { BarcodeScanner(androidContext()) }
    single { ReminderScheduler(androidContext()) }
}
