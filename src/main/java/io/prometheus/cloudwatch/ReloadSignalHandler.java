package io.prometheus.cloudwatch;

import sun.misc.Signal;

import java.util.logging.Level;
import java.util.logging.Logger;

class ReloadSignalHandler {
    private static final Logger LOGGER = Logger.getLogger(CloudWatchCollector.class.getName());

    protected static void start(final CloudWatchCollector collector) {
        Signal.handle(new Signal("HUP"), signal -> {
            try {
                collector.reloadConfig();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Configuration reload failed", e);
            }
        });
    }
}
