package com.aresstack.corenth.proasteion.exedra.shell.commands;

import com.aresstack.corenth.proasteion.exedra.command.ShellCommand;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

/**
 * Shows a generic About dialog. Registered as {@code "help.about"}.
 */
public final class AboutCommand implements ShellCommand {

    private final JFrame frame;
    private final String appName;
    private final String version;

    public AboutCommand(JFrame frame, String appName, String version) {
        this.frame = frame;
        this.appName = appName;
        this.version = version;
    }

    @Override public String getId() { return "help.about"; }
    @Override public String getLabel() { return "About"; }

    @Override
    public void perform() {
        JOptionPane.showMessageDialog(frame,
                appName + "\nVersion: " + version,
                "About " + appName, JOptionPane.INFORMATION_MESSAGE);
    }
}
