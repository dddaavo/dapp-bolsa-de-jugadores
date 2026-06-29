package com.unq.dapp.bolsa.shared.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Aspect
@Component
public class WebServiceAuditAspect {

    private static final Logger log = LoggerFactory.getLogger("audit");

    private static final Set<String> SENSITIVE_TYPE_FRAGMENTS = Set.of(
        "loginrequest", "registerrequest", "passwordrequest"
    );

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        String user = resolveUser();
        String operation = pjp.getSignature().toShortString();
        String params = maskArgs(pjp.getArgs());

        try {
            Object result = pjp.proceed();
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("user={} operation={} params={} durationMs={}", user, operation, params, ms);
            return result;
        } catch (Throwable ex) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("user={} operation={} params={} durationMs={} error={}", user, operation, params, ms, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private String resolveUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "anonymous";
        }
        return auth.getName();
    }

    private String maskArgs(Object[] args) {
        if (args == null || args.length == 0) return "[]";
        return Arrays.stream(args)
            .map(this::maskArg)
            .collect(Collectors.joining(", ", "[", "]"));
    }

    private String maskArg(Object arg) {
        if (arg == null) return "null";
        String typeName = arg.getClass().getSimpleName().toLowerCase();
        if (SENSITIVE_TYPE_FRAGMENTS.stream().anyMatch(typeName::contains)) {
            return "[PROTECTED:" + arg.getClass().getSimpleName() + "]";
        }
        if (arg instanceof String s && (s.startsWith("Bearer ") || s.startsWith("bearer "))) {
            return "[PROTECTED:token]";
        }
        return arg.toString();
    }
}
