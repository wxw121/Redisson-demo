package org.example.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.util.CacheSyncUtil;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 缓存同步切面
 * 拦截缓存操作并同步到其他节点
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class CacheSyncAspect {

    private final CacheSyncUtil cacheSyncUtil;
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 拦截@Cacheable注解的方法
     */
    @Around("@annotation(org.springframework.cache.annotation.Cacheable)")
    public Object aroundCacheable(ProceedingJoinPoint joinPoint) throws Throwable {
        return handleCacheOperation(joinPoint, CacheOpType.CACHEABLE);
    }

    /**
     * 拦截@CachePut注解的方法
     */
    @Around("@annotation(org.springframework.cache.annotation.CachePut)")
    public Object aroundCachePut(ProceedingJoinPoint joinPoint) throws Throwable {
        return handleCacheOperation(joinPoint, CacheOpType.CACHE_PUT);
    }

    /**
     * 拦截@CacheEvict注解的方法
     */
    @Around("@annotation(org.springframework.cache.annotation.CacheEvict)")
    public Object aroundCacheEvict(ProceedingJoinPoint joinPoint) throws Throwable {
        return handleCacheOperation(joinPoint, CacheOpType.CACHE_EVICT);
    }

    /**
     * 拦截@Caching注解的方法
     */
    @Around("@annotation(org.springframework.cache.annotation.Caching)")
    public Object aroundCaching(ProceedingJoinPoint joinPoint) throws Throwable {
        return handleCacheOperation(joinPoint, CacheOpType.CACHING);
    }

    /**
     * 缓存操作类型
     */
    private enum CacheOpType {
        CACHEABLE, CACHE_PUT, CACHE_EVICT, CACHING
    }

    /**
     * 处理缓存操作
     */
    private Object handleCacheOperation(ProceedingJoinPoint joinPoint, CacheOpType opType) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 执行原方法
        Object result = joinPoint.proceed();

        try {
            // 创建SpEL表达式上下文
            EvaluationContext context = createEvaluationContext(joinPoint.getArgs(), signature, result);

            // 根据不同的缓存操作类型处理
            switch (opType) {
                case CACHEABLE -> handleCacheable(method, context, result);
                case CACHE_PUT -> handleCachePut(method, context, result);
                case CACHE_EVICT -> handleCacheEvict(method, context);
                case CACHING -> handleCaching(method, context, result);
            }
        } catch (Exception e) {
            log.error("处理缓存同步时发生错误", e);
        }

        return result;
    }

    /**
     * 处理@Cacheable注解
     */
    private void handleCacheable(Method method, EvaluationContext context, Object result) {
        if (result == null) {
            return; // 如果结果为null，不进行缓存
        }

        Cacheable cacheable = method.getAnnotation(Cacheable.class);
        if (cacheable != null) {
            String[] cacheNames = cacheable.cacheNames().length > 0 ?
                    cacheable.cacheNames() : cacheable.value();

            String keyExpression = cacheable.key();
            if (keyExpression.isEmpty()) {
                return; // 如果没有指定key，不进行同步
            }

            Object key = parseExpression(keyExpression, context, method);

            // 同步缓存到其他节点
            for (String cacheName : cacheNames) {
                cacheSyncUtil.updateCache(cacheName, key, result);
            }
        }
    }

    /**
     * 处理@CachePut注解
     */
    private void handleCachePut(Method method, EvaluationContext context, Object result) {
        CachePut cachePut = method.getAnnotation(CachePut.class);
        if (cachePut != null) {
            String[] cacheNames = cachePut.cacheNames().length > 0 ?
                    cachePut.cacheNames() : cachePut.value();

            String keyExpression = cachePut.key();
            if (keyExpression.isEmpty()) {
                return; // 如果没有指定key，不进行同步
            }

            Object key = parseExpression(keyExpression, context, method);

            // 同步缓存到其他节点
            for (String cacheName : cacheNames) {
                cacheSyncUtil.updateCache(cacheName, key, result);
            }
        }
    }

    /**
     * 处理@CacheEvict注解
     */
    private void handleCacheEvict(Method method, EvaluationContext context) {
        CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);
        if (cacheEvict != null) {
            String[] cacheNames = cacheEvict.cacheNames().length > 0 ?
                    cacheEvict.cacheNames() : cacheEvict.value();

            // 处理allEntries=true的情况
            if (cacheEvict.allEntries()) {
                for (String cacheName : cacheNames) {
                    cacheSyncUtil.clearCache(cacheName, "*");
                }
                return;
            }

            String keyExpression = cacheEvict.key();
            if (keyExpression.isEmpty()) {
                return; // 如果没有指定key，不进行同步
            }

            Object key = parseExpression(keyExpression, context, method);

            // 同步缓存到其他节点
            for (String cacheName : cacheNames) {
                cacheSyncUtil.clearCache(cacheName, key);
            }
        }
    }

    /**
     * 处理@Caching注解
     */
    private void handleCaching(Method method, EvaluationContext context, Object result) {
        Caching caching = method.getAnnotation(Caching.class);
        if (caching != null) {
            // 处理所有的@Cacheable注解
            for (Cacheable cacheable : caching.cacheable()) {
                String[] cacheNames = cacheable.cacheNames().length > 0 ?
                        cacheable.cacheNames() : cacheable.value();

                String keyExpression = cacheable.key();
                if (!keyExpression.isEmpty() && result != null) {
                    Object key = parseExpression(keyExpression, context, method);

                    for (String cacheName : cacheNames) {
                        cacheSyncUtil.updateCache(cacheName, key, result);
                    }
                }
            }

            // 处理所有的@CachePut注解
            for (CachePut cachePut : caching.put()) {
                String[] cacheNames = cachePut.cacheNames().length > 0 ?
                        cachePut.cacheNames() : cachePut.value();

                String keyExpression = cachePut.key();
                if (!keyExpression.isEmpty()) {
                    Object key = parseExpression(keyExpression, context, method);

                    for (String cacheName : cacheNames) {
                        cacheSyncUtil.updateCache(cacheName, key, result);
                    }
                }
            }

            // 处理所有的@CacheEvict注解
            for (CacheEvict cacheEvict : caching.evict()) {
                String[] cacheNames = cacheEvict.cacheNames().length > 0 ?
                        cacheEvict.cacheNames() : cacheEvict.value();

                // 处理allEntries=true的情况
                if (cacheEvict.allEntries()) {
                    for (String cacheName : cacheNames) {
                        cacheSyncUtil.clearCache(cacheName, "*");
                    }
                    continue;
                }

                String keyExpression = cacheEvict.key();
                if (!keyExpression.isEmpty()) {
                    Object key = parseExpression(keyExpression, context, method);

                    for (String cacheName : cacheNames) {
                        cacheSyncUtil.clearCache(cacheName, key);
                    }
                }
            }
        }
    }

    /**
     * 创建SpEL表达式上下文
     */
    private EvaluationContext createEvaluationContext(Object[] args, MethodSignature method, Object result) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 添加方法参数
        String[] parameterNames = method.getParameterNames();


        if (parameterNames == null) {
            // 回退方案：使用arg0, arg1...作为参数名
            parameterNames = new String[args.length];
            for (int i = 0; i < args.length; i++) {
                parameterNames[i] = "arg" + i;
            }
        }

        // 添加特殊变量
        context.setVariable("result", result);

        return context;
    }

    /**
     * 解析SpEL表达式
     */
    private Object parseExpression(String expressionString, EvaluationContext context, Method method) {
        Expression expression = parser.parseExpression(expressionString);
        AnnotatedElementKey methodKey = new AnnotatedElementKey(method, method.getDeclaringClass());
        return expression.getValue(context);
    }
}
