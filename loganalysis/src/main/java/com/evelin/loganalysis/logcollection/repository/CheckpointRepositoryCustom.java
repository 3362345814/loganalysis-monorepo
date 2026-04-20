package com.evelin.loganalysis.logcollection.repository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface CheckpointRepositoryCustom {

    int upsertCheckpoint(UUID id,
                         UUID sourceId,
                         String filePath,
                         Long fileOffset,
                         Long fileSize,
                         String fileInode,
                         LocalDateTime fileMtime,
                         LocalDateTime createdAt,
                         LocalDateTime updatedAt);
}
