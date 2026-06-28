package com.yuzong.yuzongpicturebackend.manager.sharding;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;

/**
 * 图片表分表算法
 * 泛型 <Long> 表示分片键（也就是用来决定分表的字段，这里是 spaceId）的数据类型是 Long
 */
public class PictureShardingAlgorithm implements StandardShardingAlgorithm<Long> {

    /**
     * 【核心方法 1】：精确分片算法
     * 当 SQL 中使用 "=" 或 "IN" 查询时触发。例如：WHERE space_id = 100
     *
     * @param availableTargetNames 当前数据源中实际存在的真实表名集合（比如 [picture, picture_1, picture_2]）
     *                             由application的actual-data-nodes决定的
     *                             如果我们actual-data-nodes不设置范围值，就过不了sharding的校验拿不到可用的表名。
     *                             写范围值的话，我们无法加入范围值，因为我们是他+我们的spaceId。死循环。如何解决？
     *                             答：我们可以实现一个动态分表的管理器：
     * @param preciseShardingValue 精确分片值（包含了逻辑表名、分片列名、以及具体的 SQL 传入值）
     * @return 最终路由到的真实表名
     */
    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Long> preciseShardingValue) {

        // 1. 获取 SQL 中传入的具体分片键的值（即 spaceId 的值）
        // 备注：这里是我们在application.yml里配置的分片键是 spaceId
        Long spaceId = preciseShardingValue.getValue();

        // 2. 获取配置中的"逻辑表名"（也就是你在代码或 XML 里写的表名，是 "picture"）
        String logicTableName = preciseShardingValue.getLogicTableName();

        // 3. 边界情况处理：如果 spaceId 为 null（比如执行了 SELECT * FROM picture 没带条件）
        if (spaceId == null) {
            // 返回逻辑表名，意味着不分表，直接去查主表（或者让框架去查所有表，取决于你的框架配置）
            return logicTableName;
        }

        // 4. 核心逻辑：根据 spaceId 动态拼接出"真实表名"
        // 例如：spaceId 是 88，拼接出来的 realTableName 就是 "picture_88"
        String realTableName = "picture_" + spaceId;

        // 5. 安全检查：判断拼接出来的表名，在数据库中是否真实存在
        if (availableTargetNames.contains(realTableName)) {
            // 如果存在这张分表，就告诉框架：去查这张具体的表！
            return realTableName;
        } else {
            // 如果不存在（比如 spaceId=99，但数据库里还没建 picture_99 这张表）
            // 兜底策略：返回逻辑主表名（防止程序直接报错崩溃，去主表里找找看）
            return logicTableName;
        }
    }

    /**
     * 【核心方法 2】：范围分片算法【没写不用管】
     * 当 SQL 中使用 "BETWEEN"、">"、"<" 等范围查询时触发。例如：WHERE space_id BETWEEN 1 AND 10
     *
     * @param collection 可用的真实表名集合
     * @param rangeShardingValue 范围分片值
     * @return 路由到的真实表名集合
     */
    @Override
    public Collection<String> doSharding(Collection<String> collection, RangeShardingValue<Long> rangeShardingValue) {
        // 直接返回空集合。
        // 含义：本算法【不支持】范围查询！如果写了 BETWEEN 等范围 SQL，框架会因为找不到目标表而报错或查不出数据。
        return new ArrayList<>();
    }

    /**
     * 获取算法的配置属性（当前不需要额外配置，返回 null）【没写不用管】
     */
    @Override
    public Properties getProps() {
        return null;
    }

    /**
     * 初始化方法（在算法被加载时执行，当前不需要初始化逻辑）【没写不用管】
     */
    @Override
    public void init(Properties properties) {
        // 留空
    }
}