package io.github.fantasticname.mybilibili.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户行为实体类（轻量级埋点），对应数据库中的 user_behavior 表
 *
 * <p>用户行为收集是推荐系统的数据基础。不用接入专业埋点系统（如神策），
 * 使用轻量级方案：在Controller关键接口处插入行为记录代码。</p>
 *
 * <p>埋点位置（哪些接口需要记录行为）：</p>
 * <ul>
 *   <li>观看视频：GET /api/video/detail → 记录 VIEW 行为</li>
 *   <li>点赞：POST /api/like/toggle → 记录 LIKE 行为</li>
 *   <li>评论：POST /api/comment/create → 记录 COMMENT 行为</li>
 *   <li>关注：POST /api/follow → 记录 FOLLOW 行为</li>
 * </ul>
 *
 * <p>行为权重（用于推荐排序）：</p>
 * <ul>
 *   <li>VIEW = 1分（浅层次交互）</li>
 *   <li>LIKE = 3分（中等层次交互）</li>
 *   <li>COMMENT = 5分（深度交互）</li>
 *   <li>FOLLOW = 10分（最强兴趣信号）</li>
 * </ul>
 *
 * <p>推荐引擎利用这些行为数据离线计算CTR（点击率）模型，
 * 生成针对每个用户的个性化推荐列表。</p>
 *
 * @author FantasticName
 */
@Data
public class UserBehavior implements Serializable {

    /**
     * 记录ID（主键，自增）
     */
    private Long id;

    /**
     * 用户ID（做出行为的用户）
     */
    private Long userId;

    /**
     * 行为类型（VIEW/LIKE/COMMENT/FOLLOW/SHARE）
     */
    private String behaviorType;

    /**
     * 目标ID（视频ID/动态ID/用户ID等）
     */
    private Long targetId;

    /**
     * 目标类型（0-动态，1-视频，2-评论，3-用户）
     */
    private Integer targetType;

    /**
     * 行为权重（VIEW=1, LIKE=3, COMMENT=5, FOLLOW=10）
     *
     * <p>权重越高表示用户兴趣越强。推荐系统排序时按权重加权。</p>
     */
    private Integer weight;

    /**
     * 行为时间
     */
    private LocalDateTime createdAt;
}