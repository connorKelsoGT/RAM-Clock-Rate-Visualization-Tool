package com.ramclock;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

/*
GUI controller managing user interation, data loading, and interaction between data parser and visualizer.

User Action -> Method Chain:
1. "Select Directory" clicked -> selectDataDirectory()
2. File chooser opens -> user selects folder
3. loadClockData(directory) called
4. ClockCSVParser.parseDirectory(directory) processes CSVs
5. updateBlockFilters() creates checkboxes
6. updateFileMappingDisplay() shows file assignments
7. refreshVisualization() updates chart
*/

public class RamClockerApp {
    private static Visualizer visualizer;
    private static List<ClockCSVParser.RAMBlockData> loadedData;
    private static JFrame mainFrame;
    private static JPanel blockFilterPanel;
    private static Map<String, JCheckBox> blockCheckBoxes = new HashMap<>();
    private static Map<String, String> fileLabelMap;
    private static JTextArea fileMappingDisplay;

    public static void main(String[] args) {
        /*
        Generates GUI from input directory if provided as command-line argument.
        Otherwise, starts with empty GUI for user to select directory.
        */
        if (args.length > 0) {
            File inputDir = new File(args[0]);
            if (inputDir.isDirectory()) {
                loadClockData(inputDir);
            } else {
                System.err.println("Invalid directory: " + args[0]);
            }
        }

        SwingUtilities.invokeLater(() -> generateGUI());
    }  

    private static void generateGUI() {
        /*
        Builds general GUI layout with main control, filter, and visualization panels.
        1. Control Panel (Right): Buttons for directory selection, sample loading, select/deselect all, refresh.
        2. Filter Panel (Left): Checkboxes for each RAM block to filter displayed data.
        3. Visualization Panel (Center): Chart area for displaying clock rate data.
        4. Mapping Panel (Bottom): Text area showing file-to-block name assignments.
        */
        mainFrame = new JFrame("RAM Block Clocking Visualizer");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(1200, 800);

        JPanel mainPanel = new JPanel(new BorderLayout());
        visualizer = new Visualizer();
        mainPanel.add(visualizer, BorderLayout.CENTER);

        JPanel controlPanel = generateConPanel();
        mainPanel.add(controlPanel, BorderLayout.EAST);

        blockFilterPanel = new JPanel();
        blockFilterPanel.setLayout(new BoxLayout(blockFilterPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(blockFilterPanel);
        scrollPane.setPreferredSize(new Dimension(200, 600));
        mainPanel.add(scrollPane, BorderLayout.WEST);

        JPanel mappingPanel = generateMappingPanel();
        mainPanel.add(mappingPanel, BorderLayout.SOUTH);
        
        mainFrame.setJMenuBar(generateMenuBar());
        mainFrame.add(mainPanel);
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setVisible(true);
    }

    private static JPanel generateConPanel() {
        /*
        Function to generate control panel for basic user actions.
        1. Select Input Directory
        2. Load Sample Data
        3. Select/Deselect All RAM Blocks
        4. Refresh Visualization
        5. Export Chart
        */
        JPanel panel = new JPanel();

        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Controls"));
        panel.setPreferredSize(new Dimension(200, 600));

        JButton selectDir = new JButton("Select Input Directory");
        selectDir.addActionListener(e -> selectDataDirectory());

        JButton loadSample = new JButton("Load Sample");
        loadSample.addActionListener(e -> loadSampleData());

        JButton selectAll = new JButton("Select All");
        selectAll.addActionListener(e -> selectAllBlocks(true));

        JButton deselectAll = new JButton("Deselect All");
        deselectAll.addActionListener(e -> selectAllBlocks(false));

        JButton refresh = new JButton("Refresh Visualization");
        refresh.addActionListener(e -> refreshVisualization());

        JButton export = new JButton("Export Chart");
        export.addActionListener(e -> exportVisualization());

        panel.add(Box.createVerticalStrut(10));
        panel.add(selectDir);
        panel.add(Box.createVerticalStrut(10));
        panel.add(loadSample);
        panel.add(Box.createVerticalStrut(20));
        panel.add(selectAll);
        panel.add(Box.createVerticalStrut(5));
        panel.add(deselectAll);
        panel.add(Box.createVerticalStrut(20));
        panel.add(refresh);
        panel.add(Box.createVerticalStrut(20));
        panel.add(export);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private static JMenuBar generateMenuBar() {
        /*
        Function to generate menu bar with file operations.
        1. Open Directory
        2. Exit Application
        */
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        JMenuItem openDir = new JMenuItem("Open Directory");
        openDir.addActionListener(e -> selectDataDirectory());

        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> System.exit(0));

        fileMenu.add(openDir);
        fileMenu.addSeparator();
        fileMenu.add(exit);
        menuBar.add(fileMenu);
        return menuBar;
    }

    private static JPanel generateMappingPanel() {
        /*
        Function to generate file-to-block mapping display panel.
        1. Text area showing current file assignments to RAM block labels.
        2. Scrollable view for long lists.
        */
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("File to Block Name Mapping"));
        panel.setPreferredSize(new Dimension(1200, 100));

        fileMappingDisplay = new JTextArea(4, 80);
        fileMappingDisplay.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(fileMappingDisplay);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private static void updateFileMappingDisplay() {
        /*
        Updates the file-to-block mapping display area with current assignments.
        1. Clears existing text.
        2. Iterates over fileLabelMap to build display string.
        3. Appends average clock rate and range for each block.
        4. Sets text area content.
        */
        if (fileLabelMap == null || fileLabelMap.isEmpty()) {
            fileMappingDisplay.setText("No files loaded; please select a directory.");
            return;
        }

        StringBuilder mappingText = new StringBuilder();
        mappingText.append("File -> Block  Assignment (sorted by average clock rate):\n");
        mappingText.append("=".repeat(80)).append("\n");

        List<Map.Entry<String, String>> sortedEntries = new ArrayList<>(fileLabelMap.entrySet());
        sortedEntries.sort(Map.Entry.comparingByValue());
        
        for (Map.Entry<String, String> record : sortedEntries) {
            String filename = record.getKey();
            String label = record.getValue();
            
            String additionalInfo = "";
            if (loadedData != null) {
                for (ClockCSVParser.RAMBlockData block : loadedData) {
                    if (block.getSourceFileName().equals(filename)) {
                        double avgRate = calculateAverageRate(block);
                        additionalInfo = String.format(" (Avg: %.2f MHz, Range: %.2f MHz)", 
                            avgRate, calculateRateRange(block));
                        break;
                    }
                }
            }
            
            mappingText.append(String.format("  %s → RAM Block %s%s\n", 
                filename, label, additionalInfo));
        }
        
        fileMappingDisplay.setText(mappingText.toString());

    }

    private static double calculateAverageRate(ClockCSVParser.RAMBlockData block) {
        /*
        Helper function to calculate average clock rate for a RAM block.
        */
        if (block.getClockRateRecords().isEmpty()) return 0;
        double sum = 0;
        for (ClockCSVParser.ClockRateRecord record : block.getClockRateRecords()) {
            sum += record.getClockRate();
        }
        return sum / block.getClockRateRecords().size();
    }

    private static double calculateRateRange(ClockCSVParser.RAMBlockData block) {
        /*
        Helper function to calculate clock rate range for a RAM block.  
        */
        if (block.getClockRateRecords().isEmpty()) return 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (ClockCSVParser.ClockRateRecord record : block.getClockRateRecords()) {
            min = Math.min(min, record.getClockRate());
            max = Math.max(max, record.getClockRate());
        }
        return max - min;
    }

    private static void selectDataDirectory() {
        /*
        Handles user action to select input directory.
        1. Opens file chooser dialog.
        2. On selection, calls loadClockData(directory).
        */
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Select Directory with RAM Clocking Data");

        int returnValue = fileChooser.showOpenDialog(mainFrame);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedDir = fileChooser.getSelectedFile();
            loadClockData(selectedDir);
        }
    }

    private static void loadClockData(File directory) {
        /*
        Loads clocking data from a directory.
        1. Parses all CSV files in the directory.
        2. Stores parsed data in loadedData and fileLabelMap.
        3. Updates UI components to reflect loaded data.
        */
        try {
            ClockCSVParser.ParsedResult result = ClockCSVParser.parseDirectory(directory);
            
            loadedData = result.getBlockDataList();
            fileLabelMap = result.getFileLabelMap();
            
            updateBlockFilters();
            updateFileMappingDisplay(); 
            refreshVisualization();
            
            mainFrame.setTitle("RAM Block Clocking Visualizer - " + directory.getName());
            
            JOptionPane.showMessageDialog(mainFrame, 
                String.format("Loaded %d RAM blocks from %d files:\n%s",
                    loadedData.size(),
                    fileLabelMap.size(),
                    getMappingSummary()),
                "Data Loaded", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainFrame, "Error loading data: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String getMappingSummary() {
        /*
        Maps file-to-block assignments into a summary string for display.
        1. Iterates over fileLabelMap to build summary string.
        2. Formats each entry as "  • filename → label".
        */
        if (fileLabelMap == null) return "";
        
        StringBuilder summary = new StringBuilder();
        summary.append("\n");
        
        List<String> labels = new ArrayList<>(fileLabelMap.values());
        Collections.sort(labels);
        
        for (String label : labels) {
            for (Map.Entry<String, String> entry : fileLabelMap.entrySet()) {
                if (entry.getValue().equals(label)) {
                    summary.append(String.format("  • %s → %s\n", entry.getKey(), label));
                    break;
                }
            }
        }
        
        return summary.toString();
    }

    private static void loadSampleData() {
        /*
        One-click function to load built-in sample data.
        1. Checks for existence of sample_data directory.
        2. If not present, creates sample CSV files.
        3. Loads sample data into application.
        4. Updates UI components accordingly.        
        */
        try {
            File sampleDir = new File("sample_data");
            if(!sampleDir.exists() || !sampleDir.isDirectory()) {
                createSampleData(sampleDir);
            }
            loadClockData(sampleDir);
            mainFrame.setTitle("Ram Block Clocking Visualizer - Sample Data");
            JOptionPane.showMessageDialog(mainFrame, "Sample data loaded successfully.",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainFrame, "Error loading sample data: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void updateBlockFilters() {
        /*
        Sets up checkbox filters for laoded data/
        1. Clears existing checkboxes.
        2. Creates new checkbox for each RAM block in loadedData.
        3. Adds action listener to each checkbox to refresh visualization on change.
        4. Refreshes panel display.
        */
        blockFilterPanel.removeAll();
        blockCheckBoxes.clear();

        if (loadedData == null || loadedData.isEmpty()) {
            blockFilterPanel.add(new JLabel("No data loaded."));
            return;
        }

        List<String> blockNames = new ArrayList<>();
        for (ClockCSVParser.RAMBlockData record : loadedData) {
            blockNames.add(record.getBlockName());
        }
        Collections.sort(blockNames);

        for (String blockName : blockNames) {
            JCheckBox checkBox = new JCheckBox(blockName, true);
            checkBox.addActionListener(e -> refreshVisualization());
            blockCheckBoxes.put(blockName, checkBox);
            blockFilterPanel.add(checkBox);
        }

        blockFilterPanel.revalidate();
        blockFilterPanel.repaint();
    }

    private static void selectAllBlocks(boolean select) {
        /*
        Handles select/deselect all RAM block checkboxes.
        1. Iterates over all checkboxes to set selected state.
        2. Calls refreshVisualization() to update chart.
        */
        for (JCheckBox checkBox : blockCheckBoxes.values()) {
            checkBox.setSelected(select);
        }
        refreshVisualization();
    }

    private static void refreshVisualization() {
        /*
        Changes visualizer data based on selected RAM blocks and loaded data.
        1. Gathers list of selected RAM block names.
        2. Calls visualizer.setData() with loadedData and selected block names.
        */
        if (loadedData == null || visualizer == null) return;

        List<String> selectedBlocks = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> entry : blockCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selectedBlocks.add(entry.getKey());
            }
        }

        visualizer.setData(loadedData, selectedBlocks);
    }

    private static void exportVisualization() {
        if (visualizer == null) {
            JOptionPane.showMessageDialog(mainFrame, 
                "No visualization to export. Load data first.",
                "Export Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Chart As Image");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "PNG Image Files", "png"));
        fileChooser.setSelectedFile(new File("ram_clocking_chart.png"));
        
        int result = fileChooser.showSaveDialog(mainFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String filePath = selectedFile.getAbsolutePath();

            if (!filePath.toLowerCase().endsWith(".png")) {
                filePath = filePath + ".png";
            }
            
            try {
                visualizer.exportAsImage(filePath);
                JOptionPane.showMessageDialog(mainFrame, 
                    "Chart saved successfully to:\n" + filePath,
                    "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(mainFrame, 
                    "Error saving chart: " + e.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void createSampleData(File sampleDir) throws Exception{
        /*
        Generates random sample CSV files for demonstration.
        1. Creates sample_data directory.
        2. Generates 4 sample CSV files (block_A.csv to block_D.csv).
        3. Each file contains 100 timestamped clock rate entries with random variations.
        4. Writes generated data to respective CSV files.
        */
       sampleDir.mkdir();

       for (char block = 'A'; block <= 'D'; block++) {
           String blockName = String.valueOf(block);
           File sampleBlock = new File(sampleDir, "block_" + blockName + ".csv");

           StringBuilder sampleRecorder = new StringBuilder();
           sampleRecorder.append("timestamp,clock_rate_mhz\n");
           Random rand = new Random(block);

           for (int i = 0; i < 100; i++) {
               double baseRate = 1600 - (block - 'A') * 50;
               double variation = rand.nextGaussian() * 10;
               double clockRate = baseRate + variation;
               sampleRecorder.append(i).append(String.format(",%.2f", clockRate)).append("\n");
            }

            java.nio.file.Files.write(sampleBlock.toPath(), sampleRecorder.toString().getBytes());
       }
    } 
}
