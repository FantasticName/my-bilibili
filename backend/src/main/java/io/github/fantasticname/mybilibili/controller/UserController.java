package io.github.fantasticname.mybilibili.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.fantasticname.mybilibili.annotation.*;
import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.common.Result;
import io.github.fantasticname.mybilibili.context.UserContext;
import io.github.fantasticname.mybilibili.dto.LoginDTO;
import io.github.fantasticname.mybilibili.dto.RegisterDTO;
import io.github.fantasticname.mybilibili.dto.UpdateProfileDTO;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.service.*;
import io.github.fantasticname.mybilibili.util.FileUtil;
import io.github.fantasticname.mybilibili.util.IdempotentUtil;
import io.github.fantasticname.mybilibili.util.RedisUtil;
import io.github.fantasticname.mybilibili.util.SentinelUtil;
import io.github.fantasticname.mybilibili.vo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户控制器，处理注册、登录、个人信息管理等接口
 *
 * <p>接口列表：</p>
 * <ul>
 *   <li>POST /user/register - 用户注册</li>
 *   <li>POST /user/login - 用户登录</li>
 *   <li>GET  /user/profile - 查看个人信息（需登录）</li>
 *   <li>PUT  /user/profile - 修改个人信息（需登录）</li>
 *   <li>POST /user/avatar - 上传头像（需登录）</li>
 *   <li>POST /user/logout - 退出登录（需登录）</li>
 * </ul>
 *
 * @author FantasticName
 */
@RestController
@io.github.fantasticname.mybilibili.annotation.RequestMapping("/user")
public class UserController {

    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    /**
     * 用户服务，处理业务逻辑
     */
    @io.github.fantasticname.mybilibili.annotation.Autowired
    private UserServiceImpl userService;

    /**
     * 关注服务
     */
    @io.github.fantasticname.mybilibili.annotation.Autowired
    private FollowServiceImpl followService;

    /**
     * 视频服务
     */
    @io.github.fantasticname.mybilibili.annotation.Autowired
    private VideoServiceImpl videoService;

    /**
     * 动态服务
     */
    @io.github.fantasticname.mybilibili.annotation.Autowired
    private PostServiceImpl postService;

    /**
     * JSON序列化器
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 无参构造方法，供IoC容器反射使用
     */
    public UserController() {
    }

    /**
     * 构造方法，通过IoC容器注入UserServiceImpl
     *
     * @param userService 用户服务
     */
    public UserController(UserServiceImpl userService) {
        this.userService = userService;
    }

    /**
     * 用户注册
     *
     * <p>支持普通用户注册和管理员注册。管理员注册需要提供邀请码。</p>
     *
     * @param dto 注册请求DTO
     * @return 注册成功的用户VO
     */
    @PostMapping("/register")
    public Result<UserVO> register(@RequestBody RegisterDTO dto) {
        log.info("收到注册请求: phone={}", dto.getPhone());
        return SentinelUtil.executeWithProtection("user-register", () -> {
            UserVO userVO = userService.register(dto);
            return userVO;
        });
    }

    /**
     * 用户登录
     *
     * <p>手机号+密码登录，登录成功后返回JWT Token和用户信息。</p>
     *
     * @param dto 登录请求DTO
     * @return 登录响应VO（包含Token和用户信息）
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO dto) {
        log.info("收到登录请求: phone={}", dto.getPhone());
        LoginVO loginVO = userService.login(dto);
        return Result.success(loginVO);
    }

    /**
     * 获取通用幂等性Token
     *
     * <p>前端在打开表单页面时调用此接口获取Token，
     * 提交表单时在Header中带上 X-Idempotent-Token。</p>
     *
     * @return Token字符串和TTL
     */
    @GetMapping("/token")
    public Result<Map<String, Object>> getToken() {
        String token = IdempotentUtil.generateToken();
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("ttl", 300);
        return Result.success(data);
    }

    /**
     * 查看当前登录用户的个人信息
     *
     * @return 用户VO
     */
    @PostMapping("/profile")
    @RequirePermission("user:profile:view")
    public Result<UserVO> getProfile() {
        User currentUser = UserContext.get();
        log.info("查看个人信息: userId={}", currentUser.getId());
        UserVO userVO = userService.getProfile(currentUser.getId());
        return Result.success(userVO);
    }

    /**
     * 修改个人信息
     *
     * <p>支持修改昵称、手机号、密码、头像。</p>
     *
     * @param dto 修改个人信息DTO
     * @return 更新后的用户VO
     */
    @PutMapping("/profile")
    @RequirePermission("user:profile:update")
    public Result<UserVO> updateProfile(@RequestBody UpdateProfileDTO dto) {
        User currentUser = UserContext.get();
        log.info("修改个人信息: userId={}", currentUser.getId());
        UserVO userVO = userService.updateProfile(currentUser.getId(), dto);
        return Result.success(userVO);
    }

    /**
     * 上传头像
     *
     * <p>使用multipart/form-data格式上传文件，只允许图片格式。</p>
     *
     * @param request HTTP请求（用于读取multipart文件）
     * @return 上传结果（包含新头像文件名）
     */
    @PostMapping("/avatar")
    @RequirePermission("user:avatar:upload")
    public Result<Map<String, String>> uploadAvatar(HttpServletRequest request) {
        User currentUser = UserContext.get();
        log.info("上传头像: userId={}", currentUser.getId());

        try {
            // 1. 从multipart请求中获取文件
            Part filePart = request.getPart("file");
            if (filePart == null || filePart.getSize() == 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择要上传的文件");
            }

            // 2. 获取原始文件名
            String contentDisposition = filePart.getHeader("content-disposition");
            String originalFilename = extractFilename(contentDisposition);

            // 3. 读取文件字节
            byte[] fileBytes;
            try (InputStream is = filePart.getInputStream()) {
                fileBytes = is.readAllBytes();
            }

            // 4. 调用服务上传
            String newFilename = userService.uploadAvatar(currentUser.getId(), fileBytes, originalFilename);

            // 5. 返回结果
            Map<String, String> result = new HashMap<>();
            result.put("avatar", newFilename);
            return Result.success(result);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | ServletException e) {
            log.error("上传头像失败: userId={}", currentUser.getId(), e);
            throw new BusinessException(ErrorCode.UPLOAD_ERROR, "文件上传失败");
        }
    }

    /**
     * 退出登录
     *
     * <p>从请求头获取Token，从Redis中删除，使Token失效。</p>
     *
     * @param request HTTP请求
     * @return 退出结果
     */
    @PostMapping("/logout")
    @RequirePermission("user:logout")
    public Result<Void> logout(HttpServletRequest request) {
        User currentUser = UserContext.get();
        log.info("退出登录: userId={}", currentUser.getId());

        // 从请求头获取Token
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            RedisUtil.removeToken(token);
        }

        return Result.success(null);
    }

    /**
     * 查看个人中心（含关注数、粉丝数）
     *
     * @return ProfileVO
     */
    @GetMapping("/profile")
    @RequirePermission("user:profile:view")
    public Result<ProfileVO> getProfileDetail() {
        User currentUser = UserContext.get();
        log.info("查看个人中心: userId={}", currentUser.getId());
        UserVO userVO = userService.getProfile(currentUser.getId());
        ProfileVO vo = new ProfileVO();
        vo.setId(userVO.getId());
        vo.setPhone(userVO.getPhone());
        vo.setNickname(userVO.getNickname());
        vo.setAvatar(userVO.getAvatar());
        vo.setRole(userVO.getRole());
        vo.setFollowCount(followService.countFollowing(currentUser.getId()));
        vo.setFansCount(followService.countFollowers(currentUser.getId()));
        vo.setCreatedAt(userVO.getCreatedAt());
        return Result.success(vo);
    }

    /**
     * 查看公开用户信息（外人看博主）
     *
     * @param userId 用户ID
     * @return PublicUserVO
     */
    @GetMapping("/public/{userId}")
    public Result<PublicUserVO> getPublicUser(@PathVariable("userId") Long userId) {
        log.info("查看公开用户信息: userId={}", userId);
        // 使用三层缓存防护（布隆过滤器→Redis缓存→分布式锁+双重检查→DB）
        PublicUserVO vo = userService.getPublicUserWithCacheProtection(userId);
        // 关注数/粉丝数不缓存（变化频繁），实时查询
        vo.setFollowCount(followService.countFollowing(userId));
        vo.setFansCount(followService.countFollowers(userId));
        User currentUser = UserContext.getNullable();
        if (currentUser != null) {
            vo.setIsFollowed(followService.isFollowing(currentUser.getId(), userId));
        }
        return Result.success(vo);
    }

    /**
     * 获取用户发布的视频列表
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页数量
     * @return 视频列表和总数
     */
    @GetMapping("/{userId}/videos")
    public Result<Map<String, Object>> getUserVideos(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        log.info("获取用户视频列表: userId={}, page={}, size={}", userId, page, size);
        List<VideoVO> list = videoService.listByUser(userId, page, size);
        int total = videoService.countByUser(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return Result.success(result);
    }

    /**
     * 获取用户发布的动态列表
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页数量
     * @return 动态列表和总数
     */
    @GetMapping("/{userId}/posts")
    public Result<Map<String, Object>> getUserPosts(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        log.info("获取用户动态列表: userId={}, page={}, size={}", userId, page, size);
        List<PostVO> list = postService.listByUser(userId, page, size);
        int total = postService.countByUser(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return Result.success(result);
    }

    /**
     * 从Content-Disposition头中提取文件名
     *
     * @param contentDisposition Content-Disposition头
     * @return 文件名
     */
    private String extractFilename(String contentDisposition) {
        if (contentDisposition == null) {
            return "unknown";
        }
        for (String part : contentDisposition.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("filename")) {
                int eqIndex = trimmed.indexOf('=');
                if (eqIndex != -1) {
                    return trimmed.substring(eqIndex + 1).replace("\"", "").trim();
                }
            }
        }
        return "unknown";
    }
}
