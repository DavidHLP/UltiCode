package com.ulticode.core;

import com.ulticode.common.lifecycle.DrainGate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Readiness Module for Core Owner contexts and independent Judge availability. */
@Service
public final class CoreReadinessService {

    private final CoreOwnerContextManager ownerContexts;
    private final DrainGate drainGate;
    private final String judgeReadinessUrl;
    private final boolean judgeRequired;
    private final HttpClient judgeClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public CoreReadinessService(
            CoreOwnerContextManager ownerContexts,
            DrainGate drainGate,
            @Value("${core.judge.readiness-url:}") String judgeReadinessUrl,
            @Value("${core.judge.required:false}") boolean judgeRequired) {
        this.ownerContexts = ownerContexts;
        this.drainGate = drainGate;
        this.judgeReadinessUrl = judgeReadinessUrl == null ? "" : judgeReadinessUrl.trim();
        this.judgeRequired = judgeRequired;
    }

    public Snapshot snapshot() {
        Map<String, CoreOwnerContextManager.State> owners = ownerContexts.states();
        JudgeProbe judge = probeJudge();
        boolean ready = !drainGate.isDraining()
                && ownerContexts.allReady()
                && judge.ready();
        return new Snapshot(ready, drainGate.isDraining(), owners, judge.state());
    }

    private JudgeProbe probeJudge() {
        if (!judgeRequired) {
            return new JudgeProbe(true, "OPTIONAL");
        }
        if (judgeReadinessUrl.isBlank()) {
            return new JudgeProbe(false, "NOT_CONFIGURED");
        }
        try {
            URI uri = URI.create(judgeReadinessUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    && !"http".equalsIgnoreCase(uri.getScheme())) {
                return new JudgeProbe(false, "INVALID_URL");
            }
            HttpResponse<Void> response = judgeClient.send(
                    HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(2))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
            return new JudgeProbe(response.statusCode() >= 200
                    && response.statusCode() < 300, response.statusCode() >= 200
                    && response.statusCode() < 300 ? "READY" : "UNAVAILABLE");
        } catch (Exception exception) {
            return new JudgeProbe(false, "UNAVAILABLE");
        }
    }

    private record JudgeProbe(boolean ready, String state) {
    }

    public record Snapshot(
            boolean ready,
            boolean draining,
            Map<String, CoreOwnerContextManager.State> owners,
            String judgeState) {
        public Snapshot {
            owners = Map.copyOf(owners);
        }
    }
}
