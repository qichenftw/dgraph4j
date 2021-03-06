package io.dgraph;

import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import io.opencensus.common.Scope;
import io.opencensus.exporter.trace.jaeger.JaegerTraceExporter;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.config.TraceParams;
import io.opencensus.trace.samplers.Samplers;
import org.junit.Test;

public class OpencensusJaegerTest extends DgraphIntegrationTest {
    public static final String JAEGER_COLLECTOR = "http://localhost:14268/api/traces";

    @Test
    public void testOpencensusJaeger() {
        // 1. configure the jaeger exporter
        JaegerTraceExporter.createAndRegister(JAEGER_COLLECTOR, "my-service");

        // 2. Configure 100% sample rate, otherwise, few traces will be sampled.
        TraceConfig traceConfig = Tracing.getTraceConfig();
        TraceParams activeTraceParams = traceConfig.getActiveTraceParams();
        traceConfig.updateActiveTraceParams(
            activeTraceParams.toBuilder().setSampler(
                Samplers.alwaysSample()).build());

        // 3. Get the global singleton Tracer object.
        Tracer tracer = Tracing.getTracer();

        // 4. Create a scoped span, a scoped span will automatically end when closed.
        // It implements AutoClosable, so it'll be closed when the try block ends.
        try (Scope scope = tracer.spanBuilder("query").startScopedSpan()) {
            runTransactions();
        }

        // 5. Gracefully shutdown the exporter, so that it'll flush queued traces to Jaeger.
        Tracing.getExportComponent().shutdown();
    }

    private static void runTransactions() {
        // change schema
        DgraphProto.Operation op =
            DgraphProto.Operation.newBuilder().setSchema("name: string @index(fulltext) @upsert .").build();
        dgraphClient.alter(op);

        // Add data
        JsonObject json = new JsonObject();
        json.addProperty("name", "Alice");

        DgraphProto.Mutation mu =
            DgraphProto.Mutation.newBuilder()
                .setCommitNow(true)
                .setSetJson(ByteString.copyFromUtf8(json.toString()))
                .build();
        dgraphClient.newTransaction().mutate(mu);
    }
}
