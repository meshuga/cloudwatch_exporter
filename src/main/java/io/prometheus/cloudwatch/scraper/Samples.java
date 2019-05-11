package io.prometheus.cloudwatch.scraper;

import com.amazonaws.services.cloudwatch.model.Datapoint;
import io.prometheus.client.Collector;
import io.prometheus.cloudwatch.MetricRule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Samples {
    private String baseName;
    private List<Collector.MetricFamilySamples.Sample> sumSamples = new ArrayList<>();
    private List<Collector.MetricFamilySamples.Sample> sampleCountSamples = new ArrayList<>();
    private List<Collector.MetricFamilySamples.Sample> minimumSamples = new ArrayList<>();
    private List<Collector.MetricFamilySamples.Sample> maximumSamples = new ArrayList<>();
    private List<Collector.MetricFamilySamples.Sample> averageSamples = new ArrayList<>();
    private Map<String, List<Collector.MetricFamilySamples.Sample>> extendedSamples = new HashMap<>();

    public Samples(final String baseName) {
        this.baseName = baseName;
    }

    public Samples(final String baseName,
                   final Datapoint dp,
                   final List<String> labelNames,
                   final List<String> labelValues,
                   final MetricRule rule) {
        this.baseName = baseName;

        Long timestamp = null;
        if (rule.isCloudwatchTimestamp()) {
          timestamp = dp.getTimestamp().getTime();
        }

        if (dp.getSum() != null) {
            sumSamples.add(new Collector.MetricFamilySamples.Sample(
                    baseName + "_sum", labelNames, labelValues, dp.getSum(), timestamp));
        }
        if (dp.getSampleCount() != null) {
            sampleCountSamples.add(new Collector.MetricFamilySamples.Sample(
                    baseName + "_sample_count", labelNames, labelValues, dp.getSampleCount(), timestamp));
        }
        if (dp.getMinimum() != null) {
            minimumSamples.add(new Collector.MetricFamilySamples.Sample(
                    baseName + "_minimum", labelNames, labelValues, dp.getMinimum(), timestamp));
        }
        if (dp.getMaximum() != null) {
            maximumSamples.add(new Collector.MetricFamilySamples.Sample(
                    baseName + "_maximum", labelNames, labelValues, dp.getMaximum(), timestamp));
        }
        if (dp.getAverage() != null) {
            averageSamples.add(new Collector.MetricFamilySamples.Sample(
                    baseName + "_average", labelNames, labelValues, dp.getAverage(), timestamp));
        }
        if (dp.getExtendedStatistics() != null) {
        	final Long tmstmp = timestamp;
            dp.getExtendedStatistics().forEach((key, value) -> extendedSamples
                .computeIfAbsent(key, k -> new ArrayList<>())
                .add(new Collector.MetricFamilySamples.Sample(
                        baseName + "_" + Common.safeName(Common.toSnakeCase(key)), labelNames, labelValues, value, tmstmp)));
        }
    }

    public List<Collector.MetricFamilySamples> partialMfs(final String unit, final MetricRule rule) {
        List<Collector.MetricFamilySamples> partialMfs = new ArrayList<>();
        if (!sumSamples.isEmpty()) {
            partialMfs.add(new Collector.MetricFamilySamples(baseName + "_sum",
                    Collector.Type.GAUGE, help(rule, unit, "Sum"), sumSamples));
        }
        if (!sampleCountSamples.isEmpty()) {
            partialMfs.add(new Collector.MetricFamilySamples(baseName + "_sample_count",
                    Collector.Type.GAUGE, help(rule, unit, "SampleCount"), sampleCountSamples));
        }
        if (!minimumSamples.isEmpty()) {
            partialMfs.add(new Collector.MetricFamilySamples(baseName + "_minimum",
                    Collector.Type.GAUGE, help(rule, unit, "Minimum"), minimumSamples));
        }
        if (!maximumSamples.isEmpty()) {
            partialMfs.add(new Collector.MetricFamilySamples(baseName + "_maximum",
                    Collector.Type.GAUGE, help(rule, unit, "Maximum"), maximumSamples));
        }
        if (!averageSamples.isEmpty()) {
            partialMfs.add(new Collector.MetricFamilySamples(baseName + "_average",
                    Collector.Type.GAUGE, help(rule, unit, "Average"), averageSamples));
        }
        extendedSamples.forEach((key, value) -> partialMfs.add(new Collector.MetricFamilySamples(
                baseName + "_" + Common.safeName(Common.toSnakeCase(key)),
                Collector.Type.GAUGE, help(rule, unit, key), value)));
        return partialMfs;
    }

    private String help(MetricRule rule, String unit, String statistic) {
        if (rule.getHelp() != null) {
            return rule.getHelp();
        }
        return "CloudWatch metric " + rule.getAwsNamespace() + " " + rule.getAwsMetricName()
                + " Dimensions: " + rule.getAwsDimensions() + " Statistic: " + statistic
                + " Unit: " + unit;
    }

    public void addSamples(Samples samples) {
        sumSamples.addAll(samples.sumSamples);
        sampleCountSamples.addAll(samples.sampleCountSamples);
        minimumSamples.addAll(samples.minimumSamples);
        maximumSamples.addAll(samples.maximumSamples);
        averageSamples.addAll(samples.averageSamples);
        samples.extendedSamples.forEach((key, value) -> extendedSamples.merge(key, value,
				(list1, list2) -> Stream.of(list1, list2).flatMap(Collection::stream).collect(Collectors.toList())));
       
    }
}
