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
package com.clarius.conductor.server.metrics;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.coursera.metrics.datadog.MetricNameFormatter;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.netflix.conductor.server.metrics.ConductorMetricsNameFormatter;
import com.netflix.conductor.server.metrics.MetricTagFilter;
import com.netflix.conductor.server.metrics.MetricTagValueFormatter;

public class ConductorMetricsNameFormatterTest {
    @DataProvider(name = "metricsFilterTestDataProvider")
    public Object[][] metricsFilterTestDataProvider() {
        return new Object[][] {
            // Verify 'class' tag is removed.
            new Object[] {
                "event_queue_messages_processed.class-WorkflowMonitor.queueName-basic_dag:start_workflow_with_event.queueType-conductor",
                "event_queue_messages_processed.count[queueName:basic_dag:start_workflow_with_event,queueType:conductor]"
            },
            // Verify both 'taskType' and 'percentile' tags are removed.
            new Object[] {
                "task_queue_wait.class-WorkflowMonitor.taskType-config-schema_2_case_dag.percentile-t0073",
                "task_queue_wait.count[]"
            },
            // Verify only 'percentile' tag is removed.
            new Object[] {
                "task_execution.class-WorkflowMonitor.taskType-sf_history_current_crawl.percentile-t0073.status-COMPLETED",
                "task_execution.count[taskType:sf_history_current_crawl,status:COMPLETED]"
            },
            // Verify the taskType tag's value is formatted as per the config
            new Object[] {
                "system_task_completed.class-WorkflowMonitor.taskType-extract_subworkflow_clari_basic_extract_incremental_3_INDEXES_email_data_dag",
                "system_task_completed.count[taskType:extract_subworkflow_clari_basic_extract_incremental]"
            }
        };
    }

    @Test(dataProvider = "metricsFilterTestDataProvider")
    public void testMetricsFilter(String unFormattedName, String expectedFormattedName) {
        Map<String, List<String>> metricTagConfig =
                Collections.singletonMap("task_queue_wait", Collections.singletonList("taskType"));
        MetricTagFilter tagFilter = new MetricTagFilter(metricTagConfig);
        MetricTagValueFormatter tagValueFormatter =
                new MetricTagValueFormatter(
                        Collections.singletonMap(
                                "system_task_completed",
                                Collections.singletonMap("taskType", "([A-Za-z_]+[A-Za-z]{1})")));
        MetricNameFormatter metricsNameFormatter =
                new ConductorMetricsNameFormatter(tagFilter, tagValueFormatter);

        String formattedName = metricsNameFormatter.format(unFormattedName, "count");

        Assert.assertEquals(formattedName, expectedFormattedName);
    }
}
