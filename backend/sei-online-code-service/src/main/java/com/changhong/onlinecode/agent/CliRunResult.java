package com.changhong.onlinecode.agent;

import lombok.Data;

/**
 * CLI Runner 单次执行的内部结果。
 *
 * <p>processSucceeded 只表示 CLI/协议层是否正常完成，不替代业务侧对输出内容的判断。</p>
 *
 * @author sei-online-code
 */
@Data
public class CliRunResult {

    /** 业务输出文本。 */
    private String output;

    /** 归一化 token 消耗。 */
    private AgentUsage usage;

    /** CLI/协议层是否正常完成。 */
    private boolean processSucceeded;

    /** 失败原因；processSucceeded = false 时有值。 */
    private String failureReason;
}
