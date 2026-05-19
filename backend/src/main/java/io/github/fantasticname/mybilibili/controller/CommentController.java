package io.github.fantasticname.mybilibili.controller;

import io.github.fantasticname.mybilibili.annotation.*;
import io.github.fantasticname.mybilibili.common.Result;
import io.github.fantasticname.mybilibili.context.UserContext;
import io.github.fantasticname.mybilibili.dto.CreateCommentDTO;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.service.CommentServiceImpl;
import io.github.fantasticname.mybilibili.util.IdempotentUtil;
import io.github.fantasticname.mybilibili.util.SentinelUtil;
import io.github.fantasticname.mybilibili.vo.CommentVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 评论控制器，处理评论创建、查看、删除等接口
 *
 * @author FantasticName
 */
@RestController
@io.github.fantasticname.mybilibili.annotation.RequestMapping("/comment")
public class CommentController {

    private static final Logger log = LoggerFactory.getLogger(CommentController.class);

    @io.github.fantasticname.mybilibili.annotation.Autowired
    private CommentServiceImpl commentService;

    public CommentController() {
    }

    /**
     * 创建评论
     *
     * @param dto 创建评论DTO
     * @return 评论VO
     */
    @PostMapping("/create")
    @RequirePermission("comment:create")
    public Result<CommentVO> create(@RequestBody CreateCommentDTO dto, HttpServletRequest req) {
        User currentUser = UserContext.get();
        log.info("创建评论: userId={}, targetType={}, targetId={}", currentUser.getId(), dto.getTargetType(), dto.getTargetId());

        // 幂等性校验：从Header获取Token，防止重复提交
        String idempotentToken = req.getHeader("X-Idempotent-Token");
        if (idempotentToken != null && !idempotentToken.isEmpty()) {
            if (!IdempotentUtil.consumeToken(idempotentToken)) {
                return Result.error(40000, "请勿重复提交");
            }
        }

        return SentinelUtil.executeWithProtection("comment-create", () -> {
            CommentVO vo = commentService.create(currentUser.getId(), dto.getContent(),
                    dto.getTargetType(), dto.getTargetId(), dto.getParentId());
            return vo;
        });
    }

    /**
     * 获取评论列表（热门排序 + 游标分页 + 树形结构）
     *
     * @param targetType 目标类型（1=视频，2=动态）
     * @param targetId   目标ID
     * @param sort       排序方式（默认hot=热门）
     * @param cursor     游标（上一页最后一条评论的likeCount，首次请求不传）
     * @param cursorId   游标对应的评论ID（首次请求不传）
     * @param size       每页数量（默认10）
     * @return 评论列表和分页游标
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam("targetType") Integer targetType,
            @RequestParam("targetId") Long targetId,
            @RequestParam(value = "sort", defaultValue = "hot") String sort,
            @RequestParam(value = "cursor", required = false) Integer cursor,
            @RequestParam(value = "cursorId", required = false) Long cursorId,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        log.info("获取评论列表: targetType={}, targetId={}, sort={}, cursor={}, cursorId={}, size={}",
                targetType, targetId, sort, cursor, cursorId, size);
        Map<String, Object> data = commentService.listByTarget(targetType, targetId, sort, cursor, cursorId, size);
        return Result.success(data);
    }

    /**
     * 获取子回复列表（用于"展开更多回复"，支持游标分页）
     *
     * <p>只返回直接子回复，不递归子孙。
     * 支持游标分页，每次加载一定数量的子回复，避免一次性加载大量数据。
     * 返回结果包含 {list, nextCursor, nextCursorId}，前端根据nextCursor判断是否继续加载。</p>
     *
     * @param parentId 父评论ID
     * @param cursor   游标（上一页最后一条的likeCount，首次请求不传）
     * @param cursorId 游标对应的评论ID（首次请求不传）
     * @param size     每页数量（默认10，最大50）
     * @return 子回复列表和分页游标
     */
    @GetMapping("/replies/{parentId}")
    public Result<Map<String, Object>> listReplies(
            @PathVariable("parentId") Long parentId,
            @RequestParam(value = "cursor", required = false) Integer cursor,
            @RequestParam(value = "cursorId", required = false) Long cursorId,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        log.info("获取回复列表: parentId={}, cursor={}, cursorId={}, size={}", parentId, cursor, cursorId, size);
        Map<String, Object> data = commentService.listReplies(parentId, cursor, cursorId, size);
        return Result.success(data);
    }

    /**
     * 删除评论（软删除）
     *
     * <p>普通用户只能删除自己的评论，管理员可删除任何评论。</p>
     *
     * @param commentId 评论ID
     * @return 操作结果
     */
    @DeleteMapping("/{commentId}")
    @RequirePermission("comment:delete")
    public Result<Void> delete(@PathVariable("commentId") Long commentId) {
        User currentUser = UserContext.get();
        // role=2为管理员
        boolean isAdmin = currentUser.getRole() != null && currentUser.getRole() == 2;
        log.info("删除评论: commentId={}, userId={}, isAdmin={}", commentId, currentUser.getId(), isAdmin);
        commentService.delete(commentId, currentUser.getId(), isAdmin);
        return Result.success(null);
    }
}
