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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.core.definition.Definition
import org.koin.core.definition.KoinDefinition
import org.koin.core.module.Module
import org.koin.core.module.factory
import org.koin.mp.KoinPlatform
import kotlin.reflect.KClass

// Same KN DCE/KClass-hash fix as iosMain — see ViewModelHelper.ios.kt for full explanation.

@PublishedApi
internal val vmKClassCache = HashMap<String, KClass<*>>()

actual inline fun <reified T : Any> Module.viewModelFactory(
    crossinline definition: Definition<T>
): KoinDefinition<T> {
    val klass = T::class
    vmKClassCache[klass.simpleName!!] = klass
    return factory(kClass = klass, definition = { definition(it) })
}

// viewModel { } retains the factory-built instance across recompositions (so StateFlow-backed
// input is not discarded on every keystroke) AND clears it — calling onCleared() — when the
// owner is destroyed. The instance is built in the initializer via the name-keyed KClass cache
// to avoid the Kotlin/Native KClass-identity bug. See ViewModelHelper.ios.kt for the full note.
@Composable
@Suppress("UNCHECKED_CAST")
actual inline fun <reified T : ViewModel> getViewModel(): T = viewModel(key = T::class.simpleName) {
    val klass = vmKClassCache[T::class.simpleName!!]!! as KClass<T>
    KoinPlatform.getKoin().get(klass)
}
