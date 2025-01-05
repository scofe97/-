package com.onion.backend.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Aspect
@Component
public class ExecutionTimeAspect {

    @Around("execution(* com.onion.backend.controller..*(..))")
    public Object executionAspect(ProceedingJoinPoint joinPoint) throws Throwable {
        // 메서드 실행 시간 측정
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        Object result = joinPoint.proceed(); // 실제 메서드 호출

        stopWatch.stop();

        // 실행 시간 로깅
        String methodName = joinPoint.getSignature().toShortString();
        System.out.println("Method [" + methodName + "] 실행 시간: " + stopWatch.getTotalTimeMillis() + " ms");

        return result;
    }
}
