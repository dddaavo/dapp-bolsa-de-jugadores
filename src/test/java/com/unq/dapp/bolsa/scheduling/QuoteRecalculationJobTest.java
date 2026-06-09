package com.unq.dapp.bolsa.scheduling;

import com.unq.dapp.bolsa.pricing.application.QuoteRecalculationOrchestrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Test
    void deberiaLlamarAlOrchestratorConEstrategiaDefault() {
        when(orchestrator.recalculateAll(null)).thenReturn(10);

        job.execute();

        verify(orchestrator).recalculateAll(null);
    }

    @Test
    void deberiaCompletarSinExcepcionCuandoHayJugadores() {
        when(orchestrator.recalculateAll(null)).thenReturn(50);

        job.execute();

        verify(orchestrator).recalculateAll(null);
    }

    @Test
    void deberiaCompletarSinExcepcionCuandoNoHayJugadores() {
        when(orchestrator.recalculateAll(null)).thenReturn(0);

        job.execute();

        verify(orchestrator).recalculateAll(null);
    }
}
