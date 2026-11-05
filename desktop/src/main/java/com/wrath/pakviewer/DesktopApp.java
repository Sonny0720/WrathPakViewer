package com.wrath.pakviewer;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipOutputStream;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

public final class DesktopApp {
    private static final String[] CATEGORIES = {"all", "models", "textures", "rigs", "animation", "audio"};
    private static final String[] LABELS = {
        "Export Everything", "Export Models", "Export Textures", "Export Rigs",
        "Export Animation Data", "Export Audio"
    };
    private final JFrame frame = new JFrame("WrathPakViewer 2.4");
    private final JLabel sourceLabel = new JLabel("No PAK selected");
    private final JLabel status = new JLabel("Choose an Xbox PAK to get started.");
    private final JButton open = new JButton("Open PAK File");
    private final List<JButton> exports = new ArrayList<>();
    private Path source;
    private Path lastOutput;

    public static void main(String[] args) throws IOException {
        if (args.length == 3 && Arrays.asList(CATEGORIES).contains(args[1])) {
            export(Paths.get(args[0]), args[1], Paths.get(args[2]));
            return;
        }
        if (args.length != 0) throw new IllegalArgumentException(
            "Usage: java -jar WrathPakViewer.jar [input.pak all|models|textures|rigs|animation|audio output.zip]");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ignored) {
                // Use Swing's default theme.
            }
            new DesktopApp().show();
        });
    }

    private void show() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.add(open, BorderLayout.WEST);
        header.add(sourceLabel, BorderLayout.CENTER);
        open.addActionListener(event -> chooseSource());
        content.add(header, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 6));
        for (int i = 0; i < CATEGORIES.length; i++) {
            String category = CATEGORIES[i];
            JButton button = new JButton(LABELS[i]);
            button.addActionListener(event -> chooseDestination(category));
            exports.add(button);
            buttons.add(button);
        }
        content.add(buttons, BorderLayout.CENTER);
        content.add(status, BorderLayout.SOUTH);
        setBusy(false);
        frame.setContentPane(content);
        frame.pack();
        frame.setSize(Math.max(520, frame.getWidth()), frame.getHeight());
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void chooseSource() {
        JFileChooser chooser = new JFileChooser(source == null ? null : source.getParent().toFile());
        chooser.setDialogTitle("Open Xbox PAK");
        chooser.setFileFilter(new FileNameExtensionFilter("PAK archives (*.pak)", "pak"));
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        source = chooser.getSelectedFile().toPath().toAbsolutePath();
        sourceLabel.setText(source.getFileName().toString());
        sourceLabel.setToolTipText(source.toString());
        status.setText("Ready to export.");
        setBusy(false);
    }

    private void chooseDestination(String category) {
        Path input = source;
        JFileChooser chooser = new JFileChooser((lastOutput == null ? input.getParent() : lastOutput).toFile());
        chooser.setDialogTitle("Save export ZIP");
        chooser.setFileFilter(new FileNameExtensionFilter("ZIP archives (*.zip)", "zip"));
        String stem = input.getFileName().toString().replaceFirst("(?i)\\.pak$", "");
        chooser.setSelectedFile(new java.io.File(stem + "_" + (category.equals("all") ? "everything" : category) + ".zip"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        Path selected = chooser.getSelectedFile().toPath().toAbsolutePath();
        if (!selected.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            selected = selected.resolveSibling(selected.getFileName() + ".zip");
        }
        Path output = selected;
        if (Files.exists(output) && JOptionPane.showConfirmDialog(frame,
                "Replace " + output.getFileName() + "?", "Replace export",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        lastOutput = output.getParent();
        status.setText("Exporting " + category + "...");
        setBusy(true);
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws IOException {
                export(input, category, output);
                return null;
            }

            @Override protected void done() {
                setBusy(false);
                try {
                    get();
                    status.setText("Export complete.");
                    JOptionPane.showMessageDialog(frame, "Saved to " + output, "Export complete",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (ExecutionException error) {
                    status.setText("Export failed.");
                    JOptionPane.showMessageDialog(frame, error.getCause().getMessage(), "Export failed",
                            JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    status.setText("Export interrupted.");
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        open.setEnabled(!busy);
        for (JButton button : exports) button.setEnabled(!busy && source != null);
        frame.setDefaultCloseOperation(busy ? JFrame.DO_NOTHING_ON_CLOSE : JFrame.EXIT_ON_CLOSE);
    }

    private static void export(Path source, String category, Path destination) throws IOException {
        Path output = destination.toAbsolutePath();
        if (source.toAbsolutePath().normalize().equals(output.normalize())
                || (Files.exists(output) && Files.isSameFile(source, output))) {
            throw new IOException("Choose a destination different from the source PAK.");
        }
        byte[] data = Files.readAllBytes(source);
        if (data.length < 28 || WrathTextures.u32(data, 0) != 0x52049) {
            throw new IOException("Unsupported PAK header. Select an Xbox Wrath Unleashed archive.");
        }
        Path temporary = Files.createTempFile(output.getParent(), ".wrath-export-", ".tmp");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                WrathExports.write(data, source.getFileName().toString(), category, zip);
            }
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
