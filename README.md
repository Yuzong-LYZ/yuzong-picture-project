# 灵图云库 

基于Vue 3 + Spring Boot + OSS + WebSocket的智能协同云图库：《灵图云库》

## 一、项目演示

（1）**项目首页**：所有用户都可以在平台公开上传和检索图片素材，快速找到需要的图片。可用作表情包网站、设计素材网站、壁纸网站等：

![image-20260802225322137](https://img-yuzong.oss-cn-guangzhou.aliyuncs.com/imgTypora/image-20260802225322137.webp)

（2）**管理员**：管理员可以审核和管理图片和管理空间，并对系统内的图片进行分析

![image-20260802225758063](https://img-yuzong.oss-cn-guangzhou.aliyuncs.com/imgTypora/image-20260802225758063.webp)

![image-20260802225916130](https://img-yuzong.oss-cn-guangzhou.aliyuncs.com/imgTypora/image-20260802225916130.webp)

![image-20260802225947071](https://img-yuzong.oss-cn-guangzhou.aliyuncs.com/imgTypora/image-20260802225947071.webp)

（3）**个人用户**：对于个人用户，可将图片上传至私有空间进行批量管理、检索、编辑和分析，用作个人网盘、个人相册、作品集等：。

![image-20260802230541591](https://img-yuzong.oss-cn-guangzhou.aliyuncs.com/imgTypora/image-20260802230541591.webp)

![image-20260802230917499](https://img-yuzong.oss-cn-guangzhou.aliyuncs.com/imgTypora/image-20260802230917499.webp)



（4）**图片批量抓取**：可以通过关键字从互联网上抓取对应图片

![image-20260802231210698](https://img-yuzong.oss-cn-guangzhou.aliyuncs.com/imgTypora/image-20260802231210698.webp)

可对图片进行相关操作：

![image-20260802231320143](https://img-yuzong.oss-cn-guangzhou.aliyuncs.com/imgTypora/image-20260802231320143.webp)

可以二维码进行分享
备注：这里需要改为自己的域名或者公网ip或者自己的存储桶地址。不然查不到，这里就不给展示了，被有心人利用会消耗很多流量。这里暂时没想到好的方法

![image-20260802231428661](https://img-yuzong.oss-cn-guangzhou.aliyuncs.com/imgTypora/image-20260802231428661.webp)

（5）**ai扩图**：点击编辑可以ai扩图和对图片的编辑：

AI扩图

<img src="https://img-yuzong.oss-cn-guangzhou.aliyuncs.com/imgTypora/image-20260802231722498.webp" alt="image-20260802231722498" style="zoom: 50%;" />

<img src="https://img-yuzong.oss-cn-guangzhou.aliyuncs.com/imgTypora/image-20260802231734778.webp" alt="image-20260802231734778" style="zoom:50%;" />

编辑：

<img src="https://img-yuzong.oss-cn-guangzhou.aliyuncs.com/imgTypora/image-20260802231831783.webp" alt="image-20260802231831783" style="zoom:50%;" />



## 二、技术选型

### 核心

Java Spring Boot 框架
MySQL 数据库 + MyBatis-Plus 框架 + MyBatis X
Redis 分布式缓存 + Caffeine 本地缓存
Jsoup 数据抓取
⭐️ OSS 对象存储
⭐️ ShardingSphere 分库分表
⭐️ Sa-Token 权限控制
⭐️ DDD 领域驱动设计
⭐️ WebSocket 双向通信
⭐️ Disruptor 高性能无锁队列
⭐️ JUC 并发和异步编程
⭐️ AI 绘图大模型接入
⭐️ 多种设计模式的运用
⭐️ 多角度项目优化：性能、成本、安全性等

### 开发工具

⭐️ Cursor 编辑器 AI Vibe Coding
JetBrains IDEA 后端
JetBrains WebStorm 前端

## 三、架构设计

![image-20260802233447260](https://img-yuzong.oss-cn-guangzhou.aliyuncs.com/imgTypora/image-20260802233447260.webp)



## 四、功能模块：

![image-20260802235029507](https://img-yuzong.oss-cn-guangzhou.aliyuncs.com/imgTypora/image-20260802235029507.webp)





## 五、配置说明

数据库，redis，knife4j接口文那些就不多说了。其实也没啥配置好说明的

重点：直接查看application里面，配置一下阿里云OSS

## 六、快速运行
后端；
1. 下载项目文件（直接下载zip即可）
2. mysql那些就不说了，写这个项目的时候jdk我用的是17，但是我实测20和8都可以运行
3. 反正本地jdk版本和idea的一致就行
4. 配置阿里云oss，不要配置腾讯云cos，腾讯云的cos和阿里云oss的sdk文档不一样，如果换了腾讯云的，上传逻辑和对象存储的客户端要改，比较麻烦。动手能力强的可以考虑配置腾讯云的，腾讯云的文档写的比较好，操作比较容易，但是我这里阿里云的已经写好了，可以直接用，换个key即可。
5. 依赖那些直接把maven clean一下，刷新一下，然后打包packson看下有没有问题，没问题运行基本就没问题

前端：

1. 项目终端执行：npm install --force
2. 然后直接运行即可。






