/**
 * Copyright (C) 2013-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.styx.proxy.plugin;

import com.hotels.styx.api.Environment;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.api.plugins.spi.Plugin;
import com.hotels.styx.api.plugins.spi.PluginFactory;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayListWithExpectedSize;
import static com.hotels.styx.proxy.plugin.NamedPlugin.namedPlugin;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A helper class for creating plugin supplier objects.
 */
public class PluginSuppliers {
    private static final String DEFAULT_PLUGINS_METRICS_SCOPE = "styx.plugins";
    private static final Logger LOG = getLogger(PluginSuppliers.class);

    private final Configuration configuration;
    private final PluginFactoryLoader pluginFactoryLoader;
    private final Environment environment;

    public PluginSuppliers(Environment environment) {
        this(environment, new FileSystemPluginFactoryLoader());
    }

    public PluginSuppliers(Environment environment, PluginFactoryLoader pluginFactoryLoader) {
        this.configuration = environment.configuration();
        this.pluginFactoryLoader = checkNotNull(pluginFactoryLoader);
        this.environment = checkNotNull(environment);
    }

    private Optional<PluginsMetadata> readPluginsConfig() {
        return configuration.get("plugins", PluginsMetadata.class);
    }

    public Supplier<Iterable<NamedPlugin>> fromConfigurations() {
        Iterable<NamedPlugin> plugins = readPluginsConfig()
                .map(this::activePlugins)
                .orElse(emptyList());

        return () -> plugins;
    }

    private Iterable<NamedPlugin> activePlugins(PluginsMetadata pluginsMetadata) {
        List<PluginMetadata> pluginMetadataList = pluginsMetadata.activePlugins();

        List<NamedPlugin> plugins = plugins(pluginMetadataList);

        if (pluginMetadataList.size() > plugins.size()) {
            throw new RuntimeException(format("%s plugins could not be loaded", pluginMetadataList.size() - plugins.size()));
        }

        return plugins;
    }

    private List<NamedPlugin> plugins(List<PluginMetadata> pluginMetadataList) {
        List<NamedPlugin> plugins = newArrayListWithExpectedSize(pluginMetadataList.size());

        for (PluginMetadata pluginMetadata : pluginMetadataList) {
            try {
                plugins.add(loadPlugin(pluginMetadata));
            } catch (Throwable e) {
                LOG.error(format("Could not load plugin %s: %s", pluginMetadata.name(), pluginMetadata.newPluginFactory().getClass().getName()), e);
            }
        }

        return plugins;
    }

    private NamedPlugin loadPlugin(PluginMetadata pluginMetadata) {
        PluginFactory factory = pluginFactoryLoader.load(pluginMetadata);
        Plugin plugin = factory.create(new PluginEnvironment(environment, pluginMetadata, DEFAULT_PLUGINS_METRICS_SCOPE));
        return namedPlugin(pluginMetadata.name(), plugin);
    }

}
