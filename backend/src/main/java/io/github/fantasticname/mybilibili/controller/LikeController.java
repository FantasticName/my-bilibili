package io.github.fantasticname.mybilibili.controller;

import io.github.fantasticname.mybilibili.annotation.*;
import io.github.fantasticname.mybilibili.common.Result;
import io.github.fantasticname.mybilibili.context.UserContext;
import io.github.fantasticname.mybilibili.dto.LikeRequestDTO;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.service.LikeServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 点赞控制器，处理点赞/取消点赞、一键二连等接口
 *
 * @author FantasticName
 */
@RestController
@io.github.fantasticname.mybilibili.annotation.RequestMapping("/like")
public class LikeController {

    private static final Logger log = LoggerFactory.getLogger(LikeController.class);

    @io.github.fantasticname.mybilibili.annotation.Autowired
    private LikeServiceImpl likeService;

    public LikeController() {
    }

    /**
     * 点赞/取消点赞
     *
     * @param dto 点赞请求DTO
     * @return 操作结果
     */
    @PostMapping("/toggle")
    @RequirePermission("like:toggle")
    public Result<Map<String, Object>> toggle(@RequestBody LikeRequestDTO dto) {
        User currentUser = UserContext.get();
        log.info("点赞/取消点赞: userId={}, targetType={}, targetId={}", currentUser.getId(), dto.getTargetType(), dto.getTargetId());
        boolean liked = likeService.toggle(currentUser.getId(), dto.getTargetType(), dto.getTargetId());
        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);
        return Result.success(result);
    }

    /**
     * 一键二连：点赞视频 + 收藏进默认收藏夹
     * <p>
     * 一键二连是单向操作，只添加不取消：
     * - 如果未点赞，则点赞；已点赞则保持不变
     * - 如果未收藏进默认收藏夹，则收藏；已收藏则保持不变
     * </p>
     *
     * @param videoId 视频ID
     * @return 操作结果，包含 liked、favorited、message
     */
    @PostMapping("/double-tap/{videoId}")
    @RequirePermission("like:toggle")
    public Result<Map<String, Object>> doubleTap(@PathVariable("videoId") Long videoId) {
        User currentUser = UserContext.get();
        log.info("一键二连: userId={}, videoId={}", currentUser.getId(), videoId);
        Map<String, Object> data = likeService.doubleTap(currentUser.getId(), videoId);
        return Result.success(data);
    }
}
