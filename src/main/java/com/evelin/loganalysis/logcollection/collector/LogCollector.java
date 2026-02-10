package com.evelin.loganalysis.logcollection.collector;

import com.evelin.loganalysis.logcollection.model.CollectionState;

/**
 * 日志采集器接口
 *
 * 定义采集器的统一行为，所有采集器实现都需要实现此接口
 *
 * @author Evelin
 */
public interface LogCollector {

    /**
     * 获取采集器ID
     *
     * @return 采集器唯一标识
     */
    String getId();

    /**
     * 获取采集器名称
     *
     * @return 采集器名称
     */
    String getName();

    /**
     * 获取当前采集器状态
     *
     * @return 采集器状态
     */
    CollectionState getState();

    /**
     * 启动采集器
     *
     * 调用此方法后，采集器开始工作，实时采集日志数据
     */
    void start();

    /**
     * 停止采集器
     *
     * 调用此方法后，采集器停止工作，释放资源
     */
    void stop();

    /**
     * 暂停采集器
     *
     * 暂时停止采集，保留当前状态，可恢复
     */
    void pause();

    /**
     * 恢复采集器
     *
     * 从暂停状态恢复，继续采集
     */
    void resume();

    /**
     * 检查采集器是否正在运行
     *
     * @return true表示正在运行
     */
    boolean isRunning();

    /**
     * 获取已采集的日志行数
     *
     * @return 已采集的行数
     */
    long getCollectedLines();

    /**
     * 获取采集器健康状态
     *
     * @return 健康状态，true表示健康
     */
    boolean isHealthy();
}
