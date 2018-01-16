package io.prometheus.cloudwatch;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsync;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import io.prometheus.client.Collector;
import io.prometheus.cloudwatch.scraper.CloudWatchScraper;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CloudWatchCollector extends Collector {
    private static final Logger LOGGER = Logger.getLogger(CloudWatchCollector.class.getName());

    private final ActiveConfig activeConfig = new ActiveConfig();
    private final CloudWatchScraper scraper = new CloudWatchScraper();
    private final MetricRulesBuilder metricRulesBuilder = new MetricRulesBuilder();
    private final ClientBuilder clientBuilder;

    public CloudWatchCollector(Reader in) {
        this(in, null);
    }

    public CloudWatchCollector(String yamlConfig) {
        this(new StringReader(yamlConfig), null);
    }

    CloudWatchCollector(String jsonConfig, AmazonCloudWatchAsyncClient client) {
        this(new StringReader(jsonConfig), client);
    }

    private CloudWatchCollector(Reader in, AmazonCloudWatchAsyncClient client) {
        this.clientBuilder = new ClientBuilder(client);

        loadActiveConfig(in);
    }

    /**
     * Convenience function to run standalone.
     */
    public static void main(String[] args) {
        String region = "eu-west-1";
        if (args.length > 0) {
            region = args[0];
        }
        CloudWatchCollector jc = new CloudWatchCollector(("{"
                + "`region`: `" + region + "`,"
                + "`metrics`: [{`aws_namespace`: `AWS/ELB`, `aws_metric_name`: `RequestCount`, `aws_dimensions`: [`AvailabilityZone`, `LoadBalancerName`]}] ,"
                + "}").replace('`', '"'));
        for (MetricFamilySamples mfs : jc.collect()) {
            System.out.println(mfs);
        }
    }

    public void reloadConfig() throws IOException {
        LOGGER.log(Level.INFO, "Reloading configuration");

        loadActiveConfig(new FileReader(WebServer.configFilePath));
    }

    private void loadActiveConfig(Reader in) {
        Map<String, Object> yamlConfig = (Map<String, Object>) new Yaml().load(in);
        if (yamlConfig == null) {
            throw new IllegalArgumentException("Configuration needs to be set");
        }
        loadActiveConfig(yamlConfig);
    }

    private void loadActiveConfig(final Map<String, Object> yamlConfig) {
        Map<String, Object> config = yamlConfig != null ? yamlConfig : new HashMap<>();
        if (!config.containsKey("region")) {
            throw new IllegalArgumentException("Must provide region");
        }

        final AmazonCloudWatchAsync client = clientBuilder.build(config, activeConfig);
        List<MetricRule> rules = metricRulesBuilder.buildRules(config);

        activeConfig.updateConfig(rules, client);
    }

    public List<MetricFamilySamples> collect() {
        long start = System.nanoTime();
        double error = 0;
        List<MetricFamilySamples> mfs;
        try {
            mfs = scraper.scrape((ActiveConfig) activeConfig.clone())
                    .get();
        } catch (Exception e) {
            mfs = new ArrayList<>();
            error = 1;
            LOGGER.log(Level.WARNING, "CloudWatch scrape failed", e);
        }

        return addGeneralMetrics(start, error, mfs);
    }

    private List<MetricFamilySamples> addGeneralMetrics(final long start,
                                                        final double error,
                                                        final List<MetricFamilySamples> mfs) {
        List<MetricFamilySamples> fullMfs = new ArrayList<>(mfs);

        List<MetricFamilySamples.Sample> samples = new ArrayList<>();
        samples.add(new MetricFamilySamples.Sample(
                "cloudwatch_exporter_scrape_duration_seconds", new ArrayList<>(), new ArrayList<>(), (System.nanoTime() - start) / 1.0E9));
        fullMfs.add(new MetricFamilySamples("cloudwatch_exporter_scrape_duration_seconds", Type.GAUGE, "Time this CloudWatch scrape took, in seconds.", samples));

        samples = new ArrayList<>();
        samples.add(new MetricFamilySamples.Sample(
                "cloudwatch_exporter_scrape_error", new ArrayList<>(), new ArrayList<>(), error));
        fullMfs.add(new MetricFamilySamples("cloudwatch_exporter_scrape_error", Type.GAUGE, "Non-zero if this scrape failed.", samples));

        return fullMfs;
    }
}
