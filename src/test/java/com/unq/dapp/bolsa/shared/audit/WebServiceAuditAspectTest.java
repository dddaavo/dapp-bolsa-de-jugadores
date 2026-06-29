package com.unq.dapp.bolsa.shared.audit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.unq.dapp.bolsa.auth.api.LoginRequest;
import com.unq.dapp.bolsa.auth.api.RegisterRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebServiceAuditAspectTest {

    private WebServiceAuditAspect aspect;
    private ListAppender<ILoggingEvent> listAppender;

    @Mock
    private ProceedingJoinPoint pjp;

    @Mock
    private Signature signature;

    @BeforeEach
    void setUp() {
        aspect = new WebServiceAuditAspect();

        Logger auditLogger = (Logger) LoggerFactory.getLogger("audit");
        listAppender = new ListAppender<>();
        listAppender.start();
        auditLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        Logger auditLogger = (Logger) LoggerFactory.getLogger("audit");
        auditLogger.detachAppender(listAppender);
        SecurityContextHolder.clearContext();
    }

    @Test
    void deberiaLoguearLosCincoAtributosDeAuditoria() throws Throwable {
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("PlayerController.list()");
        when(pjp.getArgs()).thenReturn(new Object[]{});
        when(pjp.proceed()).thenReturn("ok");

        aspect.audit(pjp);

        String log = singleLog();
        assertThat(log).contains("user=");
        assertThat(log).contains("operation=PlayerController.list()");
        assertThat(log).contains("params=");
        assertThat(log).contains("durationMs=");
    }

    @Test
    void deberiaLoguearUsuarioAutenticado() throws Throwable {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("alice@example.com", null, List.of())
        );
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("QuoteController.current()");
        when(pjp.getArgs()).thenReturn(new Object[]{});
        when(pjp.proceed()).thenReturn("ok");

        aspect.audit(pjp);

        assertThat(singleLog()).contains("user=alice@example.com");
    }

    @Test
    void deberiaLoguearAnonymousCuandoNoHayAutenticacion() throws Throwable {
        SecurityContextHolder.clearContext();
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("AuthController.login()");
        when(pjp.getArgs()).thenReturn(new Object[]{});
        when(pjp.proceed()).thenReturn("ok");

        aspect.audit(pjp);

        assertThat(singleLog()).contains("user=anonymous");
    }

    @Test
    void deberiaMascararPasswordEnLoginRequest() throws Throwable {
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("AuthController.login()");
        when(pjp.getArgs()).thenReturn(new Object[]{new LoginRequest("alice@example.com", "s3cr3t!")});
        when(pjp.proceed()).thenReturn("ok");

        aspect.audit(pjp);

        String log = singleLog();
        assertThat(log).doesNotContain("s3cr3t!");
        assertThat(log).contains("[PROTECTED:LoginRequest]");
    }

    @Test
    void deberiaMascararPasswordEnRegisterRequest() throws Throwable {
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("AuthController.register()");
        when(pjp.getArgs()).thenReturn(new Object[]{new RegisterRequest("bob@example.com", "myPass123")});
        when(pjp.proceed()).thenReturn("ok");

        aspect.audit(pjp);

        String log = singleLog();
        assertThat(log).doesNotContain("myPass123");
        assertThat(log).contains("[PROTECTED:RegisterRequest]");
    }

    @Test
    void deberiaMascararTokenJwtEnArgumenoString() throws Throwable {
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("OrderController.buy()");
        when(pjp.getArgs()).thenReturn(new Object[]{"Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig"});
        when(pjp.proceed()).thenReturn("ok");

        aspect.audit(pjp);

        String log = singleLog();
        assertThat(log).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
        assertThat(log).contains("[PROTECTED:token]");
    }

    private String singleLog() {
        assertThat(listAppender.list).isNotEmpty();
        return listAppender.list.get(0).getFormattedMessage();
    }
}
