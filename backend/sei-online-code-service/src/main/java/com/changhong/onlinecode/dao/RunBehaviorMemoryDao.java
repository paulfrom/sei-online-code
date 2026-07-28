package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.RunBehaviorMemory;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RunBehaviorMemoryDao extends BaseEntityDao<RunBehaviorMemory> {

    List<RunBehaviorMemory> findByRunId(String runId);
}
