package com.yuzong.yuzongpicture.infrastructure.api.aliyunai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.yuzong.yuzongpicture.infrastructure.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.yuzong.yuzongpicture.infrastructure.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.yuzong.yuzongpicture.infrastructure.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.yuzong.yuzongpicture.infrastructure.exception.BusinessException;
import com.yuzong.yuzongpicture.infrastructure.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


/**
 * 阿里云 AI API 调用类
 * <p>
 * 【功能说明】
 * 这个类用于调用阿里云的 AI 服务，实现图片扩图（Out Painting）功能
 * 【工作流程】
 * 1. 调用 createOutPaintingTask() 创建扩图任务（异步）
 * 2. 获取任务 ID（taskId）
 * 3. 调用 getOutPaintingTask() 查询任务状态
 * 4. 任务完成后，获取扩图后的图片 URL
 */
@Slf4j
@Component
public class AliYunAiApi {
    // 备注：这两个地址是从阿里云的文档中给的，在请求头那里有给。具体看官方文档。
    // 创建任务地址
    public static final String CREATE_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting";
    // 查询任务状态
    public static final String GET_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/%s";
    // 读取配置文件：API Key（从 application.yml 中读取）
    @Value("${aliYunAi.apiKey}")
    private String apiKey;

    /**
     * 创建扩图任务
     * <p>
     * 【功能说明】
     * 向阿里云 AI 服务发送请求，创建一个扩图任务
     * 这是一个异步操作，会立即返回任务 ID，但扩图结果需要稍后查询
     * 【注意事项】
     * 1. 必须设置 X-DashScope-Async 头为 "enable"，否则不支持异步任务
     * 2. 必须使用 POST 请求，Content-Type 为 application/json
     * 3. 必须在请求头中携带 API Key 进行身份验证
     */
    public CreateOutPaintingTaskResponse createOutPaintingTask(CreateOutPaintingTaskRequest createOutPaintingTaskRequest) {
        // 1. 参数校验
        if (createOutPaintingTaskRequest == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "扩图参数为空");
        }
        // 2. 构建http请求
        //    创建post请求
        HttpRequest httpRequest = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                //  设置AUTHORIZATION头，携带apiKey【属于身份鉴权】
                .header(Header.AUTHORIZATION, "Bearer " + apiKey)
                // 开启异步处理，设置为enable。为啥enable是阿里云异步所要求的。
                .header("X-DashScope-Async", "enable")
                // 设置请求头，声明请求体是json。相当于说，喂，等等我发给你的请求体是json格式
                .header(Header.CONTENT_TYPE, ContentType.JSON.getValue())
                // 将java对象序列化为json字符串装入请求体
                .body(JSONUtil.toJsonStr(createOutPaintingTaskRequest));
        /**
         * 这里解释一下流程：序列化=请求参数打包成json数据
         *                发送请求 = 将json数据通过http发给阿里云
         *                接收响应 = 阿里云处理完了，返回回来的json数据
         *                反序列化 = 将json数据拆包还原为java对象
         */

        // 3. 发送请求 并 处理响应（接收响应）
        try (HttpResponse httpResponse = httpRequest.execute()) {
            // 判断http状态码是否ok，如200 ok。如果不是，可能就出问题了，比如401未授权，502网关错误
            if (!httpResponse.isOk()) {
                log.error("请求异常：{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图失败");
            }
            // 数据转换：反序列化
            // 如果上面通过了，则将响应体数据解析为java对象【】
            CreateOutPaintingTaskResponse response = JSONUtil.toBean(httpResponse.body(), CreateOutPaintingTaskResponse.class);

            // 虽然http返回状态码为：200，也不代表业务成功。比如 余额不足之类的。
            // 备注：这里获取的getCode不是状态码。而是响应体里面的code
            String errorCode = response.getCode();
            if (StrUtil.isNotBlank(errorCode)) {
                String errorMessage = response.getMessage();
                log.error("AI 扩图失败，errorCode:{}, errorMessage:{}", errorCode, errorMessage);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图接口响应异常");
            }

            return response;
        }
    }

    /**
     * 查询创建的任务的结果
     * 【功能说明】
     * 上面的createOutPaintingTask()创建了任务，虽然经历了序列化，发送请求，接收响应，反序列化。这不是说接收响应就是扩图完成了。
     * 而是说你这个任务创建好了，给你一张小票，等待任务完成。现在我们需要写这个方法，获取任务结果。
     * 根据任务 ID 查询扩图任务的执行状态和结果
     * 这是一个轮询操作，需要多次调用直到任务完成
     *
     */
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务 id 不能为空");
        }
        // 构建http请求 身份鉴权
        try (HttpResponse httpResponse = HttpRequest.get(String.format(GET_OUT_PAINTING_TASK_URL, taskId))
                .header(Header.AUTHORIZATION, "Bearer " + apiKey)
                .execute()) {
            // 如果http状态码不是200，则抛出异常，没完成任务呢
            if (!httpResponse.isOk()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取任务失败");
            }
            // 否则就是完成任务了，直接返回反序列化结果。
            return JSONUtil.toBean(httpResponse.body(), GetOutPaintingTaskResponse.class);
        }
    }
}