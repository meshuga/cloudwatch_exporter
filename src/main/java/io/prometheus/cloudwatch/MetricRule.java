package io.prometheus.cloudwatch;

import java.util.List;
import java.util.Map;

public class MetricRule {
    private String awsNamespace;
    private String awsMetricName;
    private int periodSeconds;
    private int rangeSeconds;
    private int delaySeconds;
    private List<String> awsStatistics;
    private List<String> awsExtendedStatistics;
    private List<String> awsDimensions;
    private Map<String, List<String>> awsDimensionSelect;
    private Map<String, List<String>> awsDimensionSelectRegex;
    private String help;
    boolean cloudwatchTimestamp;

    public String getAwsNamespace() {
        return awsNamespace;
    }

    public MetricRule setAwsNamespace(final String awsNamespace) {
        this.awsNamespace = awsNamespace;
        return this;
    }

    public String getAwsMetricName() {
        return awsMetricName;
    }

    public MetricRule setAwsMetricName(final String awsMetricName) {
        this.awsMetricName = awsMetricName;
        return this;
    }

    public int getPeriodSeconds() {
        return periodSeconds;
    }

    public MetricRule setPeriodSeconds(final int periodSeconds) {
        this.periodSeconds = periodSeconds;
        return this;
    }

    public int getRangeSeconds() {
        return rangeSeconds;
    }

    public MetricRule setRangeSeconds(final int rangeSeconds) {
        this.rangeSeconds = rangeSeconds;
        return this;
    }

    public int getDelaySeconds() {
        return delaySeconds;
    }

    public MetricRule setDelaySeconds(final int delaySeconds) {
        this.delaySeconds = delaySeconds;
        return this;
    }

    public List<String> getAwsStatistics() {
        return awsStatistics;
    }

    public MetricRule setAwsStatistics(final List<String> awsStatistics) {
        this.awsStatistics = awsStatistics;
        return this;
    }

    public List<String> getAwsExtendedStatistics() {
        return awsExtendedStatistics;
    }

    public MetricRule setAwsExtendedStatistics(final List<String> awsExtendedStatistics) {
        this.awsExtendedStatistics = awsExtendedStatistics;
        return this;
    }

    public List<String> getAwsDimensions() {
        return awsDimensions;
    }

    public MetricRule setAwsDimensions(final List<String> awsDimensions) {
        this.awsDimensions = awsDimensions;
        return this;
    }

    public Map<String, List<String>> getAwsDimensionSelect() {
        return awsDimensionSelect;
    }

    public MetricRule setAwsDimensionSelect(final Map<String, List<String>> awsDimensionSelect) {
        this.awsDimensionSelect = awsDimensionSelect;
        return this;
    }

    public Map<String, List<String>> getAwsDimensionSelectRegex() {
        return awsDimensionSelectRegex;
    }

    public MetricRule setAwsDimensionSelectRegex(final Map<String, List<String>> awsDimensionSelectRegex) {
        this.awsDimensionSelectRegex = awsDimensionSelectRegex;
        return this;
    }

    public String getHelp() {
        return help;
    }

    public MetricRule setHelp(final String help) {
        this.help = help;
        return this;
    }

    public boolean isCloudwatchTimestamp() {
        return cloudwatchTimestamp;
    }

}
