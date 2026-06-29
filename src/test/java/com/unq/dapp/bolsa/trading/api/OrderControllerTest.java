package com.unq.dapp.bolsa.trading.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unq.dapp.bolsa.trading.application.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@MockBean(JpaMetamodelMappingContext.class)
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private OrderService orderService;
    @MockBean private com.unq.dapp.bolsa.auth.application.JwtService jwtService;
    @MockBean private com.unq.dapp.bolsa.auth.infrastructure.CustomUserDetailsService customUserDetailsService;

    @Test
    @WithMockUser
    void deberiaRetornar400CuandoFaltaIdempotencyKeyEnBuy() throws Exception {
        String body = objectMapper.writeValueAsString(new BuyRequest(1L, 5));

        mockMvc.perform(post("/api/v1/orders/buy")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_IDEMPOTENCY_KEY"));
    }

    @Test
    @WithMockUser
    void deberiaRetornar400CuandoFaltaIdempotencyKeyEnSell() throws Exception {
        String body = objectMapper.writeValueAsString(new SellRequest(1L, 3));

        mockMvc.perform(post("/api/v1/orders/sell")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_IDEMPOTENCY_KEY"));
    }

    @Test
    @WithMockUser
    void deberiaRetornar400CuandoBodyInvalidoEnBuy() throws Exception {
        mockMvc.perform(post("/api/v1/orders/buy")
                        .with(csrf())
                        .header("Idempotency-Key", "some-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
