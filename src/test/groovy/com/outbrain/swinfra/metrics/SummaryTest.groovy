package com.outbrain.swinfra.metrics

import com.outbrain.swinfra.metrics.samples.SampleCreator
import com.outbrain.swinfra.metrics.samples.StaticLablesSampleCreator
import spock.lang.Specification
import spock.lang.Unroll

import static com.outbrain.swinfra.metrics.Summary.QUANTILE_LABEL
import static com.outbrain.swinfra.metrics.Summary.SummaryBuilder
import static com.outbrain.swinfra.metrics.timing.TimingMetric.COUNT_SUFFIX
import static com.outbrain.swinfra.metrics.timing.TimingMetric.SUM_SUFFIX
import static io.prometheus.client.Collector.MetricFamilySamples
import static io.prometheus.client.Collector.MetricFamilySamples.Sample
import static io.prometheus.client.Collector.Type.SUMMARY

class SummaryTest extends Specification {

    private static final String NAME = "mySummary"
    private static final String SUM_NAME = NAME + SUM_SUFFIX
    private static final String COUNT_NAME = NAME + COUNT_SUFFIX
    private static final String HELP = "HELP"

    private final SampleConsumer sampleConsumer = Mock(SampleConsumer)
    private final SampleCreator sampleCreator = new StaticLablesSampleCreator([:])


    final TestClock clock = new TestClock()


    def 'Summary with no labels should return correct samples for newly initialized metric'() {
        given:
            final List<Sample> samples = generateSummarySamples([], [], 0)
            final MetricFamilySamples metricFamilySamples = new MetricFamilySamples(NAME, SUMMARY, HELP, samples)
            final Summary summary = new SummaryBuilder(NAME, HELP).withClock(clock).build()
        when:
            final MetricFamilySamples actualMetricFamilySamples = summary.getSample(sampleCreator)
        then:
            actualMetricFamilySamples.samples as Set == metricFamilySamples.samples as Set
            actualMetricFamilySamples.name == metricFamilySamples.name
            actualMetricFamilySamples.help == metricFamilySamples.help
            actualMetricFamilySamples.type == metricFamilySamples.type
        when:
            summary.forEachSample(sampleConsumer)
        then:
            1 * sampleConsumer.apply(NAME, 0, [], QUANTILE_LABEL, '0.5')
            1 * sampleConsumer.apply(NAME, 0, [], QUANTILE_LABEL, '0.75')
            1 * sampleConsumer.apply(NAME, 0, [], QUANTILE_LABEL, '0.95')
            1 * sampleConsumer.apply(NAME, 0, [], QUANTILE_LABEL, '0.98')
            1 * sampleConsumer.apply(NAME, 0, [], QUANTILE_LABEL, '0.99')
            1 * sampleConsumer.apply(NAME, 0, [], QUANTILE_LABEL, '0.999')
            1 * sampleConsumer.apply(SUM_NAME, 0, [], null, null)
            1 * sampleConsumer.apply(COUNT_NAME, 0, [], null, null)
    }

    def 'Summary with no labels should return correct samples after some measurements'() {
        given:
            final List<Sample> samples = generateSummarySamples([], [], 1000)
            final MetricFamilySamples metricFamilySamples = new MetricFamilySamples(NAME, SUMMARY, HELP, samples)
            final double sum = (1..1000).sum() as double
        when:
            final Summary summary = new SummaryBuilder(NAME, HELP).withClock(clock).build()
            1.upto(1000, {
                clock.tick = it - 1
                summary.observe(it)
            })
        then:
            actualMetricFamilySamples.samples as Set == metricFamilySamples.samples as Set
            actualMetricFamilySamples.name == metricFamilySamples.name
            actualMetricFamilySamples.help == metricFamilySamples.help
            actualMetricFamilySamples.type == metricFamilySamples.type
        when:
            summary.forEachSample(sampleConsumer)
        then:
            1 * sampleConsumer.apply(NAME, 500, [], QUANTILE_LABEL, '0.5')
            1 * sampleConsumer.apply(NAME, 750, [], QUANTILE_LABEL, '0.75')
            1 * sampleConsumer.apply(NAME, 950, [], QUANTILE_LABEL, '0.95')
            1 * sampleConsumer.apply(NAME, 980, [], QUANTILE_LABEL, '0.98')
            1 * sampleConsumer.apply(NAME, 990, [], QUANTILE_LABEL, '0.99')
            1 * sampleConsumer.apply(NAME, 999, [], QUANTILE_LABEL, '0.999')
            1 * sampleConsumer.apply(SUM_NAME, sum, [], null, null)
            1 * sampleConsumer.apply(COUNT_NAME, 1000, [], null, null)
    }

    def 'Summary with no labels and sampleCreator with labels should return correct samples after some measurements'() {
        final Map<String, String> labelsMap = [sam1: "sam-val1", sam2: "sam-val2"]
        final SampleCreator sampleCreator = new StaticLablesSampleCreator(labelsMap)

        given:
            final List<Sample> samples = generateSummarySamples(
                    labelsMap.keySet() as List,
                    labelsMap.values() as List,
                    1000)
            final MetricFamilySamples metricFamilySamples = new MetricFamilySamples(NAME, SUMMARY, HELP, samples)
            final Summary summary = new SummaryBuilder(NAME, HELP).withClock(clock).build()
            1.upto(1000, {
                clock.tick = it - 1
                summary.observe(it)
            })
        when:
            actualMetricFamilySamples.samples as Set == metricFamilySamples.samples as Set
            actualMetricFamilySamples.name == metricFamilySamples.name
            actualMetricFamilySamples.help == metricFamilySamples.help
            actualMetricFamilySamples.type == metricFamilySamples.type
        then:
            summary.getSample(sampleCreator) == metricFamilySamples

        // static labels for sampleConsumer are handled at the collector levels so testing this scenario is not needed for it
    }

    def 'Summary with labels should return correct samples after some measurements'() {
        final List<String> labelNames = ["label1", "label2"]
        final List<String> labelValues1 = ["value1", "value2"]
        final List<String> labelValues2 = ["value3", "value4"]

        given:
            final List<Sample> samples1 = generateSummarySamples(labelNames, labelValues1, 1000)
            final List<Sample> samples2 = generateSummarySamples(labelNames, labelValues2, 1000)

            final MetricFamilySamples metricFamilySamples = new MetricFamilySamples(
                    NAME,
                    SUMMARY,
                    HELP,
                    (samples1 + samples2) as List)

            final Summary summary = new SummaryBuilder(NAME, HELP).withClock(clock)
                                                                  .
                    withReservoir().
                    withExponentiallyDecayingReservoir(
                            1028,
                            0.15) //Use a "custom" reservoir just for the sake of having compilation error if this is not working
                                                                  .
                    withLabels(labelNames as String[])
                                                                  .
                    build()
            1.upto(1000, {
                clock.tick = it - 1
                summary.observe(it, labelValues1 as String[])
                summary.observe(it, labelValues2 as String[])
            })

        when:
            final MetricFamilySamples actualMetricFamilySamples = summary.getSample(sampleCreator)
        then:
            actualMetricFamilySamples.samples as Set == metricFamilySamples.samples as Set
            actualMetricFamilySamples.name == metricFamilySamples.name
            actualMetricFamilySamples.help == metricFamilySamples.help
            actualMetricFamilySamples.type == metricFamilySamples.type
    }

    def 'Timer should add one sample for each time it is started and then stopped'() {
        given:
            long startTime = System.currentTimeMillis()
            clock.tick = startTime
            final List<Sample> samples = generateSummarySamples([], [], 1000, 1)
            final MetricFamilySamples metricFamilySamples = new MetricFamilySamples(NAME, SUMMARY, HELP, samples)
            final double sum = (1..1000).sum() as double
            final Summary summary = new SummaryBuilder(NAME, HELP).withClock(clock).
                    withReservoir().withExponentiallyDecayingReservoir(1024, 0.00001).build()

            //Make a 1000 measurements with values 1, 2, 3, ... 1000
            1.upto(1000, {
                clock.tick = startTime
                final com.outbrain.swinfra.metrics.timing.Timer timer = summary.startTimer()
                clock.tick = startTime + it
                timer.stop()
            })

        when:
            final MetricFamilySamples actualMetricFamilySamples = summary.getSample(sampleCreator)
        then:
            actualMetricFamilySamples.samples as Set == metricFamilySamples.samples as Set
            actualMetricFamilySamples.name == metricFamilySamples.name
            actualMetricFamilySamples.help == metricFamilySamples.help
            actualMetricFamilySamples.type == metricFamilySamples.type

        when:
            summary.forEachSample(sampleConsumer)
        then:
            1 * sampleConsumer.apply(NAME, 500, [], QUANTILE_LABEL, '0.5')
            1 * sampleConsumer.apply(NAME, 750, [], QUANTILE_LABEL, '0.75')
            1 * sampleConsumer.apply(NAME, 950, [], QUANTILE_LABEL, '0.95')
            1 * sampleConsumer.apply(NAME, 980, [], QUANTILE_LABEL, '0.98')
            1 * sampleConsumer.apply(NAME, 990, [], QUANTILE_LABEL, '0.99')
            1 * sampleConsumer.apply(NAME, 999, [], QUANTILE_LABEL, '0.999')
            1 * sampleConsumer.apply(SUM_NAME, sum, [], null, null)
            1 * sampleConsumer.apply(COUNT_NAME, 1000, [], null, null)
    }

    def 'Summary without labels should throw an exception when attempting to observe a value with labels'() {
        given:
            final Summary summary = new SummaryBuilder(NAME, HELP).build()

        when:
            summary.observe(1, "labelValue")

        then:
            thrown(IllegalArgumentException.class)
    }

    @Unroll
    def 'Summary with labels should throw an exception when attempting to observe a value with labels #labels'() {
        given:
            final Summary summary = new SummaryBuilder(NAME, HELP).withLabels("l1", "l2").build()

        when:
            summary.observe(1, labels as String[])

        then:
            thrown(IllegalArgumentException.class)

        where:
            labels << [[], ["v1", ""], ["v1", "v2", "v3"]]
    }

    private static List<Sample> generateSummarySamples(
            final List<String> labelNames,
            final List<String> labelValues,
            final int count) {
        return generateSummarySamples(labelNames, labelValues, count, 0)
    }

    private static List<Sample> generateSummarySamples(
            final List<String> labelNames,
            final List<String> labelValues,
            final int count,
            final int valueShift) {
        [
                sampleForQuantile("0.5", count * 0.5 + valueShift, labelNames, labelValues),
                sampleForQuantile("0.75", count * 0.75 + valueShift, labelNames, labelValues),
                sampleForQuantile("0.95", count * 0.95 + valueShift, labelNames, labelValues),
                sampleForQuantile("0.98", count * 0.98 + valueShift, labelNames, labelValues),
                sampleForQuantile("0.99", count * 0.99 + valueShift, labelNames, labelValues),
                sampleForQuantile("0.999", count * 0.999 + valueShift, labelNames, labelValues),
                new Sample(COUNT_NAME, labelNames, labelValues, count),
                new Sample(SUM_NAME, labelNames, labelValues, (0..count).sum() as int)
        ]
    }

    private static Sample sampleForQuantile(final String quantile,
                                            final double value,
                                            final List<String> labelNames,
                                            final List<String> labelValues) {
        final List<String> labels = labelNames + [QUANTILE_LABEL]
        final List<String> values = labelValues + quantile
        return new Sample(NAME, labels, values, value)
    }
}
