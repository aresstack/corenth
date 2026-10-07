package com.aresstack.corenth.proasteion.katagogion;

/**
 * Decides whether a plugin may install tools and which mediated capabilities it is granted.
 *
 * <p>Tool admission is deliberately separate from the Tamias resource policy: admission answers
 * "may this plugin offer tools using these capabilities", while every resource access a granted
 * capability performs is still decided by Tamias inside the mediated use case. An admission policy
 * never grants access to a resource and never isolates plugin code.
 */
public interface ToolAdmissionPolicy {

    /**
     * Decide about a plugin.
     *
     * @param plugin the plugin identity and requested capabilities
     * @return the decision; never {@code null}
     */
    ToolAdmission evaluate(PluginDescriptor plugin);
}
