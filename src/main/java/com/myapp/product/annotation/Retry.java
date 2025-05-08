package com.myapp.product.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD) //메서드에만 붙일 수 있음
@Retention(RetentionPolicy.RUNTIME) // 런타임
public @interface Retry {
    int maxAttempts() default 5;
    int retryDelay() default 100;
}
