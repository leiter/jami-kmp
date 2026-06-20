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

// On Kotlin/Native release builds with DCE, T::class obtained inside a nested inline function
// (e.g. viewModelFactory → factory<T>, or getViewModel → koinInject → get<T>) can be a
// different KClass instance than the one captured at the outermost call site. If qualifiedName
// is stripped, Koin falls back to "KClass@<hash>", causing an IndexKey mismatch between
// registration and lookup → NoDefinitionFoundException at startup.
//
// Fix for both sides: capture T::class at the outermost inline boundary and pass it explicitly
// to the non-reified overloads, so the same KClass instance is used for both registration and
// lookup regardless of DCE.
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
