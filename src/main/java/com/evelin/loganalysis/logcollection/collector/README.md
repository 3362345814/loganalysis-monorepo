# LocalFileCollector 使用说明

## 概述

`LocalFileCollector` 是基于 Java NIO 实现的本地文件日志采集器，支持：
- ✅ **断点续读** - 系统重启后从上次位置继续读取
- ✅ **文件轮转检测** - 自动检测日志文件轮转（如 logrotate）
- ✅ **实时监听** - 使用 WatchService 监听文件变化
- ✅ **背压处理** - 有界队列缓冲，防止内存溢出

## 核心类

```
logcollection/
├── collector/
│   ├── LogCollector.java              # 采集器接口
│   ├── LocalFileCollector.java        # 本地文件采集器实现
│   └── LocalFileCollectorFactory.java # ✅ 采集器工厂（Spring Bean）
├── service/
│   └── CheckpointManager.java         # 检查点管理器
├── model/
│   ├── CollectionCheckpoint.java      # 检查点模型
│   ├── CollectionState.java           # 采集状态枚举
│   └── RawLogEvent.java               # 原始日志事件
└── config/
    └── CollectionConfig.java          # 采集配置
```

## 使用方法

### 1. 配置文件 (application.yml)

```yaml
app:
  collection:
    # 检查点保存间隔（行数）
    checkpoint-interval: 1000
    # 检查点保存间隔（毫秒）
    checkpoint-interval-ms: 5000
    # 文件读取缓冲区大小（字节）
    read-buffer-size: 8192
    # 采集队列容量
    queue-capacity: 10000
    # 文件轮转检测间隔（毫秒）
    file-rotate-check-interval-ms: 1000
    # 是否启用文件监听
    enable-file-watcher: true
```

### 2. 注入工厂类

```java
@Autowired
private LocalFileCollectorFactory collectorFactory;
```

### 3. 创建日志源

```java
LogSource logSource = new LogSource();
logSource.setId(UUID.randomUUID());
logSource.setName("my-app-logs");
logSource.setSourceType(LogSourceType.LOCAL_FILE);
logSource.setPath("/var/log/myapp/app.log");
logSource.setEncoding("UTF-8");
logSource.setEnabled(true);
```

### 4. 使用工厂创建采集器

```java
// 使用工厂创建采集器
LocalFileCollector collector = collectorFactory.create(logSource);

// 启动采集
collector.start();

// 检查状态
System.out.println("采集器状态: " + collector.getState());
System.out.println("已采集行数: " + collector.getCollectedLines());
System.out.println("是否运行中: " + collector.isRunning());
```

### 5. 从队列消费日志

```java
BlockingQueue<RawLogEvent> queue = collector.getLogQueue();

while (collector.isRunning()) {
    RawLogEvent event = queue.poll(1, TimeUnit.SECONDS);
    if (event != null) {
        // 处理日志事件
        System.out.println("行号: " + event.getLineNumber());
        System.out.println("内容: " + event.getRawContent());
        System.out.println("时间: " + event.getCollectionTime());
    }
}
```

### 6. 暂停/恢复采集

```java
// 暂停采集
collector.pause();
System.out.println("暂停后的状态: " + collector.getState()); // PAUSED

// 恢复采集
collector.resume();
System.out.println("恢复后的状态: " + collector.getState()); // RUNNING
```

### 7. 停止采集器

```java
collector.stop();
System.out.println("停止后的状态: " + collector.getState()); // STOPPED
System.out.println("总共采集行数: " + collector.getCollectedLines());

// 从工厂移除
collectorFactory.remove(logSource);
```

## 完整示例

```java
import com.evelin.loganalysis.logcollection.collector.LocalFileCollector;
import com.evelin.loganalysis.logcollection.collector.LocalFileCollectorFactory;
import com.evelin.loganalysis.logcollection.model.RawLogEvent;
import com.evelin.loganalysis.logcommon.enums.LogSourceType;
import com.evelin.loganalysis.logcommon.model.LogSource;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class CollectorDemo {

    @Autowired
    private LocalFileCollectorFactory collectorFactory;

    public void startCollecting() throws Exception {
        // 1. 创建日志源
        LogSource logSource = new LogSource();
        logSource.setId(UUID.randomUUID());
        logSource.setName("demo-logs");
        logSource.setSourceType(LogSourceType.LOCAL_FILE);
        logSource.setPath("/tmp/demo.log");
        logSource.setEncoding("UTF-8");

        // 2. 使用工厂创建采集器
        LocalFileCollector collector = collectorFactory.create(logSource);

        // 3. 启动
        collector.start();
        System.out.println("采集器已启动: " + collector.isRunning());

        // 4. 消费日志
        BlockingQueue<RawLogEvent> queue = collector.getLogQueue();

        while (collector.isRunning()) {
            RawLogEvent event = queue.poll(100, TimeUnit.MILLISECONDS);
            if (event != null) {
                System.out.println("[" + event.getLineNumber() + "] " + event.getRawContent());
            }
        }

        // 5. 停止
        collector.stop();
        collectorFactory.remove(logSource);
    }
}
```

## 检查点机制

### 加载检查点（启动时）
```
优先从 Redis 加载
    ↓ (失败)
从数据库加载
    ↓ (失败)
返回空检查点（从位置0开始）
```

### 保存检查点
```
数据库持久化（同步）
    ↓
Redis缓存（异步）
    ↓
内存缓存（同步）
```

### 断点续读原理
```
1. 启动时读取保存的 offset
2. RandomAccessFile.seek(offset) 定位
3. 读取新数据
4. 定期更新 offset 到检查点
5. 重启时从 offset 继续读取
```

## 文件轮转处理

当检测到文件轮转时（如 logrotate 创建新文件）：

```
1. 检测到原文件大小 < 当前指针位置
2. 关闭原文件
3. 打开新文件
4. 重置指针为 0
5. 继续从新文件读取
```

## 监控指标

| 指标 | 说明 |
|-----|------|
| `collectedLines` | 已采集的总行数 |
| `state` | 当前状态（STOPPED/RUNNING/PAUSED/ERROR） |
| `isHealthy()` | 健康检查 |

## 注意事项

1. **文件编码**：确保配置的编码与日志文件实际编码一致
2. **文件权限**：运行程序需要有读取日志文件的权限
3. **磁盘空间**：检查点数据会占用 Redis 和数据库空间
4. **队列积压**：如果消费速度慢于采集速度，队列会满，需要告警
5. **文件轮转**：确保轮转后的文件仍然是同一种格式
6. **使用工厂**：请使用 `LocalFileCollectorFactory` 创建采集器，而不是直接 `new`

## 常见问题

### Q: 文件不存在会怎样？
A: 采集器会持续监听，等待文件创建

### Q: 如何处理二进制日志文件？
A: 目前仅支持文本日志文件，二进制文件需要额外处理

### Q: 如何调整采集速度？
A: 修改 `readBufferSize` 和 `checkpointInterval` 配置

### Q: 检查点丢失怎么办？
A: 会从文件开头重新读取，可能产生重复数据

### Q: 为什么 LocalFileCollector 不是 @Component？
A: 因为 LogSource 是实体类（@Entity），不是 Spring Bean。直接 @Component 会导致无法注入。
   使用工厂模式可以解耦创建逻辑，由工厂管理采集器的生命周期。
