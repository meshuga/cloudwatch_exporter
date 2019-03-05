package io.prometheus.cloudwatch.scraper;

import com.amazonaws.regions.Region;
import io.prometheus.cloudwatch.MetricRule;

import java.util.Arrays;
import java.util.List;

public class Common {
    private static final List<String> brokenDynamoMetrics = Arrays.asList(
            "ConsumedReadCapacityUnits", "ConsumedWriteCapacityUnits",
            "ProvisionedReadCapacityUnits", "ProvisionedWriteCapacityUnits",
            "ReadThrottleEvents", "WriteThrottleEvents");

    public static String toSnakeCase(String str) {
        return str.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    public static String safeName(String s) {
        // Change invalid chars to underscore, and merge underscores.
        return s.replaceAll("[^a-zA-Z0-9:_]", "_").replaceAll("__+", "_");
    }

    public static String baseName(final MetricRule rule) {
        String baseName = safeName(rule.getAwsNamespace().toLowerCase() + "_" +
                toSnakeCase(rule.getAwsMetricName()));

        if (rule.getAwsNamespace().equals("AWS/DynamoDB")
                && rule.getAwsDimensions().contains("GlobalSecondaryIndexName")
                && brokenDynamoMetrics.contains(rule.getAwsMetricName())) {
            baseName += "_index";
        }
        return baseName;
    }

    public static String getMonitoringEndpoint(Region region) {
        return "https://" + region.getServiceEndpoint("monitoring");
    }
}
