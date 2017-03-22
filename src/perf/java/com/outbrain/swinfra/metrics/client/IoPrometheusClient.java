package com.outbrain.swinfra.metrics.client;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class IoPrometheusClient extends AbstractClient {

    private CollectorRegistry collectorRegistry;


    @Override
    public void setUp() {
        super.setUp();
        collectorRegistry = new CollectorRegistry();
        collectorRegistry.register(createPrometheusCollector());
    }

    @Override
    public void executeLogic(final Writer writer) throws IOException {
        TextFormat.write004(writer, collectorRegistry.metricFamilySamples());
    }

    protected String getExpectedFileName() {
        return "PublishMetricsTestSimpleClientOutput.txt";
    }

    private Collector createPrometheusCollector() {
        final io.prometheus.client.Counter counter = io.prometheus.client.Counter.build()
            .name("Counter" + NAME).help(HELP).labelNames("label1", "label2")
            .register();


        final io.prometheus.client.Histogram histogram = io.prometheus.client.Histogram.build()
            .name("Histogram" + NAME).help(HELP).labelNames("label1", "label2").linearBuckets(0, 1000, 100)
            .register();

        for (int i = 0; i < 100; i++) {
            counter.labels("val", "val" + i).inc(i);
            histogram.labels("val", "val" + i).observe(i);
        }
        return new Collector() {
            @Override
            public List<MetricFamilySamples> collect() {
                final List<MetricFamilySamples> samples = new ArrayList<>();
                samples.addAll(counter.collect());
                samples.addAll(histogram.collect());
                return samples;
            }
        };
    }
}
