package com.evelin.loganalysis.logcollection.collector.strategy;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 文件读取上下文，封装单个文件的读取状态。
 * 以 SSH 采集为准（不支持 inode、RandomAccessFile 句柄），
 * 但提供 seek 和 read 抽象方法由子类实现具体读取逻辑。
 */
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

    public String getFilePath() {
        return filePath;
    }

    public long getFilePointer() {
        return filePointer;
    }

    public void setFilePointer(long filePointer) {
        this.filePointer = filePointer;
    }

    public long getCurrentFileSize() {
        return currentFileSize;
    }

    public void setCurrentFileSize(long currentFileSize) {
        this.currentFileSize = currentFileSize;
    }

    public long getCollectedLines() {
        return collectedLines;
    }

    public void incrementCollectedLines() {
        this.collectedLines++;
    }

    public void setCollectedLines(long collectedLines) {
        this.collectedLines = collectedLines;
    }

    public StringBuilder getMultiLineBuffer() {
        return multiLineBuffer;
    }

    public long getMultiLineStartLineNumber() {
        return multiLineStartLineNumber;
    }

    public void setMultiLineStartLineNumber(long multiLineStartLineNumber) {
        this.multiLineStartLineNumber = multiLineStartLineNumber;
    }

    public AtomicBoolean getActive() {
        return active;
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
