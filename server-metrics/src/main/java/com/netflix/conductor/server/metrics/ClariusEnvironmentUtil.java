package com.netflix.conductor.server.metrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class ClariusEnvironmentUtil
{
    private static final String CLARIUS_ENVIRONMENT = "CLARIUS_ENVIRONMENT";
    private static final String CONDUCTOR_DATADOG_METRICS_ENABLED_KEY = "CONDUCTOR_DATADOG_METRICS_ENABLED";
    private static final boolean CONDUCTOR_DATADOG_METRICS_ENABLED_VALUE = true;

    enum ClariusEnvironment
    {
        DEVELOPMENT,
        STAGING,
        STEELIX,
        PRODUCTION,
        SANDBOX;

        @Override
        public String toString()
        {
            return super.toString().toLowerCase();
        }

        public boolean in(ClariusEnvironment... environments)
        {
            return Arrays.stream(environments).anyMatch(v -> v == this);
        }
    }

    static ClariusEnvironment getDeploymentEnvironment()
    {
        ClariusEnvironment deployEnv = ClariusEnvironment.DEVELOPMENT;

        if (System.getenv().containsKey(CLARIUS_ENVIRONMENT)) {
            deployEnv = ClariusEnvironment.valueOf(System.getenv().get(CLARIUS_ENVIRONMENT).toUpperCase());
        }

        return deployEnv;
    }

    static int getStatSDPORT()
    {
        int dogStatSDPORT = 8125;

        if (System.getenv().containsKey("STATSD_PORT")) {
            dogStatSDPORT = Integer.parseInt(System.getenv().get("STATSD_PORT"));
        }

        return dogStatSDPORT;
    }

    static String getStatSDHOST()
    {
        String host = "localhost";

        if (System.getenv().containsKey("STATSD_HOST")) {
            host = System.getenv().get("STATSD_HOST");
        }

        return host;
    }

    static List<String> getAdditionalTags()
    {
        List<String> tags = new ArrayList<>();
        tags.add("clari_comp:server");

        if (System.getenv().containsKey("OTEL_SERVICE_NAME")) {
            tags.add("service.name:" + System.getenv().get("OTEL_SERVICE_NAME"));
            tags.add("service:" + System.getenv().get("OTEL_SERVICE_NAME"));
            tags.add("clari_app:" + System.getenv().get("OTEL_SERVICE_NAME"));

            if (System.getenv().containsKey(CLARIUS_ENVIRONMENT)) {
                tags.add("chef_asg:" + System.getenv().get("OTEL_SERVICE_NAME") + "-server-" + System.getenv().get(CLARIUS_ENVIRONMENT).toLowerCase());
            }
        }

        return tags;
    }

    static boolean isConductorDatadogMetricsEnabled()
    {
        return System.getenv().containsKey(CONDUCTOR_DATADOG_METRICS_ENABLED_KEY) ? Boolean.parseBoolean(System.getenv().get(CONDUCTOR_DATADOG_METRICS_ENABLED_KEY)) : CONDUCTOR_DATADOG_METRICS_ENABLED_VALUE;
    }
}

