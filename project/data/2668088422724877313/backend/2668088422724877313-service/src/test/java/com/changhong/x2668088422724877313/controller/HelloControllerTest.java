package com.changhong.x2668088422724877313.controller;

import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.test.BaseUnitTest;
import com.changhong.sei.core.util.JsonUtils;
import com.changhong.x2668088422724877313.controller.HelloController;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.*;

/**
 * 实现功能: Hello 单元测试
 */
public class HelloControllerTest extends BaseUnitTest {
    @Autowired
    private HelloController controller;

    @Test
    public void sayHello() {
        String name = "程序员";
        ResultData<String> result = controller.sayHello(name);
        LOG.debug(JsonUtils.toJson(result));
    }
}