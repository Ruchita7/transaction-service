package org.example.banking;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Arrays;


@Aspect
@Component
@Slf4j
public class LoggingAspect {

   // private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @Pointcut("within(org.example.banking.controller..*) || within(org.example.banking.service..*)")
    public void applicationLayer() {

    }

    @Around("applicationLayer()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        log.info("[{}::{}] called with args: {}", className, methodName, Arrays.toString(args));

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;
            log.info("[{}::{}] completed in {}ms | result: {}", className, methodName, elapsed, result);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[{}::{}] failed after {}ms | error: {}", className, methodName, elapsed, e.getMessage());
            throw e;
        }
    }
}
