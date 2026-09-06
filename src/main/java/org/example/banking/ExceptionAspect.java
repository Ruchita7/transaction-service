package org.example.banking;


import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class ExceptionAspect {

    @Pointcut("within(org.example.banking.service..*)")
    public void serviceLayer() {}

    @AfterThrowing(pointcut = "serviceLayer()", throwing = "ex")
    public void handleException(JoinPoint joinPoint, Exception ex) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        if (ex instanceof IllegalArgumentException) {
            log.warn("[{}::{}] Bad input — {}", className, methodName, ex.getMessage());
        } else if (ex instanceof jakarta.persistence.EntityNotFoundException) {
            log.warn("[{}::{}] Entity not found — {}", className, methodName, ex.getMessage());
        } else if (ex instanceof IllegalStateException) {
            log.warn("[{}::{}] Business rule violation — {}", className, methodName, ex.getMessage());
        } else {
            log.error("[{}::{}] Unexpected error — {}", className, methodName, ex.getMessage(), ex);
        }
    }
}
