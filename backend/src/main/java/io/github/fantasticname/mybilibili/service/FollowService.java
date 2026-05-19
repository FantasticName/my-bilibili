package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.vo.PublicUserVO;

import java.util.List;

/**
 * 关注服务接口
 *
 * @author FantasticName
 */
public interface FollowService {

    /**
     * 关注/取关（Toggle模式）
     *
     * @param followerId 关注者ID
     * @param followeeId 被关注者ID
     * @return true-已关注，false-已取关
     */
    boolean toggle(Long followerId, Long followeeId);

    /**
     * 判断是否已关注
     *
     * @param followerId 关注者ID
     * @param followeeId 被关注者ID
     * @return true-已关注
     */
    boolean isFollowing(Long followerId, Long followeeId);

    /**
     * 获取关注列表（分页）
     *
     * @param followerId 关注者ID
     * @param page       页码
     * @param size       每页数量
     * @return 关注用户列表
     */
    List<PublicUserVO> listFollowing(Long followerId, int page, int size);

    /**
     * 获取粉丝列表（分页）
     *
     * @param followeeId 被关注者ID
     * @param page       页码
     * @param size       每页数量
     * @return 粉丝用户列表
     */
    List<PublicUserVO> listFollowers(Long followeeId, int page, int size);

    /**
     * 获取关注数
     *
     * @param userId 用户ID
     * @return 关注数
     */
    int countFollowing(Long userId);

    /**
     * 获取粉丝数
     *
     * @param userId 用户ID
     * @return 粉丝数
     */
    int countFollowers(Long userId);
}
