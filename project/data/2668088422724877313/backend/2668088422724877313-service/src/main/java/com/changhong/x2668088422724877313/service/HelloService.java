package com.changhong.x2668088422724877313.service;

import com.changhong.sei.core.log.LogUtil;
import org.springframework.stereotype.Service;

/**
 * 实现功能: 你好的业务逻辑实现
 */
@Service
public class HelloService {
    /**
     * 你好业务逻辑
     *
     * @param name  姓名
     * @param param 参数
     * @return 返回句子
     */
    public String sayHello(String name, String param) {
        LogUtil.bizLog("执行业务逻辑说：你好！");
        return "你好，" + name + "！参数：" + param;
    }
}
