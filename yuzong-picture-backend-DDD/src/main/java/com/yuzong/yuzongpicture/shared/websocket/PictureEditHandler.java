package com.yuzong.yuzongpicture.shared.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.yuzong.yuzongpicture.shared.websocket.disruptor.PictureEditEventProducer;
import com.yuzong.yuzongpicture.shared.websocket.model.PictureEditActionEnum;
import com.yuzong.yuzongpicture.shared.websocket.model.PictureEditMessageTypeEnum;
import com.yuzong.yuzongpicture.shared.websocket.model.PictureEditRequestMessage;
import com.yuzong.yuzongpicture.shared.websocket.model.PictureEditResponseMessage;
import com.yuzong.yuzongpicture.domain.user.entity.User;
import com.yuzong.yuzongpicture.application.service.UserApplicationService;
import groovyjarjarantlr4.v4.runtime.misc.NotNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片编辑 WebSocket 处理器
 */
@Component
public class PictureEditHandler extends TextWebSocketHandler {
    @Resource
    private UserApplicationService userApplicationService;
    @Resource
    @Lazy
    private PictureEditEventProducer pictureEditEventProducer;

    // 每张图片的编辑状态，key: pictureId, value: 当前正在编辑的用户 ID
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    // 保存所有连接的会话，key: pictureId, value: 用户会话集合
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();


    /**
     * 1. WebSocket 连接成功建立后的回调方法
     * 作用：管理在线会话（记录谁在编辑哪张图），并向房间内其他人广播“新人加入”通知
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 1. 从握手阶段（beforeHandshake）存入的 attributes（工牌）中，拿出用户和图片信息
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");

        // 2. putIfAbsent：如果这张图片还没有人在线（Map中没有这个key），
        // 就创建一个线程安全的 Set（ConcurrentHashMap.newKeySet()）作为“房间”
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        // 将当前新建立的 WebSocket 连接（session）加入到这张图片的“房间”中
        pictureSessions.get(pictureId).add(session);

        // 3. 构造通知消息
        // 创建一个标准的响应消息对象，准备发给前端
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        // 设置消息类型为 INFO（系统提示类消息，区别于“编辑操作”等业务消息）
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        // 拼接提示文案
        String message = String.format("%s加入编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        // 【安全细节】：将 User 实体转换为 UserVO（视图对象）
        // 目的：脱敏！绝对不能把数据库里的密码、手机号等敏感信息通过 WebSocket 广播给其他人
        pictureEditResponseMessage.setUser(userApplicationService.getUserVO(user));

        // 4. 广播消息
        // 将这条“新人加入”的消息，发送给当前正在看这张图片的所有人
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }


    /**
     * 收到前端发送的消息，根据消息类型进行处理
     *
     * @param session 会话
     * @param message 消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        super.handleTextMessage(session, message);
        // 获取消息内容，将json转化为 PictureEditRequestMessage 对象
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        // 从session属性中获取用户和图片信息
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");

        //根据消息类型处理消息（生产消息到Disruptor 环形队列中）
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, session, user, pictureId);
    }


    /**
     * 3. WebSocket 连接关闭时调用
     *
     * @param session 会话
     * @param status  状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, @NotNull CloseStatus status) throws Exception {
        Map<String, Object> attributes = session.getAttributes();
        Long pictureId = (Long) attributes.get("pictureId");
        User user = (User) attributes.get("user");
        // 移除当前用户的编辑状态
        handleExitEditMessage(null, session, user, pictureId);

        // 删除会话
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (sessionSet != null) {
            sessionSet.remove(session);
            if (sessionSet.isEmpty()) {
                pictureSessions.remove(pictureId);
            }
        }

        // 响应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s离开编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userApplicationService.getUserVO(user));
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }


    /**
     * 4. 广播给该图片所有用户（支持排除掉某个session）
     *
     * @param pictureId 图片 ID
     * @param pictureEditResponseMessage 图片编辑响应消息
     * @param excludeSession 排除的会话
     */
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage, WebSocketSession excludeSession) throws Exception {
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (CollUtil.isNotEmpty(sessionSet)) {
            // 创建 ObjectMapper
            ObjectMapper objectMapper = new ObjectMapper();
            // 配置序列化：将 Long 类型转为 String，解决丢失精度问题
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance); // 支持 long 基本类型
            objectMapper.registerModule(module);
            // 序列化为 JSON 字符串
            String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
            TextMessage textMessage = new TextMessage(message);
            for (WebSocketSession session : sessionSet) {
                // 排除掉的 session 不发送
                if (excludeSession != null && excludeSession.equals(session)) {
                    continue;
                }
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            }
        }
    }
    // 5. 全部广播（不支持排除掉某个session）
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) throws Exception {
        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }

    /**
     * 6. 进入编辑状态；首先是用户进入编辑辑状态，要设置当前用户为编辑用户，并且向其他客户端发送消息
     *
     * @param pictureEditRequestMessage 图片编辑请求消息
     * @param session 会话
     * @param user 用户
     * @param pictureId 图片 ID
     */
    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        // 没有用户正在编辑该图片，才能进入编辑
        if (!pictureEditingUsers.containsKey(pictureId)) {
            // 设置当前用户为编辑用户
            pictureEditingUsers.put(pictureId, user.getId());
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            String message = String.format("%s开始编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userApplicationService.getUserVO(user));
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }
    /**
     * 7. 处理编辑操作；用户执行编辑操作时，将该操作同步给【除当前用户之外的其他用户】的客户端，也就是该编辑操作不再同步给自己
     *
     * @param pictureEditRequestMessage 图片编辑请求消息
     * @param session 会话
     * @param user 用户
     * @param pictureId 图片 ID
     */
    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            return;
        }
        // 确认是当前编辑者
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("%s执行%s", user.getUserName(), actionEnum.getText());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(userApplicationService.getUserVO(user));
            // 广播给除了当前客户端之外的其他用户，否则会造成重复编辑
            broadcastToPicture(pictureId, pictureEditResponseMessage, session);
        }
    }

    /**
     * 8. 退出编辑状态；用户退出编辑状态时，移除当前用户编辑状态，并且向其他客户端发送消息
     *
     * @param pictureEditRequestMessage 图片编辑请求消息
     * @param session 会话
     * @param user 用户
     * @param pictureId 图片 ID
     */
    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 移除当前用户的编辑状态
            pictureEditingUsers.remove(pictureId);
            // 构造响应，发送退出编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("%s退出编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userApplicationService.getUserVO(user));
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }
}