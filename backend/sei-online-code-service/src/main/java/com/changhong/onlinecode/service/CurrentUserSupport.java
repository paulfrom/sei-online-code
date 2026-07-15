package com.changhong.onlinecode.service;

import com.changhong.sei.core.context.ApplicationContextHolder;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

/**
 * 当前操作人读取工具（BE-002 学生管理专用）。
 *
 * <p>sei-core 在容器上下文中通常通过自定义 {@code SessionUser} 类传递当前用户；
 * 本项目没有把 Spring Security / sei SessionUser 当作强依赖，因此仅以反射手段尝试
 * 读取。失败返回 {@code null}，仅用于软删场景下回填 {@code deletedBy}，不影响主流程。</p>
 *
 * <p>失败时返回 null 而非抛异常，避免软删路径与 sei-core 现有行为产生意外耦合。</p>
 *
 * @author sei-online-code
 */
final class CurrentUserSupport {

    private CurrentUserSupport() {
    }

    /**
     * 尝试获取当前用户 id。
     *
     * <p>通过 {@link ApplicationContextHolder#getInstance()} 拿到容器上下文，
     * 然后反射查找可能存在的当前用户 bean（如 sei-core 的 {@code ContextUtil} /
     * {@code SessionContext} / Spring Security 的 {@code SecurityContextHolder}）。
     * 任何一种存在并能取到 id 即返回，否则返回 {@code null}。</p>
     *
     * @return 用户 id 或 null（运行时不可用时）
     */
    static Long tryGetCurrentUserId() {
        if (ApplicationContextHolder.getApplicationContext() == null) {
            return null;
        }
        // 探针：在容器里尝试按常规 bean 名获取「当前用户」对象，再反射 getId / getUserId
        String[] candidateBeanNames = {"sessionUser", "currentUser", "contextUser"};
        for (String name : candidateBeanNames) {
            try {
                Object bean = ApplicationContextHolder.getApplicationContext().getBean(name);
                Long id = readId(bean);
                if (id != null) {
                    return id;
                }
            } catch (NoSuchBeanDefinitionException ignored) {
                // 继续探针
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Long readId(Object source) {
        if (source == null) {
            return null;
        }
        for (String getter : new String[]{"getId", "getUserId", "getUserID"}) {
            try {
                Object value = source.getClass().getMethod(getter).invoke(source);
                if (value instanceof Number n) {
                    return n.longValue();
                }
                if (value instanceof String s && !s.isEmpty()) {
                    return Long.valueOf(s);
                }
            } catch (ReflectiveOperationException ignored) {
                // 继续探测下个 getter
            }
        }
        return null;
    }
}
