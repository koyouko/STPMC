package com.stp.missioncontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.stp.missioncontrol.config.AppProperties;
import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.model.MetricsTarget;
import com.stp.missioncontrol.repository.MetricsTargetRepository;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the scraper's parse + extract path tolerates the lowercase
 * metric names and label keys emitted by the Prometheus JMX Exporter when the
 * Confluent kafka_broker.yml sets {@code lowercaseOutputName: true} and
 * {@code lowercaseOutputLabelNames: true}.
 *
 * <p>Regression guard for the case mismatch that made scrapes succeed HTTP-wise
 * but produce all -1 metric values and a null discoveredClusterId.
 */
class MetricsScraperServiceParseTests {

    private static final String LOWERCASE_FIXTURE = """
            # HELP jvm_memory_bytes_used Used bytes of a given JVM memory area.
            # TYPE jvm_memory_bytes_used gauge
            jvm_memory_bytes_used{area="heap"} 2.147483648E9
            jvm_memory_bytes_max{area="heap"} 1.7179869184E10
            kafka_server_kafkaserver_clusterid{clusterid="aB12_3cD-xyz"} 1.0
            kafka_server_brokertopicmetrics_messagesinpersec_oneminuterate 4500.5
            kafka_server_brokertopicmetrics_bytesinpersec_oneminuterate 1048576.0
            kafka_server_brokertopicmetrics_bytesoutpersec_oneminuterate 2097152.0
            kafka_server_replicamanager_underreplicatedpartitions 0.0
            kafka_server_replicamanager_leadercount 120.0
            kafka_server_replicamanager_partitioncount 360.0
            kafka_server_replicamanager_isrshrinkspersec_oneminuterate 0.0
            kafka_server_replicamanager_isrexpandspersec_oneminuterate 0.0
            kafka_controller_kafkacontroller_activecontrollercount 1.0
            kafka_controller_kafkacontroller_offlinepartitionscount 0.0
            kafka_server_kafkaserver_brokerstate 3.0
            kafka_server_kafkarequesthandlerpool_requesthandleravgidlepercent_total 0.87
            process_start_time_seconds 1.0
            """;

    @Test
    void extractsMetricsFromLowercaseExporterOutput() throws Exception {
        MetricsScraperService service = newService();

        Method parse = MetricsScraperService.class.getDeclaredMethod("parsePrometheusText", String.class);
        parse.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> samples = (List<Object>) parse.invoke(service, LOWERCASE_FIXTURE);
        assertTrue(samples.size() >= 14, "Expected all fixture lines to parse, got " + samples.size());

        MetricsTarget target = new MetricsTarget();
        target.setHost("broker1.example.net");
        target.setMetricsPort(9404);
        target.setRole("BROKER");

        Method extract = MetricsScraperService.class.getDeclaredMethod(
                "extractBrokerMetrics", MetricsTarget.class, long.class, List.class);
        extract.setAccessible(true);
        ApiDtos.BrokerMetricsSample sample =
                (ApiDtos.BrokerMetricsSample) extract.invoke(service, target, 42L, samples);

        assertEquals("aB12_3cD-xyz", sample.discoveredClusterId(),
                "Cluster ID label value must survive lowercase label-key normalization");
        assertEquals(4500.5, sample.messagesInPerSec(), 1e-6);
        assertEquals(1048576.0, sample.bytesInPerSec(), 1e-6);
        assertEquals(2097152.0, sample.bytesOutPerSec(), 1e-6);
        assertEquals(0.0, sample.underReplicatedPartitions(), 1e-6);
        assertEquals(1.0, sample.activeControllerCount(), 1e-6);
        assertEquals(0.0, sample.offlinePartitionsCount(), 1e-6);
        assertEquals(3.0, sample.brokerState(), 1e-6);
        assertEquals(120.0, sample.leaderCount(), 1e-6);
        assertEquals(360.0, sample.partitionCount(), 1e-6);
        assertEquals(0.87, sample.requestHandlerIdle(), 1e-6,
                "_total suffix variant must be picked up as a fallback");
        assertEquals(2.147483648E9, sample.heapUsedBytes(), 1e-6);
        assertEquals(1.7179869184E10, sample.heapMaxBytes(), 1e-6);
        assertNotNull(sample.scrapedAt());
        // Uptime must be non-negative (process_start_time_seconds was in the fixture)
        assertTrue(sample.uptimeSeconds() >= 0, "uptime should resolve when process_start_time_seconds present");
    }

    @Test
    void mixedCaseInputIsAlsoTolerated() throws Exception {
        // An exporter configured WITHOUT lowercaseOutputName would emit mixed case.
        // The normalization at parse time must make this also work.
        String mixed = """
                kafka_server_KafkaServer_ClusterId{clusterId="MIXED-CASE-UUID"} 1.0
                kafka_server_BrokerTopicMetrics_MessagesInPerSec_OneMinuteRate 10.0
                """;
        MetricsScraperService service = newService();

        Method parse = MetricsScraperService.class.getDeclaredMethod("parsePrometheusText", String.class);
        parse.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> samples = (List<Object>) parse.invoke(service, mixed);

        MetricsTarget target = new MetricsTarget();
        target.setHost("broker2.example.net");
        target.setMetricsPort(9404);
        target.setRole("BROKER");

        Method extract = MetricsScraperService.class.getDeclaredMethod(
                "extractBrokerMetrics", MetricsTarget.class, long.class, List.class);
        extract.setAccessible(true);
        ApiDtos.BrokerMetricsSample sample =
                (ApiDtos.BrokerMetricsSample) extract.invoke(service, target, 10L, samples);

        assertEquals("MIXED-CASE-UUID", sample.discoveredClusterId());
        assertEquals(10.0, sample.messagesInPerSec(), 1e-6);
    }

    private static MetricsScraperService newService() {
        AppProperties properties = new AppProperties(
                new AppProperties.Security("dev", List.of(), null),
                new AppProperties.Health(0, 0, 0, 0, 0),
                new AppProperties.Metrics(5000, 0, 0),
                new AppProperties.Defaults(false, false, null, null)
        );
        return new MetricsScraperService(
                mock(MetricsTargetRepository.class),
                mock(ClusterService.class),
                properties,
                Executors.newSingleThreadExecutor()
        );
    }
}
