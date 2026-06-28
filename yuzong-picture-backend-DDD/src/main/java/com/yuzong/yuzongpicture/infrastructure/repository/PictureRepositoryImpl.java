package com.yuzong.yuzongpicture.infrastructure.repository;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuzong.yuzongpicture.domain.picture.entity.Picture;
import com.yuzong.yuzongpicture.domain.picture.repository.PictureRepository;
import com.yuzong.yuzongpicture.infrastructure.mapper.PictureMapper;
import org.springframework.stereotype.Service;

/**
 * 图片仓储实现类
 *
 * @author makejava
 * @since 2020-06-03 17:32:35
 */
@Service
public class PictureRepositoryImpl extends ServiceImpl<PictureMapper, Picture> implements PictureRepository {
}