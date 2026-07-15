package com.unq.dapp.bolsa.pricing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unq.dapp.bolsa.pricing.api.StrategyConfigResponse;
import com.unq.dapp.bolsa.pricing.api.UpdateStrategyWeightsRequest;
import com.unq.dapp.bolsa.pricing.domain.*;
import com.unq.dapp.bolsa.pricing.infrastructure.StrategyConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StrategyConfigServiceTest {

    @Mock private StrategyConfigRepository configRepository;
    @Mock private StrategyRegistry strategyRegistry;

    private StrategyConfigService service;

    @BeforeEach
    void setUp() {
        service = new StrategyConfigService(configRepository, strategyRegistry, new ObjectMapper());
    }

    @Test
    void deberiaContruirMatchMetricsConPesosDelDB() {
        StrategyConfig config = configMatchMetrics("{\"goals\":0.5,\"assists\":0.3,\"rating\":0.2}", 1);
        when(configRepository.findByName("GlobalMetrics")).thenReturn(Optional.of(config));

        PricingStrategy strategy = service.buildStrategy("GlobalMetrics");

        assertThat(strategy.name()).isEqualTo("GlobalMetrics");
        assertThat(strategy.version()).isEqualTo("v1.1");
    }

    @Test
    void deberiaUsarFallbackDelRegistryCuandoNoHayConfig() {
        when(configRepository.findByName("GlobalMetrics")).thenReturn(Optional.empty());
        MatchMetricsStrategy fallback = new MatchMetricsStrategy();
        when(strategyRegistry.get("GlobalMetrics")).thenReturn(Optional.of(fallback));

        PricingStrategy strategy = service.buildStrategy("GlobalMetrics");

        assertThat(strategy).isSameAs(fallback);
    }

    @Test
    void deberiaConstruirDefaultDesdeRegistryName() {
        MatchMetricsStrategy defaultStrategy = new MatchMetricsStrategy();
        when(strategyRegistry.getDefault()).thenReturn(defaultStrategy);
        StrategyConfig config = configMatchMetrics("{\"goals\":0.4,\"assists\":0.3,\"rating\":0.3}", 0);
        when(configRepository.findByName("GlobalMetrics")).thenReturn(Optional.of(config));

        PricingStrategy result = service.buildDefaultStrategy();

        assertThat(result.name()).isEqualTo("GlobalMetrics");
        assertThat(result.version()).isEqualTo("v1.0");
    }

    @Test
    void deberiaActualizarPesosEIncrementarVersion() {
        StrategyConfig config = configMatchMetrics("{\"goals\":0.4,\"assists\":0.3,\"rating\":0.3}", 0);
        when(configRepository.findByName("GlobalMetrics")).thenReturn(Optional.of(config));
        when(configRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UpdateStrategyWeightsRequest request = new UpdateStrategyWeightsRequest(
                "{\"goals\":0.5,\"assists\":0.3,\"rating\":0.2}");
        StrategyConfigResponse response = service.updateWeights("GlobalMetrics", request);

        assertThat(response.version()).isEqualTo(1);
        assertThat(response.pesos()).isNotNull();
    }

    @Test
    void deberiaLanzarExcepcionSiPesosNoSuman1() {
        StrategyConfig config = configMatchMetrics("{\"goals\":0.4,\"assists\":0.3,\"rating\":0.3}", 0);
        when(configRepository.findByName("GlobalMetrics")).thenReturn(Optional.of(config));

        UpdateStrategyWeightsRequest request = new UpdateStrategyWeightsRequest(
                "{\"goals\":0.9,\"assists\":0.9,\"rating\":0.9}");

        assertThatThrownBy(() -> service.updateWeights("GlobalMetrics", request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberiaRetornarListaDeConfiguracionesActivas() {
        StrategyConfig mm = configMatchMetrics("{\"goals\":0.4,\"assists\":0.3,\"rating\":0.3}", 0);
        when(configRepository.findAllByActiveTrue()).thenReturn(List.of(mm));

        List<StrategyConfigResponse> result = service.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("GlobalMetrics");
    }

    // --- helpers ---

    private StrategyConfig configMatchMetrics(String weightsJson, int configVersion) {
        StrategyConfig c = new StrategyConfig();
        c.setName("GlobalMetrics");
        c.setActive(true);
        c.setConfigVersion(configVersion);
        c.setWeightsJson(weightsJson);
        return c;
    }
}
