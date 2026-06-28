package com.yuzong.yuzongpicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuzong.yuzongpicturebackend.exception.BusinessException;
import com.yuzong.yuzongpicturebackend.exception.ErrorCode;
import com.yuzong.yuzongpicturebackend.exception.ThrowUtils;
import com.yuzong.yuzongpicturebackend.mapper.SpaceUserMapper;
import com.yuzong.yuzongpicturebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.yuzong.yuzongpicturebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.yuzong.yuzongpicturebackend.model.entity.Space;
import com.yuzong.yuzongpicturebackend.model.entity.SpaceUser;
import com.yuzong.yuzongpicturebackend.model.entity.User;
import com.yuzong.yuzongpicturebackend.model.enums.SpaceRoleEnum;
import com.yuzong.yuzongpicturebackend.model.vo.SpaceUserVO;
import com.yuzong.yuzongpicturebackend.model.vo.SpaceVO;
import com.yuzong.yuzongpicturebackend.model.vo.UserVO;
import com.yuzong.yuzongpicturebackend.service.SpaceService;
import com.yuzong.yuzongpicturebackend.service.SpaceUserService;
import com.yuzong.yuzongpicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author yuzong
 *  针对表【space_user(空间用户关联)】的数据库操作Service实现
 * @createDate 2026-06-17 21:21:44
 */
@Service
public class SpaceUserServiceImpl extends ServiceImpl<SpaceUserMapper, SpaceUser>
        implements SpaceUserService {
    @Resource
    private UserService userService;

    @Resource
    @Lazy //补充： 延迟加载
    private SpaceService spaceService;

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

    /**
     * 4. 获取空间用户视图对象列表（批量查询，解决 N+1 性能问题）
     *
     * @param spaceUserList 空间用户实体列表 (DO)
     * @return 包含完整关联信息的 SpaceUserVO 列表
     */
    @Override
    public List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList) {
        // 1. 边界处理：如果传入的列表为空，直接返回空列表，防止后续空指针异常
        if (CollUtil.isEmpty(spaceUserList)) {
            return Collections.emptyList();
        }

        // 2. 基础转换：将 DO 列表批量转换为 VO 列表（此时 User 和 Space 字段还是空的）
        List<SpaceUserVO> spaceUserVOList = spaceUserList.stream()
                .map(SpaceUserVO::objToVo)
                .collect(Collectors.toList());

        // ================= 核心优化：批量查询代替循环查询 =================

        // 3. 收集 ID 并去重：
        // 把所有需要关联的 userId 和 spaceId 提取出来放到 Set 中（Set 自动去重，减少查询量）
        Set<Long> userIdSet = spaceUserList.stream()
                .map(SpaceUser::getUserId)
                .collect(Collectors.toSet());

        Set<Long> spaceIdSet = spaceUserList.stream()
                .map(SpaceUser::getSpaceId)
                .collect(Collectors.toSet());

        // 4. 批量查询数据库（⚠️ 关键：不管列表有 10 条还是 1000 条，这里只查 2 次数据库！）
        // 查询用户，并按 userId 分组，生成 Map<userId, List<User>>
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));

        // 查询空间，并按 spaceId 分组，生成 Map<spaceId, List<Space>>
        Map<Long, List<Space>> spaceIdSpaceListMap = spaceService.listByIds(spaceIdSet).stream()
                .collect(Collectors.groupingBy(Space::getId));

        // 5. 在内存中组装数据（从 Map 中取数据，不再访问数据库）
        spaceUserVOList.forEach(spaceUserVO -> {
            Long userId = spaceUserVO.getUserId();
            Long spaceId = spaceUserVO.getSpaceId();

            // 5.1 填充用户信息
            User user = null;
            // 如果 Map 中包含该 userId，就取出 List 中的第一个 User
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            // 转为 UserVO（脱敏）并 set 进去
            spaceUserVO.setUser(userService.getUserVO(user));

            // 5.2 填充空间信息
            Space space = null;
            if (spaceIdSpaceListMap.containsKey(spaceId)) {
                space = spaceIdSpaceListMap.get(spaceId).get(0);
            }
            // 转为 SpaceVO 并 set 进去
            spaceUserVO.setSpace(SpaceVO.objToVo(space));
        });

        // 6. 返回组装完毕的 VO 列表
        return spaceUserVOList;
    }

    /**
     * 3.获取空间用户视图对象（单个）
     *
     * @param spaceUser 空间用户对象
     * @param request
     * @return
     */
    @Override
    public SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request) {
        // 1. 基础转换：将 DO 对象的字段拷贝到 VO 对象
        SpaceUserVO spaceUserVO = SpaceUserVO.objToVo(spaceUser);

        // 备注：如果要关联某信息。比如a要关联用户信息，则查询用户信息的表，然后set给a对象，如果要给前端看，则要把对象都转化为vo再set
        // 2. 关联查询用户信息（多查一次 user 表）
        Long userId = spaceUser.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);          // 根据 userId 查 user 表
            UserVO userVO = userService.getUserVO(user);      // 将 User 也转成 VO（脱敏/格式化）
            spaceUserVO.setUser(userVO);                      // 塞进 spaceUserVO 里
        }

        // 3. 关联查询空间信息（多查一次 space 表）
        Long spaceId = spaceUser.getSpaceId();
        if (spaceId != null && spaceId > 0) {
            Space space = spaceService.getById(spaceId);      // 根据 spaceId 查 space 表
            SpaceVO spaceVO = spaceService.getSpaceVO(space, request); // Space 也转 VO
            spaceUserVO.setSpace(spaceVO);                    // 塞进 spaceUserVO 里
        }

        // 4. 返回包含完整信息的 VO
        return spaceUserVO;
    }

    /**
     * 2.添加空间用户（将用户关联到某个空间）
     *
     * @param spaceUserAddRequest 前端/调用方传入的添加请求对象
     * @return 新创建的空间用户记录的 ID
     */
    @Override
    public long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest) {
        // 1. 请求对象不能为 null
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);

        // 2. 将请求对象（DTO）转换为实体对象（DO）
        //    BeanUtils.copyProperties：自动把同名同类型的字段从 source 拷贝到 target
        //    例如：spaceUserAddRequest.spaceId → spaceUser.spaceId
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserAddRequest, spaceUser);

        // 3. 参数校验（调用上一个方法，add=true 表示新增场景）
        //    会校验：spaceId/userId 不为空、用户存在、空间存在、角色合法
        validSpaceUser(spaceUser, true);

        // 4. 执行数据库插入操作（MyBatis-Plus 的 save 方法）
        //    save 成功后会自动将生成的主键 ID 回填到 spaceUser.id
        boolean result = this.save(spaceUser);

        // 5. 如果插入失败（比如唯一索引冲突等），抛出操作异常
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        // 6. 返回新记录的主键 ID
        return spaceUser.getId();
    }


    /**
     * 1. 校验空间用户信息是否合法
     *
     * @param spaceUser 空间用户对象
     * @param add       是否为创建（新增）操作。true=新增，false=更新
     */
    @Override
    public void validSpaceUser(SpaceUser spaceUser, boolean add) {
        // 1. 基础校验：spaceUser 对象本身不能为 null
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.PARAMS_ERROR);

        // 获取空间 ID 和用户 ID，后续校验会用到
        Long spaceId = spaceUser.getSpaceId();
        Long userId = spaceUser.getUserId();

        // 2. 新增操作时的额外校验（更新时允许部分字段为空，所以跳过）
        if (add) {
            // 2.1 spaceId 和 userId 必须同时存在，任一为空都报错
            ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR);

            // 2.2 根据 userId 查数据库，确认该用户真实存在（防止传入一个不存在的用户 ID）
            User user = userService.getById(userId);
            ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

            // 2.3 根据 spaceId 查数据库，确认该空间真实存在（防止传入一个不存在的空间 ID）
            // 备注：这个空间是用户要加入的空间，不是用户当前所在的空
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }

        // 3. 校验空间角色（新增和更新都需要校验）
        String spaceRole = spaceUser.getSpaceRole();
        // 通过枚举类查找该角色值是否合法
        SpaceRoleEnum spaceRoleEnum = SpaceRoleEnum.getEnumByValue(spaceRole);

        // 如果传了角色值，但在枚举中找不到对应项 → 说明角色值非法
        // 注意：spaceRole 为 null 时不报错，因为更新时角色字段可以不传
        if (spaceRole != null && spaceRoleEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间角色不存在");
        }
    }


}




