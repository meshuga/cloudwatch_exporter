package io.prometheus.cloudwatch;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsync;

import java.util.List;

public class ActiveConfig implements Cloneable {
    private volatile List<MetricRule> rules;
    private volatile AmazonCloudWatchAsync client;

    public synchronized ActiveConfig updateConfig(final List<MetricRule> rules,
                                          final AmazonCloudWatchAsync client) {
        this.rules = rules;
        this.client = client;
        return this;
    }

    public List<MetricRule> getRules() {
        return rules;
    }

    public AmazonCloudWatchAsync getClient() {
        return client;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
