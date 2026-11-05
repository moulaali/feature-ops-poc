package com.example.search.controller;

import com.example.search.config.UnleashConfigProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/flag")
public class FlagAdminController {

    private final UnleashConfigProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FlagAdminController(UnleashConfigProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    private String getAdminBaseUrl() {
        String apiUrl = properties.apiUrl();
        if (apiUrl.endsWith("/api")) {
            return apiUrl.substring(0, apiUrl.length() - 4);
        }
        return apiUrl;
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        try {
            String url = getAdminBaseUrl() + "/api/admin/projects/default/features/search-v2";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", properties.adminToken())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return ResponseEntity.status(response.statusCode()).body(response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode envs = root.path("environments");
            boolean enabled = false;
            int rollout = 0;

            for (JsonNode env : envs) {
                if ("development".equals(env.path("name").asText())) {
                    enabled = env.path("enabled").asBoolean(false);
                    for (JsonNode strategy : env.path("strategies")) {
                        if ("userId".equals(strategy.path("parameters").path("stickiness").asText())) {
                            rollout = strategy.path("parameters").path("rollout").asInt(0);
                        }
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("feature", "search-v2");
            result.put("environment", "development");
            result.put("enabled", enabled);
            result.put("rolloutPercentage", rollout);
            result.put("allowlist", new String[]{"alice", "bob", "qa-123"});

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/toggle")
    public ResponseEntity<?> toggleFlag(@RequestParam boolean enabled) {
        try {
            String path = enabled ? "on" : "off";
            String url = getAdminBaseUrl() + "/api/admin/projects/default/features/search-v2/environments/development/" + path;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", properties.adminToken())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(Map.of(
                    "success", response.statusCode() < 300,
                    "enabled", enabled
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/rollout")
    public ResponseEntity<?> updateRollout(@RequestParam int percentage) {
        try {
            // Find strategy ID
            String stratUrl = getAdminBaseUrl() + "/api/admin/projects/default/features/search-v2/environments/development/strategies";
            HttpRequest stratReq = HttpRequest.newBuilder()
                    .uri(URI.create(stratUrl))
                    .header("Authorization", properties.adminToken())
                    .GET()
                    .build();

            HttpResponse<String> stratResp = httpClient.send(stratReq, HttpResponse.BodyHandlers.ofString());
            JsonNode strats = objectMapper.readTree(stratResp.body());

            String strategyId = null;
            for (JsonNode s : strats) {
                if ("userId".equals(s.path("parameters").path("stickiness").asText())) {
                    strategyId = s.path("id").asText();
                    break;
                }
            }

            if (strategyId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Rollout strategy not found"));
            }

            ObjectNode updateNode = objectMapper.createObjectNode();
            updateNode.put("name", "flexibleRollout");
            updateNode.put("title", "Percentage Rollout (" + percentage + "%)");
            updateNode.put("disabled", false);
            updateNode.putArray("constraints");
            ObjectNode params = updateNode.putObject("parameters");
            params.put("rollout", String.valueOf(percentage));
            params.put("stickiness", "userId");
            params.put("groupId", "search-v2");

            String putUrl = stratUrl + "/" + strategyId;
            HttpRequest putReq = HttpRequest.newBuilder()
                    .uri(URI.create(putUrl))
                    .header("Authorization", properties.adminToken())
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(updateNode)))
                    .build();

            HttpResponse<String> putResp = httpClient.send(putReq, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(putResp.statusCode()).body(Map.of(
                    "success", putResp.statusCode() < 300,
                    "rolloutPercentage", percentage
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
