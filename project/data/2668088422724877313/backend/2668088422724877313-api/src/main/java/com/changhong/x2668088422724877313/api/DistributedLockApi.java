package com.changhong.x2668088422724877313.api;

import com.changhong.sei.core.dto.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 实现功能：分布式锁演示
 *
 * @author 马超(Vision.Mac)
 * @version 1.0.00  2021-01-27 11:19
 */
@FeignClient(name = "2668088422724877313", path = DistributedLockApi.PATH)
public interface DistributedLockApi {
    String PATH = "demo";

    @GetMapping(value = "lock")
    @Operation(summary = "分布式锁演示", description = "分布式锁演示")
    ResultData<String> lock(@RequestParam("key") String key);

    @GetMapping(value = "lockNonParam")
    @Operation(summary = "分布式锁演示无入参", description = "分布式锁演示无入参")
    ResultData<String> lockNonParam();
}
