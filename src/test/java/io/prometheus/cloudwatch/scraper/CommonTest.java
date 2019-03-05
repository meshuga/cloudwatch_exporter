package io.prometheus.cloudwatch.scraper;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class CommonTest {

    @Test
    public void testRegionAddresses() throws Exception {
        // GIVEN
        Region usEast = RegionUtils.getRegion("us-east-1");

        // WHEN
        String endpoint = Common.getMonitoringEndpoint(usEast);

        // THEN
        assertEquals("https://monitoring.us-east-1.amazonaws.com", endpoint);
    }
}