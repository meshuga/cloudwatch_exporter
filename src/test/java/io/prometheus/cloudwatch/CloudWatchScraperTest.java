package io.prometheus.cloudwatch;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.AmazonWebServiceResult;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.DimensionFilter;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.cloudwatch.model.ListMetricsRequest;
import com.amazonaws.services.cloudwatch.model.ListMetricsResult;
import com.amazonaws.services.cloudwatch.model.Metric;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.cloudwatch.scraper.CloudWatchScraper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;

@RunWith(MockitoJUnitRunner.class)
public class CloudWatchScraperTest {
    static final double DELTA = 0.01;

    @Mock
    AmazonCloudWatchAsyncClient client;

    CollectorRegistry registry;

    Fixtures fixtures = new Fixtures();

    private static Answer<Object> asyncAnswer(final AmazonWebServiceResult answer) {
        return ans -> {
            ((AsyncHandler) (ans.getArguments()[1]))
                    .onSuccess((AmazonWebServiceRequest) (ans.getArguments()[0]), answer);
            return CompletableFuture.completedFuture(null);
        };
    }

    @Before
    public void setUp() {
        registry = new CollectorRegistry();
    }

    @Test
    public void testMetricPeriod() throws Exception {
        // GIVEN
        ActiveConfig config = new ActiveConfig()
                .updateConfig(Collections.singletonList(fixtures.metricRule), client);

        Mockito.when(client.getMetricStatisticsAsync(any(GetMetricStatisticsRequest.class),
                any(AsyncHandler.class))).thenAnswer(asyncAnswer(new GetMetricStatisticsResult()
                .withDatapoints(new Datapoint()
                        .withAverage(1.)
                        .withUnit("unit"))));

        // WHEN
        List<Collector.MetricFamilySamples> result = new CloudWatchScraper()
                .scrape(config)
                .get();

        // THEN
        Mockito.verify(client).getMetricStatisticsAsync(argThat(
                new GetMetricStatisticsRequestMatcher()
                        .Namespace("AWS/ELB")
                        .MetricName("RequestCount")
                        .Period(100)
        ), any());
        assertEquals(1, result.size());
    }

    @Test
    public void testDefaultPeriod() throws Exception {
        // GIVEN
        ActiveConfig config = new ActiveConfig()
                .updateConfig(Collections.singletonList(fixtures.metricRule), client);

        Mockito.when(client.getMetricStatisticsAsync(any(GetMetricStatisticsRequest.class),
                any(AsyncHandler.class))).thenAnswer(asyncAnswer(new GetMetricStatisticsResult()));

        // WHEN
        List<Collector.MetricFamilySamples> result = new CloudWatchScraper()
                .scrape(config)
                .get();

        Mockito.verify(client).getMetricStatisticsAsync(argThat(
                new GetMetricStatisticsRequestMatcher()
                        .Namespace("AWS/ELB")
                        .MetricName("RequestCount")
                        .Period(100)
        ), any());
        assertEquals(0, result.size());
    }

    @Test
    public void testUsesNewestDatapoint() throws Exception {
        // GIVEN
        ActiveConfig config = new ActiveConfig()
                .updateConfig(Collections.singletonList(fixtures.metricRule), client);

        Mockito.when(client.getMetricStatisticsAsync(argThat(
                new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount")),
                any(AsyncHandler.class))).thenAnswer(asyncAnswer(
                new GetMetricStatisticsResult().withDatapoints(
                        new Datapoint().withTimestamp(new Date(1)).withAverage(1.0),
                        new Datapoint().withTimestamp(new Date(3)).withAverage(3.0),
                        new Datapoint().withTimestamp(new Date(2)).withAverage(2.0))));

        // WHEN
        List<Collector.MetricFamilySamples> result = new CloudWatchScraper()
                .scrape(config)
                .get();

        // THEN
        assertEquals(3.0, result.get(0).samples.get(0).value, DELTA);
    }

    @Test
    public void testDimensions() throws Exception {
        // GIVEN
        ActiveConfig config = new ActiveConfig()
                .updateConfig(Collections.singletonList(new MetricRule()
                        .setAwsNamespace("AWS/ELB")
                        .setAwsMetricName("RequestCount")
                        .setAwsDimensions(Arrays.asList("AvailabilityZone", "LoadBalancerName"))), client);

        Mockito.when(client.listMetricsAsync(argThat(
                new ListMetricsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimensions("AvailabilityZone", "LoadBalancerName")), any()))
                .thenAnswer(asyncAnswer((new ListMetricsResult().withMetrics(
                        new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myLB")),
                        new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myLB"), new Dimension().withName("ThisExtraDimensionIsIgnored").withValue("dummy")),
                        new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("b"), new Dimension().withName("LoadBalancerName").withValue("myOtherLB"))))));

        Mockito.when(client.getMetricStatisticsAsync(argThat(
                new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "a").Dimension("LoadBalancerName", "myLB")), any()))
                .thenAnswer(asyncAnswer(new GetMetricStatisticsResult().withDatapoints(
                        new Datapoint().withTimestamp(new Date()).withAverage(2.0))));
        Mockito.when(client.getMetricStatisticsAsync(argThat(
                new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "b").Dimension("LoadBalancerName", "myOtherLB")), any()))
                .thenAnswer(asyncAnswer(new GetMetricStatisticsResult().withDatapoints(
                        new Datapoint().withTimestamp(new Date()).withAverage(3.0))));

        // WHEN
        List<Collector.MetricFamilySamples> result = new CloudWatchScraper()
                .scrape(config)
                .get();

        // THEN
        assertEquals("aws_elb_request_count_average", result.get(0).name);

        assertEquals(2, result.get(0).samples.size());
        assertEquals(2.0, result.get(0).samples.get(0).value, DELTA);
        assertEquals(Arrays.asList("aws_elb", "", "a", "myLB"), result.get(0).samples.get(0).labelValues);
        assertEquals(3.0, result.get(0).samples.get(1).value, DELTA);
        assertEquals(Arrays.asList("aws_elb", "", "b", "myOtherLB"), result.get(0).samples.get(1).labelValues);
    }

    @Test
    public void testDimensionSelect() throws Exception {
        // GIVEN
        ActiveConfig config = new ActiveConfig()
                .updateConfig(Collections.singletonList(new MetricRule()
                        .setAwsNamespace("AWS/ELB")
                        .setAwsMetricName("RequestCount")
                        .setAwsDimensions(Arrays.asList("AvailabilityZone", "LoadBalancerName"))
                        .setAwsDimensionSelect(Collections.singletonMap("LoadBalancerName", Collections.singletonList("myLB")))), client);

        Mockito.when(client.listMetricsAsync(argThat(
                new ListMetricsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimensions("AvailabilityZone", "LoadBalancerName")), any()))
                .thenAnswer(asyncAnswer(new ListMetricsResult().withMetrics(
                        new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myLB")),
                        new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("b"), new Dimension().withName("LoadBalancerName").withValue("myLB")),
                        new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myOtherLB")))));

        Mockito.when(client.getMetricStatisticsAsync(argThat(
                new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "a").Dimension("LoadBalancerName", "myLB")), any()))
                .thenAnswer(asyncAnswer(new GetMetricStatisticsResult().withDatapoints(
                        new Datapoint().withTimestamp(new Date()).withAverage(2.0))));

        Mockito.when(client.getMetricStatisticsAsync(argThat(
                new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "b").Dimension("LoadBalancerName", "myLB")), any()))
                .thenAnswer(asyncAnswer(new GetMetricStatisticsResult().withDatapoints(
                        new Datapoint().withTimestamp(new Date()).withAverage(2.0))));
        // WHEN
        List<Collector.MetricFamilySamples> result = new CloudWatchScraper()
                .scrape(config)
                .get();

        // THEN
        assertEquals("aws_elb_request_count_average", result.get(0).name);

        assertEquals(2, result.get(0).samples.size());
        assertEquals(2.0, result.get(0).samples.get(0).value, DELTA);
        assertEquals(Arrays.asList("job", "instance", "availability_zone", "load_balancer_name"), result.get(0).samples.get(0).labelNames);
        assertEquals(Arrays.asList("aws_elb", "", "a", "myLB"), result.get(0).samples.get(0).labelValues);
        assertEquals(2.0, result.get(0).samples.get(1).value, DELTA);
        assertEquals(Arrays.asList("job", "instance", "availability_zone", "load_balancer_name"), result.get(0).samples.get(1).labelNames);
        assertEquals(Arrays.asList("aws_elb", "", "b", "myLB"), result.get(0).samples.get(1).labelValues);
    }

    @Test
    public void testDimensionSelectRegex() throws Exception {
        // GIVEN
        ActiveConfig config = new ActiveConfig()
                .updateConfig(Collections.singletonList(new MetricRule()
                        .setAwsNamespace("AWS/ELB")
                        .setAwsMetricName("RequestCount")
                        .setAwsDimensions(Arrays.asList("AvailabilityZone", "LoadBalancerName"))
                        .setAwsDimensionSelectRegex(Collections.singletonMap("LoadBalancerName", Collections.singletonList("myLB(.*)")))), client);

        Mockito.when(client.listMetricsAsync(argThat(
                new ListMetricsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimensions("AvailabilityZone", "LoadBalancerName")), any()))
                .thenAnswer(asyncAnswer(new ListMetricsResult().withMetrics(
                        new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myLB1")),
                        new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("b"), new Dimension().withName("LoadBalancerName").withValue("myLB2")),
                        new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myOtherLB")))));

        Mockito.when(client.getMetricStatisticsAsync(argThat(
                new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "a").Dimension("LoadBalancerName", "myLB1")), any()))
                .thenAnswer(asyncAnswer(new GetMetricStatisticsResult().withDatapoints(
                        new Datapoint().withTimestamp(new Date()).withAverage(2.0))));

        Mockito.when(client.getMetricStatisticsAsync(argThat(
                new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "b").Dimension("LoadBalancerName", "myLB2")), any()))
                .thenAnswer(asyncAnswer(new GetMetricStatisticsResult().withDatapoints(
                        new Datapoint().withTimestamp(new Date()).withAverage(2.0))));

        // WHEN
        List<Collector.MetricFamilySamples> result = new CloudWatchScraper()
                .scrape(config)
                .get();

        // THEN
        assertEquals("aws_elb_request_count_average", result.get(0).name);

        assertEquals(2, result.get(0).samples.size());
        assertEquals(2.0, result.get(0).samples.get(0).value, DELTA);
        assertEquals(Arrays.asList("job", "instance", "availability_zone", "load_balancer_name"), result.get(0).samples.get(0).labelNames);
        assertEquals(Arrays.asList("aws_elb", "", "a", "myLB1"), result.get(0).samples.get(0).labelValues);
        assertEquals(2.0, result.get(0).samples.get(1).value, DELTA);
        assertEquals(Arrays.asList("job", "instance", "availability_zone", "load_balancer_name"), result.get(0).samples.get(1).labelNames);
        assertEquals(Arrays.asList("aws_elb", "", "b", "myLB2"), result.get(0).samples.get(1).labelValues);
    }

    @Test
    public void testGetDimensionsUsesNextToken() throws Exception {
        // GIVEN
        ActiveConfig config = new ActiveConfig()
                .updateConfig(Collections.singletonList(new MetricRule()
                        .setAwsNamespace("AWS/ELB")
                        .setAwsMetricName("RequestCount")
                        .setAwsDimensions(Arrays.asList("AvailabilityZone", "LoadBalancerName"))
                        .setAwsDimensionSelect(Collections.singletonMap("LoadBalancerName", Collections.singletonList("myLB")))), client);

        new CloudWatchCollector(
                "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  aws_dimension_select:\n    LoadBalancerName:\n    - myLB", client).register(registry);

        Mockito.when(client.listMetricsAsync(argThat(
                new ListMetricsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimensions("AvailabilityZone", "LoadBalancerName")), any()))
                .thenAnswer(asyncAnswer(new ListMetricsResult().withNextToken("ABC")));

        Mockito.when(client.listMetricsAsync(argThat(
                new ListMetricsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimensions("AvailabilityZone", "LoadBalancerName").NextToken("ABC")), any()))
                .thenAnswer(asyncAnswer(new ListMetricsResult().withMetrics(
                        new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myLB")))));

        Mockito.when(client.getMetricStatisticsAsync(argThat(
                new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "a").Dimension("LoadBalancerName", "myLB")), any()))
                .thenAnswer(asyncAnswer(new GetMetricStatisticsResult().withDatapoints(
                        new Datapoint().withTimestamp(new Date()).withAverage(2.0))));

        // WHEN
        List<Collector.MetricFamilySamples> result = new CloudWatchScraper()
                .scrape(config)
                .get();

        // THEN
        assertEquals("aws_elb_request_count_average", result.get(0).name);

        assertEquals(1, result.get(0).samples.size());
        assertEquals(2.0, result.get(0).samples.get(0).value, DELTA);
        assertEquals(Arrays.asList("job", "instance", "availability_zone", "load_balancer_name"), result.get(0).samples.get(0).labelNames);
        assertEquals(Arrays.asList("aws_elb", "", "a", "myLB"), result.get(0).samples.get(0).labelValues);
    }

    @Test
    public void testExtendedStatistics() throws Exception {
        // GIVEN
        ActiveConfig config = new ActiveConfig()
                .updateConfig(Collections.singletonList(new MetricRule()
                        .setAwsNamespace("AWS/ELB")
                        .setAwsMetricName("Latency")
                        .setAwsExtendedStatistics(Arrays.asList("p95", "p99.99"))), client);
        new CloudWatchCollector(
                "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: Latency\n  aws_extended_statistics:\n  - p95\n  - p99.99", client).register(registry);

        HashMap<String, Double> extendedStatistics = new HashMap<>();
        extendedStatistics.put("p95", 1.0);
        extendedStatistics.put("p99.99", 2.0);

        Mockito.when(client.getMetricStatisticsAsync(argThat(
                new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("Latency")), any()))
                .thenAnswer(asyncAnswer(new GetMetricStatisticsResult().withDatapoints(
                        new Datapoint().withTimestamp(new Date()).withExtendedStatistics(extendedStatistics))));

        // WHEN
        List<Collector.MetricFamilySamples> result = new CloudWatchScraper()
                .scrape(config)
                .get();

        // THEN
        assertEquals("aws_elb_latency_p95", result.get(0).name);
        assertEquals(1, result.get(0).samples.size());
        assertEquals(1.0, result.get(0).samples.get(0).value, DELTA);
        assertEquals(Arrays.asList("job", "instance"), result.get(0).samples.get(0).labelNames);
        assertEquals(Arrays.asList("aws_elb", ""), result.get(0).samples.get(0).labelValues);

        assertEquals("aws_elb_latency_p99_99", result.get(1).name);
        assertEquals(1, result.get(1).samples.size());
        assertEquals(2.0, result.get(1).samples.get(0).value, DELTA);
        assertEquals(Arrays.asList("job", "instance"), result.get(1).samples.get(0).labelNames);
        assertEquals(Arrays.asList("aws_elb", ""), result.get(1).samples.get(0).labelValues);
    }

    @Test
    public void testDynamoIndexDimensions() throws Exception {
        // GIVEN
        ActiveConfig config = new ActiveConfig()
                .updateConfig(Arrays.asList(new MetricRule()
                                .setAwsNamespace("AWS/DynamoDB")
                                .setAwsMetricName("ConsumedReadCapacityUnits")
                                .setAwsDimensions(Arrays.asList("TableName", "GlobalSecondaryIndexName")),
                        new MetricRule()
                                .setAwsNamespace("AWS/DynamoDB")
                                .setAwsMetricName("OnlineIndexConsumedWriteCapacity")
                                .setAwsDimensions(Arrays.asList("TableName", "GlobalSecondaryIndexName")),
                        new MetricRule()
                                .setAwsNamespace("AWS/DynamoDB")
                                .setAwsMetricName("ConsumedReadCapacityUnits")
                                .setAwsDimensions(Collections.singletonList("TableName"))), client);

        Mockito.when(client.listMetricsAsync(argThat(
                new ListMetricsRequestMatcher().Namespace("AWS/DynamoDB").MetricName("ConsumedReadCapacityUnits").Dimensions("TableName", "GlobalSecondaryIndexName")), any()))
                .thenAnswer(asyncAnswer(new ListMetricsResult().withMetrics(
                        new Metric().withDimensions(new Dimension().withName("TableName").withValue("myTable"), new Dimension().withName("GlobalSecondaryIndexName").withValue("myIndex")))));
        Mockito.when(client.listMetricsAsync(argThat(
                new ListMetricsRequestMatcher().Namespace("AWS/DynamoDB").MetricName("OnlineIndexConsumedWriteCapacity").Dimensions("TableName", "GlobalSecondaryIndexName")), any()))
                .thenAnswer(asyncAnswer(new ListMetricsResult().withMetrics(
                        new Metric().withDimensions(new Dimension().withName("TableName").withValue("myTable"), new Dimension().withName("GlobalSecondaryIndexName").withValue("myIndex")))));
        Mockito.when(client.listMetricsAsync(argThat(
                new ListMetricsRequestMatcher().Namespace("AWS/DynamoDB").MetricName("ConsumedReadCapacityUnits").Dimensions("TableName")), any()))
                .thenAnswer(asyncAnswer(new ListMetricsResult().withMetrics(
                        new Metric().withDimensions(new Dimension().withName("TableName").withValue("myTable")))));

        Mockito.when(client.getMetricStatisticsAsync(argThat(
                new GetMetricStatisticsRequestMatcher().Namespace("AWS/DynamoDB").MetricName("ConsumedReadCapacityUnits").Dimension("TableName", "myTable").Dimension("GlobalSecondaryIndexName", "myIndex")), any()))
                .thenAnswer(asyncAnswer(new GetMetricStatisticsResult().withDatapoints(
                        new Datapoint().withTimestamp(new Date()).withSum(1.0))));
        Mockito.when(client.getMetricStatisticsAsync(argThat(
                new GetMetricStatisticsRequestMatcher().Namespace("AWS/DynamoDB").MetricName("OnlineIndexConsumedWriteCapacity").Dimension("TableName", "myTable").Dimension("GlobalSecondaryIndexName", "myIndex")), any()))
                .thenAnswer(asyncAnswer(new GetMetricStatisticsResult().withDatapoints(
                        new Datapoint().withTimestamp(new Date()).withSum(2.0))));
        Mockito.when(client.getMetricStatisticsAsync(argThat(
                new GetMetricStatisticsRequestMatcher().Namespace("AWS/DynamoDB").MetricName("ConsumedReadCapacityUnits").Dimension("TableName", "myTable")), any()))
                .thenAnswer(asyncAnswer(new GetMetricStatisticsResult().withDatapoints(
                        new Datapoint().withTimestamp(new Date()).withSum(3.0))));
        // WHEN
        List<Collector.MetricFamilySamples> result = new CloudWatchScraper()
                .scrape(config)
                .get();

        // THEN
        assertEquals("aws_dynamodb_consumed_read_capacity_units_index_sum", result.get(0).name);
        assertEquals(1, result.get(0).samples.size());
        assertEquals(1.0, result.get(0).samples.get(0).value, DELTA);
        assertEquals(Arrays.asList("job", "instance", "table_name", "global_secondary_index_name"), result.get(0).samples.get(0).labelNames);
        assertEquals(Arrays.asList("aws_dynamodb", "", "myTable", "myIndex"), result.get(0).samples.get(0).labelValues);

        assertEquals("aws_dynamodb_online_index_consumed_write_capacity_sum", result.get(1).name);
        assertEquals(1, result.get(1).samples.size());
        assertEquals(2.0, result.get(1).samples.get(0).value, DELTA);
        assertEquals(Arrays.asList("job", "instance", "table_name", "global_secondary_index_name"), result.get(1).samples.get(0).labelNames);
        assertEquals(Arrays.asList("aws_dynamodb", "", "myTable", "myIndex"), result.get(1).samples.get(0).labelValues);

        assertEquals("aws_dynamodb_consumed_read_capacity_units_sum", result.get(2).name);
        assertEquals(1, result.get(2).samples.size());
        assertEquals(3.0, result.get(2).samples.get(0).value, DELTA);
        assertEquals(Arrays.asList("job", "instance", "table_name"), result.get(2).samples.get(0).labelNames);
        assertEquals(Arrays.asList("aws_dynamodb", "", "myTable"), result.get(2).samples.get(0).labelValues);
    }

    class ListMetricsRequestMatcher extends ArgumentMatcher<ListMetricsRequest> {
        String namespace;
        String metricName;
        String nextToken;
        List<DimensionFilter> dimensions = new ArrayList<DimensionFilter>();

        public ListMetricsRequestMatcher Namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public ListMetricsRequestMatcher MetricName(String metricName) {
            this.metricName = metricName;
            return this;
        }

        public ListMetricsRequestMatcher NextToken(String nextToken) {
            this.nextToken = nextToken;
            return this;
        }

        public ListMetricsRequestMatcher Dimensions(String... dimensions) {
            this.dimensions = new ArrayList<>();
            for (int i = 0; i < dimensions.length; i++) {
                this.dimensions.add(new DimensionFilter().withName(dimensions[i]));
            }
            return this;
        }

        public boolean matches(Object o) {
            ListMetricsRequest request = (ListMetricsRequest) o;
            if (request == null) return false;
            if (namespace != null && !namespace.equals(request.getNamespace())) {
                return false;
            }
            if (metricName != null && !metricName.equals(request.getMetricName())) {
                return false;
            }
            if (nextToken == null ^ request.getNextToken() == null) {
                return false;
            }
            if (nextToken != null && !nextToken.equals(request.getNextToken())) {
                return false;
            }
            if (!dimensions.equals(request.getDimensions())) {
                return false;
            }
            return true;
        }
    }

    class GetMetricStatisticsRequestMatcher extends ArgumentMatcher<GetMetricStatisticsRequest> {
        String namespace;
        String metricName;
        List<Dimension> dimensions = new ArrayList<>();
        Integer period;

        public GetMetricStatisticsRequestMatcher Namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public GetMetricStatisticsRequestMatcher MetricName(String metricName) {
            this.metricName = metricName;
            return this;
        }

        public GetMetricStatisticsRequestMatcher Dimension(String name, String value) {
            dimensions.add(new Dimension().withName(name).withValue(value));
            return this;
        }

        public GetMetricStatisticsRequestMatcher Period(int period) {
            this.period = period;
            return this;
        }

        public boolean matches(Object o) {
            GetMetricStatisticsRequest request = (GetMetricStatisticsRequest) o;
            if (request == null) return false;
            if (namespace != null && !namespace.equals(request.getNamespace())) {
                return false;
            }
            if (metricName != null && !metricName.equals(request.getMetricName())) {
                return false;
            }
            if (!dimensions.equals(request.getDimensions())) {
                return false;
            }
            if (period != null && !period.equals(request.getPeriod())) {
                return false;
            }
            return true;
        }
    }

    class Fixtures {
        final MetricRule metricRule = new MetricRule()
                .setAwsNamespace("AWS/ELB")
                .setAwsMetricName("RequestCount")
                .setPeriodSeconds(100)
                .setRangeSeconds(200)
                .setDelaySeconds(300);
    }
}
