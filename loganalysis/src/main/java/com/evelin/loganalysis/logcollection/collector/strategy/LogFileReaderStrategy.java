package com.evelin.loganalysis.logcollection.collector.strategy;

import java.io.IOException;

/**
 * 文件读取策略接口，定义文件读取的抽象操作。
 * <p>
 * 此接口定义文件级别的读取能力，由 {@link AbstractLogFileReaderStrategy} 实现
 * 并继承 {@link com.evelin.loganalysis.logcollection.collector.LogCollector}。
 * <p>
 * 设计原则：LogCollector 接口定义了采集器的生命周期（start/stop/pause/resume），
 * 此接口专注于文件操作细节。
 */
public interface LogFileReaderStrategy {

    /**
     * 建立连接（如文件句柄、网络连接等）
     */
    void connect() throws Exception;

    /**
     * 断开连接，清理资源
     */
    void disconnect();

    /**
     * 初始化文件（如加载检查点、打开文件流等）
     */
    void initFiles() throws Exception;

    /**
     * 从文件上下文读取数据
     *
     * @param fileContext 文件上下文对象
     * @param filePath    文件路径
     */
    void readFromFileContext(FileContext fileContext, String filePath) throws Exception;

    /**
     * 检查文件轮转
     */
    void checkFileRotation();

    /**
     * 检查指定文件是否存在
     *
     * @param filePath 文件路径
     * @return 是否存在
     */
    boolean fileExists(String filePath);

    /**
     * 获取文件大小
     *
     * @param filePath 文件路径
     * @return 文件大小（字节）
     * @throws IOException 如果获取失败
     */
    long getFileSize(String filePath) throws IOException;

    /**
     * 获取文件指针位置
     *
     * @param filePath 文件路径
     * @return 当前读取位置
     */
    long getFilePointer(String filePath);

    /**
     * 检查文件是否处于激活状态
     *
     * @param filePath 文件路径
     * @return 是否激活
     */
    boolean isFileActive(String filePath);

    /**
     * 保存检查点到持久化存储
     *
     * @param filePath 文件路径
     * @param ctx      文件上下文
     */
    void saveCheckpoint(String filePath, FileContext ctx);

    /**
     * 保存所有文件的检查点
     */
    void saveAllCheckpoints();

    /**
     * 关闭所有打开的文件资源
     */
    void closeAllFiles();

    /**
     * 获取当前读取的行数
     *
     * @return 总行数
     */
    long getCollectedLines();

    /**
     * 关闭策略相关的线程池
     */
    default void shutdown() {
    }
}
