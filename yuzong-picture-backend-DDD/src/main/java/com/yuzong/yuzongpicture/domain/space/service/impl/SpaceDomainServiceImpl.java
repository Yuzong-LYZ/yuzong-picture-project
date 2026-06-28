package com.yuzong.yuzongpicture.domain.space.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuzong.yuzongpicture.application.service.SpaceApplicationService;
import com.yuzong.yuzongpicture.application.service.SpaceUserApplicationService;
import com.yuzong.yuzongpicture.application.service.UserApplicationService;
import com.yuzong.yuzongpicture.domain.space.entity.Space;
import com.yuzong.yuzongpicture.domain.space.entity.SpaceUser;
import com.yuzong.yuzongpicture.domain.space.repository.SpaceRepository;
import com.yuzong.yuzongpicture.domain.space.service.SpaceDomainService;
import com.yuzong.yuzongpicture.domain.space.valueobject.SpaceLevelEnum;
import com.yuzong.yuzongpicture.domain.space.valueobject.SpaceRoleEnum;
import com.yuzong.yuzongpicture.domain.space.valueobject.SpaceTypeEnum;
import com.yuzong.yuzongpicture.domain.user.entity.User;
import com.yuzong.yuzongpicture.infrastructure.exception.BusinessException;
import com.yuzong.yuzongpicture.infrastructure.exception.ErrorCode;
import com.yuzong.yuzongpicture.infrastructure.exception.ThrowUtils;
import com.yuzong.yuzongpicture.infrastructure.mapper.SpaceMapper;
import com.yuzong.yuzongpicture.interfaces.dto.sapce.SpaceAddRequest;
import com.yuzong.yuzongpicture.interfaces.dto.sapce.SpaceQueryRequest;
import com.yuzong.yuzongpicture.interfaces.vo.space.SpaceVO;
import com.yuzong.yuzongpicture.interfaces.vo.user.UserVO;
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
public class SpaceDomainServiceImpl
        implements SpaceDomainService {

    @Resource
    private SpaceRepository spaceRepository;

    /**
     * 校验空间权限
     *
     * @param loginUser 登录用户
     * @param space     空间实体类
     */
    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
        //仅本人或者管理员可以编辑
        if (!loginUser.isAdmin() && !loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无访问权限");
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



}




