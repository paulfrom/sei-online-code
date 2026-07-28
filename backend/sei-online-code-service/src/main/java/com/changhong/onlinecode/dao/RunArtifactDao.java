package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.dto.enums.RunArtifactType;
import com.changhong.onlinecode.entity.RunArtifact;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RunArtifactDao extends BaseEntityDao<RunArtifact> {

    List<RunArtifact> findByRunIdOrderByCreatedDateAsc(String runId);

    Optional<RunArtifact> findTopByRunIdAndArtifactTypeOrderByCreatedDateDesc(
            String runId, RunArtifactType artifactType);
}
