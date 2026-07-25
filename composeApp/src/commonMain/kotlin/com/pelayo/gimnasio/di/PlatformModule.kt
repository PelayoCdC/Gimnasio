package com.pelayo.gimnasio.di

import org.koin.core.module.Module

/** Módulo con los bindings específicos de cada plataforma (driver de DB, etc.). */
expect val platformModule: Module
