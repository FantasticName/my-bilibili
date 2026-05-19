package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.annotation.Autowired;
import io.github.fantasticname.mybilibili.annotation.Service;
import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.dao.CommentDao;
import io.github.fantasticname.mybilibili.dao.FavoriteFolderDao;
import io.github.fantasticname.mybilibili.dao.FavoriteRecordDao;
import io.github.fantasticname.mybilibili.dao.LikeRecordDao;
import io.github.fantasticname.mybilibili.dao.PostDao;
import io.github.fantasticname.mybilibili.dao.UserBehaviorDao;
import io.github.fantasticname.mybilibili.dao.VideoDao;
import io.github.fantasticname.mybilibili.entity.Comment;
import io.github.fantasticname.mybilibili.entity.FavoriteFolder;
import io.github.fantasticname.mybilibili.entity.FavoriteRecord;
import io.github.fantasticname.mybilibili.entity.LikeRecord;
import io.github.fantasticname.mybilibili.entity.Notification;
import io.github.fantasticname.mybilibili.entity.Post;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.entity.UserBehavior;
import io.github.fantasticname.mybilibili.entity.Video;
import io.github.fantasticname.mybilibili.dao.UserDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 点赞服务实现类
 *
 * @author FantasticName
 */
@Service
public class LikeServiceImpl implements LikeService {

    private static final Logger log = LoggerFactory.getLogger(LikeServiceImpl.class);

    @Autowired
    private LikeRecordDao likeRecordDao;

    @Autowired
    private VideoDao videoDao;

    @Autowired
    private CommentDao commentDao;

    @Autowired
    private PostDao postDao;

    @Autowired
    private FavoriteFolderDao favoriteFolderDao;

    @Autowired
    private FavoriteRecordDao favoriteRecordDao;

    @Autowired
    private FollowService followService;

    @Autowired
    private NotificationServiceImpl notificationService;

    @Autowired
    private UserDao userDao;

    /**
     * 用户行为DAO，用于异步写入行为埋点数据
     */
    @Autowired
    private UserBehaviorDao userBehaviorDao;

    /**
     * 行为埋点异步线程池（用于异步写入user_behavior表，不阻塞主流程）
     */
    private static final java.util.concurrent.ExecutorService BEHAVIOR_EXECUTOR =
            java.util.concurrent.Executors.newFixedThreadPool(4);

    public LikeServiceImpl() {
    }

    @Override
    public boolean toggle(Long userId, Integer targetType, Long targetId) {
        LikeRecord existing = likeRecordDao.findByUserAndTarget(userId, targetType, targetId);
        if (existing != null) {
            // 已点赞 → 取消点赞
            likeRecordDao.delete(userId, targetType, targetId);
            if (targetType == 1) {
                // 取消视频点赞
                videoDao.decrementLikeCount(targetId);
            } else if (targetType == 2) {
                // 取消评论点赞
                commentDao.decrementLikeCount(targetId);
            } else if (targetType == 3) {
                // 取消动态点赞
                postDao.decrementLikeCount(targetId);
            }
            log.info("取消点赞: userId={}, targetType={}, targetId={}", userId, targetType, targetId);
            return false;
        }

        // 未点赞 → 点赞
        LikeRecord record = new LikeRecord();
        record.setUserId(userId);
        record.setTargetType(targetType);
        record.setTargetId(targetId);
        likeRecordDao.insert(record);

        if (targetType == 1) {
            // 视频点赞
            videoDao.incrementLikeCount(targetId);
        } else if (targetType == 2) {
            // 评论点赞
            commentDao.incrementLikeCount(targetId);
        } else if (targetType == 3) {
            // 动态点赞
            postDao.incrementLikeCount(targetId);
        }

        // 【消息通知】点赞成功后，通知内容拥有者
        try {
            Long targetOwnerId = null;
            String targetTypeName = "";
            if (targetType == 1) {
                Video video = videoDao.findById(targetId);
                if (video != null) targetOwnerId = video.getUserId();
                targetTypeName = "视频";
            } else if (targetType == 2) {
                Comment comment = commentDao.findById(targetId);
                if (comment != null) targetOwnerId = comment.getUserId();
                targetTypeName = "评论";
            } else if (targetType == 3) {
                Post post = postDao.findById(targetId);
                if (post != null) targetOwnerId = post.getUserId();
                targetTypeName = "动态";
            }
            // 不通知自己（自己赞自己的内容不发通知）
            if (targetOwnerId != null && !targetOwnerId.equals(userId)) {
                User liker = userDao.findById(userId);
                Notification notification = new Notification();
                notification.setUserId(targetOwnerId);
                notification.setFromUserId(userId);
                notification.setNotifyType("LIKE");
                notification.setTargetId(targetId);
                notification.setTargetType(targetType);
                notification.setContent("用户【" + (liker != null ? liker.getNickname() : "未知用户") + "】赞了你的" + targetTypeName);
                notification.setIsRead(0);
                notification.setStatus(0);
                notificationService.sendNotification(notification);
                log.info("点赞通知发送成功: userId={}, targetType={}, targetId={}", userId, targetType, targetId);
            }
        } catch (Exception e) {
            log.warn("点赞通知发送失败: userId={}, targetType={}, targetId={}, error={}", userId, targetType, targetId, e.getMessage());
        }

        // 【行为埋点】异步记录用户点赞行为（推荐系统数据源）
        BEHAVIOR_EXECUTOR.submit(() -> {
            try {
                UserBehavior behavior = new UserBehavior();
                behavior.setUserId(userId);
                behavior.setBehaviorType("LIKE");
                behavior.setTargetId(targetId);
                behavior.setTargetType(targetType);
                behavior.setWeight(3);
                userBehaviorDao.insert(behavior);
            } catch (Exception ex) {
                log.warn("行为埋点失败(LIKE): userId={}, targetId={}, error={}", userId, targetId, ex.getMessage());
            }
        });

        log.info("点赞成功: userId={}, targetType={}, targetId={}", userId, targetType, targetId);
        return true;
    }

    @Override
    public boolean isLiked(Long userId, Integer targetType, Long targetId) {
        if (userId == null) return false;
        return likeRecordDao.findByUserAndTarget(userId, targetType, targetId) != null;
    }

    @Override
    public Map<String, Object> doubleTap(Long userId, Long videoId) {
        // 校验视频是否存在
        Video video = videoDao.findById(videoId);
        if (video == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "视频不存在");
        }

        // 校验视频是否已下架
        if (video.getStatus() != null && video.getStatus() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "视频已下架");
        }

        // 一键二连是单向操作：只添加，不取消
        // 1. 如果未点赞，则点赞
        boolean wasLiked = likeRecordDao.findByUserAndTarget(userId, 1, videoId) != null;
        boolean liked = wasLiked;
        if (!wasLiked) {
            // 未点赞，插入点赞记录并递增点赞数
            LikeRecord record = new LikeRecord();
            record.setUserId(userId);
            record.setTargetType(1);
            record.setTargetId(videoId);
            likeRecordDao.insert(record);
            videoDao.incrementLikeCount(videoId);
            liked = true;
            log.info("一键二连-点赞: userId={}, videoId={}", userId, videoId);
        }

        // 2. 如果未收藏进默认收藏夹，则收藏
        boolean wasFavorited = favoriteRecordDao.findByUserAndTarget(userId, 1, videoId) != null;
        boolean favorited = wasFavorited;
        if (!wasFavorited) {
            // 查找用户的默认收藏夹
            FavoriteFolder defaultFolder = favoriteFolderDao.findDefaultByUserId(userId);
            if (defaultFolder == null) {
                // 默认收藏夹不存在，自动创建
                defaultFolder = new FavoriteFolder();
                defaultFolder.setUserId(userId);
                defaultFolder.setName("默认收藏夹");
                defaultFolder.setIsDefault(1);
                long folderId = favoriteFolderDao.insert(defaultFolder);
                defaultFolder.setId(folderId);
                log.info("一键二连-自动创建默认收藏夹: userId={}, folderId={}", userId, folderId);
            }
            // 插入收藏记录到默认收藏夹
            FavoriteRecord favRecord = new FavoriteRecord();
            favRecord.setFolderId(defaultFolder.getId());
            favRecord.setUserId(userId);
            favRecord.setTargetType(1);
            favRecord.setTargetId(videoId);
            favoriteRecordDao.insert(favRecord);
            favorited = true;
            log.info("一键二连-收藏: userId={}, videoId={}, folderId={}", userId, videoId, defaultFolder.getId());
        }

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);
        result.put("favorited", favorited);

        // 构建描述信息
        StringBuilder sb = new StringBuilder();
        if (!wasLiked) {
            sb.append("已点赞");
        } else {
            sb.append("已点赞（之前已点赞）");
        }
        if (!wasFavorited) {
            sb.append("，已收藏");
        } else {
            sb.append("，已收藏（之前已收藏）");
        }
        result.put("message", sb.toString());

        log.info("一键二连完成: userId={}, videoId={}, liked={}, favorited={}", userId, videoId, liked, favorited);
        return result;
    }
}
