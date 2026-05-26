package com.aresstack.corenth.proasteion.exedra.toolbar;

import com.aresstack.corenth.proasteion.exedra.command.CommandRegistry;
import com.aresstack.corenth.proasteion.exedra.command.ShellCommand;
import org.junit.Assume;
import org.junit.Test;

import javax.swing.JButton;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * Verifies that toolbar button clicks route through {@link CommandRegistry#execute},
 * so execution listeners are notified (and events like CommandExecutedEvent are emitted).
 */
public class ConfigurableToolbarExecutionTest {

    @Test
    public void toolbarButton_triggersExecutionListener() {
        Assume.assumeFalse("Requires a display", GraphicsEnvironment.isHeadless());

        CommandRegistry commands = new CommandRegistry();
        AtomicBoolean performed = new AtomicBoolean(false);

        ShellCommand testCmd = new ShellCommand() {
            @Override public String getId() { return "test.action"; }
            @Override public String getLabel() { return "Test"; }
            @Override public void perform() { performed.set(true); }
            @Override public boolean isEnabled() { return true; }
            @Override public boolean isToolbarVisible() { return true; }
            @Override public String getIconText() { return "T"; }
        };
        commands.register(testCmd);

        List<ShellCommand> executedCommands = new ArrayList<>();
        commands.addExecutionListener(executedCommands::add);

        ToolbarCommandRegistry toolbarRegistry = new ToolbarCommandRegistry(commands);
        ToolbarConfigRepository repo = new ToolbarConfigRepository() {
            @Override public ToolbarConfig load(ToolbarConfig defaultConfig) { return defaultConfig; }
            @Override public void save(ToolbarConfig config) { }
        };

        ConfigurableToolbar toolbar = new ConfigurableToolbar(toolbarRegistry, repo);

        // Find the button and simulate a click
        Component[] components = toolbar.getComponents();
        assertTrue("Toolbar should have at least one button", components.length > 0);
        JButton button = (JButton) components[0];
        button.doClick();

        // The command should have been performed
        assertTrue("Command should have been performed", performed.get());
        // The execution listener should have been notified
        assertEquals("Execution listener should have been called once", 1, executedCommands.size());
        assertEquals("test.action", executedCommands.get(0).getId());
    }
}
