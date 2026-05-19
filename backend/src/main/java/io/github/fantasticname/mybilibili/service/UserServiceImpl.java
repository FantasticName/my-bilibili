package io.github.fantasticname.mybilibili.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.fantasticname.mybilibili.annotation.Service;
import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.dao.RoleDao;
import io.github.fantasticname.mybilibili.dao.UserDao;
import io.github.fantasticname.mybilibili.dao.UserRoleDao;
import io.github.fantasticname.mybilibili.dto.LoginDTO;
import io.github.fantasticname.mybilibili.dto.RegisterDTO;
import io.github.fantasticname.mybilibili.dto.UpdateProfileDTO;
import io.github.fantasticname.mybilibili.util.FileUtil;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.entity.UserRole;
import io.github.fantasticname.mybilibili.util.*;
import io.github.fantasticname.mybilibili.vo.LoginVO;
import io.github.fantasticname.mybilibili.vo.PublicUserVO;
import io.github.fantasticname.mybilibili.vo.UserVO;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 用户服务实现类，处理注册、登录、个人信息管理等业务逻辑
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>用户注册：手机号+密码注册，管理员注册需要邀请码</li>
 *   <li>用户登录：手机号+密码登录，生成JWT Token并存入Redis</li>
 *   <li>个人信息：查看和修改昵称、手机号、密码、头像</li>
 * </ul>
 *
 * <p>安全策略：</p>
 * <ul>
 *   <li>密码使用BCrypt哈希加盐加密，不存明文</li>
 *   <li>注册时使用synchronized(phone.intern())防止并发重复注册</li>
 *   <li>所有输入做长度校验和正则校验</li>
 *   <li>手机号等业务层唯一性校验，不依赖数据库约束</li>
 * </ul>
 *
 * @author FantasticName
 */
@Service
public class UserServiceImpl implements UserService {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    /**
     * 用户DAO，负责数据库操作
     */
    @io.github.fantasticname.mybilibili.annotation.Autowired
    private UserDao userDao;

    @io.github.fantasticname.mybilibili.annotation.Autowired
    private RoleDao roleDao;

    @io.github.fantasticname.mybilibili.annotation.Autowired
    private UserRoleDao userRoleDao;

    /**
     * Jackson的ObjectMapper，用于JSON序列化/反序列化
     * 配置了 JavaTimeModule 以支持 LocalDateTime 等 Java 8 时间类型的序列化
     */
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 管理员邀请码，从env.properties读取
     */
    private static String ADMIN_INVITE_CODE;

    /**
     * 环境变量配置
     */
    static {
        Properties props = new Properties();
        try (InputStream is = UserServiceImpl.class.getClassLoader().getResourceAsStream("env.properties")) {
            if (is == null) {
                throw new RuntimeException("未找到环境变量配置文件: env.properties");
            }
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("读取环境变量配置文件失败", e);
        }
        // 优先从环境变量读取管理员邀请码，回退到配置文件
        // 这样在生产环境可以通过环境变量注入敏感信息，避免明文存储在配置文件中
        String envCode = System.getenv("ADMIN_INVITE_CODE");
        if (envCode != null && !envCode.isEmpty()) {
            ADMIN_INVITE_CODE = envCode;
        } else {
            ADMIN_INVITE_CODE = props.getProperty("admin.invite.code", "123456");
        }
        log.info("管理员邀请码已加载");
    }

    /**
     * 无参构造方法，供IoC容器反射使用
     */
    public UserServiceImpl() {
    }

    /**
     * 构造方法，通过IoC容器注入UserDao
     *
     * @param userDao 用户DAO
     */
    public UserServiceImpl(UserDao userDao) {
        this.userDao = userDao;
    }

    /**
     * 根据用户ID查询用户信息
     *
     * @param id 用户ID
     * @return 用户实体对象，如果用户不存在则返回null
     */
    @Override
    public User getById(Long id) {
        return userDao.findById(id);
    }

    /**
     * 获取用户公开信息（三层缓存防护）
     *
     * <p>与VideoServiceImpl.getDetail()相同的三层防护模式：</p>
     * <ul>
     *   <li>第1层：布隆过滤器防缓存穿透</li>
     *   <li>第2层：Redis缓存（主查询）</li>
     *   <li>第3层：Redisson分布式锁防缓存击穿 + 双重检查</li>
     * </ul>
     *
     * @param userId 用户ID
     * @return PublicUserVO
     */
    public PublicUserVO getPublicUserWithCacheProtection(Long userId) {
        String cacheKey = "user:profile:" + userId;

        // 第1层：布隆过滤器
        if (!BloomFilterUtil.mightContain(userId.intValue())) {
            log.warn("缓存穿透拦截(用户): userId={}", userId);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        // 第2层：Redis缓存
        try {
            PublicUserVO cached =
                RedisUtil.getObject(cacheKey, PublicUserVO.class);
            if (cached != null) {
                if (cached.getId() != null && cached.getId() == -1) {
                    throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
                }
                return cached;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redis查询异常，降级查DB: userId={}", userId);
        }

        // 第3层：分布式锁 + 双重检查
        String lockKey = "user:lock:" + userId;
        org.redisson.api.RLock lock = io.github.fantasticname.mybilibili.config.RedissonConfig.getRedissonClient().getLock(lockKey);
        try {
            if (lock.tryLock(3, 10, java.util.concurrent.TimeUnit.SECONDS)) {
                try {
                    // 双重检查
                    PublicUserVO recheck =
                        RedisUtil.getObject(cacheKey, PublicUserVO.class);
                    if (recheck != null) {
                        if (recheck.getId() != null && recheck.getId() == -1) {
                            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
                        }
                        return recheck;
                    }

                    // 查DB
                    User user = userDao.findById(userId);
                    if (user == null) {
                        // 缓存空值
                        PublicUserVO nullMarker = new PublicUserVO();
                        nullMarker.setId(-1L);
                        RedisUtil.setObject(cacheKey, nullMarker, 60);
                        throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
                    }

                    // 构建VO并缓存
                    PublicUserVO vo = new PublicUserVO();
                    vo.setId(user.getId());
                    vo.setNickname(user.getNickname());
                    vo.setAvatar(FileUtil.toUrl(user.getAvatar()));
                    vo.setRole(user.getRole());
                    vo.setCreatedAt(user.getCreatedAt());
                    int ttl = 300 + new java.util.Random().nextInt(60);
                    RedisUtil.setObject(cacheKey, vo, ttl);
                    return vo;
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                // 没拿到锁，降级查DB
                User user = userDao.findById(userId);
                if (user == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
                }
                PublicUserVO vo = new PublicUserVO();
                vo.setId(user.getId());
                vo.setNickname(user.getNickname());
                vo.setAvatar(FileUtil.toUrl(user.getAvatar()));
                vo.setRole(user.getRole());
                vo.setCreatedAt(user.getCreatedAt());
                return vo;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "服务繁忙");
        }
    }

    /**
     * 用户注册
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>参数校验（手机号格式、密码格式、昵称格式、两次密码一致）</li>
     *   <li>管理员注册需要验证邀请码</li>
     *   <li>业务层查表校验手机号是否已注册</li>
     *   <li>使用synchronized(phone.intern())加锁防止并发注册</li>
     *   <li>BCrypt加密密码</li>
     *   <li>写入数据库</li>
     * </ol>
     *
     * @param dto 注册请求DTO
     * @return 注册成功的用户VO
     */
    public UserVO register(RegisterDTO dto) {
        log.info("开始处理注册请求: phone={}, role={}", dto.getPhone(), dto.getRole());

        // 1. 参数校验
        validateRegisterDTO(dto);

        // 2. 管理员注册需要验证邀请码
        if (dto.getRole() != null && dto.getRole() == 2) {
            if (!ADMIN_INVITE_CODE.equals(dto.getInviteCode())) {
                log.warn("管理员注册邀请码错误: phone={}, inviteCode={}", dto.getPhone(), dto.getInviteCode());
                throw new BusinessException(ErrorCode.INVITE_CODE_ERROR);
            }
        }

        // 3. 使用synchronized(phone.intern())加锁防止并发重复注册
        // intern()确保相同字符串使用同一个锁对象
        synchronized (dto.getPhone().intern()) {
            // 4. 业务层查表校验手机号是否已注册
            User existingUser = userDao.findByPhone(dto.getPhone());
            if (existingUser != null) {
                log.warn("手机号已注册: {}", dto.getPhone());
                throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
            }

            // 5. BCrypt加密密码
            String passwordHash = BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt());

            // 6. 创建User实体
            User user = new User();
            user.setPhone(dto.getPhone());
            user.setPasswordHash(passwordHash);
            user.setNickname(dto.getNickname());
            user.setRole(dto.getRole() != null ? dto.getRole() : 0);
            user.setStatus(0);

            // 7. 写入数据库
            long userId = userDao.insert(user);
            log.info("用户注册成功: userId={}, phone={}", userId, dto.getPhone());

            // 8. RBAC0: 写入用户角色关联表
            try {
                int roleId = (dto.getRole() != null && dto.getRole() == 2) ? 2 : 1;
                UserRole userRole = new UserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                userRoleDao.insert(userRole);
                log.info("RBAC角色分配成功: userId={}, roleId={}", userId, roleId);
            } catch (Exception e) {
                log.error("RBAC角色分配失败: userId={}", userId, e);
            }

            // 9. 查询并返回用户VO
            return convertToUserVO(userDao.findById(userId));
        }
    }

    /**
     * 用户登录
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>参数校验（手机号格式、密码格式）</li>
     *   <li>查数据库校验手机号是否存在</li>
     *   <li>BCrypt校验密码是否匹配</li>
     *   <li>校验账号是否被封禁</li>
     *   <li>生成JWT Token（不设exp过期，依靠Redis TTL）</li>
     *   <li>将Token和User信息存入Redis，TTL=30分钟</li>
     * </ol>
     *
     * @param dto 登录请求DTO
     * @return 登录响应VO（包含Token和用户信息）
     */
    public LoginVO login(LoginDTO dto) {
        log.info("开始处理登录请求: phone={}", dto.getPhone());

        // 1. 参数校验
        if (!RegexUtil.isValidPhone(dto.getPhone())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "手机号格式错误");
        }
        if (!RegexUtil.isValidPassword(dto.getPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码格式错误，必须6-12位");
        }

        // 2. 查数据库校验手机号是否存在
        User user = userDao.findByPhone(dto.getPhone());
        if (user == null) {
            log.warn("登录失败: 手机号不存在, phone={}", dto.getPhone());
            throw new BusinessException(ErrorCode.PHONE_OR_PASSWORD_ERROR);
        }

        // 3. BCrypt校验密码是否匹配
        if (!BCrypt.checkpw(dto.getPassword(), user.getPasswordHash())) {
            log.warn("登录失败: 密码错误, userId={}", user.getId());
            throw new BusinessException(ErrorCode.PHONE_OR_PASSWORD_ERROR);
        }

        // 4. 校验账号是否被封禁
        if (user.getStatus() != null && user.getStatus() == 1) {
            log.warn("登录失败: 账号已被封禁, userId={}", user.getId());
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        }

        // 5. 生成JWT Token（不设exp过期，依靠Redis TTL）
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("role", user.getRole());
        String token = JwtUtil.generateTokenWithoutExp(claims);

        // 6. 将User对象序列化为JSON，存入Redis
        try {
            // 使用配置好的全局 objectMapper 进行序列化
            String userJson = objectMapper.writeValueAsString(user);
            RedisUtil.saveToken(token, userJson);
        } catch (Exception e) {
            // 打印完整异常堆栈，方便定位序列化或Redis存储问题
            log.error("Token存入Redis失败: userId={}, 错误详情: ", user.getId(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "登录失败，请稍后重试");
        }

        // 7. 构建登录响应
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setExpiresIn(RedisUtil.getTokenTtl());
        loginVO.setUser(convertToUserVO(user));

        log.info("用户登录成功: userId={}, phone={}", user.getId(), user.getPhone());
        return loginVO;
    }

    /**
     * 查看当前登录用户的个人信息
     *
     * @param userId 当前登录用户ID
     * @return 用户VO
     */
    public UserVO getProfile(Long userId) {
        User user = userDao.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        return convertToUserVO(user);
    }

    /**
     * 修改个人信息
     *
     * <p>支持修改：昵称、手机号、密码、头像。</p>
     * <p>修改手机号：需要验证旧密码，业务层校验新手机号唯一性</p>
     * <p>修改密码：需要验证旧密码，两次新密码一致</p>
     *
     * @param userId 当前登录用户ID
     * @param dto    修改个人信息DTO
     * @return 更新后的用户VO
     */
    public UserVO updateProfile(Long userId, UpdateProfileDTO dto) {
        log.info("开始修改个人信息: userId={}", userId);

        // 1. 查询当前用户
        User user = userDao.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        boolean needUpdate = false;

        // 2. 修改昵称
        if (dto.getNickname() != null && !dto.getNickname().isEmpty()) {
            if (!RegexUtil.isValidNickname(dto.getNickname())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "昵称格式错误，1-20位中英文、数字、下划线");
            }
            user.setNickname(dto.getNickname());
            needUpdate = true;
        }

        // 3. 修改手机号
        if (dto.getNewPhone() != null && !dto.getNewPhone().isEmpty()) {
            // 3.1 校验新手机号格式
            if (!RegexUtil.isValidPhone(dto.getNewPhone())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "手机号格式错误");
            }
            // 3.2 验证旧密码
            if (dto.getOldPassword() == null || !BCrypt.checkpw(dto.getOldPassword(), user.getPasswordHash())) {
                throw new BusinessException(ErrorCode.OLD_PASSWORD_ERROR);
            }
            // 3.3 业务层校验新手机号唯一性
            User existingUser = userDao.findByPhone(dto.getNewPhone());
            if (existingUser != null && !existingUser.getId().equals(userId)) {
                throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
            }
            user.setPhone(dto.getNewPhone());
            needUpdate = true;
        }

        // 4. 修改密码
        if (dto.getNewPassword() != null && !dto.getNewPassword().isEmpty()) {
            // 4.1 验证旧密码
            if (dto.getOldPassword() == null || !BCrypt.checkpw(dto.getOldPassword(), user.getPasswordHash())) {
                throw new BusinessException(ErrorCode.OLD_PASSWORD_ERROR);
            }
            // 4.2 校验新密码格式
            if (!RegexUtil.isValidPassword(dto.getNewPassword())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码格式错误，必须6-12位");
            }
            // 4.3 两次新密码一致
            if (!dto.getNewPassword().equals(dto.getConfirmNewPassword())) {
                throw new BusinessException(ErrorCode.PASSWORD_NOT_MATCH);
            }
            // 4.4 BCrypt加密新密码
            user.setPasswordHash(BCrypt.hashpw(dto.getNewPassword(), BCrypt.gensalt()));
            needUpdate = true;
        }

        // 5. 修改头像
        if (dto.getNewAvatar() != null && !dto.getNewAvatar().isEmpty()) {
            // 删除旧头像文件
            if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                String oldFilename = extractFilename(user.getAvatar());
                FileUtil.deleteFile(oldFilename);
            }
            user.setAvatar(dto.getNewAvatar());
            needUpdate = true;
        }

        // 6. 更新数据库
        if (needUpdate) {
            userDao.update(user);
            log.info("个人信息修改成功: userId={}", userId);
            // 【缓存一致性】修改个人信息后，删除用户资料缓存
            deleteCacheWithRetry("user:profile:" + userId);
        }

        // 7. 如果修改了手机号，需要重新查询以获取最新数据
        if (dto.getNewPhone() != null && !dto.getNewPhone().isEmpty()) {
            user = userDao.findByPhone(dto.getNewPhone());
        }

        return convertToUserVO(user);
    }

    /**
     * 上传头像
     *
     * @param userId     当前登录用户ID
     * @param fileBytes  文件字节数组
     * @param filename   原始文件名
     * @return 新头像文件名
     */
    public String uploadAvatar(Long userId, byte[] fileBytes, String filename) {
        log.info("开始上传头像: userId={}", userId);

        // 1. 查询当前用户
        User user = userDao.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        // 2. 校验文件是否为图片
        if (!FileUtil.isImage(filename)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "只允许上传图片文件");
        }

        // 3. 从文件名中提取纯扩展名（如 "把帽子.png" → "png"）
        String extension = FileUtil.getFileExtension(filename);
        // 4. 保存文件（传入纯扩展名，不是完整文件名）
        String newFilename = FileUtil.saveFile(fileBytes, extension);

        // 5. 删除旧头像
        if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
            String oldFilename = extractFilename(user.getAvatar());
            FileUtil.deleteFile(oldFilename);
        }

        // 6. 更新数据库
        user.setAvatar(newFilename);
        userDao.update(user);
        // 【缓存一致性】上传头像后，删除用户资料缓存
        deleteCacheWithRetry("user:profile:" + userId);

        log.info("头像上传成功: userId={}, filename={}", userId, newFilename);
        return newFilename;
    }

    /**
     * 校验注册DTO参数
     *
     * @param dto 注册请求DTO
     */
    private void validateRegisterDTO(RegisterDTO dto) {
        // 手机号校验
        if (dto.getPhone() == null || dto.getPhone().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "手机号不能为空");
        }
        if (!RegexUtil.isValidPhone(dto.getPhone())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "手机号格式错误，必须1开头11位数字");
        }

        // 密码校验
        if (dto.getPassword() == null || dto.getPassword().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码不能为空");
        }
        if (!RegexUtil.isValidPassword(dto.getPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码格式错误，必须6-12位");
        }

        // 确认密码校验
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_NOT_MATCH);
        }

        // 昵称校验
        if (dto.getNickname() == null || dto.getNickname().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "昵称不能为空");
        }
        if (!RegexUtil.isValidNickname(dto.getNickname())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "昵称格式错误，1-20位中英文、数字、下划线");
        }
    }

    /**
     * 将User实体转换为UserVO
     *
     * @param user User实体
     * @return UserVO
     */
    private UserVO convertToUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setPhone(user.getPhone());
        vo.setNickname(user.getNickname());
        vo.setAvatar(FileUtil.toUrl(user.getAvatar()));
        vo.setRole(user.getRole());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    /**
     * 从头像URL中提取文件名
     *
     * @param avatarUrl 头像URL
     * @return 文件名
     */
    private String extractFilename(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            return null;
        }
        int lastSlash = avatarUrl.lastIndexOf('/');
        if (lastSlash != -1) {
            return avatarUrl.substring(lastSlash + 1);
        }
        return avatarUrl;
    }

    /**
     * 删除缓存并重试（缓存一致性过渡方案）
     *
     * <p>写操作后同步删除Redis缓存，如果删除失败则重试最多3次。
     * 这是RocketMQ事务消息方案引入前的过渡方案。</p>
     *
     * @param cacheKey 缓存Key
     */
    private void deleteCacheWithRetry(String cacheKey) {
        for (int i = 0; i < 3; i++) {
            try {
                if (RedisUtil.del(cacheKey) >= 1) {
                    log.info("缓存删除成功: key={}", cacheKey);
                    return;
                }
                log.warn("缓存删除返回0，重试第{}次: key={}", i + 1, cacheKey);
            } catch (Exception e) {
                log.warn("缓存删除异常，重试第{}次: key={}, error={}", i + 1, cacheKey, e.getMessage());
            }
        }
        log.error("缓存删除失败，3次重试均未成功: key={}", cacheKey);
    }
}
