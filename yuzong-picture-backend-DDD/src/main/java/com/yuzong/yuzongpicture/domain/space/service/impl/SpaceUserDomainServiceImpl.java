package com.yuzong.yuzongpicture.domain.space.service.impl;

import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuzong.yuzongpicture.domain.space.entity.SpaceUser;
import com.yuzong.yuzongpicture.domain.space.service.SpaceUserDomainService;
import com.yuzong.yuzongpicture.infrastructure.mapper.SpaceUserMapper;
import com.yuzong.yuzongpicture.interfaces.dto.spaceuser.SpaceUserQueryRequest;
import org.springframework.stereotype.Service;

/**
 * @author yuzong
 *  针对表【space_user(空间用户关联)】的数据库操作Service实现
 * @createDate 2026-06-17 21:21:44
 */
@Service
public class SpaceUserDomainServiceImpl
        implements SpaceUserDomainService {
    /**
     * 5. 构建查询条件。构建 SpaceUser（空间用户）实体的查询条件构造器
     * 根据传入的查询请求对象，动态拼接 SQL 的 WHERE 子句
     *
     * @param spaceUserQueryRequest 前端传入的查询请求参数对象
     * @return 构建完成的 MyBatis-Plus QueryWrapper 条件构造器
     */
    @Override
    public QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest) {
        // 1. 初始化条件构造器
        QueryWrapper<SpaceUser> queryWrapper = new QueryWrapper<>();

        // 2. 防御性编程：参数非空校验。若请求对象为 null，则直接返回空构造器（等价于不加 WHERE 条件）
        if (spaceUserQueryRequest == null) {
            return queryWrapper;
        }

        // 3. 提取查询参数：从请求对象中解构出具体的查询字段
        Long id = spaceUserQueryRequest.getId();
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        String spaceRole = spaceUserQueryRequest.getSpaceRole();

        // 4. 动态拼接等值查询条件（eq 对应 SQL 中的 "="）
        // 参数说明：(condition, column, val)
        // - condition: 拼接条件开关，仅当参数非空时（isNotEmpty）才拼接该条件，防止 null 值导致 SQL 异常
        // - column: 数据库表中的列名
        // - val: 参与比对的具体参数值
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceRole), "spaceRole", spaceRole);

        // 5. 返回构建完成的条件构造器，交由 Service 层执行具体的数据库操作
        return queryWrapper;
    }




}




