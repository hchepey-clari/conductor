package com.netflix.conductor.server.metrics;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/*
    Filter that tags based on the config
 */
public class MetricTagFilter implements BiPredicate<String, String>
{
    private final Map<String, List<String>> metricTagFilterConfig;

    /*Following tags are ignored from all the metrics published to datadog,
    a) "class" : All the metrics emitter from conductor server has same tag naming 'class' and this is not useful and hence ignoring this tag
    b) "percentile": The cardinality of this tags is so high and leads to explosion of metrics.
    */
    Set<String> defaultIgnoredTags = new HashSet<>(Arrays.asList("class", "percentile"));

    MetricTagFilter(Map<String, List<String>> metricTagFilterConfig)
    {
        this.metricTagFilterConfig = metricTagFilterConfig;
    }

    @Override
    public boolean test(String metricName, String tagName)
    {
        return !(defaultIgnoredTags.contains(tagName) || metricTagFilterConfig.getOrDefault(metricName, Collections.emptyList()).contains(tagName));
    }
}
