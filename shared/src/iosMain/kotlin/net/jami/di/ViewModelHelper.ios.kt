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
import org.koin.core.definition.Definition
import org.koin.core.definition.KoinDefinition
import org.koin.core.module.Module
import org.koin.core.module.factory

// On Kotlin/Native, T::class at the lookup call site can be a different KClass object
// (with a different identity-hash fallback name) than the one captured at registration,
// so Koin's index keys mismatch and a plain get<T>() throws NoDefinitionFoundException.
//
// Registration: register under the registration-site T::class (Koin native factory).
// Lookup: do NOT trust the lookup-site T::class — delegate to jamiResolveViewModel, which
// scans Koin's own registry for the factory whose registered primaryType matches by name
// and resolves with that exact registered KClass object. See KoinDiagnostics.ios.kt.

actual inline fun <reified T : Any> Module.viewModelFactory(
    crossinline definition: Definition<T>
): KoinDefinition<T> =
    factory(kClass = T::class, definition = { definition(it) })

@Composable
@Suppress("UNCHECKED_CAST")
actual inline fun <reified T : Any> getViewModel(): T =
    jamiResolveViewModel(T::class.qualifiedName, T::class.simpleName) as T
