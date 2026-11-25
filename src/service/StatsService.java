package service;

import java.util.*;
import java.util.stream.Collectors;

// Analytical service calculating distribution, quartiles, and frequency metrics for chart visualizations.
public class StatsService {

    public static class HistogramBin {
        private final String label;
        private final double minVal;
        private final double maxVal;
        private final long count;

        public HistogramBin(String label, double minVal, double maxVal, long count) {
            this.label = label;
            this.minVal = minVal;
            this.maxVal = maxVal;
            this.count = count;
        }

        public String getLabel() { return label; }
        public double getMinVal() { return minVal; }
        public double getMaxVal() { return maxVal; }
        public long getCount() { return count; }
    }

    public static class FrequencyItem {
        private final String value;
        private final long count;
        private final double percentage;

        public FrequencyItem(String value, long count, double percentage) {
            this.value = value;
            this.count = count;
            this.percentage = percentage;
        }

        public String getValue() { return value; }
        public long getCount() { return count; }
        public double getPercentage() { return percentage; }
    }

    public static class ColumnStats {
        private final String columnName;
        private final int totalCount;
        private final long distinctCount;
        private final boolean hasDuplicates;
        private final boolean isNumeric;
        private final Double average;
        private final Double min;
        private final Double max;
        private final Double median;
        private final Double q1;
        private final Double q3;
        private final Double stdDev;
        private final List<FrequencyItem> topFrequent;
        private final List<HistogramBin> histogramBins;

        public ColumnStats(String columnName, int totalCount, long distinctCount, boolean hasDuplicates,
                           boolean isNumeric, Double average, Double min, Double max,
                           Double median, Double q1, Double q3, Double stdDev,
                           List<FrequencyItem> topFrequent, List<HistogramBin> histogramBins) {
            this.columnName = columnName;
            this.totalCount = totalCount;
            this.distinctCount = distinctCount;
            this.hasDuplicates = hasDuplicates;
            this.isNumeric = isNumeric;
            this.average = average;
            this.min = min;
            this.max = max;
            this.median = median;
            this.q1 = q1;
            this.q3 = q3;
            this.stdDev = stdDev;
            this.topFrequent = topFrequent;
            this.histogramBins = histogramBins;
        }

        public String getColumnName() { return columnName; }
        public int getTotalCount() { return totalCount; }
        public long getDistinctCount() { return distinctCount; }
        public boolean hasDuplicates() { return hasDuplicates; }
        public boolean isNumeric() { return isNumeric; }
        public Double getAverage() { return average; }
        public Double getMin() { return min; }
        public Double getMax() { return max; }
        public Double getMedian() { return median; }
        public Double getQ1() { return q1; }
        public Double getQ3() { return q3; }
        public Double getStdDev() { return stdDev; }
        public List<FrequencyItem> getTopFrequent() { return topFrequent; }
        public List<HistogramBin> getHistogramBins() { return histogramBins; }
    }

    /**
     * Calculates statistical distribution and chart data for the selected column.
     */
    public ColumnStats computeStats(List<String> headers, List<List<String>> rows, String colName) {
        int colIndex = -1;
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).equalsIgnoreCase(colName.trim())) {
                colIndex = i;
                break;
            }
        }

        if (colIndex == -1 || rows == null || rows.isEmpty()) {
            return new ColumnStats(colName, 0, 0, false, false,
                    null, null, null, null, null, null, null,
                    Collections.emptyList(), Collections.emptyList());
        }

        final int idx = colIndex;

        // Extract column values
        List<String> values = rows.stream()
                .map(row -> (idx < row.size()) ? row.get(idx) : "")
                .collect(Collectors.toList());

        int totalCount = values.size();
        long distinctCount = values.stream().distinct().count();

        // Duplicate check via Set
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (String val : values) {
            if (!seen.add(val)) {
                duplicates.add(val);
            }
        }
        boolean hasDuplicates = !duplicates.isEmpty();

        // Numeric evaluation
        boolean isNumeric = !values.isEmpty() && values.stream()
                .allMatch(v -> v != null && v.matches("^-?\\d+(\\.\\d+)?$"));

        Double average = null;
        Double min = null;
        Double max = null;
        Double median = null;
        Double q1 = null;
        Double q3 = null;
        Double stdDev = null;
        List<HistogramBin> bins = new ArrayList<>();

        if (isNumeric) {
            List<Double> numList = values.stream()
                    .filter(v -> !v.isBlank())
                    .map(Double::parseDouble)
                    .sorted()
                    .collect(Collectors.toList());

            if (!numList.isEmpty()) {
                min = numList.get(0);
                max = numList.get(numList.size() - 1);
                double sum = numList.stream().mapToDouble(Double::doubleValue).sum();
                average = sum / numList.size();

                median = computePercentile(numList, 50.0);
                q1 = computePercentile(numList, 25.0);
                q3 = computePercentile(numList, 75.0);

                final double meanVal = average;
                double variance = numList.stream()
                        .mapToDouble(v -> Math.pow(v - meanVal, 2))
                        .average()
                        .orElse(0.0);
                stdDev = Math.sqrt(variance);

                // Build 6 to 8 histogram bins for numeric distribution curve
                int numBins = Math.min(8, Math.max(4, (int) Math.sqrt(distinctCount)));
                double range = Math.max(1.0, max - min);
                double binWidth = range / numBins;

                for (int b = 0; b < numBins; b++) {
                    final double bStart = min + (b * binWidth);
                    final double bEnd = (b == numBins - 1) ? max : bStart + binWidth;
                    final boolean isLast = (b == numBins - 1);
                    long count = numList.stream()
                            .filter(v -> isLast ? (v >= bStart && v <= bEnd) : (v >= bStart && v < bEnd))
                            .count();
                    String label = String.format(Locale.US, "%.0f - %.0f", bStart, bEnd);
                    bins.add(new HistogramBin(label, bStart, bEnd, count));
                }
            }
        }

        // Frequency distribution for discrete categories
        Map<String, Long> frequencyMap = values.stream()
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

        List<FrequencyItem> topFrequent = frequencyMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(10)
                .map(e -> {
                    double pct = (totalCount > 0) ? (e.getValue() * 100.0 / totalCount) : 0.0;
                    return new FrequencyItem(e.getKey(), e.getValue(), Math.round(pct * 10.0) / 10.0);
                })
                .collect(Collectors.toList());

        return new ColumnStats(colName, totalCount, distinctCount, hasDuplicates, isNumeric,
                average, min, max, median, q1, q3, stdDev, topFrequent, bins);
    }

    private double computePercentile(List<Double> sortedList, double percentile) {
        if (sortedList.isEmpty()) return 0.0;
        int index = (int) Math.ceil((percentile / 100.0) * sortedList.size()) - 1;
        return sortedList.get(Math.max(0, Math.min(index, sortedList.size() - 1)));
    }
}
