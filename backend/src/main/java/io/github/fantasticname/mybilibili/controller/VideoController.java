package io.github.fantasticname.mybilibili.controller;

import io.github.fantasticname.mybilibili.annotation.*;
import io.github.fantasticname.mybilibili.common.Result;
import io.github.fantasticname.mybilibili.context.UserContext;
import io.github.fantasticname.mybilibili.dto.PublishVideoDTO;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.service.VideoServiceImpl;
import io.github.fantasticname.mybilibili.util.IdempotentUtil;
import io.github.fantasticname.mybilibili.util.SentinelUtil;
import io.github.fantasticname.mybilibili.vo.VideoVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频控制器，处理视频发布、查看、列表等接口
 *
 * @author FantasticName
 */
@RestController
@io.github.fantasticname.mybilibili.annotation.RequestMapping("/video")
public class VideoController {

    private static final Logger log = LoggerFactory.getLogger(VideoController.class);

    @io.github.fantasticname.mybilibili.annotation.Autowired
    private VideoServiceImpl videoService;

    public VideoController() {
    }

    /**
     * 发布视频
     *
     * @param dto 发布视频DTO
     * @return 视频VO
     */
    @PostMapping("/publish")
    @RequirePermission("video:publish")
    public Result<VideoVO> publish(@RequestBody PublishVideoDTO dto, HttpServletRequest req) {
        User currentUser = UserContext.get();
        log.info("发布视频: userId={}, title={}", currentUser.getId(), dto.getTitle());

        // 幂等性校验：从Header获取Token，防止重复提交
        String idempotentToken = req.getHeader("X-Idempotent-Token");
        if (idempotentToken != null && !idempotentToken.isEmpty()) {
            if (!IdempotentUtil.consumeToken(idempotentToken)) {
                return Result.error(40000, "请勿重复提交");
            }
        }

        VideoVO vo = videoService.publish(currentUser.getId(), dto.getTitle(), dto.getDescription(),
                dto.getCoverUrl(), dto.getVideoUrl(), dto.getCategory());
        return Result.success(vo);
    }

    /**
     * 获取视频详情
     *
     * @param videoId 视频ID
     * @return 视频VO
     */
    @GetMapping("/{videoId}")
    public Result<VideoVO> getDetail(@PathVariable("videoId") Long videoId) {
        User currentUser = UserContext.getNullable();
        Long userId = currentUser != null ? currentUser.getId() : null;
        log.info("查看视频详情: videoId={}", videoId);
        return SentinelUtil.executeWithProtection("video-detail", () -> {
            VideoVO vo = videoService.getDetail(videoId, userId);
            return vo;
        });
    }

    /**
     * 获取视频列表（支持分区筛选）
     *
     * @param category 分区（可选）
     * @param page     页码
     * @param size     每页数量
     * @return 视频列表和总数
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        log.info("获取视频列表: category={}, page={}, size={}", category, page, size);
        List<VideoVO> list = videoService.list(category, page, size);
        int total = videoService.count(category);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return Result.success(result);
    }

    /**
     * 获取用户发布的视频列表
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页数量
     * @return 视频列表和总数
     */
    @GetMapping("/user/{userId}")
    public Result<Map<String, Object>> listByUser(
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
     * 删除视频（下架）
     *
     * @param videoId 视频ID
     * @return 操作结果
     */
    @DeleteMapping("/{videoId}")
    @RequirePermission("video:publish")
    public Result<Void> deleteVideo(@PathVariable("videoId") Long videoId) {
        User currentUser = UserContext.get();
        log.info("删除视频: videoId={}, userId={}", videoId, currentUser.getId());
        videoService.deleteVideo(videoId, currentUser.getId());
        return Result.success(null);
    }
}
