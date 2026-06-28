package com.yuzong.yuzongpicture.shared.auth;


import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.yuzong.yuzongpicture.shared.auth.model.SpaceUserAuthConfig;
import com.yuzong.yuzongpicture.shared.auth.model.SpaceUserRole;
import com.yuzong.yuzongpicture.domain.space.entity.Space;
import com.yuzong.yuzongpicture.domain.space.entity.SpaceUser;
import com.yuzong.yuzongpicture.domain.user.entity.User;
import com.yuzong.yuzongpicture.domain.space.valueobject.SpaceRoleEnum;
import com.yuzong.yuzongpicture.domain.space.valueobject.SpaceTypeEnum;
import com.yuzong.yuzongpicture.application.service.SpaceUserApplicationService;
import com.yuzong.yuzongpicture.application.service.UserApplicationService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 空间用户权限管理器
 * 负责管理空间内不同角色的权限分配。
 * 权限规则配置在项目启动时从 JSON 文件中一次性加载到内存中，避免频繁读取磁盘。
 *
 * @author yuzong
 * @since 1.0
 */
@Component
public class SpaceUserAuthManager {

    @Resource
    private SpaceUserApplicationService spaceUserApplicationService;

    @Resource
    private UserApplicationService userApplicationService;

    /**
     * 根据空间和用户获取权限列表
     * 【备注】：我们之前也有一个StpInterfaceImpl的getPermissionList方法，之前那个是controller使用注解时，sa-token会自动调用的，给他用的。
     *         现在这个getPermissionList方法是给别的地方使用的，比如service，controller都可以调用。
     * 【问题】：为什么StpInterfaceImpl的get方法有了之后，我一些权限校验为什么还要用现在这个get方法呢？我直接全部在API接口用那个注解不行吗？
     * 【解答】：sa-token调用的get方法，只是说，你有没有权限进来。现在这个get方法是说：我返回权限列表，比如前端需要。
     *
     * @param space    空间对象，可为空
     * @param loginUser 当前登录用户，可为空
     * @return 当前用户在当前空间下的权限列表，如果用户未登录或无权限则返回空列表
     */
    public List<String> getPermissionList(Space space, User loginUser) {
        // 1. 拦截没登录的用户
        if (loginUser == null) {
            return new ArrayList<>();
        }

        // 2. 提取管理员权限
        List<String> ADMIN_PERMISSIONS = getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());

        // 3. 公共图库
        if (space == null) {
            // 只有系统管理员有权限
            if (loginUser.isAdmin()) {
                return ADMIN_PERMISSIONS;
            }
            return new ArrayList<>();
        }

        // 4. 获取空间类型美剧类，如果为空返回空列表
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(space.getSpaceType());
        if (spaceTypeEnum == null) {
            return new ArrayList<>();
        }
        // 5. 根据空间获取对应的权限
        switch (spaceTypeEnum) {
            case PRIVATE:
                // 私有空间，仅本人或管理员有所有权限
                if (space.getUserId().equals(loginUser.getId()) || loginUser.isAdmin()) {
                    return ADMIN_PERMISSIONS;
                } else {
                    return new ArrayList<>();
                }
            case TEAM:
                // 团队空间，查询 SpaceUser 并获取角色和权限
                SpaceUser spaceUser = spaceUserApplicationService.lambdaQuery()
                        .eq(SpaceUser::getSpaceId, space.getId()) // 去数据库左边参数找右边参数
                        .eq(SpaceUser::getUserId, loginUser.getId()) // 去数据库右边参数找左边参数
                        .one();
                if (spaceUser == null) {  // 如果空间用户不存在
                    return new ArrayList<>();
                } else {
                    // 否则返回对应角色的权限
                    return getPermissionsByRole(spaceUser.getSpaceRole());
                }
        }
        return new ArrayList<>();
    }

    /**
     * 空间用户权限配置（全局单例，类加载时初始化）
     */
    public static final SpaceUserAuthConfig SPACE_USER_AUTH_CONFIG;

    // 静态代码块：在类加载阶段读取并解析 JSON 配置文件，确保配置数据常驻内存
    static {
        // 从 classpath 下的 biz 目录读取 UTF-8 编码的 JSON 配置文件
        String json = ResourceUtil.readUtf8Str("biz/spaceUserAuthConfig.json");
        // 将 JSON 字符串反序列化为 Java 配置对象
        SPACE_USER_AUTH_CONFIG = JSONUtil.toBean(json, SpaceUserAuthConfig.class);
    }

    /**
     * 根据空间角色标识获取对应的权限列表
     *
     * @param spaceUserRole 空间角色标识（如：admin, user，不可为空）
     * @return 该角色拥有的权限标识列表；若角色无效或未配置，则返回空列表（保证不返回 null）
     */
    public List<String> getPermissionsByRole(String spaceUserRole) {
        // 1. 防御性校验：角色标识为空时，直接返回空列表，避免后续空指针异常
        if (StrUtil.isBlank(spaceUserRole)) {
            return new ArrayList<>();
        }

        // 2. 从内存配置中流式查找匹配的角色对象
        SpaceUserRole role = SPACE_USER_AUTH_CONFIG.getRoles().stream()
                .filter(r -> spaceUserRole.equals(r.getKey())) // 匹配角色 Key
                .findFirst()                                   // 获取第一个匹配项
                .orElse(null);                                 // 未找到则返回 null

        // 3. 兜底处理：若配置中不存在该角色，返回空列表
        if (role == null) {
            return new ArrayList<>();
        }

        // 4. 返回该角色对应的权限列表
        return role.getPermissions();
    }
}