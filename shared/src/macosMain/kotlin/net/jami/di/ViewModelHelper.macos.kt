/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package net.jami.di

import androidx.compose.runtime.Composable
import org.koin.compose.currentKoinScope
import org.koin.core.definition.Definition
import org.koin.core.definition.KoinDefinition
import org.koin.core.module.Module
import org.koin.core.module.factory
import kotlin.reflect.KClass

// Same KN DCE/KClass-hash fix as iosMain — see ViewModelHelper.ios.kt for full explanation.
actual inline fun <reified T : Any> Module.viewModelFactory(
    crossinline definition: Definition<T>
): KoinDefinition<T> {
    val klass: KClass<T> = T::class
    return factory(kClass = klass, definition = { definition(it) })
}

@Composable
actual inline fun <reified T : Any> getViewModel(): T {
    val klass: KClass<T> = T::class
    return currentKoinScope().get(klass)
}
