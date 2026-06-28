package com.yuzong.yuzongpicture.infrastructure.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yuzong.yuzongpicture.domain.user.entity.User;
import com.yuzong.yuzongpicture.domain.user.repository.UserRepository;
import com.yuzong.yuzongpicture.infrastructure.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * 用户仓库（储）实现类
 */
@Service
public class UserRepositoryImpl extends ServiceImpl<UserMapper, User> implements UserRepository {
}