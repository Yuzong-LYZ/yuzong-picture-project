package com.yuzong.yuzongpicture.application.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yuzong.yuzongpicture.interfaces.dto.sapce.analyze.*;
import com.yuzong.yuzongpicture.interfaces.vo.space.analyze.*;
import com.yuzong.yuzongpicture.domain.space.entity.Space;
import com.yuzong.yuzongpicture.domain.user.entity.User;

import java.util.List;

public interface SpaceAnalyzeApplicationService {

    /**
     * 获取空间排行分析数据【管理员】
     *
     * @param spaceRankAnalyzeRequest 排行分析请求参数（包含空间ID、筛选条件等）
     * @param loginUser               当前登录用户（用于权限校验）
     * @return 空间排行列表
     */
    List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser);

    /**
     * 获取空间图片用户上传分析数据
     *
     * @param spaceUserAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser);

    /**
     * 获取空间图片大小分析数据
     *
     * @param spaceSizeAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser);

    /**
     * 获取空间标签分析数据
     *
     * @param spaceTagAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser);

    /**
     * 获取空间分类分析数据
     *
     * @param spaceCategoryAnalyzeRequest
     * @param loginUser
     * @return
     */
    List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser);

    /**
     * 获取空间使用分析数据
     *
     * @param spaceUsageAnalyzeRequest
     * @param loginUser
     * @return
     */
    SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser);
}
