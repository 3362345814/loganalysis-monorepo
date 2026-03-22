package com.evelin.loganalysis.logcollection.collector.strategy;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 文件读取上下文，封装单个文件的读取状态。
 * 但提供 seek 和 read 抽象方法由子类实现具体读取逻辑。
 */
@Getter
@Setter
public class FileContext {

    private final String filePath;
    private long filePointer;
    private long currentFileSize;
    protected long collectedLines = 0;
    private final StringBuilder multiLineBuffer = new StringBuilder();
    private long multiLineStartLineNumber;
    private final AtomicBoolean active = new AtomicBoolean(false);

    public FileContext(String filePath) {
        this.filePath = filePath;
    }

    public void incrementCollectedLines() {
        this.collectedLines++;
    }

    public boolean isActive() {
        return active.get();
    }

    public void setActive(boolean active) {
        this.active.set(active);
    }

    public synchronized void resetBuffer() {
        multiLineBuffer.setLength(0);
        multiLineStartLineNumber = 0;
    }

    public void reset() {
        this.filePointer = 0L;
        this.currentFileSize = 0L;
        this.collectedLines = 0L;
        resetBuffer();
    }
}
