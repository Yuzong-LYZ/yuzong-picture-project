package com.yuzong.yuzongpicture.shared.websocket;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.yuzong.yuzongpicture.shared.auth.SpaceUserAuthManager;
import com.yuzong.yuzongpicture.shared.auth.model.SpaceUserPermissionConstant;
import com.yuzong.yuzongpicture.domain.picture.entity.Picture;
import com.yuzong.yuzongpicture.domain.space.entity.Space;
import com.yuzong.yuzongpicture.domain.user.entity.User;
import com.yuzong.yuzongpicture.domain.space.valueobject.SpaceTypeEnum;
import com.yuzong.yuzongpicture.application.service.PictureApplicationService;
import com.yuzong.yuzongpicture.application.service.SpaceApplicationService;
import com.yuzong.yuzongpicture.application.service.UserApplicationService;
import groovyjarjarantlr4.v4.runtime.misc.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 握手拦截器，建立连接前先校验
 */
@Component
@Slf4j
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    @Resource
    private UserApplicationService userApplicationService;

    @Resource
    private PictureApplicationService pictureApplicationService;

    @Resource
    private SpaceApplicationService spaceApplicationService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * WebSocket 握手前的拦截校验方法
     * 作用：在建立 WebSocket 长连接前，校验用户身份、参数合法性以及操作权限
     */
    @Override
    public boolean beforeHandshake(@NotNull ServerHttpRequest request,
                                   @NotNull ServerHttpResponse response,
                                   @NotNull WebSocketHandler wsHandler,
                                   @NotNull Map<String, Object> attributes) {

        // 1. 协议适配：将 Spring 的 Request 转换为传统 Servlet 的 Request
        // 目的：为了能够使用大家熟悉的 servletRequest.getParameter() 等方法
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();

            // ================= 第一关：核心参数校验 =================
            // 获取前端传过来的要编辑的图片 ID
            String pictureId = servletRequest.getParameter("pictureId");
            if (StrUtil.isBlank(pictureId)) {
                log.error("缺少图片参数，拒绝握手");
                return false; // 拦截：直接断开，不让连接
            }

            // ================= 第二关：用户身份校验 =================
            // 获取当前登录用户
            User loginUser = userApplicationService.getLoginUser(servletRequest);
            if (ObjUtil.isEmpty(loginUser)) {
                log.error("用户未登录，拒绝握手");
                return false; // 拦截：游客不允许使用协同编辑功能
            }

            // ================= 第三关：资源存在性校验 =================
            // 查数据库，确认这张图片是否真的存在
            Picture picture = pictureApplicationService.getById(pictureId);
            if (picture == null) {
                log.error("图片不存在，拒绝握手");
                return false; // 拦截：防止恶意传入伪造的 ID 消耗服务器资源
            }

            // ================= 第四关：业务场景校验（核心亮点） =================
            Long spaceId = picture.getSpaceId();
            Space space = null;
            if (spaceId != null) {
                space = spaceApplicationService.getById(spaceId);
                if (space == null) {
                    log.error("空间不存在，拒绝握手");
                    return false;
                }
                // 【关键逻辑】：只有“团队空间”才需要多人协同编辑！
                // 如果是“个人空间”，直接拒绝握手，因为个人空间没必要搞协同。
                if (space.getSpaceType() != SpaceTypeEnum.TEAM.getValue()) {
                    log.info("不是团队空间，拒绝握手");
                    return false;
                }
            }

            // ================= 第五关：细粒度权限校验 =================
            // 检查该用户在这个空间下，是否拥有“图片编辑”权限
            List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
            if (!permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT)) {
                log.error("没有图片编辑权限，拒绝握手");
                return false; // 拦截：比如该用户只有“只读”权限，就不能建立编辑的 WS 连接
            }

            // ================= 通关：保存上下文信息 =================
            // 将校验通过的核心信息塞入 attributes 中
            // 这些信息会伴随整个 WebSocket 生命周期，后续处理消息时可以直接拿出来用
            attributes.put("user", loginUser);
            attributes.put("userId", loginUser.getId());
            // 记得转换为 Long 类型，保证和数据库实体类的类型一致，避免后续 ClassCastException
            attributes.put("pictureId", Long.valueOf(pictureId));
        }

        // 所有校验通过，返回 true，正式建立 WebSocket 连接！
        return true;
    }

    /**
     * 握手后
     */
    @Override
    public void afterHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler, Exception exception) {
    }
}