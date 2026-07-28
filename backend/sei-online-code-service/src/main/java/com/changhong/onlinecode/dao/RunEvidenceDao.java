package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.RunEvidence;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RunEvidenceDao extends BaseEntityDao<RunEvidence> {

    Optional<RunEvidence> findTopByRunIdOrderByEvidenceVersionDesc(String runId);

    List<RunEvidence> findByRunIdOrderByEvidenceVersionDesc(String runId);
}
