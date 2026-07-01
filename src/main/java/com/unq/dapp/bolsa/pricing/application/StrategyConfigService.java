package com.unq.dapp.bolsa.pricing.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unq.dapp.bolsa.catalog.domain.Position;
import com.unq.dapp.bolsa.pricing.api.StrategyConfigResponse;
import com.unq.dapp.bolsa.pricing.api.UpdateStrategyWeightsRequest;
import com.unq.dapp.bolsa.pricing.domain.*;
import com.unq.dapp.bolsa.pricing.infrastructure.StrategyConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StrategyConfigService {

    private final StrategyConfigRepository configRepository;
    private final StrategyRegistry strategyRegistry;
    private final ObjectMapper objectMapper;

    public StrategyConfigService(StrategyConfigRepository configRepository,
                                  StrategyRegistry strategyRegistry,
                                  ObjectMapper objectMapper) {
        this.configRepository = configRepository;
        this.strategyRegistry = strategyRegistry;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<StrategyConfigResponse> getAll() {
        return configRepository.findAllByActiveTrue().stream()
                .map(StrategyConfigResponse::from)
                .toList();
    }

    @Transactional
    public StrategyConfigResponse updateWeights(String name, UpdateStrategyWeightsRequest request) {
        StrategyConfig config = configRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Strategy config not found: " + name));
        String weightsJson;
        try {
            weightsJson = objectMapper.writeValueAsString(request.weights());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid weights: " + e.getMessage(), e);
        }
        validateWeightsJson(name, weightsJson);
        config.setWeightsJson(weightsJson);
        config.setConfigVersion(config.getConfigVersion() + 1);
        return StrategyConfigResponse.from(configRepository.save(config));
    }

    @Transactional(readOnly = true)
    public PricingStrategy buildStrategy(String name) {
        return configRepository.findByName(name)
                .filter(StrategyConfig::isActive)
                .map(config -> buildFromJson(name, config.getWeightsJson(), "v1." + config.getConfigVersion()))
                .orElseGet(() -> strategyRegistry.get(name).orElseGet(strategyRegistry::getDefault));
    }

    @Transactional(readOnly = true)
    public PricingStrategy buildDefaultStrategy() {
        String defaultName = strategyRegistry.getDefault().name();
        return buildStrategy(defaultName);
    }

    private void validateWeightsJson(String name, String weightsJson) {
        buildFromJson(name, weightsJson, "validate");
    }

    private PricingStrategy buildFromJson(String name, String weightsJson, String version) {
        try {
            return switch (name) {
                case "GlobalMetrics" -> {
                    Map<String, Double> raw = objectMapper.readValue(weightsJson,
                            new TypeReference<Map<String, Double>>() {});
                    StrategyWeights weights = new StrategyWeights(
                            raw.getOrDefault("goals", 0.4),
                            raw.getOrDefault("assists", 0.3),
                            raw.getOrDefault("rating", 0.3));
                    yield new MatchMetricsStrategy(weights, version);
                }
                case "PositionMetrics" -> {
                    Map<String, Map<String, Double>> raw = objectMapper.readValue(weightsJson,
                            new TypeReference<Map<String, Map<String, Double>>>() {});
                    Map<Position, StrategyWeights> weightsByPosition = raw.entrySet().stream()
                            .collect(Collectors.toMap(
                                    e -> Position.valueOf(e.getKey()),
                                    e -> new StrategyWeights(
                                            e.getValue().getOrDefault("goals", 0.0),
                                            e.getValue().getOrDefault("assists", 0.0),
                                            e.getValue().getOrDefault("rating", 0.0))));
                    yield new PositionWeightedStrategy(weightsByPosition, version);
                }
                default -> strategyRegistry.get(name).orElseGet(strategyRegistry::getDefault);
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid weightsJson for strategy " + name + ": " + e.getMessage(), e);
        }
    }
}
