package com.outbrain.swinfra.metrics;

import com.codahale.metrics.Reservoir;
import com.outbrain.swinfra.metrics.children.ChildMetricRepo;
import com.outbrain.swinfra.metrics.children.LabeledChildrenRepo;
import com.outbrain.swinfra.metrics.children.MetricData;
import com.outbrain.swinfra.metrics.children.UnlabeledChildRepo;
import com.outbrain.swinfra.metrics.samples.SampleCreator;
import com.outbrain.swinfra.metrics.utils.QuantileUtils;
import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.outbrain.swinfra.metrics.utils.LabelUtils.commaDelimitedStringToLabels;
import static io.prometheus.client.Collector.Type.SUMMARY;

/**
 * An implementation of a Timer metric. A timer measures events in milliseconds
 */
public class Timer extends AbstractMetric<com.codahale.metrics.Timer> {

  private final double measurementFactor;
  private final Supplier<Reservoir> reservoirSupplier;

  Timer(final String name,
        final String help,
        final String[] labelNames,
        final TimeUnit measurementUnit,
        final Supplier<Reservoir> reservoirSupplier) {
    super(name, help, labelNames);
    this.measurementFactor = 1.0 / measurementUnit.toNanos(1); // if measurementUnit = milliseconds, then measurementUnit.toNanos(1) = 1000000
    this.reservoirSupplier = reservoirSupplier;
  }

  @Override
  ChildMetricRepo<com.codahale.metrics.Timer> createChildMetricRepo() {
    if (getLabelNames().size() == 0) {
      return new UnlabeledChildRepo<>(new MetricData<>(createTimer(), new String[]{}));
    } else {
      return new LabeledChildrenRepo<>(commaDelimitedLabelValues -> {
        final String[] labelValues = commaDelimitedStringToLabels(commaDelimitedLabelValues);
        return new MetricData<>(createTimer(), labelValues);
      });
    }
  }

  private com.codahale.metrics.Timer createTimer() {
    return new com.codahale.metrics.Timer(reservoirSupplier.get());
  }

  @Override
  public Collector.Type getType() {
    return SUMMARY;
  }

  @Override
  List<Sample> createSamples(final MetricData<com.codahale.metrics.Timer> metricData,
                             final SampleCreator sampleCreator) {
    return QuantileUtils.createSamplesFromSnapshot(metricData, getName(), getLabelNames(), measurementFactor, sampleCreator);
  }

  public TimerContext startTimer(final String... labelValues) {
    return TimerContext.startTimer(metricForLabels(labelValues).time());
  }

  public static class TimerContext {

    private final com.codahale.metrics.Timer.Context context;

    private TimerContext(final com.codahale.metrics.Timer.Context context) {
      this.context = context;
    }

    private static TimerContext startTimer(final com.codahale.metrics.Timer.Context context) {
      return new TimerContext(context);
    }

    public void stop() {
      context.stop();
    }
  }

  public static class TimerBuilder extends AbstractMetricBuilderWithReservoirs<Timer, TimerBuilder> {

    private TimeUnit measurementUnit = TimeUnit.NANOSECONDS;

    public TimerBuilder(final String name, final String help) {
      super(name, help);
    }

    public TimerBuilder measureIn(final TimeUnit unit) {
      this.measurementUnit = unit;
      return this;
    }

    protected Timer create(final String fullName, final String help, final String[] labelNames) {
      return new Timer(fullName, help, labelNames, measurementUnit, getReservoirSupplier());
    }
  }
}
