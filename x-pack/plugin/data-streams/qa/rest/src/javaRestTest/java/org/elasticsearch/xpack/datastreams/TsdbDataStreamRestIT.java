/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.datastreams;

import org.elasticsearch.client.Request;
import org.elasticsearch.common.time.DateFormatter;
import org.elasticsearch.common.time.FormatNames;
import org.elasticsearch.test.rest.ESRestTestCase;
import org.elasticsearch.test.rest.yaml.ObjectPath;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static org.elasticsearch.cluster.metadata.DataStreamTestHelper.backingIndexEqualTo;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class TsdbDataStreamRestIT extends ESRestTestCase {

    private static final String TEMPLATE = """
        {
            "index_patterns": ["k8s*"],
            "template": {
                "settings":{
                    "index": {
                        "number_of_replicas": 0,
                        "number_of_shards": 2,
                        "mode": "time_series",
                        "routing_path": ["metricset", "time_series_dimension"]
                    }
                },
                "mappings":{
                    "properties": {
                        "@timestamp" : {
                            "type": "date"
                        },
                        "metricset": {
                            "type": "keyword",
                            "time_series_dimension": true
                        },
                        "k8s": {
                            "properties": {
                                "pod": {
                                    "properties": {
                                        "uid": {
                                            "type": "keyword",
                                            "time_series_dimension": true
                                        },
                                        "name": {
                                            "type": "keyword"
                                        },
                                        "ip": {
                                            "type": "ip"
                                        },
                                        "network": {
                                            "properties": {
                                                "tx": {
                                                    "type": "long"
                                                },
                                                "rx": {
                                                    "type": "long"
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            "data_stream": {}
        }""";

    private static final String DOC = """
        {
            "@timestamp": "$time",
            "metricset": "pod",
            "k8s": {
                "pod": {
                    "name": "dog",
                    "uid":"df3145b3-0563-4d3b-a0f7-897eb2876ea9",
                    "ip": "10.10.55.3",
                    "network": {
                        "tx": 1434595272,
                        "rx": 530605511
                    }
                }
            }
        }
        """;

    public void testTsdbDataStreams() throws Exception {
        // Create a template
        var putComposableIndexTemplateRequest = new Request("POST", "/_index_template/1");
        putComposableIndexTemplateRequest.setJsonEntity(TEMPLATE);
        assertOK(client().performRequest(putComposableIndexTemplateRequest));

        var bulkRequest = new Request("POST", "/k8s/_bulk");
        bulkRequest.setJsonEntity(
            """
                {"create": {}}
                {"@timestamp": "$now", "metricset": "pod", "k8s": {"pod": {"name": "cat", "uid":"947e4ced-1786-4e53-9e0c-5c447e959507", "ip": "10.10.55.1", "network": {"tx": 2001818691, "rx": 802133794}}}}
                {"create": {}}
                {"@timestamp": "$now", "metricset": "pod", "k8s": {"pod": {"name": "cat", "uid":"947e4ced-1786-4e53-9e0c-5c447e959507", "ip": "10.10.55.1", "network": {"tx": 2005177954, "rx": 801479970}}}}
                {"create": {}}
                {"@timestamp": "$now", "metricset": "pod", "k8s": {"pod": {"name": "cat", "uid":"947e4ced-1786-4e53-9e0c-5c447e959507", "ip": "10.10.55.1", "network": {"tx": 2006223737, "rx": 802337279}}}}
                {"create": {}}
                {"@timestamp": "$now", "metricset": "pod", "k8s": {"pod": {"name": "cat", "uid":"947e4ced-1786-4e53-9e0c-5c447e959507", "ip": "10.10.55.2", "network": {"tx": 2012916202, "rx": 803685721}}}}
                {"create": {}}
                {"@timestamp": "$now", "metricset": "pod", "k8s": {"pod": {"name": "dog", "uid":"df3145b3-0563-4d3b-a0f7-897eb2876ea9", "ip": "10.10.55.3", "network": {"tx": 1434521831, "rx": 530575198}}}}
                {"create": {}}
                {"@timestamp": "$now", "metricset": "pod", "k8s": {"pod": {"name": "dog", "uid":"df3145b3-0563-4d3b-a0f7-897eb2876ea9", "ip": "10.10.55.3", "network": {"tx": 1434577921, "rx": 530600088}}}}
                {"create": {}}
                {"@timestamp": "$now", "metricset": "pod", "k8s": {"pod": {"name": "dog", "uid":"df3145b3-0563-4d3b-a0f7-897eb2876ea9", "ip": "10.10.55.3", "network": {"tx": 1434587694, "rx": 530604797}}}}
                {"create": {}}
                {"@timestamp": "$now", "metricset": "pod", "k8s": {"pod": {"name": "dog", "uid":"df3145b3-0563-4d3b-a0f7-897eb2876ea9", "ip": "10.10.55.3", "network": {"tx": 1434595272, "rx": 530605511}}}}
                """
                .replace("$now", formatInstant(Instant.now()))
        );
        bulkRequest.addParameter("refresh", "true");
        assertOK(client().performRequest(bulkRequest));

        var getDataStreamsRequest = new Request("GET", "/_data_stream");
        var response = client().performRequest(getDataStreamsRequest);
        assertOK(response);
        var dataStreams = entityAsMap(response);
        assertThat(ObjectPath.evaluate(dataStreams, "data_streams"), hasSize(1));
        assertThat(ObjectPath.evaluate(dataStreams, "data_streams.0.name"), equalTo("k8s"));
        assertThat(ObjectPath.evaluate(dataStreams, "data_streams.0.generation"), equalTo(1));
        assertThat(ObjectPath.evaluate(dataStreams, "data_streams.0.template"), equalTo("1"));
        assertThat(ObjectPath.evaluate(dataStreams, "data_streams.0.indices"), hasSize(1));
        String firstBackingIndex = ObjectPath.evaluate(dataStreams, "data_streams.0.indices.0.index_name");
        assertThat(firstBackingIndex, backingIndexEqualTo("k8s", 1));

        var indices = getIndex(firstBackingIndex);
        var escapedBackingIndex = firstBackingIndex.replace(".", "\\.");
        assertThat(ObjectPath.evaluate(indices, escapedBackingIndex + ".data_stream"), equalTo("k8s"));
        assertThat(ObjectPath.evaluate(indices, escapedBackingIndex + ".settings.index.mode"), equalTo("time_series"));
        String startTimeFirstBackingIndex = ObjectPath.evaluate(indices, escapedBackingIndex + ".settings.index.time_series.start_time");
        assertThat(startTimeFirstBackingIndex, notNullValue());
        String endTimeFirstBackingIndex = ObjectPath.evaluate(indices, escapedBackingIndex + ".settings.index.time_series.end_time");
        assertThat(endTimeFirstBackingIndex, notNullValue());

        var rolloverRequest = new Request("POST", "/k8s/_rollover");
        assertOK(client().performRequest(rolloverRequest));

        response = client().performRequest(getDataStreamsRequest);
        assertOK(response);
        dataStreams = entityAsMap(response);
        assertThat(ObjectPath.evaluate(dataStreams, "data_streams.0.name"), equalTo("k8s"));
        assertThat(ObjectPath.evaluate(dataStreams, "data_streams.0.generation"), equalTo(2));
        String secondBackingIndex = ObjectPath.evaluate(dataStreams, "data_streams.0.indices.1.index_name");
        assertThat(secondBackingIndex, backingIndexEqualTo("k8s", 2));

        indices = getIndex(secondBackingIndex);
        escapedBackingIndex = secondBackingIndex.replace(".", "\\.");
        assertThat(ObjectPath.evaluate(indices, escapedBackingIndex + ".data_stream"), equalTo("k8s"));
        String startTimeSecondBackingIndex = ObjectPath.evaluate(indices, escapedBackingIndex + ".settings.index.time_series.start_time");
        assertThat(startTimeSecondBackingIndex, equalTo(endTimeFirstBackingIndex));
        String endTimeSecondBackingIndex = ObjectPath.evaluate(indices, escapedBackingIndex + ".settings.index.time_series.end_time");
        assertThat(endTimeSecondBackingIndex, notNullValue());

        var indexRequest = new Request("POST", "/k8s/_doc");
        Instant time = parseInstant(startTimeFirstBackingIndex);
        indexRequest.setJsonEntity(DOC.replace("$time", formatInstant(time)));
        response = client().performRequest(indexRequest);
        assertOK(response);
        assertThat(entityAsMap(response).get("_index"), equalTo(firstBackingIndex));

        indexRequest = new Request("POST", "/k8s/_doc");
        time = parseInstant(endTimeSecondBackingIndex).minusMillis(1);
        indexRequest.setJsonEntity(DOC.replace("$time", formatInstant(time)));
        response = client().performRequest(indexRequest);
        assertOK(response);
        assertThat(entityAsMap(response).get("_index"), equalTo(secondBackingIndex));
    }

    public void testSimulateTsdbDataStreamTemplate() throws Exception {
        var putComposableIndexTemplateRequest = new Request("POST", "/_index_template/1");
        putComposableIndexTemplateRequest.setJsonEntity(TEMPLATE);
        assertOK(client().performRequest(putComposableIndexTemplateRequest));

        var simulateIndexTemplateRequest = new Request("POST", "/_index_template/_simulate_index/k8s");
        var response = client().performRequest(simulateIndexTemplateRequest);
        assertOK(response);
        var responseBody = entityAsMap(response);
        assertThat(ObjectPath.evaluate(responseBody, "template.settings.index"), aMapWithSize(4));
        assertThat(ObjectPath.evaluate(responseBody, "template.settings.index.number_of_shards"), equalTo("2"));
        assertThat(ObjectPath.evaluate(responseBody, "template.settings.index.number_of_replicas"), equalTo("0"));
        assertThat(ObjectPath.evaluate(responseBody, "template.settings.index.mode"), equalTo("time_series"));
        assertThat(
            ObjectPath.evaluate(responseBody, "template.settings.index.routing_path"),
            contains("metricset", "time_series_dimension")
        );
        assertThat(ObjectPath.evaluate(responseBody, "overlapping"), empty());
    }

    public void testSubsequentRollovers() throws Exception {
        // Create a template
        var putComposableIndexTemplateRequest = new Request("POST", "/_index_template/1");
        putComposableIndexTemplateRequest.setJsonEntity(TEMPLATE);
        assertOK(client().performRequest(putComposableIndexTemplateRequest));

        var createDataStreamRequest = new Request("PUT", "/_data_stream/k8s");
        assertOK(client().performRequest(createDataStreamRequest));

        int numRollovers = 16;
        for (int i = 0; i < numRollovers; i++) {
            var rolloverRequest = new Request("POST", "/k8s/_rollover?pretty");
            var rolloverResponse = client().performRequest(rolloverRequest);
            assertOK(rolloverResponse);
            var rolloverResponseBody = entityAsMap(rolloverResponse);
            assertThat(rolloverResponseBody.get("rolled_over"), is(true));

            var oldIndex = getIndex((String) rolloverResponseBody.get("old_index"));
            var newIndex = getIndex((String) rolloverResponseBody.get("new_index"));
            assertThat(getEndTime(oldIndex), equalTo(getStartTime(newIndex)));
            assertThat(getStartTime(oldIndex).isBefore(getEndTime(oldIndex)), is(true));
            assertThat(getEndTime(newIndex).isAfter(getStartTime(newIndex)), is(true));
        }
    }

    private static Map<?, ?> getIndex(String indexName) throws IOException {
        var getIndexRequest = new Request("GET", "/" + indexName + "?human");
        var response = client().performRequest(getIndexRequest);
        assertOK(response);
        return entityAsMap(response);
    }

    private static Instant getStartTime(Map<?, ?> getIndexResponse) throws IOException {
        assert getIndexResponse.keySet().size() == 1;
        String topLevelKey = (String) getIndexResponse.keySet().iterator().next();
        String val = ObjectPath.evaluate(getIndexResponse.get(topLevelKey), "settings.index.time_series.start_time");
        return Instant.from(DateFormatter.forPattern(FormatNames.STRICT_DATE_OPTIONAL_TIME.getName()).parse(val));
    }

    private static Instant getEndTime(Map<?, ?> getIndexResponse) throws IOException {
        assert getIndexResponse.keySet().size() == 1;
        String topLevelKey = (String) getIndexResponse.keySet().iterator().next();
        String val = ObjectPath.evaluate(getIndexResponse.get(topLevelKey), "settings.index.time_series.end_time");
        return Instant.from(DateFormatter.forPattern(FormatNames.STRICT_DATE_OPTIONAL_TIME.getName()).parse(val));
    }

    static String formatInstant(Instant instant) {
        return DateFormatter.forPattern(FormatNames.STRICT_DATE_OPTIONAL_TIME.getName()).format(instant);
    }

    static Instant parseInstant(String input) {
        return Instant.from(DateFormatter.forPattern(FormatNames.STRICT_DATE_OPTIONAL_TIME.getName()).parse(input));
    }

}
