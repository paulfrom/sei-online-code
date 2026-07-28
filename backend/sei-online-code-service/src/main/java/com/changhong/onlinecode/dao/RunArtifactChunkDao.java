package com.changhong.onlinecode.dao;

import com.changhong.onlinecode.entity.RunArtifactChunk;
import com.changhong.sei.core.dao.BaseEntityDao;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RunArtifactChunkDao extends BaseEntityDao<RunArtifactChunk> {

    List<RunArtifactChunk> findByArtifactIdOrderBySequenceNoAsc(String artifactId);

    List<RunArtifactChunk> findByArtifactIdAndLastLogSequenceGreaterThanOrderBySequenceNoAsc(
            String artifactId, Long afterSequence);
}
