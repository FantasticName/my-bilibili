package io.github.fantasticname.mybilibili.controller;

import io.github.fantasticname.mybilibili.annotation.*;
import io.github.fantasticname.mybilibili.common.Result;
import io.github.fantasticname.mybilibili.context.UserContext;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.service.FollowServiceImpl;
import io.github.fantasticname.mybilibili.vo.PublicUserVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 关注控制器，处理关注/取关、关注列表、粉丝列表等接口
 *
 * @author FantasticName
 */
@RestController
@io.github.fantasticname.mybilibili.annotation.RequestMapping("/follow")
public class FollowController {

    private static final Logger log = LoggerFactory.getLogger(FollowController.class);

    @io.github.fantasticname.mybilibili.annotation.Autowired
    private FollowServiceImpl followService;

    public FollowController() {
    }

    /**
     * 关注/取关用户
     *
     * @param followeeId 被关注者ID
     * @return 操作结果
     */
    @PostMapping("/toggle/{followeeId}")
    @RequirePermission("follow:manage")
    public Result<Map<String, Object>> toggle(@PathVariable("followeeId") Long followeeId) {
        User currentUser = UserContext.get();
        log.info("关注/取关: followerId={}, followeeId={}", currentUser.getId(), followeeId);
        boolean followed = followService.toggle(currentUser.getId(), followeeId);
        Map<String, Object> result = new HashMap<>();
        result.put("followed", followed);
        return Result.success(result);
    }

    /**
     * 判断是否已关注
     *
     * @param followeeId 被关注者ID
     * @return 是否已关注
     */
    @GetMapping("/check/{followeeId}")
    public Result<Map<String, Object>> check(@PathVariable("followeeId") Long followeeId) {
        User currentUser = UserContext.getNullable();
        boolean followed = currentUser != null && followService.isFollowing(currentUser.getId(), followeeId);
        Map<String, Object> result = new HashMap<>();
        result.put("followed", followed);
        return Result.success(result);
    }

    /**
     * 获取关注列表
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页数量
     * @return 关注列表
     */
    @GetMapping("/following/{userId}")
    public Result<List<PublicUserVO>> listFollowing(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        log.info("获取关注列表: userId={}, page={}, size={}", userId, page, size);
        List<PublicUserVO> list = followService.listFollowing(userId, page, size);
        return Result.success(list);
    }

    /**
     * 获取粉丝列表
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页数量
     * @return 粉丝列表
     */
    @GetMapping("/followers/{userId}")
    public Result<List<PublicUserVO>> listFollowers(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        log.info("获取粉丝列表: userId={}, page={}, size={}", userId, page, size);
        List<PublicUserVO> list = followService.listFollowers(userId, page, size);
        return Result.success(list);
    }

    /**
     * 获取关注数和粉丝数
     *
     * @param userId 用户ID
     * @return 关注数和粉丝数
     */
    @GetMapping("/count/{userId}")
    public Result<Map<String, Object>> count(@PathVariable("userId") Long userId) {
        int following = followService.countFollowing(userId);
        int followers = followService.countFollowers(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("following", following);
        result.put("followers", followers);
        return Result.success(result);
    }
}
