package io.github.fantasticname.mybilibili.service;

import java.util.Map;

/**
 * 点赞服务接口
 *
 * @author FantasticName
 */
public interface LikeService {

    /**
     * 点赞/取消点赞（Toggle模式）
     *
     * @param userId     用户ID
     * @param targetType 目标类型（1-视频，2-评论）
     * @param targetId   目标ID
     * @return true-已点赞，false-已取消
     */
    boolean toggle(Long userId, Integer targetType, Long targetId);

    /**
     * 判断是否已点赞
     *
     * @param userId     用户ID
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @return true-已点赞
     */
    boolean isLiked(Long userId, Integer targetType, Long targetId);

    /**
     * 一键二连：点赞视频 + 收藏进默认收藏夹
     * <p>
     * 一键二连是单向操作，只添加不取消：
     * - 如果未点赞，则点赞；已点赞则保持不变
     * - 如果未收藏进默认收藏夹，则收藏；已收藏则保持不变
     * </p>
     *
     * @param userId  用户ID
     * @param videoId 视频ID
     * @return 操作结果，包含 liked（是否已点赞）、favorited（是否已收藏）、message（描述信息）
     */
    Map<String, Object> doubleTap(Long userId, Long videoId);
}
