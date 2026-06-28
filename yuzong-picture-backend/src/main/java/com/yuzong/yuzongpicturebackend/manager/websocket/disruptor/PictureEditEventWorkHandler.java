package com.yuzong.yuzongpicturebackend.manager.websocket.disruptor;


import cn.hutool.json.JSONUtil;
import com.lmax.disruptor.WorkHandler;
import com.yuzong.yuzongpicturebackend.manager.websocket.PictureEditHandler;
import com.yuzong.yuzongpicturebackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.yuzong.yuzongpicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.yuzong.yuzongpicturebackend.manager.websocket.model.PictureEditResponseMessage;
import com.yuzong.yuzongpicturebackend.model.entity.User;
import com.yuzong.yuzongpicturebackend.service.UserService;
import groovy.lang.Lazy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.Resource;

/**
 * 图片编辑事件处理器（消费者）
 */
@Slf4j
@Component
public class PictureEditEventWorkHandler implements WorkHandler<PictureEditEvent> {

    @Resource
    @Lazy
    private PictureEditHandler pictureEditHandler;

    @Resource
    private UserService userService;

    /**
     * Disruptor 核心回调方法：处理从队列中消费到的事件
     *
     * @param event 封装了 WebSocket 会话、用户信息及业务消息的事件载体
     * @throws Exception 处理过程中的异常
     */
    @Override
    public void onEvent(PictureEditEvent event) throws Exception {
        // 1. 解构事件载荷，提取核心业务数据
        PictureEditRequestMessage pictureEditRequestMessage = event.getPictureEditRequestMessage();
        WebSocketSession session = event.getSession();
        User user = event.getUser();
        Long pictureId = event.getPictureId();
        // 2. 获取消息动作类型
        String type = pictureEditRequestMessage.getType();
        // 3. 策略路由：将字符串类型安全转换为枚举，并分发至具体处理逻辑
        PictureEditMessageTypeEnum pictureEditMessageTypeEnum = PictureEditMessageTypeEnum.valueOf(type);
        // 调用对应的消息处理方法
        switch (pictureEditMessageTypeEnum) {
            case ENTER_EDIT: // 进入编辑模式
                pictureEditHandler.handleEnterEditMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            case EDIT_ACTION: // 具体的编辑动作 (如拖拽、缩放等)
                pictureEditHandler.handleEditActionMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            case EXIT_EDIT: // 退出编辑模式
                pictureEditHandler.handleExitEditMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            default: // 未知消息类型兜底处理
                PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
                pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
                pictureEditResponseMessage.setMessage("消息类型错误");
                pictureEditResponseMessage.setUser(userService.getUserVO(user));
                session.sendMessage(new TextMessage(JSONUtil.toJsonStr(pictureEditResponseMessage)));
        }
    }
}