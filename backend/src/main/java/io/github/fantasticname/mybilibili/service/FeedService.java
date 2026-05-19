package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.vo.PostVO;

import java.util.List;

/**
 * Feed流服务接口
 *
 * @author FantasticName
 */
public interface FeedService {

    /**
     * 获取关注用户的动态Feed流（游标分页）
     *
     * @param userId 当前用户ID
     * @param cursor 游标（上一页最后一条的created_at，null表示第一页）
     * @param limit  每页数量
     * @return 动态VO列表
     */
    List<PostVO> getFeed(Long userId, String cursor, int limit);
}
