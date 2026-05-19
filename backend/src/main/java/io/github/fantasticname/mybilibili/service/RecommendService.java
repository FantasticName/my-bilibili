package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.vo.PostVO;
import java.util.List;

/**
 * 推荐服务接口
 *
 * <p>Feed流推荐是差异化体验的关键——不同的用户看到不同的内容，
 * 基于他们的历史行为（点赞、评论、观看）推荐可能感兴趣的内容。</p>
 *
 * @author FantasticName
 */
public interface RecommendService {

    /**
     * 获取个性化推荐Feed流
     *
     * <p>推荐策略：</p>
     * <ul>
     *   <li>基于用户历史行为计算兴趣向量</li>
     *   <li>融合热度得分和行为匹配得分</li>
     *   <li>按综合得分降序排列</li>
     * </ul>
     *
     * <p>用于Feed流"推荐"Tab（区别于"关注"Tab）。</p>
     *
     * @param userId 用户ID
     * @param limit  推荐数量
     * @return 推荐动态列表
     */
    List<PostVO> getRecommendedFeed(Long userId, int limit);
}