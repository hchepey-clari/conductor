package com.netflix.conductor.server.metrics;

import org.coursera.metrics.datadog.DefaultMetricNameFormatter;
import org.coursera.metrics.datadog.MetricNameFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiPredicate;

/**
 * Class for formatting the metric name to appropriate tag format for the datadog reporter
 * MetricRegistry is ingested to the Conductor's Spectator metric library to gather the metrics and org.coursera:metrics-datadog is used to periodically upload metrics.
 * Metric Name format used by MetricRegistry is not compatible with datadog reporter for resolving tags by default.
 *
 * <p>
 * Reference: @see <a href="https://github.com/coursera/metrics-datadog#tag-encoding-and-expansion">Coursera Datadog Reporter tags</a>
 */
public class ConductorMetricsNameFormatter implements MetricNameFormatter
{
    private final Logger LOGGER = LoggerFactory.getLogger(ConductorMetricsNameFormatter.class);

    private final BiPredicate<String, String> metricsTagFilter;  // Predicate which filters tags for the given metric
    private final MetricTagValueFormatter metricTagValueFormatter;

    ConductorMetricsNameFormatter(BiPredicate<String, String> metricsTagFilter, MetricTagValueFormatter metricTagValueFormatter)
    {
        this.metricsTagFilter = metricsTagFilter;
        this.metricTagValueFormatter = metricTagValueFormatter;
    }

    private final MetricNameFormatter fallbackFormatter = new DefaultMetricNameFormatter();

    /**
     * Formats the name of the metric to "metricName[tagName:tagValue,tagName:tagValue,...]" format
     *
     * For example, the metric name "event_queue_messages_processed.class-WorkflowMonitor.queueName-basic_dag:start_workflow_with_event.queueType-conductor"
     * is converted to format with tags "event_queue_messages_processed.count[queueName:basic_dag:start_workflow_with_event,queueType:conductor]"
     *
     * @param name the name of the metric in the format "metric_name.tagName1-tagValue1.tagName2-tagValue2"
     * @param path the expansion of the metric like 'count', 'mean', 'p99'
     * @return metric name compatible with datadog reporter
     */
    @Override
    public String format(String name, String... path)
    {
        try {
            final String[] segments = name.split("\\.");
            String metricName = segments[0];

            final StringBuilder metricNameBuilder = new StringBuilder();
            metricNameBuilder.append(segments[0]);

            for (String part : path) {
                metricNameBuilder.append('.').append(part);
            }

            metricNameBuilder.append("[");
            String prefix = "";

            for (int idx = 1; idx < segments.length; idx++) {
                // Get the tagName and tagValue
                final String tagName = segments[idx].substring(0, segments[idx].indexOf("-"));
                final String tagVal = segments[idx].substring(segments[idx].indexOf("-") + 1);

                if (metricsTagFilter.test(metricName, tagName)) {
                    metricNameBuilder.append(prefix);
                    String formattedTagVal = metricTagValueFormatter.getFormattedTagValue(metricName, tagName, tagVal);
                    metricNameBuilder.append(String.format("%s:%s", tagName, formattedTagVal));
                    prefix = ",";
                }
            }

            metricNameBuilder.append("]");

            return metricNameBuilder.toString();
        }
        catch (Exception ex) {
            LOGGER.error("Exception formatting the metric name {} with message {}", name, ex.getMessage());
            // In case of exception, use the fallback default formatter
            return fallbackFormatter.format(name, path);
        }
    }
}
