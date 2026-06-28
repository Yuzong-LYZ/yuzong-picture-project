package com.yuzong.yuzongpicture.application.service.impl;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuzong.yuzongpicture.application.service.UserApplicationService;
import com.yuzong.yuzongpicture.infrastructure.exception.BusinessException;
import com.yuzong.yuzongpicture.infrastructure.exception.ErrorCode;
import com.yuzong.yuzongpicture.infrastructure.exception.ThrowUtils;
import com.yuzong.yuzongpicture.infrastructure.mapper.SpaceMapper;
import com.yuzong.yuzongpicture.interfaces.dto.sapce.analyze.*;
import com.yuzong.yuzongpicture.domain.picture.entity.Picture;
import com.yuzong.yuzongpicture.interfaces.vo.space.analyze.*;
import com.yuzong.yuzongpicture.domain.space.entity.Space;
import com.yuzong.yuzongpicture.domain.user.entity.User;
import com.yuzong.yuzongpicture.application.service.PictureApplicationService;
import com.yuzong.yuzongpicture.application.service.SpaceAnalyzeApplicationService;
import com.yuzong.yuzongpicture.application.service.SpaceApplicationService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author : Yuzong
 * @date 2026/6/13 18:26
 *
 **/
@Service  // 补充：之前忘记加了
public class SpaceAnalyzeApplicationServiceImpl implements SpaceAnalyzeApplicationService {
    @Resource
    private SpaceApplicationService spaceApplicationService;
    @Resource
    private PictureApplicationService pictureApplicationService;

    /**
     * 根据请求对象封装查询条件
     *
     * @param spaceAnalyzeRequest
     * @param queryWrapper
     */
    private static void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest, QueryWrapper<Picture> queryWrapper) {
        // 全空间分析
        if (spaceAnalyzeRequest.isQueryAll()) {
            return;
        }
        // 公共图库分析
        if (spaceAnalyzeRequest.isQueryPublic()) {
            queryWrapper.isNull("spaceId");
            return;
        }
        // 分析特定空间
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        if (spaceId != null) {
            queryWrapper.eq("spaceId", spaceId);
            return;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "未指定查询范围");
    }

    /**
     * 获取空间排行分析数据【管理员】
     *
     * @param spaceRankAnalyzeRequest 排行分析请求参数（包含空间ID、筛选条件等）
     * @param loginUser               当前登录用户（用于权限校验）
     * @return 空间排行列表
     */
    @Override
    public List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);

// 仅管理员可查看空间排行
        ThrowUtils.throwIf(!loginUser.isAdmin(), ErrorCode.NO_AUTH_ERROR, "无权查看空间排行");

// 构造查询条件
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "spaceName", "userId", "totalSize")
                .orderByDesc("totalSize")
                .last("LIMIT " + spaceRankAnalyzeRequest.getTopN()); // 取前 N 名

// 查询结果
        return spaceApplicationService.list(queryWrapper);
    }

    /**
     * 获取空间图片用户上传分析数据
     *
     * @param spaceUserAnalyzeRequest 用户分析请求参数（包含空间ID、筛选条件等）
     * @param loginUser               当前登录用户（用于权限校验）
     * @return 按用户分组的图片数量统计响应列表
     */
    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 检查权限
        checkSpaceAnalyzeAuth(spaceUserAnalyzeRequest, loginUser);

        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        Long userId = spaceUserAnalyzeRequest.getUserId();
        queryWrapper.eq(ObjUtil.isNotNull(userId), "userId", userId);
        fillAnalyzeQueryWrapper(spaceUserAnalyzeRequest, queryWrapper);

        // 分析维度：每日、每周、每月
        String timeDimension = spaceUserAnalyzeRequest.getTimeDimension();
        switch (timeDimension) {
            case "day":
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m-%d') AS period", "COUNT(*) AS count");
                break;
            case "week":
                queryWrapper.select("YEARWEEK(createTime) AS period", "COUNT(*) AS count");
                break;
            case "month":
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m') AS period", "COUNT(*) AS count");
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的时间维度");
        }

        // 分组和排序
        queryWrapper.groupBy("period").orderByAsc("period");

        // 查询结果并转换
        List<Map<String, Object>> queryResult = pictureApplicationService.getBaseMapper().selectMaps(queryWrapper);
        return queryResult.stream()
                .map(result -> {
                    String period = result.get("period").toString();
                    Long count = ((Number) result.get("count")).longValue();
                    return new SpaceUserAnalyzeResponse(period, count);
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取空间图片大小分析数据（按预设区间统计各尺寸段的图片数量）
     *
     * @param spaceSizeAnalyzeRequest 大小分析请求参数（包含空间ID、筛选条件等）
     * @param loginUser               当前登录用户（用于权限校验）
     * @return 图片大小分段统计响应列表，按区间从小到大排列
     */
    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser) {
        // 1. 参数校验：请求对象不能为空
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);

        // 2. 权限校验：验证当前用户是否有权查看该空间的分析数据
        checkSpaceAnalyzeAuth(spaceSizeAnalyzeRequest, loginUser);

        // 3. 构造查询条件：根据请求参数填充 WHERE 子句（如空间ID、时间范围等）
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceSizeAnalyzeRequest, queryWrapper);

        // 4. 仅查询 picSize 字段，减少不必要的数据传输
        queryWrapper.select("picSize");

        // 5. 执行查询并将结果统一转换为 Long 类型的图片大小列表（单位：字节）
        //    selectObjs 返回的是 Object，数据库数值类型可能是 Integer/BigDecimal 等，
        //    通过 Number.longValue() 安全转换，避免 ClassCastException
        List<Long> picSizes = pictureApplicationService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .map(size -> ((Number) size).longValue())
                .collect(Collectors.toList());

        // 6. 定义大小分段范围并统计各区间的图片数量
        //    ⚠️ 必须使用 LinkedHashMap 保证插入顺序，使返回结果按区间从小到大展示
        Map<String, Long> sizeRanges = new LinkedHashMap<>();
        sizeRanges.put("<100KB", picSizes.stream()
                .filter(size -> size < 100 * 1024L).count());
        sizeRanges.put("100KB-500KB", picSizes.stream()
                .filter(size -> size >= 100 * 1024L && size < 500 * 1024L).count());
        sizeRanges.put("500KB-1MB", picSizes.stream()
                .filter(size -> size >= 500 * 1024L && size < 1024 * 1024L).count());
        sizeRanges.put(">1MB", picSizes.stream()
                .filter(size -> size >= 1024 * 1024L).count());

        // 7. 将分段统计结果转换为响应对象列表（LinkedHashMap 保证了有序性）
        return sizeRanges.entrySet().stream()
                .map(entry -> new SpaceSizeAnalyzeResponse(
                        entry.getKey(),   // 区间名称，如 "<100KB"
                        entry.getValue()  // 该区间内的图片数量
                ))
                .collect(Collectors.toList());
    }

    /**
     * 获取空间标签分析数据
     *
     * @param spaceTagAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser) {
        // 1. 参数校验：请求对象不能为空
        ThrowUtils.throwIf(spaceTagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);

        // 2. 权限校验：验证当前用户是否有权查看该空间的分析数据
        checkSpaceAnalyzeAuth(spaceTagAnalyzeRequest, loginUser);

        // 3. 构造查询条件：根据请求参数填充 WHERE 子句（如空间ID、时间范围等）
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceTagAnalyzeRequest, queryWrapper);

        // 4. 仅查询 tags 字段，避免查出不必要的列，提升查询性能
        queryWrapper.select("tags");

        // 5. 执行查询并提取所有符合条件的标签JSON字符串列表
        //    selectObjs 返回第一列的值列表，这里即 tags 字段的原始JSON串
        List<String> tagsJsonList = pictureApplicationService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .filter(ObjUtil::isNotNull)       // 过滤掉 tags 为 null 的记录
                .map(Object::toString)            // 将 Object 转为 String
                .collect(Collectors.toList());

        // 6. 解析每条记录的标签JSON数组，展平后按标签名分组统计出现次数
        //    例如: ["风景","人像"] + ["风景","美食"] → {风景:2, 人像:1, 美食:1}
        Map<String, Long> tagCountMap = tagsJsonList.stream()
                .flatMap(tagsJson -> JSONUtil.toList(tagsJson, String.class).stream()) // JSON数组 → Stream<String>，再展平
                .collect(Collectors.groupingBy(
                        tag -> tag,                // 按标签名称分组
                        Collectors.counting()      // 统计每组数量
                ));

        // 7. 将统计结果转换为响应对象，并按使用次数降序排序后返回
        return tagCountMap.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue())) // 按 value 降序排列
                .map(entry -> new SpaceTagAnalyzeResponse(
                        entry.getKey(),   // 标签名称
                        entry.getValue()  // 使用次数
                ))
                .collect(Collectors.toList());
    }

    /**
     * 获取空间分类分组分析数据
     *
     * @param spaceCategoryAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);

        // 检查权限
        checkSpaceAnalyzeAuth(spaceCategoryAnalyzeRequest, loginUser);

        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        // 根据分析范围补充查询条件
        fillAnalyzeQueryWrapper(spaceCategoryAnalyzeRequest, queryWrapper);

        // 使用 MyBatis-Plus 分组查询
        queryWrapper.select("category AS category",
                        "COUNT(*) AS count",
                        "SUM(picSize) AS totalSize")
                .groupBy("category");

        // 查询并转换结果
        return pictureApplicationService.getBaseMapper().selectMaps(queryWrapper)
                .stream()
                .map(result -> {
                    String category = result.get("category") != null ? result.get("category").toString() : "未分类";
                    Long count = ((Number) result.get("count")).longValue();
                    Long totalSize = ((Number) result.get("totalSize")).longValue();
                    return new SpaceCategoryAnalyzeResponse(category, count, totalSize);
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取空间使用分析数据
     * 【功能说明】
     * 统计并返回空间的使用情况，包括：
     * - 已用大小 / 最大大小 / 使用比例
     * - 已用数量 / 最大数量 / 使用比例
     * <p>
     * 【支持三种查询模式】
     * 1. 查询全部空间（queryAll = true）：统计所有空间的总使用情况（仅管理员）
     * 2. 查询公共图库（queryPublic = true）：统计公共图库的使用情况（仅管理员）
     * 3. 查询指定空间（spaceId 不为空）：统计某个特定空间的使用情况（空间所有者或管理员）
     * <p>
     * 【注意事项】
     * 1. 查询全部或公共图库时，只有管理员有权限
     * 2. 查询指定空间时，只有空间所有者或管理员有权限
     * 3. 公共图库没有上限，所以 maxSize、maxCount 等为 null
     * 4. 使用比例保留两位小数，方便前端直接展示
     *
     * @param spaceUsageAnalyzeRequest SpaceUsageAnalyzeRequest 请求参数
     * @param loginUser                当前登录用户
     * @return SpaceUsageAnalyzeResponse 分析结果
     */
    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser) {
//        1. 参数校验
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
//        2. 判断查询模式
        // 2.1 如果查询全部或公共图库
        if (spaceUsageAnalyzeRequest.isQueryAll() || spaceUsageAnalyzeRequest.isQueryPublic()) {

            // 2.2仅管理员可以访问
            boolean isAdmin = loginUser.isAdmin();
            ThrowUtils.throwIf(!isAdmin, ErrorCode.NO_AUTH_ERROR, "无权访问空间");

            // 统计公共图库的资源使用
            // 2.3 构造查询条件
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            queryWrapper.select("picSize");
            // 2.4 如果不是查询全部图库，证明查询公共图库，添加过滤条件
            if (!spaceUsageAnalyzeRequest.isQueryAll()) {
                queryWrapper.isNull("spaceId");
            }
            // 2.5 到这里，如果查询是全部图库空间，则不需要过滤条件
            // 2.6 执行数据库查询
            List<Object> pictureObjList = pictureApplicationService.getBaseMapper().selectObjs(queryWrapper);

            // 2.7 计算总大小
            // 将 Object 列表转为 Long 流，然后求和
            // 注意：需要判断类型，防止 ClassCastException
            long usedSize = pictureObjList.stream()
                    .mapToLong(result -> result instanceof Long ? (Long) result : 0)
                    .sum();

            // 2.8 计算总数量
            // 列表的大小就是图片数量
            long usedCount = pictureObjList.size();
            // 2.9 封装返回结果
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(usedSize);      // 已用大小
            spaceUsageAnalyzeResponse.setUsedCount(usedCount);    // 已用数量

            // 公共图库无上限、无比例（设置为 null）
            spaceUsageAnalyzeResponse.setMaxSize(null);           // 最大大小：无限制
            spaceUsageAnalyzeResponse.setSizeUsageRatio(null);    // 大小使用比例：无意义
            spaceUsageAnalyzeResponse.setMaxCount(null);          // 最大数量：无限制
            spaceUsageAnalyzeResponse.setCountUsageRatio(null);   // 数量使用比例：无意义
            return spaceUsageAnalyzeResponse;
        } else {
//           1. 查询指定空间
            // 1.1 获取空间id
            Long spaceId = spaceUsageAnalyzeRequest.getSpaceId();
            // 校验参数
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
            // 1.2 查询空间信息
            Space space = spaceApplicationService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");


            // 1.3 权限校验：仅空间所有者或管理员可访问
            spaceApplicationService.checkSpaceAuth(loginUser, space);

            // 2. 构造返回结果
            SpaceUsageAnalyzeResponse response = new SpaceUsageAnalyzeResponse();
            // 设置已用大小（从 space 表的 totalSize 字段读取）
            response.setUsedSize(space.getTotalSize());
            // 设置最大大小（从 space 表的 maxSize 字段读取）
            response.setMaxSize(space.getMaxSize());

            // 2.1 后端直接算好百分比，这样前端可以直接展示
            // 计算公式：(已用大小 / 最大大小) * 100
            // NumberUtil.round() 保留两位小数
            // 例如：totalSize=600, maxSize=1000 → 60.00
            double sizeUsageRatio = NumberUtil.round(space.getTotalSize() * 100.0 / space.getMaxSize(), 2).doubleValue();
            response.setSizeUsageRatio(sizeUsageRatio);  // 大小使用比例
            response.setUsedCount(space.getTotalCount());  // 已用数量
            response.setMaxCount(space.getMaxCount());  // 最大数量
            double countUsageRatio = NumberUtil.round(space.getTotalCount() * 100.0 / space.getMaxCount(), 2).doubleValue();
            response.setCountUsageRatio(countUsageRatio);  // 数量使用比例

            return response;
        }
    }

    /**
     * 检查空间分析权限
     *
     * @param spaceAnalyzeRequest
     * @param loginUser
     */
    private void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {
        // 检查权限
        // 全空间分析或者公共图库权限校验：仅管理员可访问
        if (spaceAnalyzeRequest.isQueryAll() || spaceAnalyzeRequest.isQueryPublic()) {
            ThrowUtils.throwIf(!loginUser.isAdmin(), ErrorCode.NO_AUTH_ERROR, "无权访问公共图库");
        } else {
            // 分析特定空间，仅本人或管理员可访问
            Long spaceId = spaceAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
            Space space = spaceApplicationService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            spaceApplicationService.checkSpaceAuth(loginUser, space);
        }
    }


}
