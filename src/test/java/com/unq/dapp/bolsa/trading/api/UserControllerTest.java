package com.unq.dapp.bolsa.trading.api;

import com.unq.dapp.bolsa.trading.application.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@MockBean(JpaMetamodelMappingContext.class)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private OrderService orderService;
    @MockBean private com.unq.dapp.bolsa.auth.application.JwtService jwtService;
    @MockBean private com.unq.dapp.bolsa.auth.infrastructure.CustomUserDetailsService customUserDetailsService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void deberiaRetornarTransaccionesParaAdmin() throws Exception {
        when(orderService.getTransactions(eq(1L), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/users/1/transactions"))
                .andExpect(status().isOk());
    }

    @Test
    void deberiaRetornar401SinAutenticacion() throws Exception {
        mockMvc.perform(get("/api/v1/users/1/transactions"))
                .andExpect(status().isUnauthorized());
    }
}
