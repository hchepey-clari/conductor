package com.netflix.conductor.server.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Class for formatting the tag value based on the regex pattern configured for given metric and tag pair
 */
public class MetricTagValueFormatter
{
    private final Map<String, Map<String, Pattern>> metricToTagNameRegexMatchMap;
    private final Logger logger = LoggerFactory.getLogger(MetricTagValueFormatter.class);

    public MetricTagValueFormatter(Map<String, Map<String, String>> metricToTagNameRegexMatchStrMap)
    {
        //Convert the mapping of  string regex to Pattern object
        this.metricToTagNameRegexMatchMap = metricToTagNameRegexMatchStrMap.entrySet()
                                                                           .stream()
                                                                           .collect(Collectors.toMap(Map.Entry::getKey,
                                                                                                     entry -> compileRegexStrToPatternMap(entry.getValue())));
    }

    private Map<String, Pattern> compileRegexStrToPatternMap(Map<String, String> tagNameToRegexStrMap)
    {
        return tagNameToRegexStrMap.entrySet().stream()
                                   .collect(Collectors.toMap(Map.Entry::getKey,
                                                             stringPattern -> Pattern.compile(stringPattern.getValue())));
    }

    /**
     * Extracts the tag value based on the regex pattern specified for given metric name and tag name
     * If no config is available for metric/tagName, the tagValue is returned without any modification
     *
     * @param metricName the name of the metric
     * @param tagName    the tag name
     * @param tagValue   the tag value to be formatted according to the defined pattern
     * @return the formatted tag value as per pattern configured
     */
    public String getFormattedTagValue(String metricName, String tagName, String tagValue)
    {
        Pattern matchPattern = metricToTagNameRegexMatchMap.getOrDefault(metricName, Collections.emptyMap()).get(tagName);

        String formattedTagName = tagValue;

        if (matchPattern != null) {
            final Matcher matcher = matchPattern.matcher(tagValue);
            if (matcher.find()) {
                formattedTagName = matcher.group(0);
            }
            else {
                logger.debug("Could not extract matching value for the metric {}, tag {}", metricName, tagName);
            }
        }

        return formattedTagName;
    }
}
