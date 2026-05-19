package io.github.fantasticname.mybilibili.controller;

import io.github.fantasticname.mybilibili.annotation.*;
import io.github.fantasticname.mybilibili.common.Result;
import io.github.fantasticname.mybilibili.context.UserContext;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.service.FeedServiceImpl;
import io.github.fantasticname.mybilibili.util.SentinelUtil;
import io.github.fantasticname.mybilibili.vo.PostVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Feed流控制器，处理关注动态Feed流接口
 *
 * @author FantasticName
 */
@RestController
@io.github.fantasticname.mybilibili.annotation.RequestMapping("/feed")
public class FeedController {

    private static final Logger log = LoggerFactory.getLogger(FeedController.class);

    @io.github.fantasticname.mybilibili.annotation.Autowired
    private FeedServiceImpl feedService;

    public FeedController() {
    }

    /**
     * 获取关注用户的动态Feed流（游标分页）
     *
     * @param cursor 游标（可选）
     * @param limit  每页数量
     * @return 动态VO列表
     */
    @GetMapping("/following")
    @RequirePermission("feed:view")
    public Result<List<PostVO>> getFeed(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        User currentUser = UserContext.get();
        log.info("获取Feed流: userId={}, cursor={}, limit={}", currentUser.getId(), cursor, limit);
        return SentinelUtil.executeWithProtection("feed-get", () -> {
            List<PostVO> list = feedService.getFeed(currentUser.getId(), cursor, limit);
            return list;
        }, () -> feedService.getHotFallback());
    }
}
