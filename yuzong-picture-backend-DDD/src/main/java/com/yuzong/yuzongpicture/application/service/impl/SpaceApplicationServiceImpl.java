package com.yuzong.yuzongpicture.application.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuzong.yuzongpicture.application.service.SpaceApplicationService;
import com.yuzong.yuzongpicture.domain.space.service.SpaceDomainService;
import com.yuzong.yuzongpicture.infrastructure.exception.BusinessException;
import com.yuzong.yuzongpicture.infrastructure.exception.ErrorCode;
import com.yuzong.yuzongpicture.infrastructure.exception.ThrowUtils;
import com.yuzong.yuzongpicture.infrastructure.mapper.SpaceMapper;
import com.yuzong.yuzongpicture.interfaces.dto.sapce.SpaceAddRequest;
import com.yuzong.yuzongpicture.interfaces.dto.sapce.SpaceQueryRequest;
import com.yuzong.yuzongpicture.domain.space.entity.Space;
import com.yuzong.yuzongpicture.domain.space.entity.SpaceUser;
import com.yuzong.yuzongpicture.domain.user.entity.User;
import com.yuzong.yuzongpicture.domain.space.valueobject.SpaceLevelEnum;
import com.yuzong.yuzongpicture.domain.space.valueobject.SpaceRoleEnum;
import com.yuzong.yuzongpicture.domain.space.valueobject.SpaceTypeEnum;
import com.yuzong.yuzongpicture.interfaces.vo.space.SpaceVO;
import com.yuzong.yuzongpicture.interfaces.vo.user.UserVO;
import com.yuzong.yuzongpicture.application.service.SpaceUserApplicationService;
import com.yuzong.yuzongpicture.application.service.UserApplicationService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author yuzong
 *  针对表【space(空间)】的数据库操作Service实现
 * @createDate 2026-06-07 00:54:49
 */
@Service
public class SpaceApplicationServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceApplicationService {
    @Resource
    private SpaceDomainService spaceDomainService;

    @Resource
    private UserApplicationService userApplicationService;

    @Resource
    private TransactionTemplate transactionTemplate;  // 事务模板

    @Resource
    private SpaceUserApplicationService spaceUserApplicationService;
//    @Resource
//    @Lazy
//    private DynamicShardingManager dynamicShardingManager;

    /**
     * 校验空间权限
     *
     * @param loginUser 登录用户
     * @param space     空间实体类
     */
    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
       spaceDomainService.checkSpaceAuth(loginUser, space);
    }

    /**
     * 6. 添加空间
     *
     * @param spaceAddRequest 添加空间请求参数
     * @param loginUser       登录用户
     * @return 新创建的空间id
     */
    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        // 1. dto转实体类
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        // 2.设置默认值
        if (StrUtil.isBlank(spaceAddRequest.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (spaceAddRequest.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());  //默认普通版
        }
        // 补充：
        if (space.getSpaceType() == null) {
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());  //默认私有空间
        }
        // 3. 根据空间级别自动填充容量限制
        this.fillSpaceBySpaceLevel(space);
        // 4. 数据校验，备注，add参数要填true，因为是首次创建空间
        space.validSpace(true);

        Long userId = loginUser.getId();
        space.setUserId(userId); //设置创建者id
        // 5. 权限校验：非管理员只能创建普通版空间
        if (SpaceLevelEnum.COMMON.getValue() != spaceAddRequest.getSpaceLevel() && !loginUser.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的空间");
        }

//        以下针对用户id 加锁 和 事务控制
        // 6. 并发控制：基于用户ID加锁（防止同一用户同时创建多个空间）
        // intern() 确保相同字符串使用同一个锁对象
        String lock = String.valueOf(userId).intern();
        synchronized (lock) {
            // 7. 事务控制：确保以下操作要么全部成功，要么全部回滚
            Long newSpaceId = transactionTemplate.execute(status -> {
                // 7.1 检查用户是否已有空间（一个用户只能有一个私有空间）
                boolean exists = this.lambdaQuery()
                        .eq(Space::getUserId, userId)
                        .eq(Space::getSpaceType, space.getSpaceType()) //补充：检查空间类型
                        .exists();
                ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户每类空间只能创建一个");

                // 7.2 保存空间到数据库
                boolean result = this.save(space);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "空间创建失败");

                // 补充：如果是团队空间，关联新增团队成员记录
                if (SpaceTypeEnum.TEAM.getValue() == spaceAddRequest.getSpaceType()) {
                    SpaceUser spaceUser = new SpaceUser();
                    spaceUser.setSpaceId(space.getId()); // 设置空间ID
                    spaceUser.setUserId(userId); // 设置用户ID
                    spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue()); // 创建者默认管理员

                    result = spaceUserApplicationService.save(spaceUser); // 保存团队成员记录
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建团队成员记录失败");
                }
                // 补充：关于分表的补充：创建分表
                //      仅对团队空间生效
//                dynamicShardingManager.createSpacePictureTable(space);

                // 7.3 返回新创建的空间ID
                return space.getId();
            });

            // 8. 处理可能的空值情况
            return Optional.ofNullable(newSpaceId).orElse(-1L);
        }
    }


    /**
     * 5.根据空间等级填充空间
     *
     * @param space 获取查询条件的对象
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        spaceDomainService.fillSpaceBySpaceLevel(space);
    }


    /**
     * 4.获取查询条件对象
     * 获取查询条件的对象.这里参数是查询用户请求封装类。啥意思呢，就是查询用户功能的request（请求体那样（不严谨））
     *
     * @param spaceQueryRequest 获取查询条件的对象
     * @return 获取查询条件的对象
     */
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        return spaceDomainService.getQueryWrapper(spaceQueryRequest);
    }

    /**
     * 3.分页获取空间列表转换为VO类【多个】
     *
     * @param spacePage 获取查询条件的对象
     * @param request   获取查询条件的对象
     * @return 获取查询条件的对象
     */
    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        // 对象列表 => 封装对象列表
        List<SpaceVO> spaceVOList = spaceList.stream().map(SpaceVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userApplicationService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));

        // 2. 填充信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceVO.setUser(userApplicationService.getUserVO(user));
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    /**
     * 2.将空间转化为VO类【单个】
     *
     * @param space   获取查询条件的对象
     * @param request 获取查询条件的对象
     * @return 获取查询条件的对象
     */
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // space对象转封vo封装类
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        //  关联查询空间创建者的用户信息
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            // 根据用户ID查询用户实体
            User user = userApplicationService.getUserById(userId);
            // 将用户实体转换为用户VO对象
            UserVO userVO = userApplicationService.getUserVO(user);
            // 将用户信息设置到空间VO中
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }





}




