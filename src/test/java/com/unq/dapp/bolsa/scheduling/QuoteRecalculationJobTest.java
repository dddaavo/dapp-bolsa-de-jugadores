package com.unq.dapp.bolsa.scheduling;

import com.unq.dapp.bolsa.pricing.application.QuoteRecalculationOrchestrator;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteRecalculationJobTest {

    @Mock
    private QuoteRecalculationOrchestrator orchestrator;

    @InjectMocks
    private QuoteRecalculationJob job;

    @ParameterizedTest
    @ValueSource(ints = {0, 10, 50})
    void deberiaLlamarAlOrchestratorConEstrategiaDefault(int jugadoresRecalculados) {
        when(orchestrator.recalculateAll(null)).thenReturn(jugadoresRecalculados);

        job.execute();

        verify(orchestrator).recalculateAll(null);
    }
}
