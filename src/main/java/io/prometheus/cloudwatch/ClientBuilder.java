package io.prometheus.cloudwatch;

import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsync;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClientBuilder;
import io.prometheus.cloudwatch.scraper.Common;

import java.util.Map;
import java.util.Properties;

import static java.util.concurrent.Executors.newFixedThreadPool;

import java.util.Enumeration;

public class ClientBuilder {
    private final AmazonCloudWatchAsync defaultClient;

    public ClientBuilder(final AmazonCloudWatchAsyncClient defaultClient) {
        this.defaultClient = defaultClient;
    }

    public AmazonCloudWatchAsync build(final Map<String, Object> config, final ActiveConfig activeConfig) {
        if (defaultClient == null) {
            if (activeConfig != null && activeConfig.getClient() != null) {
                activeConfig.getClient().shutdown();
            }
            return buildClient(config);
        } else {
            return defaultClient;
        }
    }

    private AmazonCloudWatchAsync buildClient(final Map<String, Object> config) {
        final AmazonCloudWatchAsyncClientBuilder builder =
                AmazonCloudWatchAsyncClientBuilder.standard();
        if (config.containsKey("role_arn")) {
            builder.setCredentials(new STSAssumeRoleSessionCredentialsProvider.Builder(
                    (String) config.get("role_arn"), "cloudwatch_exporter")
                    .build());
        }
        if (config.containsKey("thread_pool_size")) {
            builder.setExecutorFactory(() ->
                    newFixedThreadPool(threadPoolSize(config)));
        }
        Region region = RegionUtils.getRegion((String) config.get("region"));
        builder.setEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(
                        Common.getMonitoringEndpoint(region), region.getName()));
        return builder.build();
    }

    private int threadPoolSize(final Map<String, Object> config) {
        return config.get("thread_pool_size") != null ? (int) config.get("thread_pool_size") : 50;
    }
}
