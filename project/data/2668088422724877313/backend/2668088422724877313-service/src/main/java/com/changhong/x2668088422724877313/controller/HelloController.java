package com.changhong.x2668088422724877313.controller;

import com.changhong.sei.core.context.ContextUtil;
import com.changhong.sei.core.context.SessionUser;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.log.LogUtil;
import com.changhong.sei.core.log.annotation.Log;
import com.changhong.sei.core.util.JsonUtils;
import com.changhong.x2668088422724877313.api.HelloApi;
import com.changhong.x2668088422724877313.service.HelloService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 实现功能: 你好的API服务实现
 */
@RestController
@RefreshScope
@Tag(name = "HelloApi", description = "调试你好的API服务")
@RequestMapping(path = HelloApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class HelloController implements HelloApi {
    @Autowired
    private HelloService service;

    @Value("${demo.test-key:123456}")
    private String testKey;

    /**
     * 你好
     *
     * @param name 姓名
     * @return 返回句子
     */
    @Override
    @Log(value = "演示业务日志记录.平台还提供有: @ParamLog, @ResultLog, @ThrowingLog")
    public ResultData<String> sayHello(String name) {
        try {
            SessionUser sessionUser = ContextUtil.getSessionUser();
            LogUtil.bizLog(JsonUtils.toJson(sessionUser));
            String data = service.sayHello(name, testKey);
            return ResultData.success(data);
        } catch (Exception e) {
            e.printStackTrace();
            return ResultData.fail("你好说失败了！" + e.getMessage());
        }
    }

    /**
     * say hello 无返回参数
     *
     * @param name name
     * @return hello name
     */
    @Override
    public void sayVoid(String name) {
        String data = service.sayHello(name, testKey);
        LogUtil.bizLog("已经执行了说你好的方法！" + data);
    }
}
