package org.example.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 会话审计注解
 * 用于标记需要进行审计的会话操作方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SessionAudit {

    /**
     * 操作类型
     */
    String type() default "SESSION_OPERATION";

    /**
     * 操作描述
     */
    String description() default "";

    /**
     * 是否记录参数
     */
    boolean logParams() default true;

    /**
     * 是否记录返回值
     */
    boolean logResult() default false;

    /**
     * 是否记录异常
     */
    boolean logException() default true;
}
