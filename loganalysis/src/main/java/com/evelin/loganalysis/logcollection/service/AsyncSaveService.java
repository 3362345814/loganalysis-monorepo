package com.evelin.loganalysis.logcollection.service;

import com.evelin.loganalysis.logcollection.model.RawLogEvent;
import com.evelin.loganalysis.logcollection.model.entity.RawLogEventEntity;
import com.evelin.loganalysis.logcollection.repository.RawLogEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncSaveService {

    private final RawLogEventRepository rawLogEventRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAllAsync(List<RawLogEvent> events) {
        List<RawLogEventEntity> entities = events.stream()
                .map(RawLogEventEntity::from)
                .collect(Collectors.toList());
        List<RawLogEventEntity> saved = rawLogEventRepository.saveAll(entities);
        log.info("异步批量保存原始日志事件: {} 条", saved.size());
    }
}
