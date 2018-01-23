package io.prometheus.cloudwatch.scraper;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsync;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.DimensionFilter;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.cloudwatch.model.ListMetricsRequest;
import com.amazonaws.services.cloudwatch.model.ListMetricsResult;
import com.amazonaws.services.cloudwatch.model.Metric;
import io.prometheus.client.Collector;
import io.prometheus.client.Counter;
import io.prometheus.cloudwatch.ActiveConfig;
import io.prometheus.cloudwatch.MetricRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CloudWatchScraper {
    private static final Counter cloudwatchRequests = Counter.build()
            .name("cloudwatch_requests_total").help("API requests made to CloudWatch").register();

    /**
     * Check if any regex string in a list matches a given input value
     */
    protected static boolean regexListMatch(List<String> regexList, String input) {
        for (String regex : regexList) {
            if (Pattern.matches(regex, input)) {
                return true;
            }
        }
        return false;
    }

    private CompletableFuture<List<List<Dimension>>> getDimensions(MetricRule rule, AmazonCloudWatchAsync client) {
        if (rule.getAwsDimensions() == null) {
            return CompletableFuture.completedFuture(Collections.singletonList(Collections.emptyList()));
        }

        ListMetricsRequest request = new ListMetricsRequest();
        request.setNamespace(rule.getAwsNamespace());
        request.setMetricName(rule.getAwsMetricName());
        List<DimensionFilter> dimensionFilters = new ArrayList<>(rule.getAwsDimensions().size());
        for (String dimension : rule.getAwsDimensions()) {
            dimensionFilters.add(new DimensionFilter().withName(dimension));
        }
        request.setDimensions(dimensionFilters);

        return listMetric(client, request, rule, dimensionFilters);
    }

    private CompletableFuture<List<List<Dimension>>> listMetric(final AmazonCloudWatchAsync client,
                                                                final ListMetricsRequest request,
                                                                final MetricRule rule,
                                                                final List<DimensionFilter> dimensionFilters) {

        CompletableFuture<ListMetricsResult> future = new CompletableFuture<>();
        client.listMetricsAsync(request, new AsyncHandler<ListMetricsRequest, ListMetricsResult>() {
            @Override
            public void onError(Exception exception) {
                future.completeExceptionally(exception);
            }

            @Override
            public void onSuccess(ListMetricsRequest request, ListMetricsResult sendMessageResult) {
                future.complete(sendMessageResult);
            }
        });
        cloudwatchRequests.inc();
        return future.thenCompose(result -> {
            List<List<Dimension>> mappedResult = result.getMetrics()
                    .stream()
                    .filter(metric -> metric.getDimensions().size() == dimensionFilters.size()) // AWS returns all the metrics with dimensions
                    // beyond the ones we ask for, so filter them out.
                    .filter(metric -> useMetric(rule, metric))
                    .map(Metric::getDimensions)
                    .collect(Collectors.toList());
            if (result.getNextToken() != null) {
                request.setNextToken(result.getNextToken());
                return listMetric(client, request, rule, dimensionFilters)
                        .thenApply(list -> {
                            ArrayList<List<Dimension>> lists = new ArrayList<>(mappedResult);
                            lists.addAll(list);
                            return lists;
                        });
            } else {
                return CompletableFuture.completedFuture(mappedResult);
            }
        });
    }

    /**
     * Check if a metric should be used according to `aws_dimension_select` or `aws_dimension_select_regex`
     */
    private boolean useMetric(MetricRule rule, Metric metric) {
        if (rule.getAwsDimensionSelect() == null && rule.getAwsDimensionSelectRegex() == null) {
            return true;
        }
        if (rule.getAwsDimensionSelect() != null && metricsIsInAwsDimensionSelect(rule, metric)) {
            return true;
        }
        if (rule.getAwsDimensionSelectRegex() != null && metricIsInAwsDimensionSelectRegex(rule, metric)) {
            return true;
        }
        return false;
    }

    /**
     * Check if a metric is matched in `aws_dimension_select`
     */
    private boolean metricsIsInAwsDimensionSelect(MetricRule rule, Metric metric) {
        Set<String> dimensionSelectKeys = rule.getAwsDimensionSelect().keySet();
        for (Dimension dimension : metric.getDimensions()) {
            String dimensionName = dimension.getName();
            String dimensionValue = dimension.getValue();
            if (dimensionSelectKeys.contains(dimensionName)) {
                List<String> allowedDimensionValues = rule.getAwsDimensionSelect().get(dimensionName);
                if (!allowedDimensionValues.contains(dimensionValue)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check if a metric is matched in `aws_dimension_select_regex`
     */
    private boolean metricIsInAwsDimensionSelectRegex(MetricRule rule, Metric metric) {
        Set<String> dimensionSelectRegexKeys = rule.getAwsDimensionSelectRegex().keySet();
        for (Dimension dimension : metric.getDimensions()) {
            String dimensionName = dimension.getName();
            String dimensionValue = dimension.getValue();
            if (dimensionSelectRegexKeys.contains(dimensionName)) {
                List<String> allowedDimensionValues = rule.getAwsDimensionSelectRegex().get(dimensionName);
                if (!regexListMatch(allowedDimensionValues, dimensionValue)) {
                    return false;
                }
            }
        }
        return true;
    }

    private Datapoint getNewestDatapoint(java.util.List<Datapoint> datapoints) {
        Datapoint newest = null;
        for (Datapoint d : datapoints) {
            if (newest == null || newest.getTimestamp().before(d.getTimestamp())) {
                newest = d;
            }
        }
        return newest;
    }

    public CompletableFuture<List<Collector.MetricFamilySamples>> scrape(ActiveConfig config) {
        long start = System.currentTimeMillis();

        CompletableFuture[] rulesMetricLists = config.getRules().stream()
                .map(rule -> ruleMetrics(rule, config, start))
                .toArray(CompletableFuture[]::new);

        return mergeRuleMetrics(rulesMetricLists);
    }

    private CompletableFuture<List<Collector.MetricFamilySamples>> mergeRuleMetrics(
            final CompletableFuture[] rulesStream) {
        return CompletableFuture.allOf(rulesStream)
                .thenApply(v -> {
                    List<Collector.MetricFamilySamples> mfs = new ArrayList<>(rulesStream.length);
                    Stream.of(rulesStream)
                            .map(future -> (List<Collector.MetricFamilySamples>) future.join())
                            .forEach(mfs::addAll);
                    return mfs;
                });
    }

    private CompletableFuture<List<Collector.MetricFamilySamples>> ruleMetrics(
            final MetricRule rule,
            final ActiveConfig config,
            final long start) {
        Date startDate = new Date(start - 1000 * rule.getDelaySeconds());
        Date endDate = new Date(start - 1000 * (rule.getDelaySeconds() + rule.getRangeSeconds()));
        GetMetricStatisticsRequest request = new GetMetricStatisticsRequest();
        request.setNamespace(rule.getAwsNamespace());
        request.setMetricName(rule.getAwsMetricName());
        request.setStatistics(rule.getAwsStatistics());
        request.setExtendedStatistics(rule.getAwsExtendedStatistics());
        request.setEndTime(startDate);
        request.setStartTime(endDate);
        request.setPeriod(rule.getPeriodSeconds());

        String baseName = Common.baseName(rule);
        String jobName = Common.safeName(rule.getAwsNamespace().toLowerCase());

        AtomicReference<String> unit = new AtomicReference<>();

        return getDimensions(rule, config.getClient())
                .thenCompose(dimensionsList -> {
                    CompletableFuture[] futures = dimensionsList.stream()
                            .map(dimensions -> getMetricStatistics(config.getClient(), request, dimensions)
                                    .thenApply(result -> {
                                        Datapoint dp = getNewestDatapoint(result.getDatapoints());
                                        if (dp == null) {
                                            return Optional.<Samples>empty();
                                        }
                                        unit.set(dp.getUnit());
                                        List<String> labelNames = new ArrayList<>(dimensions.size());
                                        List<String> labelValues = new ArrayList<>(dimensions.size());
                                        labelNames.add("job");
                                        labelValues.add(jobName);
                                        labelNames.add("instance");
                                        labelValues.add("");
                                        for (Dimension d : dimensions) {
                                            labelNames.add(Common.safeName(Common.toSnakeCase(d.getName())));
                                            labelValues.add(d.getValue());
                                        }

                                        return Optional.of(new Samples(baseName, dp, labelNames, labelValues));
                                    })).toArray(CompletableFuture[]::new);
                    return CompletableFuture.allOf(futures)
                            .thenApply(v -> {
                                Samples dimensionsSamples = new Samples(baseName);
                                Stream.of(futures)
                                        .map(future -> (Optional<Samples>) future.join())
                                        .forEach(samplesOptional -> samplesOptional.ifPresent(dimensionsSamples::addSamples));
                                return dimensionsSamples;
                            });
                }).thenApply(samples -> samples.partialMfs(unit.get(), rule));
    }

    private CompletableFuture<GetMetricStatisticsResult> getMetricStatistics(
            final AmazonCloudWatchAsync client,
            final GetMetricStatisticsRequest request,
            final List<Dimension> dimensions) {
        CompletableFuture<GetMetricStatisticsResult> future = new CompletableFuture<>();
        client.getMetricStatisticsAsync(request
                .clone()
                .withDimensions(dimensions), new AsyncHandler<GetMetricStatisticsRequest, GetMetricStatisticsResult>() {
            @Override
            public void onError(Exception exception) {
                future.completeExceptionally(exception);
            }

            @Override
            public void onSuccess(GetMetricStatisticsRequest request, GetMetricStatisticsResult sendMessageResult) {
                future.complete(sendMessageResult);
            }
        });
        cloudwatchRequests.inc();
        return future;
    }
}
