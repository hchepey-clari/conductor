/*
 * Copyright 2025 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
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
public class MetricTagFilter implements BiPredicate<String, String> {
    private final Map<String, List<String>> metricTagFilterConfig;

    /*Following tags are ignored from all the metrics published to datadog,
    a) "class" : All the metrics emitter from conductor server has same tag naming 'class' and this is not useful and hence ignoring this tag
    b) "percentile": The cardinality of this tags is so high and leads to explosion of metrics.
    */
    Set<String> defaultIgnoredTags = new HashSet<>(Arrays.asList("class", "percentile"));

    MetricTagFilter(Map<String, List<String>> metricTagFilterConfig) {
        this.metricTagFilterConfig = metricTagFilterConfig;
    }

    @Override
    public boolean test(String metricName, String tagName) {
        return !(defaultIgnoredTags.contains(tagName)
                || metricTagFilterConfig
                        .getOrDefault(metricName, Collections.emptyList())
                        .contains(tagName));
    }
}
