package com.changhong.x2668088422724877313.controller;

import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.limiter.support.lock.SeiLock;
import com.changhong.sei.core.limiter.support.lock.SeiLockHelper;
import com.changhong.sei.core.log.LogUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.changhong.x2668088422724877313.api.DistributedLockApi;

import java.util.Random;

/**
 * 实现功能：
 *
 * @author 马超(Vision.Mac)
 * @version 1.0.00  2021-01-27 11:23
 */
@RestController
@Tag(name = "DistributedLockApi", description = "分布式锁演示服务")
@RequestMapping(path = DistributedLockApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class DistributedLockController implements DistributedLockApi {

    /**
     * 分布式锁演示
     * 通过key锁定资源,防止重复处理
     * fallback为降级处理策略,fallbackToBusy为当前类中相同返回类型及入参的处理方法
     * argumentInjectors注入当前会话信息,根据需要添加
     * sessionUserArgumentInjector为spring管理的bean
     *
     * @param param key
     * @return 返回结果
     */
    @Override
    @SeiLock(key = "'demo:' + #param + ':' + #sessionUser.userId", fallback = "fallbackToBusy", argumentInjectors = "sessionUserArgumentInjector")
    public ResultData<String> lock(String param) {
        Random random = new Random();
        int time = random.nextInt(20000);
        LogUtil.debug(Thread.currentThread().getName() + " sleep " + time + "millis");
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return ResultData.success("ok");
    }

    /**
     * 降级处理方法
     *
     * @param key 参数
     * @return 返回结果
     */
    public ResultData<String> fallbackToBusy(String key) {
        System.out.println(key + "被降级处理...");
        return ResultData.fail("被降级处理..." + SeiLockHelper.checkLocked(key));
    }

    /**
     * 无入参的分布式锁演示
     *
     * @return 返回结果
     */
    @Override
    @SeiLock(key = "'lockNonParam'")
    public ResultData<String> lockNonParam() {
        Random random = new Random();
        int time = random.nextInt(20000);
        LogUtil.debug(Thread.currentThread().getName() + " sleep " + time + "millis");
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return ResultData.success("ok");
    }
}
