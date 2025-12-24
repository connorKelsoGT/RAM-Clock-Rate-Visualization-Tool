package com.ramclock;

import org.jfree.chart.*;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.*;
import org.jfree.data.xy.*;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.io.File;
/*
Renders interactive multi-line charts of RAM block clock rates over time using JFreeChart.
*/
public class Visualizer extends JPanel{
    private ChartPanel chartPanel;
    private JFreeChart chart;
    private XYSeriesCollection dataset;

    public Visualizer() {
        setLayout(new BorderLayout());
        initializeChart();
    }

    private void initializeChart() {
        /*
        Creates the initial empty chart with axes and gridlines.
        1. Initializes an empty XYSeriesCollection dataset.
        2. Creates an XY line chart with titles and labels.
        3. Configures plot appearance (background, gridlines).
        4. Sets up the ChartPanel for display and interaction.
        5. Adds the ChartPanel to the Visualizer JPanel.
        */
        dataset = new XYSeriesCollection();
        chart = ChartFactory.createXYLineChart(
                "RAM Block Clock Rates",
                "Time (Cycles)",
                "Clock Rate (MHz)",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        plot.setRenderer(renderer);
        NumberAxis xAxis = (NumberAxis) plot.getDomainAxis();
        xAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        yAxis.setLabel("Clock Rate (MHz)");

        chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(800, 600));
        chartPanel.setMouseWheelEnabled(true);
        add(chartPanel, BorderLayout.CENTER);
    }

    public void setData(List<ClockCSVParser.RAMBlockData> allData, List<String> selectedBlocks) {
        /*
        Configures the chart to display clock rate data for selected RAM blocks.
        1. Clears existing data series from the dataset.
        2. If no data or no blocks selected, updates title and exits.
        3. Defines a color palette for line series.
        4. Iterates over all RAM block data:
            a. If the block is selected, creates a new XYSeries.
            b. Adds timestamp and clock rate data points to the series.
            c. Adds the series to the dataset.
            d. Assigns a color from the palette to the series.
        5. Updates the chart title to reflect the number of selected blocks.
        6. Repaints the chart panel to reflect changes.
        */
        dataset.removeAllSeries();

        if(allData == null || allData.isEmpty() || selectedBlocks.isEmpty()) {
            chart.setTitle("RAM Block Clock Rates - No Data Selected");
            return;
        }
        
        Color[] colors = {Color.RED, Color.BLUE, Color.GREEN, Color.MAGENTA, Color.ORANGE, Color.CYAN, Color.PINK, Color.YELLOW,
            new Color(128, 0, 128) /* Purple */, new Color(0, 128, 128) /* Teal */, new Color(128, 128, 0) /* Olive */};
    
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();

        int colorIndex = 0;

        for (ClockCSVParser.RAMBlockData blockData : allData) {
            if (selectedBlocks.contains(blockData.getBlockName())) {
                XYSeries series = new XYSeries(blockData.getBlockName());
                
                for (ClockCSVParser.ClockRateRecord dp : blockData.getClockRateRecords()) {
                    series.add(dp.getTimestamp(), dp.getClockRate());
                }
                dataset.addSeries(series);
                
                if (colorIndex < colors.length) {
                    renderer.setSeriesPaint(dataset.getSeriesCount() - 1, colors[colorIndex % colors.length]);
                }
                colorIndex++;
            }
        }

        chart.setTitle("RAM Block Clock Rates - Selected Blocks: " + selectedBlocks.size());

        chartPanel.repaint();
    }

    public void exportAsImage(String filePath) {
        /*
        Exports the current chart as a PNG image to the specified file path.
        1. Checks if the file path ends with .png, appends if necessary.
        2. Uses ChartUtils to save the chart as a PNG file with specified dimensions.
        3. Catches exceptions and shows error dialog if export fails.
        */
        try {
            File file = new File(filePath);
            if (!filePath.toLowerCase().endsWith(".png")) {
                file = new File(filePath + ".png");
            }
            ChartUtils.saveChartAsPNG(file, chart, 1200, 800);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error exporting image: " + e.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
