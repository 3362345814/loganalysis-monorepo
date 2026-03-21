package com.evelin.loganalysis.logcommon.utils;

/**
 * 雪花算法ID生成器
 *
 * @author Evelin
 */
public class SnowflakeIdGenerator {

    /**
     * 起始时间戳
     */
    private static final long START_TIMESTAMP = 1704067200000L; // 2024-01-01 00:00:00

    /**
     * 序列号占用位数
     */
    private static final int SEQUENCE_BITS = 12;

    /**
     * 机器ID占用位数
     */
    private static final int WORKER_ID_BITS = 5;

    /**
     * 数据中心ID占用位数
     */
    private static final int DATA_CENTER_ID_BITS = 5;

    /**
     * 最大序列号
     */
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    /**
     * 最大机器ID
     */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    /**
     * 最大数据中心ID
     */
    private static final long MAX_DATA_CENTER_ID = ~(-1L << DATA_CENTER_ID_BITS);

    /**
     * 机器ID左移位数
     */
    private static final int WORKER_ID_SHIFT = SEQUENCE_BITS;

    /**
     * 数据中心ID左移位数
     */
    private static final int DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /**
     * 时间戳左移位数
     */
    private static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;

    /**
     * 机器ID
     */
    private long workerId;

    /**
     * 数据中心ID
     */
    private long dataCenterId;

    /**
     * 序列号
     */
    private long sequence = 0L;

    /**
     * 上次时间戳
     */
    private long lastTimestamp = -1L;

    /**
     * 序列号自增锁
     */
    private final Object sequenceLock = new Object();

    /**
     * 构造函数
     *
     * @param workerId     机器ID (0-31)
     * @param dataCenterId 数据中心ID (0-31)
     */
    public SnowflakeIdGenerator(long workerId, long dataCenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("Worker ID can't be greater than " + MAX_WORKER_ID + " or less than 0");
        }
        if (dataCenterId > MAX_DATA_CENTER_ID || dataCenterId < 0) {
            throw new IllegalArgumentException("Data Center ID can't be greater than " + MAX_DATA_CENTER_ID + " or less than 0");
        }
        this.workerId = workerId;
        this.dataCenterId = dataCenterId;
    }

    /**
     * 默认构造函数（使用默认机器ID和数据中心ID）
     */
    public SnowflakeIdGenerator() {
        this(1L, 1L);
    }

    /**
     * 获取下一个序列号
     *
     * @return 序列号
     */
    public long nextSequence() {
        synchronized (sequenceLock) {
            long timestamp = System.currentTimeMillis();
            if (timestamp == lastTimestamp) {
                sequence = (sequence + 1) & MAX_SEQUENCE;
                if (sequence == 0) {
                    // 序列号溢出，等待下一毫秒
                    while (timestamp <= lastTimestamp) {
                        timestamp = System.currentTimeMillis();
                    }
                }
            } else {
                sequence = 0L;
            }
            lastTimestamp = timestamp;
            return sequence;
        }
    }

    /**
     * 生成雪花ID
     *
     * @return 雪花ID
     */
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        // 如果当前时间小于上一次生成的时间戳，说明发生时钟回拨
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate id for " + (lastTimestamp - timestamp) + " milliseconds");
        }

        // 如果是同一时间生成的，则进行序列号自增
        if (lastTimestamp == timestamp) {
            synchronized (sequenceLock) {
                sequence = (sequence + 1) & MAX_SEQUENCE;
                // 序列号溢出
                if (sequence == 0) {
                    // 等待下一毫秒
                    while (timestamp <= lastTimestamp) {
                        timestamp = System.currentTimeMillis();
                    }
                }
            }
        } else {
            // 时间戳改变，重置序列号
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        // 生成ID
        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                | (dataCenterId << DATA_CENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 生成雪花ID字符串
     *
     * @return 雪花ID字符串
     */
    public String nextIdStr() {
        return String.valueOf(nextId());
    }

    /**
     * 解析雪花ID
     *
     * @param id 雪花ID
     * @return 解析结果
     */
    public static SnowflakeIdInfo parse(long id) {
        long timestamp = (id >> TIMESTAMP_SHIFT) + START_TIMESTAMP;
        long dataCenterId = (id >> DATA_CENTER_ID_SHIFT) & MAX_DATA_CENTER_ID;
        long workerId = (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
        long sequence = id & MAX_SEQUENCE;

        return new SnowflakeIdInfo(timestamp, dataCenterId, workerId, sequence);
    }

    /**
     * 雪花ID信息
     */
    public record SnowflakeIdInfo(
            long timestamp,
            long dataCenterId,
            long workerId,
            long sequence
    ) {
    }
}
