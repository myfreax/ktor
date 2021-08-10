/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application.plugins.api

import io.ktor.server.application.*
import io.ktor.util.pipeline.*

/**
 * Factory class that can be passed to install function in order to produce an instance of [ServerPlugin]
 * that will be installed into the current application context.
 **/
public abstract class ServerPluginFactory<Configuration : Any>(
    public val name: String
) : ApplicationPlugin<ApplicationCallPipeline, Configuration, ServerPlugin<Configuration>>

/**
 * Gets plugin instance for this pipeline, or fails with [MissingApplicationPluginException] if the feature is not
 * installed.
 * @throws MissingApplicationPluginException
 * @param plugin plugin to lookup
 * @return an instance of plugin
 */
public fun <A : Pipeline<*, ApplicationCall>, ConfigurationT : Any> A.plugin(
    plugin: ServerPluginFactory<ConfigurationT>
): ServerPlugin<ConfigurationT> {
    return attributes[pluginRegistryKey].getOrNull(plugin.key)
        ?: throw MissingApplicationPluginException(plugin.key)
}

internal fun <A : Pipeline<*, ApplicationCall>> A.findServerPlugin(
    plugin: ServerPluginFactory<*>
): ServerPlugin<*> {
    return attributes[pluginRegistryKey].getOrNull(plugin.key)
        ?: throw MissingApplicationPluginException(plugin.key)
}

internal typealias PipelineHandler = (Pipeline<*, ApplicationCall>) -> Unit
