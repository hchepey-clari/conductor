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

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.coursera.metrics.datadog.DatadogReporter;
import org.coursera.metrics.datadog.transport.UdpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.metrics3.MetricsRegistry;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;

@Service
public class SpectatorRegistryModule {
    private final Logger logger = LoggerFactory.getLogger(SpectatorRegistryModule.class);

    private final EnumSet<DatadogReporter.Expansion> DATADOG_EXPANSIONS =
            EnumSet.of(
                    DatadogReporter.Expansion.COUNT,
                    DatadogReporter.Expansion.P75,
                    DatadogReporter.Expansion.P95,
                    DatadogReporter.Expansion.P99);

    @PostConstruct
    protected void configure() {
        MetricRegistry registry = new MetricRegistry();

        MetricsRegistry metricsRegistry = new MetricsRegistry(Clock.SYSTEM, registry);
        Spectator.globalRegistry().add(metricsRegistry);

        ClariusEnvironmentUtil.ClariusEnvironment env =
                ClariusEnvironmentUtil.getDeploymentEnvironment();
        if ((env.in(
                        ClariusEnvironmentUtil.ClariusEnvironment.PRODUCTION,
                        ClariusEnvironmentUtil.ClariusEnvironment.STAGING,
                        ClariusEnvironmentUtil.ClariusEnvironment.STEELIX))
                && (ClariusEnvironmentUtil.isConductorDatadogMetricsEnabled())) {
            initDatadogReporter(registry);
        }

        logger.info("Initialized spectator registry module...");
    }

    private DatadogReporter.Builder getDatadogReporterBuilder(MetricRegistry registry) {
        logger.error(
                "SD_PORT: {} , SD_HOST: {}, Additional Tags: {}",
                ClariusEnvironmentUtil.getStatSDPORT(),
                ClariusEnvironmentUtil.getStatSDHOST(),
                ClariusEnvironmentUtil.getAdditionalTags());
        return new DatadogReporter.Builder(registry)
                .withMetricNameFormatter(
                        new ConductorMetricsNameFormatter(
                                new MetricTagFilter(getMetricTagConfig()),
                                new MetricTagValueFormatter(getMetricTagFormatterConfig())))
                .withExpansions(DATADOG_EXPANSIONS)
                .withTags(ClariusEnvironmentUtil.getAdditionalTags())
                .withTransport(
                        new UdpTransport.Builder()
                                .withPort(ClariusEnvironmentUtil.getStatSDPORT())
                                .withStatsdHost(ClariusEnvironmentUtil.getStatSDHOST())
                                .build());
    }

    private void initDatadogReporter(MetricRegistry registry) {
        long reportFrequency = 1L;

        // Report metrics to DataDog periodically
        DatadogReporter.Builder ddBuilder = getDatadogReporterBuilder(registry);
        ddBuilder.build().start(reportFrequency, TimeUnit.MINUTES);
    }

    private Map<String, List<String>> getMetricTagConfig() {
        Map<String, List<String>> metricTagConfig = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            metricTagConfig =
                    mapper.readValue(
                            this.getClass()
                                    .getClassLoader()
                                    .getResourceAsStream("metric-filter-config.json"),
                            Map.class);
        } catch (IOException ex) {
            logger.error("Error reading the metric tag filter config file ", ex);
        }

        return metricTagConfig;
    }

    private Map<String, Map<String, String>> getMetricTagFormatterConfig() {
        Map<String, Map<String, String>> metricFormatConfig = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            metricFormatConfig =
                    mapper.readValue(
                            this.getClass()
                                    .getClassLoader()
                                    .getResourceAsStream("metric-tag-formatter.json"),
                            Map.class);
        } catch (IOException ex) {
            logger.error("Error reading the metric tag formatter config file ", ex);
        }

        return metricFormatConfig;
    }
}
