package io.prometheus.cloudwatch;

import com.amazonaws.services.cloudwatch.model.Datapoint;
import io.prometheus.client.Collector;
import io.prometheus.cloudwatch.scraper.Samples;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class SamplesTest {
    static final double DELTA = 0.01;

    private Fixtures fixtures = new Fixtures();

    @Test
    public void onDatapointValuesShouldReturnCorrectMfs() throws Exception {
        // GIVEN
        Datapoint datapoint = new Datapoint()
                .withSum(1.)
                .withSampleCount(2.)
                .withMinimum(3.)
                .withMaximum(4.)
                .withAverage(5.)
                .addExtendedStatisticsEntry(fixtures.extendedEntry, 6.).withTimestamp(new Date());

        // WHEN
        Samples sut = new Samples(fixtures.baseName, datapoint, fixtures.labelNames, fixtures.labelValues, fixtures.rule);
        List<Collector.MetricFamilySamples> metricFamilySamples = sut.partialMfs(fixtures.unit, fixtures.rule);

        // THEN
        assertEquals(1., metricValue(metricFamilySamples, "baseName_sum"), DELTA);
        assertEquals(2., metricValue(metricFamilySamples, "baseName_sample_count"), DELTA);
        assertEquals(3., metricValue(metricFamilySamples, "baseName_minimum"), DELTA);
        assertEquals(4., metricValue(metricFamilySamples, "baseName_maximum"), DELTA);
        assertEquals(5., metricValue(metricFamilySamples, "baseName_average"), DELTA);
        assertEquals(6., metricValue(metricFamilySamples, "baseName_extended_entry"), DELTA);
    }

    @Test
    public void onNoDatapointDataShouldReturnNoMfs() throws Exception {
        // GIVEN
        Datapoint datapoint = new Datapoint().withTimestamp(new Date());
        
        // WHEN
        Samples sut = new Samples(fixtures.baseName, datapoint, fixtures.labelNames, fixtures.labelValues, fixtures.rule);
        List<Collector.MetricFamilySamples> metricFamilySamples = sut.partialMfs(fixtures.unit, fixtures.rule);

        // THEN
        assertEquals(metricFamilySamples.size(), 0);
    }

    @Test
    public void onAddingSampleShouldReturnCorrectData() throws Exception {
        // GIVEN
        Datapoint datapoint = new Datapoint()
                .withSum(1.).withTimestamp(new Date());
        Samples initialSamples = new Samples(fixtures.baseName, datapoint, fixtures.labelNames, fixtures.labelValues, fixtures.rule);
        Samples sut = new Samples(fixtures.baseName);

        // WHEN
        sut.addSamples(initialSamples);

        // THEN
        List<Collector.MetricFamilySamples> metricFamilySamples = sut.partialMfs(fixtures.unit, fixtures.rule);
        assertEquals(metricFamilySamples.size(), 1);
    }

    private double metricValue(final List<Collector.MetricFamilySamples> metricFamilySamples, final String postfix) {
        return metricFamilySamples.stream()
                .filter(mfs -> mfs.name.contains(postfix))
                .findFirst()
                .get()
                .samples
                .get(0)
                .value;
    }

    class Fixtures {
        final String baseName = "baseName";
        final String unit = "unit";
        final MetricRule rule = new MetricRule();
        final String extendedEntry = "extendedEntry";
        final List<String> labelNames = Collections.singletonList("name");
        final List<String> labelValues = Collections.singletonList("value");
    }
}