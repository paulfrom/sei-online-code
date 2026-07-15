package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.CompensationLogDao;
import com.changhong.onlinecode.dto.enums.TriggerSource;
import com.changhong.onlinecode.entity.CompensationLog;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 补偿日志服务。
 */
@Service
@AllArgsConstructor
public class CompensationLogService {

    private final CompensationLogDao compensationLogDao;

    public void record(String entityType, String entityId, String action, boolean success,
                       String message, String detail, TriggerSource triggerSource) {
        CompensationLog log = new CompensationLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setSuccess(success);
        log.setMessage(message);
        log.setDetail(detail);
        log.setTriggerSource(triggerSource);
        compensationLogDao.save(log);
    }
}
