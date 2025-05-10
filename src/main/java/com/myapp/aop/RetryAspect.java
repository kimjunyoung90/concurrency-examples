package com.myapp.aop;

import com.myapp.annotation.Retry;
import com.myapp.exception.CustomOptimisticLockException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(Ordered.LOWEST_PRECEDENCE - 1)//Aspect가 적용될 순서를 지정(작은 숫자일수록 우선순위가 높고, 먼저 실행) @Transactional의 기본 Order값은 LOWEST_PRECEDENCE
@Aspect
@Component
public class RetryAspect {

    @Around("@annotation(retry)")
    public Object retryOptimisticLock(ProceedingJoinPoint joinPoint, Retry retry) throws Throwable {
        Exception exceptionHolder = null;
        for (int attempt = 0; attempt < retry.maxAttempts(); attempt++) {
            try {
                System.out.println("[재시도 로직] 시도 횟수: " + (attempt + 1));
                return joinPoint.proceed();
            } catch (CustomOptimisticLockException e) {
                // 실패했을 때만 여기를 타고 재시도
                exceptionHolder = e;
                Thread.sleep(retry.retryDelay());
            }
        }
        // 위 루프를 다 돌고도 성공하지 못하면
        throw exceptionHolder;
    }
}
