package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.annotation.Autowired;
import io.github.fantasticname.mybilibili.annotation.Service;
import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.dao.CommentDao;
import io.github.fantasticname.mybilibili.dao.PostDao;
import io.github.fantasticname.mybilibili.dao.UserBehaviorDao;
import io.github.fantasticname.mybilibili.dao.UserDao;
import io.github.fantasticname.mybilibili.dao.VideoDao;
import io.github.fantasticname.mybilibili.entity.Comment;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.entity.UserBehavior;
import io.github.fantasticname.mybilibili.entity.Notification;
import io.github.fantasticname.mybilibili.entity.Post;
import io.github.fantasticname.mybilibili.entity.Video;
import io.github.fantasticname.mybilibili.util.FileUtil;
import io.github.fantasticname.mybilibili.vo.CommentVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评论服务实现类
 *
 * <p>核心特性：</p>
 * <ul>
 *   <li>热门排序：顶层评论按点赞数降序，使用游标分页</li>
 *   <li>O(n)树形组装：用HashMap按parentId分组，一次遍历完成</li>
 *   <li>每个顶层评论最多显示3条热门子回复，支持"展开更多"</li>
 *   <li>展开更多回复：支持游标分页，按需加载，避免一次性加载大量数据</li>
 *   <li>评论深度限制：最多支持10层嵌套回复，防止递归深度攻击</li>
 * </ul>
 *
 * @author FantasticName
 */
@Service
public class CommentServiceImpl implements CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentServiceImpl.class);

    /**
     * 每个顶层评论默认展示的子回复数量上限
     */
    private static final int DEFAULT_REPLY_LIMIT = 3;

    @Autowired
    private CommentDao commentDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private LikeService likeService;

    @Autowired
    private PostDao postDao;

    @Autowired
    private NotificationServiceImpl notificationService;

    @Autowired
    private VideoDao videoDao;

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

    public CommentServiceImpl() {
    }

    @Override
    public CommentVO create(Long userId, String content, Integer targetType, Long targetId, Long parentId) {
        log.info("开始创建评论: userId={}, targetType={}, targetId={}, parentId={}", userId, targetType, targetId, parentId);

        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "评论内容不能为空");
        }
        if (content.length() > 5000) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "评论内容过长");
        }

        // 如果是回复，校验父评论存在且属于同一个目标
        if (parentId != null) {
            Comment parent = commentDao.findById(parentId);
            if (parent == null || parent.getStatus() != 0) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "回复的评论不存在");
            }
            // 安全检查：计算当前回复的层级深度，防止恶意构造过深的评论树（递归深度攻击）
            int depth = calculateCommentDepth(parentId);
            if (depth >= 10) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "评论层级过深，最多支持10层回复");
            }
        }

        Comment comment = new Comment();
        comment.setContent(content.trim());
        comment.setUserId(userId);
        comment.setTargetType(targetType);
        comment.setTargetId(targetId);
        comment.setParentId(parentId);

        long commentId = commentDao.insert(comment);

        // 如果是动态评论，递增动态的评论计数
        if (targetType == 2) {
            postDao.incrementCommentCount(targetId);
        }

        // 【行为埋点】异步记录用户评论行为（推荐系统数据源）
        BEHAVIOR_EXECUTOR.submit(() -> {
            try {
                UserBehavior behavior = new UserBehavior();
                behavior.setUserId(userId);
                behavior.setBehaviorType("COMMENT");
                behavior.setTargetId(targetId);
                behavior.setTargetType(targetType);
                behavior.setWeight(5);
                userBehaviorDao.insert(behavior);
            } catch (Exception ex) {
                log.warn("行为埋点失败(COMMENT): userId={}, targetId={}, error={}", userId, targetId, ex.getMessage());
            }
        });

        // 【消息通知】评论成功后，通知内容拥有者
        try {
            Long targetOwnerId = null;
            String targetTypeName = "";
            if (targetType == 1) {
                // 视频评论
                Video video = videoDao.findById(targetId);
                if (video != null) targetOwnerId = video.getUserId();
                targetTypeName = "视频";
            } else if (targetType == 2) {
                // 动态评论
                Post post = postDao.findById(targetId);
                if (post != null) targetOwnerId = post.getUserId();
                targetTypeName = "动态";
            }
            // 不通知自己
            if (targetOwnerId != null && !targetOwnerId.equals(userId)) {
                User commenter = userDao.findById(userId);
                Notification notification = new Notification();
                notification.setUserId(targetOwnerId);
                notification.setFromUserId(userId);
                notification.setNotifyType("COMMENT");
                notification.setTargetId(targetId);
                notification.setTargetType(targetType);
                notification.setContent("用户【" + (commenter != null ? commenter.getNickname() : "未知用户") + "】评论了你的" + targetTypeName);
                notification.setIsRead(0);
                notification.setStatus(0);
                notificationService.sendNotification(notification);
                log.info("评论通知发送成功: userId={}, targetType={}, targetId={}", userId, targetType, targetId);
            }
        } catch (Exception e) {
            log.warn("评论通知发送失败: userId={}, targetType={}, targetId={}, error={}", userId, targetType, targetId, e.getMessage());
        }

        log.info("评论创建成功: commentId={}", commentId);
        return convertToVO(commentDao.findById(commentId), userId);
    }

    /**
     * 计算评论的层级深度（从指定评论追溯到顶层评论）
     *
     * <p>通过循环遍历parentId链，计算从当前评论到顶层评论（parentId为null）的层级数。
     * 此方法用于在创建回复时检查是否超过最大层级限制，防止恶意构造过深的评论树。
     * 防御：如果循环超过20次，直接报错，防止数据异常导致死循环。</p>
     *
     * @param commentId 评论ID
     * @return 层级深度（0表示顶层评论，1表示顶层评论的直接回复，以此类推）
     * @throws BusinessException 如果检测到数据异常（超过20层）
     */
    private int calculateCommentDepth(Long commentId) {
        int depth = 0;
        Long currentId = commentId;
        // 循环遍历parentId链，直到找到顶层评论（parentId为null）
        while (currentId != null) {
            Comment c = commentDao.findById(currentId);
            // 如果评论不存在或已经是顶层评论，结束循环
            if (c == null || c.getParentId() == null) {
                break;
            }
            depth++;
            currentId = c.getParentId();
            // 防御：如果循环超过20次，直接报错（防止数据异常导致死循环）
            if (depth > 20) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "评论层级异常");
            }
        }
        return depth;
    }

    @Override
    public Map<String, Object> listByTarget(Integer targetType, Long targetId,
                                              String sort, Integer cursor, Long cursorId, int size) {
        log.info("获取评论列表: targetType={}, targetId={}, sort={}, cursor={}, cursorId={}, size={}",
                targetType, targetId, sort, cursor, cursorId, size);

        // 第一步：查询顶层评论（热门排序 + 游标分页）
        List<Comment> topComments = commentDao.listTopCommentsHot(
                targetType, targetId, cursor, cursorId, size);

        if (topComments.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("list", new ArrayList<>());
            result.put("nextCursor", null);
            result.put("nextCursorId", null);
            return result;
        }

        // 收集顶层评论ID
        List<Long> topIds = new ArrayList<>();
        for (Comment c : topComments) {
            topIds.add(c.getId());
        }

        // 第二步：批量查询子回复数量（优化：1次批量COUNT替代N次单独COUNT）
        Map<Long, Integer> replyCountMap = commentDao.countRepliesByParentIds(topIds);

        // 第三步：一次性查出顶层评论的直接子回复（O(n)的关键：只做一次查询）
        List<Comment> allReplies = commentDao.listRepliesByParentIds(topIds);

        // 第四步：用HashMap按parentId分组，O(n)时间完成
        Map<Long, List<Comment>> childrenMap = new HashMap<>();
        for (Comment reply : allReplies) {
            childrenMap.computeIfAbsent(reply.getParentId(), k -> new ArrayList<>()).add(reply);
        }

        // 第五步：收集所有直接子回复的ID，用于批量查询它们是否有自己的子回复
        List<Long> allDirectReplyIds = new ArrayList<>();
        for (Comment reply : allReplies) {
            allDirectReplyIds.add(reply.getId());
        }
        Map<Long, Integer> subReplyCountMap = allDirectReplyIds.isEmpty()
                ? new HashMap<>()
                : commentDao.countRepliesByParentIds(allDirectReplyIds);

        // 第六步：组装树形结构，每个顶层评论最多展示3条热门直接子回复（不递归更深层）
        List<CommentVO> voList = new ArrayList<>();
        for (Comment top : topComments) {
            CommentVO vo = convertToVO(top, null);
            // 只取直接子回复，不递归
            List<Comment> directReplies = childrenMap.getOrDefault(top.getId(), new ArrayList<>());
            // 直接子回复已按like_count DESC排序，取前DEFAULT_REPLY_LIMIT条
            int replyLimit = Math.min(directReplies.size(), DEFAULT_REPLY_LIMIT);
            List<CommentVO> replyVos = new ArrayList<>();
            for (int i = 0; i < replyLimit; i++) {
                Comment reply = directReplies.get(i);
                CommentVO replyVo = convertToVO(reply, null);
                // 判断该子回复是否还有自己的子回复（用于显示"展开更多回复"按钮）
                int subCount = subReplyCountMap.getOrDefault(reply.getId(), 0);
                replyVo.setHasMoreReplies(subCount > 0);
                // 初始为空，用户点击该子回复的"展开更多回复"时才加载
                replyVo.setReplies(new ArrayList<>());
                replyVos.add(replyVo);
            }
            vo.setReplies(replyVos);
            // 使用批量查询结果判断是否还有更多直接子回复
            int totalReplies = replyCountMap.getOrDefault(top.getId(), 0);
            vo.setHasMoreReplies(totalReplies > DEFAULT_REPLY_LIMIT);
            voList.add(vo);
        }

        // 第五步：计算下一页游标
        Comment last = topComments.get(topComments.size() - 1);
        Integer nextCursor = last.getLikeCount();
        Long nextCursorId = last.getId();

        Map<String, Object> result = new HashMap<>();
        result.put("list", voList);
        result.put("nextCursor", nextCursor);
        result.put("nextCursorId", nextCursorId);
        return result;
    }

    @Override
    public Map<String, Object> listReplies(Long parentId, Integer cursor, Long cursorId, int size) {
        log.info("查询子回复: parentId={}, cursor={}, cursorId={}, size={}", parentId, cursor, cursorId, size);

        // 防御：限制单次查询数量上限，防刷
        size = Math.min(size, 50);

        // 第1步：查询直接子回复（支持游标分页，不递归子孙）
        List<Comment> replies = commentDao.listRepliesWithCursor(parentId, cursor, cursorId, size);

        if (replies.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("list", new ArrayList<>());
            result.put("nextCursor", null);
            result.put("nextCursorId", null);
            return result;
        }

        // 第2步：收集所有子回复ID，用于批量查询每条回复的子回复数量
        List<Long> replyIds = new ArrayList<>();
        for (Comment c : replies) {
            replyIds.add(c.getId());
        }

        // 第3步：批量查询每条回复的直接子回复数量（优化：1次批量COUNT替代N次单独COUNT）
        // 用于判断每条子回复是否还有自己的子回复（hasMoreReplies标记）
        Map<Long, Integer> replyCountMap = commentDao.countRepliesByParentIds(replyIds);

        // 第4步：转换为VO对象
        List<CommentVO> voList = new ArrayList<>();
        for (Comment c : replies) {
            CommentVO vo = convertToVO(c, null);
            // 判断该子回复是否还有自己的子回复
            int childCount = replyCountMap.getOrDefault(c.getId(), 0);
            vo.setHasMoreReplies(childCount > 0);
            // 初始为空，用户点击该子回复的"展开更多回复"时才加载
            vo.setReplies(new ArrayList<>());
            voList.add(vo);
        }

        // 第5步：计算下一页游标（用于前端继续加载更多）
        Comment last = replies.get(replies.size() - 1);
        Map<String, Object> result = new HashMap<>();
        result.put("list", voList);
        result.put("nextCursor", last.getLikeCount());
        result.put("nextCursorId", last.getId());
        return result;
    }

    @Override
    public void delete(Long commentId, Long userId, boolean isAdmin) {
        Comment comment = commentDao.findById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "评论不存在");
        }
        // 管理员可删除任何评论，普通用户只能删除自己的
        if (!isAdmin && !comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "只能删除自己的评论");
        }
        commentDao.softDelete(commentId);
        log.info("评论删除成功: commentId={}, operatorId={}, isAdmin={}", commentId, userId, isAdmin);
    }

    /**
     * 将Comment实体转换为CommentVO
     *
     * @param comment       评论实体
     * @param currentUserId 当前登录用户ID（可为null，用于判断是否已点赞）
     * @return 评论VO
     */
    private CommentVO convertToVO(Comment comment, Long currentUserId) {
        if (comment == null) return null;
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setContent(comment.getContent());
        vo.setUserId(comment.getUserId());
        vo.setTargetType(comment.getTargetType());
        vo.setTargetId(comment.getTargetId());
        vo.setParentId(comment.getParentId());
        vo.setLikeCount(comment.getLikeCount());
        vo.setCreatedAt(comment.getCreatedAt());

        User user = userDao.findById(comment.getUserId());
        if (user != null) {
            vo.setNickname(user.getNickname());
            vo.setAvatar(FileUtil.toUrl(user.getAvatar()));
        }

        // 如果有当前用户，判断是否已点赞
        if (currentUserId != null) {
            vo.setIsLiked(likeService.isLiked(currentUserId, 2, comment.getId()));
        }

        return vo;
    }
}
