package com.changhong.onlinecode.agent;

import com.changhong.onlinecode.dto.enums.UsageStatus;
import lombok.Data;

/**
 * 归一化后的 Agent token 消耗。
 *
 * <p>字段统一口径：</p>
 * <ul>
 *   <li>inputTokens：本次调用的总输入 token，包含缓存读写部分；</li>
 *   <li>outputTokens：Provider 报告的输出 token 总量；</li>
 *   <li>cacheReadTokens：从缓存读取的输入 token；</li>
 *   <li>cacheWriteTokens：写入或创建缓存的输入 token；</li>
 *   <li>totalTokens：归一化后的 inputTokens + outputTokens，优先使用语义一致的 Provider 总数。</li>
 * </ul>
 *
 * <p>CLI 明确返回 0 时保存 0；未提供某个维度时保存 null；usageStatus = UNAVAILABLE 时所有归一化 token 字段为 null。</p>
 *
 * @author sei-online-code
 */
@Data
public class AgentUsage {

    /** 总输入 token。 */
    private Long inputTokens;

    /** 输出 token 总量。 */
    private Long outputTokens;

    /** 从缓存读取的输入 token。 */
    private Long cacheReadTokens;

    /** 写入或创建缓存的输入 token。 */
    private Long cacheWriteTokens;

    /** 归一化后的总 token。 */
    private Long totalTokens;

    /** usage 可用性状态。 */
    private UsageStatus status;

    /** Provider 返回的原始 usage JSON，便于协议变化后追溯和重新解析。 */
    private String rawUsageJson;
}
