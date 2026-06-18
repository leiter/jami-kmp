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
import org.koin.compose.koinInject
import org.koin.core.definition.Definition
import org.koin.core.definition.KoinDefinition
import org.koin.core.module.Module
import org.koin.core.module.factory
import kotlin.reflect.KClass

// On Kotlin/Native release builds, T::class obtained two inline levels deep (viewModelFactory
// → factory<T>) can be a different KClass instance than the one seen at the call site.
// When qualifiedName is null (DCE stripped), Koin falls back to "KClass@<hash>", making
// the IndexKey mismatch at lookup time → NoDefinitionFoundException.
//
// Fix: use the native factory(kClass, definition) overload from koin-core nativeMain, passing
// T::class captured at THIS (outer) inline boundary. The named-param `kClass =` forces
// resolution to that overload rather than the reified factory<T>(...).
actual inline fun <reified T : Any> Module.viewModelFactory(
    crossinline definition: Definition<T>
): KoinDefinition<T> {
    val klass: KClass<T> = T::class
    return factory(kClass = klass, definition = { definition(it) })
}

@Composable
actual inline fun <reified T : Any> getViewModel(): T = koinInject()
