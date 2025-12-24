package com.ramclock;

import org.apache.commons.csv.*;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/*
For each CSV file found:
  1. Parse all clock rate values
  2. Calculate statistics:
     - Average clock rate
     - Minimum clock rate  
     - Maximum clock rate
     - Data point count
  
  3. Sort files by average clock rate (highest to lowest)
  
  4. Assign labels:
     Highest average → "A"
     Second highest → "B"
     Third highest → "C"
     ... and so on
     
  5. If >26 files: "AA", "AB", "AC", etc.

  Expects Directory of CSV files, each with 2 columns: timestamp, clock rate (MHz)
*/

public class ClockCSVParser {
    public static class ParsedResult {
        private List<RAMBlockData> blockDataList;
        private Map<String, String> fileLabelMap;

        public ParsedResult(List<RAMBlockData> blockDataList, Map<String, String> fileLabelMap) {
            this.blockDataList = blockDataList;
            this.fileLabelMap = fileLabelMap;
        }

        public List<RAMBlockData> getBlockDataList() {
            return blockDataList;
        }

        public Map<String, String> getFileLabelMap() {
            return fileLabelMap;
        }
    }

    public static ParsedResult parseDirectory(File directory) throws IOException {
        /*
        Parses all CSV files in the given directory and returns structured RAM block data along with file-to-label mappings.
        1. Validates the directory.
        2. Iterates over each CSV file, parsing clock rate records and computing statistics.
        3. Assigns labels based on average clock rates.
        4. Returns a ParsedResult containing RAM block data and label mappings.
        */
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("Not a valid directory: " + directory.getAbsolutePath());
        }

        Collection<File> csvFiles = FileUtils.listFiles(directory, new String[]{"csv"}, false);

        if (csvFiles.isEmpty()) {
            throw new IllegalArgumentException("No CSV files found in directory: " + directory.getAbsolutePath());
        }

        List<FileData> allFileData = new ArrayList<>();

        for (File csvFile : csvFiles) {
            try {
                FileData fileData = parseCSVFile(csvFile);
                allFileData.add(fileData);
            } catch (Exception e) {
                System.err.println("Error parsing file " + csvFile.getName() + ": " + e.getMessage());
            }
        }

        List<RAMBlockData> blockDataList = new ArrayList<>();
        Map<String, String> fileLabelMap = assignLabels(allFileData);
        
        for (FileData fileData : allFileData) {
            RAMBlockData blockData = new RAMBlockData();
            String label = fileLabelMap.get(fileData.getFileName());
            blockData.setBlockName(label);
            blockData.setSourceFileName(fileData.getFileName());  // Store original filename
            blockData.setClockRateRecords(fileData.getRecords());
            blockDataList.add(blockData);
        }
        
        return new ParsedResult(blockDataList, fileLabelMap);
    }

    private static FileData parseCSVFile(File csvFile) throws IOException {
        /*
        Parses a single CSV file into a FileData object containing records and statistics.
        1. Reads the CSV file using Apache Commons CSV.
        2. Extracts timestamp and clock rate values from each record.
        3. Computes statistics (min, max, average, count) for the clock rates
        */
        FileData fileData = new FileData();
        fileData.setFileName(csvFile.getName());

        List<ClockRateRecord> records = new ArrayList<>();
        BlockStatistics stats = new BlockStatistics();

        try (Reader reader = new FileReader(csvFile, StandardCharsets.UTF_8)) {
            CSVFormat format = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withIgnoreHeaderCase()
                    .withTrim()
                    .withIgnoreEmptyLines();
            CSVParser parser = new CSVParser(reader, format);

            for (CSVRecord csvRecord : parser) {
                ClockRateRecord record = new ClockRateRecord();

                long timestamp = getLongValue(csvRecord, "timestamp", "time", "cycle", "index", "time_index");
                double clockRate = getDoubleValue(csvRecord, "clock_rate", "rate", "mhz", "frequency", "clock", "clock_rate_mhz");
        
                record.setTimestamp(timestamp);
                record.setClockRate(clockRate);
                records.add(record);
                stats.update(clockRate);
            }
        }


        fileData.setRecords(records);
        fileData.setStats(stats);
        return fileData;
    }

    private static Map<String, String> assignLabels(List<FileData> fileDataList) {
        /*
        Assigns labels to files based on average clock rates.
        1. Sorts files by average clock rate in descending order.
        2. Assigns labels "A", "B", ..., "Z", "AA", "AB", etc. based on rank.
        3. Returns a mapping of file names to assigned labels.
        */
        Map<String, String> fileLabelMap = new HashMap<>();
        
        if (fileDataList.isEmpty()) {
            return fileLabelMap;
        }

        fileDataList.sort((f1, f2) -> Double.compare(f2.getStats().getAverage(), f1.getStats().getAverage()));

        char currentLabel = 'A';
        for (FileData fileData : fileDataList) {
            if (currentLabel > 'Z') {
                String label = "A" + (char)('A' + ((currentLabel - 'A') % 26));
                fileLabelMap.put(fileData.getFileName(), label);
            } else {
                fileLabelMap.put(fileData.getFileName(), String.valueOf(currentLabel));
            }
            currentLabel++;
        }
        return fileLabelMap;
    }

    private static class FileData {
        /*
        Represents a parsed CSV file with its associated clock rate records and statistics.
        */
        private String fileName;
        private List<ClockRateRecord> records;
        private BlockStatistics stats;

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public List<ClockRateRecord> getRecords() {
            return records;
        }

        public void setRecords(List<ClockRateRecord> records) {
            this.records = records;
        }

        public BlockStatistics getStats() {
            return stats;
        }

        public void setStats(BlockStatistics stats) {
            this.stats = stats;
        }
    }

    private static class BlockStatistics {
        /*
        Represents statistical data for a RAM block's clock rates.
        Tracks sum, min, max, and count of clock rates.
        */
        private double sum = 0.;
        private double min = Double.MAX_VALUE;
        private double max = Double.MIN_VALUE;
        private int count = 0;

        public void update(double clockRate) {
            sum += clockRate;
            min = Math.min(min, clockRate);
            max = Math.max(max, clockRate);
            count++;
        }

        public double getAverage() {
            return count > 0 ? sum / count : 0; 
        }

        public double getMin() { return min; }
        public double getMax() { return max; }
        public double getRange() { return max - min; }
        public int getCount() { return count; }
    }

    public static class RAMBlockData {
        /*
        Represents the clock rate data for a specific RAM block.
        Contains the block name, source file name, and a list of clock rate records.
        */
        private String blockName;
        private String sourceFileName;
        private List<ClockRateRecord> clockRateRecords;

        public String getBlockName() { return blockName; }
        public void setBlockName(String blockName) { this.blockName = blockName; }
        public String getSourceFileName() { return sourceFileName; }
        public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }
        public List<ClockRateRecord> getClockRateRecords() { return clockRateRecords; }
        public void setClockRateRecords(List<ClockRateRecord> clockRateRecords) { this.clockRateRecords = clockRateRecords; }

    }

    public static class ClockRateRecord {
        /*
        Represents a single clock rate measurement at a specific timestamp.
        */
        private long timestamp;
        private double clockRate;

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public double getClockRate() { return clockRate; }
        public void setClockRate(double clockRate) { this.clockRate = clockRate; }
    }

    private static long getLongValue(CSVRecord record, String... possibleNames) {
        /*
        Helper function to extract a long value from a CSVRecord given multiple possible column names.
        1. Iterates over possible column names.
        2. Returns the first successfully parsed long value.
        3. Returns 0 if none found or parsable.
        */
        for (String name : possibleNames) {
            if (record.isSet(name)) {
                try {
                    return Long.parseLong(record.get(name));
                } catch (NumberFormatException e) {
                    // Try next name
                }
            }
        }
        return 0;
    }
    
    private static double getDoubleValue(CSVRecord record, String... possibleNames) {
        /*
        Helper function to extract a double value from a CSVRecord given multiple possible column names.
        1. Iterates over possible column names.
        2. Returns the first successfully parsed double value.
        3. Returns 0.0 if none found or parsable.
        */
        for (String name : possibleNames) {
            if (record.isSet(name)) {
                try {
                    return Double.parseDouble(record.get(name));
                } catch (NumberFormatException e) {
                    // Try next name
                }
            }
        }
        return 0.;
    }
}
