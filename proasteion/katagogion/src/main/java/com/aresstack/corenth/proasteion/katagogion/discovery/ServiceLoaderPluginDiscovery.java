package com.aresstack.corenth.proasteion.katagogion.discovery;

import com.aresstack.corenth.proasteion.katagogion.CorenthPlugin;
import com.aresstack.corenth.proasteion.katagogion.PluginDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Discovers {@link CorenthPlugin} providers declared in {@code META-INF/services} of a given class loader.
 *
 * <p>The class loader is chosen explicitly by the host; this class neither creates class loaders nor
 * downloads or unpacks plugins. Broken providers and duplicate plugin ids are reported as problems
 * instead of aborting discovery. Plugins are returned ordered by id so that installation order is
 * deterministic.
 */
public final class ServiceLoaderPluginDiscovery implements PluginDiscovery {

    private final ClassLoader classLoader;

    public ServiceLoaderPluginDiscovery(ClassLoader classLoader) {
        if (classLoader == null) {
            throw new IllegalArgumentException("classLoader must not be null");
        }
        this.classLoader = classLoader;
    }

    @Override
    public DiscoveryReport discover() {
        List<Candidate> candidates = new ArrayList<Candidate>();
        List<String> problems = new ArrayList<String>();
        Iterator<CorenthPlugin> providers = ServiceLoader.load(CorenthPlugin.class, classLoader).iterator();
        while (true) {
            CorenthPlugin plugin;
            try {
                if (!providers.hasNext()) {
                    break;
                }
                plugin = providers.next();
            } catch (ServiceConfigurationError e) {
                problems.add("unloadable plugin provider: " + e.getMessage());
                continue;
            }
            PluginDescriptor descriptor = readDescriptor(plugin, problems);
            if (descriptor != null) {
                candidates.add(new Candidate(descriptor.id(), plugin));
            }
        }
        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate left, Candidate right) {
                return left.id.compareTo(right.id);
            }
        });
        List<CorenthPlugin> plugins = new ArrayList<CorenthPlugin>(candidates.size());
        Set<String> seen = new HashSet<String>();
        for (Candidate candidate : candidates) {
            if (seen.add(candidate.id)) {
                plugins.add(candidate.plugin);
            } else {
                problems.add("duplicate plugin id '" + candidate.id + "' from " + candidate.plugin.getClass().getName());
            }
        }
        return new DiscoveryReport(plugins, problems);
    }

    private static PluginDescriptor readDescriptor(CorenthPlugin plugin, List<String> problems) {
        try {
            PluginDescriptor descriptor = plugin.descriptor();
            if (descriptor == null) {
                problems.add("plugin without descriptor: " + plugin.getClass().getName());
            }
            return descriptor;
        } catch (RuntimeException e) {
            problems.add("plugin descriptor failed for " + plugin.getClass().getName() + ": " + e.getClass().getSimpleName());
            return null;
        }
    }

    private static final class Candidate {
        final String id;
        final CorenthPlugin plugin;

        Candidate(String id, CorenthPlugin plugin) {
            this.id = id;
            this.plugin = plugin;
        }
    }
}
