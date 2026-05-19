package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.vo.CommentVO;

import java.util.List;
import java.util.Map;

/**
 * 评论服务接口
 *
 * @author FantasticName
 */
public interface CommentService {

    /**
     * 创建评论
     *
     * @param userId     评论用户ID
     * @param content    评论内容
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @param parentId   父评论ID（可为null）
     * @return 评论VO
     */
    CommentVO create(Long userId, String content, Integer targetType, Long targetId, Long parentId);

    /**
     * 获取评论列表（热门排序 + 游标分页 + 树形结构）
     *
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @param sort       排序方式（hot=热门）
     * @param cursor     游标（上一页最后一条评论的likeCount，首次请求传null）
     * @param cursorId   游标对应的评论ID（首次请求传null）
     * @param size       每页数量
     * @return 包含list和nextCursor的Map
     */
    Map<String, Object> listByTarget(Integer targetType, Long targetId,
                                      String sort, Integer cursor, Long cursorId, int size);

    /**
     * 查询某条评论的直接子回复（支持游标分页）
     *
     * <p>只返回直接子回复，不递归子孙。
     * 支持游标分页：cursor为上一页最后一条的likeCount，cursorId为对应的评论ID。
     * 返回结果包含分页信息：{list, nextCursor, nextCursorId}。</p>
     *
     * @param parentId 父评论ID
     * @param cursor   游标（上一页最后一条的likeCount，首次请求传null）
     * @param cursorId 游标对应的评论ID（首次请求传null）
     * @param size     每页数量上限
     * @return 包含子回复列表和下一页游标的Map：{list, nextCursor, nextCursorId}
     */
    Map<String, Object> listReplies(Long parentId, Integer cursor, Long cursorId, int size);

    /**
     * 删除评论（软删除）
     *
     * @param commentId 评论ID
     * @param userId    操作用户ID
     * @param isAdmin   是否管理员
     */
    void delete(Long commentId, Long userId, boolean isAdmin);
}
