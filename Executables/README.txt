RAM Block Clock Rate Visualization Tool:

What this tool does:
Interactive visualization for analysis of RAM block clocking data from CSV files.

Key Features:
1. Automatic Data Processing - Loads CSV files, auto-labels RAM blocks (A, B, C...)
	- Expects CSVs with two columns ("timestamp" and "clock_rate")
2. Interactive Filtering - Checkbox controls for selecting RAM blocks
3. Exportable Visualization - Multi-line time-series charts exportable as PNG files
4. Sample Data Generation - Built-in test data for demonstration purposes

Quick Start:
1. Install Java 17 or later (if not already installed)
2. Double-click RAMClockRateMainExecutable.jar
3. Click "Load Sample" or select your data directory

Technical Details:
Language: Java 17
GUI Framework: Swing
Charting: JFreeChart
Build Tool: Maven
