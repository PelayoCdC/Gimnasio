package com.pelayo.gimnasio.di

import com.pelayo.gimnasio.platform.BarcodeScanner
import com.pelayo.gimnasio.platform.DatabaseDriverFactory
import com.pelayo.gimnasio.platform.FileTransfer
import com.pelayo.gimnasio.platform.ReminderScheduler
import com.pelayo.gimnasio.platform.RestTimerNotifier
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { DatabaseDriverFactory() }
    single { RestTimerNotifier() }
    single { FileTransfer() }
    single { BarcodeScanner() }
    single { ReminderScheduler() }
}
