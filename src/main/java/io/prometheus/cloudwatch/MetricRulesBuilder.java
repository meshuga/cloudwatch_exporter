package io.prometheus.cloudwatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MetricRulesBuilder {
    public List<MetricRule> buildRules(Map<String, Object> config) {
        int defaultPeriod = 60;
        if (config.containsKey("period_seconds")) {
            defaultPeriod = ((Number) config.get("period_seconds")).intValue();
        }
        int defaultRange = 600;
        if (config.containsKey("range_seconds")) {
            defaultRange = ((Number) config.get("range_seconds")).intValue();
        }
        int defaultDelay = 600;
        if (config.containsKey("delay_seconds")) {
            defaultDelay = ((Number) config.get("delay_seconds")).intValue();
        }

        if (!config.containsKey("metrics")) {
            throw new IllegalArgumentException("Must provide metrics");
        }

        List<MetricRule> rules = new ArrayList<>();

        for (Object ruleObject : (List<Map<String, Object>>) config.get("metrics")) {
            Map<String, Object> yamlMetricRule = (Map<String, Object>) ruleObject;
            MetricRule rule = new MetricRule();
            rules.add(rule);
            if (!yamlMetricRule.containsKey("aws_namespace") || !yamlMetricRule.containsKey("aws_metric_name")) {
                throw new IllegalArgumentException("Must provide aws_namespace and aws_metric_name");
            }
            rule.setAwsNamespace((String) yamlMetricRule.get("aws_namespace"));
            rule.setAwsMetricName((String) yamlMetricRule.get("aws_metric_name"));
            if (yamlMetricRule.containsKey("help")) {
                rule.setHelp((String) yamlMetricRule.get("help"));
            }
            if (yamlMetricRule.containsKey("aws_dimensions")) {
                rule.setAwsDimensions((List<String>) yamlMetricRule.get("aws_dimensions"));
            }
            if (yamlMetricRule.containsKey("aws_dimension_select") && yamlMetricRule.containsKey("aws_dimension_select_regex")) {
                throw new IllegalArgumentException("Must not provide aws_dimension_select and aws_dimension_select_regex at the same time");
            }
            if (yamlMetricRule.containsKey("aws_dimension_select")) {
                rule.setAwsDimensionSelect((Map<String, List<String>>) yamlMetricRule.get("aws_dimension_select"));
            }
            if (yamlMetricRule.containsKey("aws_dimension_select_regex")) {
                rule.setAwsDimensionSelectRegex((Map<String, List<String>>) yamlMetricRule.get("aws_dimension_select_regex"));
            }
            if (yamlMetricRule.containsKey("aws_statistics")) {
                rule.setAwsStatistics((List<String>) yamlMetricRule.get("aws_statistics"));
            } else if (!yamlMetricRule.containsKey("aws_extended_statistics")) {
                rule.setAwsStatistics(new ArrayList(Arrays.asList("Sum", "SampleCount", "Minimum", "Maximum", "Average")));
            }
            if (yamlMetricRule.containsKey("aws_extended_statistics")) {
                rule.setAwsExtendedStatistics((List<String>) yamlMetricRule.get("aws_extended_statistics"));
            }
            if (yamlMetricRule.containsKey("period_seconds")) {
                rule.setPeriodSeconds(((Number) yamlMetricRule.get("period_seconds")).intValue());
            } else {
                rule.setPeriodSeconds(defaultPeriod);
            }
            if (yamlMetricRule.containsKey("range_seconds")) {
                rule.setRangeSeconds(((Number) yamlMetricRule.get("range_seconds")).intValue());
            } else {
                rule.setRangeSeconds(defaultRange);
            }
            if (yamlMetricRule.containsKey("delay_seconds")) {
                rule.setDelaySeconds(((Number) yamlMetricRule.get("delay_seconds")).intValue());
            } else {
                rule.setDelaySeconds(defaultDelay);
            }
        }

        return rules;
    }
}
