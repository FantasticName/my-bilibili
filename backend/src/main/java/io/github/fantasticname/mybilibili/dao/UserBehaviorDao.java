package io.github.fantasticname.mybilibili.dao;

import io.github.fantasticname.mybilibili.annotation.Component;
import io.github.fantasticname.mybilibili.dao.base.BaseDao;
import io.github.fantasticname.mybilibili.dao.base.ConnectionHolder;
import io.github.fantasticname.mybilibili.entity.UserBehavior;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户行为DAO，封装用户行为表的数据库操作（埋点数据存储）
 *
 * <p>行为数据是推荐系统的输入源。本DAO提供行为写入和查询功能。</p>
 *
 * @author FantasticName
 */
@Component
public class UserBehaviorDao extends BaseDao<UserBehavior> {

    /**
     * 插入一条行为记录
     *
     * @param behavior 行为实体
     * @return 自增主键ID
     */
    public long insert(UserBehavior behavior) {
        String sql = "INSERT INTO user_behavior (user_id, behavior_type, target_id, target_type, weight, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, NOW())";
        return executeInsert(sql,
                behavior.getUserId(),
                behavior.getBehaviorType(),
                behavior.getTargetId(),
                behavior.getTargetType(),
                behavior.getWeight()
        );
    }

    /**
     * 查询用户最近的行为记录（按时间倒序）
     *
     * <p>推荐引擎使用最近N天的行为数据做推荐计算。</p>
     *
     * @param userId 用户ID
     * @param limit  查询条数
     * @return 行为列表
     */
    public List<UserBehavior> listByUser(long userId, int limit) {
        String sql = "SELECT * FROM user_behavior WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";
        return queryList(sql, userId, limit);
    }

    /**
     * 查询用户对指定类型目标的行为（如查询用户看过的视频ID列表）
     *
     * <p>推荐引擎用此方法获取"用户历史交互列表"，
     * 用于协同过滤（"和你看过同样视频的人还看了什么"）。</p>
     *
     * @param userId     用户ID
     * @param targetType 目标类型（0-动态，1-视频，3-用户）
     * @param limit      查询条数
     * @return 目标ID列表
     */
    public List<Long> listTargetIdsByUserAndType(long userId, int targetType, int limit) {
        String sql = "SELECT DISTINCT target_id FROM user_behavior WHERE user_id = ? AND target_type = ? " +
                     "ORDER BY MAX(created_at) DESC LIMIT ?";
        ConnectionHolder holder = borrow();
        try (java.sql.PreparedStatement ps = holder.connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, targetType);
            ps.setInt(3, limit);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                List<Long> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getLong("target_id"));
                }
                return ids;
            }
        } catch (Exception e) {
            throw new RuntimeException("查询用户行为目标ID失败", e);
        } finally {
            holder.closeIfBorrowed();
        }
    }
}