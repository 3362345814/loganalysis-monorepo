package com.evelin.loganalysis.logcollection.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public class CheckpointRepositoryImpl implements CheckpointRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public int upsertCheckpoint(UUID id,
                                UUID sourceId,
                                String filePath,
                                Long fileOffset,
                                Long fileSize,
                                String fileInode,
                                LocalDateTime fileMtime,
                                LocalDateTime createdAt,
                                LocalDateTime updatedAt) {
        Query query = entityManager.createNativeQuery("""
                INSERT INTO checkpoints (
                    id, source_id, file_path, file_offset, file_size, file_inode, file_mtime, created_at, updated_at
                ) VALUES (
                    :id, :sourceId, :filePath, :fileOffset, :fileSize, :fileInode, :fileMtime, :createdAt, :updatedAt
                )
                ON CONFLICT (source_id, file_path) DO UPDATE SET
                    file_offset = EXCLUDED.file_offset,
                    file_size = EXCLUDED.file_size,
                    file_inode = EXCLUDED.file_inode,
                    file_mtime = EXCLUDED.file_mtime,
                    updated_at = EXCLUDED.updated_at
                """);

        query.setParameter("id", id);
        query.setParameter("sourceId", sourceId);
        query.setParameter("filePath", filePath);
        query.setParameter("fileOffset", fileOffset);
        query.setParameter("fileSize", fileSize);
        query.setParameter("fileInode", fileInode);
        query.setParameter("fileMtime", fileMtime);
        query.setParameter("createdAt", createdAt);
        query.setParameter("updatedAt", updatedAt);
        return query.executeUpdate();
    }
}
