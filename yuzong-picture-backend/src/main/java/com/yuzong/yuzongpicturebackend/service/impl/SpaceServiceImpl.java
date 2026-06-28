package com.yuzong.yuzongpicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuzong.yuzongpicturebackend.exception.BusinessException;
import com.yuzong.yuzongpicturebackend.exception.ErrorCode;
import com.yuzong.yuzongpicturebackend.exception.ThrowUtils;
import com.yuzong.yuzongpicturebackend.manager.sharding.DynamicShardingManager;
import com.yuzong.yuzongpicturebackend.mapper.SpaceMapper;
import com.yuzong.yuzongpicturebackend.model.dto.sapce.SpaceAddRequest;
import com.yuzong.yuzongpicturebackend.model.dto.sapce.SpaceQueryRequest;
import com.yuzong.yuzongpicturebackend.model.entity.Space;
import com.yuzong.yuzongpicturebackend.model.entity.SpaceUser;
import com.yuzong.yuzongpicturebackend.model.entity.User;
import com.yuzong.yuzongpicturebackend.model.enums.SpaceLevelEnum;
import com.yuzong.yuzongpicturebackend.model.enums.SpaceRoleEnum;
import com.yuzong.yuzongpicturebackend.model.enums.SpaceTypeEnum;
import com.yuzong.yuzongpicturebackend.model.vo.SpaceVO;
import com.yuzong.yuzongpicturebackend.model.vo.UserVO;
import com.yuzong.yuzongpicturebackend.service.SpaceService;
import com.yuzong.yuzongpicturebackend.service.SpaceUserService;
import com.yuzong.yuzongpicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceService {

    @Resource
    private UserService userService;

    @Resource
    private TransactionTemplate transactionTemplate;  // 事务模板

    @Resource
    private SpaceUserService spaceUserService;
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
        //仅本人或者管理员可以编辑
        if (!userService.isAdmin(loginUser) && !loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无访问权限");
        }

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
        this.validSpace(space, true);

        Long userId = loginUser.getId();
        space.setUserId(userId); //设置创建者id
        // 5. 权限校验：非管理员只能创建普通版空间
        if (SpaceLevelEnum.COMMON.getValue() != spaceAddRequest.getSpaceLevel() && !userService.isAdmin(loginUser)) {
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

                    result = spaceUserService.save(spaceUser); // 保存团队成员记录
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
        // 获取空间等级对应的枚举对象
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());

        // 如果，不为空，则自动填充对应的容量，如何填充？
        if (spaceLevelEnum != null) {
            // 获取对应等级的容量
            // - 普通版：100 MB
            // - 专业版：1000 MB (1 GB)
            // - 旗舰版：10000 MB (10 GB)
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                //将对应等级的容量填充到空间对象中
                space.setMaxSize(maxSize);
            }
            // 获取对应等级的图片数量
            // - 普通版：100 张
            // - 专业版：1000 张
            // - 旗舰版：10000 张
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                //将对应等级的图片数量填充到空间对象中
                space.setMaxCount(maxCount);
            }
        }
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
        //创建查询条件对象
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }
        // 将前端传进来的空间查询参数 提取出来
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        Integer spaceType = spaceQueryRequest.getSpaceType(); // 补充：空间类型
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();


        // 构建查询条件
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "spaceType", spaceType); // 补充：空间类型

        // 排序：根据字段和排序方式动态排序
        // 参数说明：
        // - StrUtil.isNotEmpty(sortField)：如果排序字段不为空，才添加 ORDER BY
        // - sortOrder.equals("ascend")：如果 sortOrder 是 "ascend"，则升序；否则降序
        // - sortField：排序字段名（如 "createTime"、"picSize"）
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
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
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));

        // 2. 填充信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceVO.setUser(userService.getUserVO(user));
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
            User user = userService.getById(userId);
            // 将用户实体转换为用户VO对象
            UserVO userVO = userService.getUserVO(user);
            // 将用户信息设置到空间VO中
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }


    /**
     * 1.校验空间参数
     *
     * @param space 空间实体类
     *              新增一个参数，表示：是否 首次创建空间时的校验
     * @param add
     */
    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = space.getSpaceName();  // 空间名称
        Integer spaceLevel = space.getSpaceLevel();  // 空间等级，如普通专业旗舰
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);  // 空间等级对应的枚举对象
        Integer spaceType = space.getSpaceType(); //补充： 空间类型：私有空间、团队空间
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(spaceType); // 补充： 空间类型对应的枚举对象
        // 如果是要创建空间时的校验
        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
            // 补充：
            if (spaceType == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类型不能为空");
            }
        }
        // 创建空间 或 修改空间时，空间名称长度不能超过30个字符
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
        // 修改数据时，空间级别进行校验，如果空间级别传递进来了，但是枚举类当中没这级别，则报错
        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        // 补充：修改数据时，空间类型进行校验，如果空间类型传递进来了，但是枚举类当中没这类型，则报错
        if (spaceType != null && spaceTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类型不存在");
        }
    }


}




