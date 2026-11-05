package com.wrath.pakviewer;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
    private final JFrame frame = new JFrame("WrathPakViewer 2.5");
    private final JLabel sourceLabel = new JLabel("No PAK selected");
    private final JLabel status = new JLabel("Choose an Xbox PAK to get started.");
    private final JButton open = new JButton("Open PAK File");
    private final JButton repack = new JButton("Repack ZIP to PAK");
    private final List<JButton> exports = new ArrayList<>();
    private Path source;
    private Path lastOutput;

    public static void main(String[] args) throws IOException {
        if (args.length == 3 && args[0].equals("repack")) {
            repack(Paths.get(args[1]), Paths.get(args[2]));
            return;
        }
        if (args.length == 3 && Arrays.asList(CATEGORIES).contains(args[1])) {
            export(Paths.get(args[0]), args[1], Paths.get(args[2]));
            return;
        }
        if (args.length != 0) throw new IllegalArgumentException(
            "Usage: java -jar WrathPakViewer.jar [input.pak all|models|textures|rigs|animation|audio output.zip] or java -jar WrathPakViewer.jar repack input.zip output.pak");
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
        repack.addActionListener(event -> chooseRepack());
        buttons.add(repack);
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
        lastOutput = output.getParent();
        runOperation(output, "Exporting " + category + "...", () -> {
            export(input, category, output);
            return "Export complete.";
        });
    }

    private void chooseRepack() {
        JFileChooser chooser = new JFileChooser(lastOutput == null ? null : lastOutput.toFile());
        chooser.setDialogTitle("Open an Export Everything ZIP or other.zip");
        chooser.setFileFilter(new FileNameExtensionFilter("ZIP archives (*.zip)", "zip"));
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        Path input = chooser.getSelectedFile().toPath().toAbsolutePath();

        chooser.setDialogTitle("Save rebuilt PAK");
        chooser.resetChoosableFileFilters();
        chooser.setFileFilter(new FileNameExtensionFilter("PAK archives (*.pak)", "pak"));
        String stem = input.getFileName().toString().replaceFirst("(?i)[.]zip$", "");
        chooser.setSelectedFile(input.resolveSibling(stem + "_repacked.pak").toFile());
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        Path selected = chooser.getSelectedFile().toPath().toAbsolutePath();
        Path output = selected.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pak")
                ? selected : selected.resolveSibling(selected.getFileName() + ".pak");
        lastOutput = output.getParent();
        runOperation(output, "Repacking...", () -> {
            WrathRepack.Result result = repack(input, output);
            return "Repack complete. DDS textures changed: " + result.changedTextures + ". Original name: " + result.name;
        });
    }

    private interface Operation {
        String run() throws IOException;
    }

    private void runOperation(Path output, String message, Operation operation) {
        if (Files.exists(output) && JOptionPane.showConfirmDialog(frame,
                "Replace " + output.getFileName() + "?", "Replace export",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        status.setText(message);
        setBusy(true);
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws IOException {
                return operation.run();
            }

            @Override protected void done() {
                setBusy(false);
                try {
                    String result = get();
                    status.setText("Saved " + output.getFileName());
                    JOptionPane.showMessageDialog(frame, result + "\nSaved to " + output, "Complete",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (ExecutionException error) {
                    status.setText("Operation failed.");
                    JOptionPane.showMessageDialog(frame, error.getCause().getMessage(), "Operation failed",
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
        repack.setEnabled(!busy);
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
        write(output, raw -> {
            try (ZipOutputStream zip = new ZipOutputStream(raw)) {
                WrathExports.write(data, source.getFileName().toString(), category, zip);
            }
        });
    }

    private static WrathRepack.Result repack(Path source, Path destination) throws IOException {
        Path output = destination.toAbsolutePath();
        if (source.toAbsolutePath().normalize().equals(output.normalize())
                || (Files.exists(output) && Files.isSameFile(source, output))) {
            throw new IOException("Choose a destination different from the source ZIP.");
        }
        WrathRepack.Result result = WrathRepack.read(source);
        write(output, raw -> raw.write(result.data));
        return result;
    }

    private interface OutputWriter {
        void write(OutputStream out) throws IOException;
    }

    private static void write(Path output, OutputWriter writer) throws IOException {
        Path temporary = Files.createTempFile(output.getParent(), ".wrath-export-", ".tmp");
        try {
            try (OutputStream raw = new BufferedOutputStream(Files.newOutputStream(temporary))) {
                writer.write(raw);
            }
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
