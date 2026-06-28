package com.yuzong.yuzongpicturebackend.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import com.yuzong.yuzongpicturebackend.exception.BusinessException;
import com.yuzong.yuzongpicturebackend.exception.ErrorCode;
import com.yuzong.yuzongpicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.yuzong.yuzongpicturebackend.model.entity.Picture;
import com.yuzong.yuzongpicturebackend.model.entity.Space;
import com.yuzong.yuzongpicturebackend.model.entity.SpaceUser;
import com.yuzong.yuzongpicturebackend.model.entity.User;
import com.yuzong.yuzongpicturebackend.model.enums.SpaceRoleEnum;
import com.yuzong.yuzongpicturebackend.model.enums.SpaceTypeEnum;
import com.yuzong.yuzongpicturebackend.service.PictureService;
import com.yuzong.yuzongpicturebackend.service.SpaceService;
import com.yuzong.yuzongpicturebackend.service.SpaceUserService;
import com.yuzong.yuzongpicturebackend.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.yuzong.yuzongpicturebackend.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 自定义权限加载接口实现类
 */
@Component    // 保证此类被 SpringBoot 扫描，完成 Sa-Token 的自定义权限验证扩展 
public class StpInterfaceImpl implements StpInterface {

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;
    @Resource
    private SpaceUserService spaceUserService;
    @Resource
    private UserService userService;
    @Resource
    private PictureService pictureService;
    @Resource
    private SpaceService spaceService;

    // 默认是/api
    @Value("${server.servlet.context-path}")
    private String contextPath;

    /**
     * 返回一个账号所拥有的权限码集合
     * 备注：这个是这个是我们controller层的@SaCheckPermission注解时，Sa-Token会自动调用的
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // ================= 1. 前置校验 =================
        // 这里只处理“空间(space)”类型的登录，其他的直接返回空列表（意味着没有任何权限，或者交由其他逻辑处理）。
        if (!StpKit.SPACE_TYPE.equals(loginType)) {
            return new ArrayList<>();
        }

        // 提前获取“管理员权限全集”
        List<String> ADMIN_PERMISSIONS = spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());

        // 获取前端请求传过来的参数上下文（pictureId, spaceId 等）
        SpaceUserAuthContext authContext = getAuthContextByRequest();

        // ================= 2. 场景 A：公共列表查询（无条件放行）=================
        // 如果前端没传任何 ID（比如只是打开首页看看公共图片列表），
        // 直接把“管理员全集权限”返回，骗过框架，让请求畅通无阻。
        if (isAllFieldsNull(authContext)) {
            return ADMIN_PERMISSIONS;
        }

        // 否则就不是查看公共图库
        // ================= 3. 获取当前登录人信息 =================
        // 备注：在前面已经在登录的方法，将登录人的信息存在sa-token里面了。所以我们根据前端传进来的loginId获取登录人信息
        //      我USER_LOGIN_STATE里面是user_login，所以他会直接去session当中找user_login并返回他的User对象（因为我们强转了User）
        User loginUser = (User) StpKit.SPACE.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户未登录");
        }
        Long userId = loginUser.getId(); // 获取当前登录人 ID

        // ================= 4. 场景 B：前端直接传了 SpaceUser 对象 =================
        // 如果上下文里直接带了成员对象，直接按这个成员的角色算权限。
        // 比如空间成员是admin，将admin传入getPermissionsByRole，分配admin所对应的权限。
        SpaceUser spaceUser = authContext.getSpaceUser();
        if (spaceUser != null) {
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }

        // ================= 5. 场景 C：操作“团队空间”里的某个具体成员 =================
        // 比如：你是队长，你要“踢出”某个队员，前端传了被踢人的 spaceUserId。
        Long spaceUserId = authContext.getSpaceUserId();  // 获取被操作人 ID
        if (spaceUserId != null) {
            // 查出“被操作人”的信息，主要是为了拿到他所在的 spaceId（团队 ID）
            spaceUser = spaceUserService.getById(spaceUserId); // 查出“被操作人”的信息
            if (spaceUser == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间用户信息");
            }

            // 【核心】获取当前登录人在团队里的角色的信息，结局只有存在或者null
            SpaceUser loginSpaceUser = spaceUserService.lambdaQuery()
                    // 确保同一个空间：去去数据库的 space_id列，找 等于被操作人 spaceId的值
                    .eq(SpaceUser::getSpaceId, spaceUser.getSpaceId())
                    // 确保空间内有当前登录人：去数据库的 user_id列，找 等于当前登录人 userId的那条数据
                    .eq(SpaceUser::getUserId, userId)
                    .one();

            // 如果你根本不在这个团队，返回空列表（无权限，踢人失败）
            if (loginSpaceUser == null) {
                return new ArrayList<>();
            }
            // 返回你在团队里的角色对应的权限。如果你是队长，就有踢人权限；如果你是普通队员，就没有。
            // 这里不用真正执行踢人操作，只是返回一个权限列表
            return spaceUserAuthManager.getPermissionsByRole(loginSpaceUser.getSpaceRole());
        }

        // ================= 6. 场景 D：操作“图片”，但不知道图片在哪个空间 =================
        // 比如：你点击“删除图片”，前端只传了 pictureId。
        Long spaceId = authContext.getSpaceId();
        if (spaceId == null) {
            Long pictureId = authContext.getPictureId();
            // 如果连图片 ID 都没有（理论上走不到这），直接放行。
            if (pictureId == null) {
                return ADMIN_PERMISSIONS;
            }

            // 去数据库把这张图片查出来，看看它到底属于哪里。
            Picture picture = pictureService.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .select(Picture::getId, Picture::getSpaceId, Picture::getUserId)
                    .one();
            if (picture == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到图片信息");
            }

            // 拿到图片的真实归属空间 ID
            spaceId = picture.getSpaceId();

            // 【子场景 D1】：如果图片的 spaceId 为空，说明这是一张“公共图库”里的图片。
            if (spaceId == null) {
                // 公共图片：只有“作者本人”或“系统超级管理员”能删改（给全集权限）。
                if (picture.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    // 其他人只能看，不能删改。所以只给一个 "view" 权限。
                    return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
                }
            }
            // 【子场景 D2】：如果图片有 spaceId，说明它属于某个空间，代码继续往下走，去查这个空间的信息。
        }

        // ================= 7. 场景 E：操作“空间”本身（或者从图片推导出了空间）=================
        // 走到这里，手里一定有一个明确的 spaceId 了（可能是前端直接传的，也可能是上一步从图片身上查出来的）。
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间信息");
        }

        // 【子场景 E1】：这是一个“私有空间”（仅自己可见）
        if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            // 私有空间：只有“空间主人”或“系统超级管理员”能操作。
            if (space.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            } else {
                // 别人的私有空间，你没有任何权限。
                return new ArrayList<>();
            }
        }
        // 【子场景 E2】：这是一个“团队空间”（多人协作）
        else {
            // 去数据库查，“当前登录人”在这个团队里是什么角色。
            spaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceId)
                    .eq(SpaceUser::getUserId, userId)
                    .one();

            // 如果你不是这个团队的成员，无权限。
            if (spaceUser == null) {
                return new ArrayList<>();
            }
            // 返回你在团队里的角色对应的权限。
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
    }

    /**
     * 本项目不使用。返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 本 list 仅做模拟，实际项目中要根据具体业务逻辑来查询角色
        return new ArrayList<>();
    }


    /**
     * 判断对象的所有字段是否都为空
     */
    private boolean isAllFieldsNull(Object object) {
        if (object == null) {
            return true; // 对象本身为空
        }
        // 获取所有字段并判断是否所有字段都为空
        return Arrays.stream(ReflectUtil.getFields(object.getClass()))
                // 获取字段值
                .map(field -> ReflectUtil.getFieldValue(object, field))
                // 检查是否所有字段都为空
                .allMatch(ObjectUtil::isEmpty);
    }


    /**
     * 从当前 HTTP 请求中解析并构建“空间用户鉴权上下文”对象
     * 主要用于在 自定义注解或AOP（切面）或拦截器中，统一提取鉴权所需的参数
     * 不管前端怎么传参，也不管当前是哪个业务模块，我都能搞到一个标准的“鉴权上下文（Context）”，用来判断用户有没有权限。
     */
    private SpaceUserAuthContext getAuthContextByRequest() {
        // 1. 获取请求参数
        //    备注：这里没有参数传进来Request，但是所有请求的request都会进入这RequestContextHolder。
        //         所以只能用RequestContextHolder去获取，
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();

        // 2. 获取请求头中的 Content-Type，判断前端传参的方式（是 JSON 还是 表单/URL参数）
        String contentType = request.getHeader(Header.CONTENT_TYPE.getValue());
        SpaceUserAuthContext authRequest;

        // 3. 【核心逻辑 1】：兼容 GET（通常是 URL 参数）和 POST（可能是 JSON 或 表单）操作
        if (ContentType.JSON.getValue().equals(contentType)) {
            // 如果是 JSON 格式：读取请求体（Body）中的 JSON 字符串，并反序列化为 Context 对象
            String body = ServletUtil.getBody(request);
            authRequest = JSONUtil.toBean(body, SpaceUserAuthContext.class);
        } else {
            // 如果不是 JSON（如 GET 的 URL 参数 ?id=1，或 POST 的表单提交）：提取所有参数 Map 并转为对象
            Map<String, String> paramMap = ServletUtil.getParamMap(request);
            authRequest = BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
        }

        // 4. 【核心逻辑 2】：根据请求的 URL 路径，动态推断并映射 ID 的具体含义
        Long id = authRequest.getId(); // 获取前端传过来的通用 "id" 字段
        if (ObjUtil.isNotNull(id)) {
            String requestUri = request.getRequestURI(); // 获取完整请求路径，如 /api/picture/delete

            // 去除项目的基础路径（contextPath），比如把 /api/picture/delete 变成 picture/delete
            String partUri = requestUri.replace(contextPath + "/", "");

            // 截取第一个 "/" 之前的字符串，提取出“模块名”（如 "picture"）
            String moduleName = StrUtil.subBefore(partUri, "/", false);

            // 根据模块名，将通用的 id 赋值给 Context 对象中具体的业务 ID 字段
            switch (moduleName) {
                case "picture":
                    authRequest.setPictureId(id); // 如果是图片模块，id 就是 pictureId
                    break;
                case "spaceUser":
                    authRequest.setSpaceUserId(id); // 如果是空间用户模块，id 就是 spaceUserId
                    break;
                case "space":
                    authRequest.setSpaceId(id); // 如果是空间模块，id 就是 spaceId
                    break;
                default:
                    // 其他模块不做特殊处理
            }
        }
        return authRequest;
    }
}