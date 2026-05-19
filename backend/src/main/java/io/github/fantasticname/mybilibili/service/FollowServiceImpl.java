package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.annotation.Autowired;
import io.github.fantasticname.mybilibili.annotation.Service;
import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.dao.FollowDao;
import io.github.fantasticname.mybilibili.dao.UserDao;
import io.github.fantasticname.mybilibili.entity.Follow;
import io.github.fantasticname.mybilibili.entity.Notification;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.util.FileUtil;
import io.github.fantasticname.mybilibili.vo.PublicUserVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 关注服务实现类
 *
 * @author FantasticName
 */
@Service
public class FollowServiceImpl implements FollowService {

    private static final Logger log = LoggerFactory.getLogger(FollowServiceImpl.class);

    @Autowired
    private FollowDao followDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private NotificationServiceImpl notificationService;

    public FollowServiceImpl() {
    }

    @Override
    public boolean toggle(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能关注自己");
        }

        User followee = userDao.findById(followeeId);
        if (followee == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        Follow existing = followDao.findByFollowerAndFollowee(followerId, followeeId);
        if (existing != null) {
            followDao.delete(followerId, followeeId);
            log.info("取关成功: followerId={}, followeeId={}", followerId, followeeId);
            return false;
        }

        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setFolloweeId(followeeId);
        followDao.insert(follow);
        log.info("关注成功: followerId={}, followeeId={}", followerId, followeeId);

        // 【消息通知】关注成功后，通知被关注者
        try {
            User follower = userDao.findById(followerId);
            Notification notification = new Notification();
            notification.setUserId(followeeId);
            notification.setFromUserId(followerId);
            notification.setNotifyType("FOLLOW");
            notification.setTargetId(followerId);
            notification.setTargetType(3);
            notification.setContent("用户【" + (follower != null ? follower.getNickname() : "未知用户") + "】关注了你");
            notification.setIsRead(0);
            notification.setStatus(0);
            notificationService.sendNotification(notification);
            log.info("关注通知发送成功: followerId={}, followeeId={}", followerId, followeeId);
        } catch (Exception e) {
            // 通知发送失败不影响主流程
            log.warn("关注通知发送失败: followerId={}, followeeId={}, error={}", followerId, followeeId, e.getMessage());
        }

        return true;
    }

    @Override
    public boolean isFollowing(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null) return false;
        return followDao.findByFollowerAndFollowee(followerId, followeeId) != null;
    }

    @Override
    public List<PublicUserVO> listFollowing(Long followerId, int page, int size) {
        int offset = (page - 1) * size;
        List<Follow> follows = followDao.listFollowing(followerId, offset, size);
        return convertFollowList(follows, true, followerId);
    }

    @Override
    public List<PublicUserVO> listFollowers(Long followeeId, int page, int size) {
        int offset = (page - 1) * size;
        List<Follow> follows = followDao.listFollowers(followeeId, offset, size);
        return convertFollowList(follows, false, followeeId);
    }

    @Override
    public int countFollowing(Long userId) {
        return followDao.countFollowing(userId);
    }

    @Override
    public int countFollowers(Long userId) {
        return followDao.countFollowers(userId);
    }

    private List<PublicUserVO> convertFollowList(List<Follow> follows, boolean isFollowingList, Long currentUserId) {
        List<PublicUserVO> result = new ArrayList<>();
        for (Follow f : follows) {
            Long targetUserId = isFollowingList ? f.getFolloweeId() : f.getFollowerId();
            User user = userDao.findById(targetUserId);
            if (user != null) {
                PublicUserVO vo = convertToPublicUserVO(user, currentUserId);
                result.add(vo);
            }
        }
        return result;
    }

    private PublicUserVO convertToPublicUserVO(User user, Long currentUserId) {
        PublicUserVO vo = new PublicUserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(FileUtil.toUrl(user.getAvatar()));
        vo.setRole(user.getRole());
        vo.setFollowCount(followDao.countFollowing(user.getId()));
        vo.setFansCount(followDao.countFollowers(user.getId()));
        vo.setCreatedAt(user.getCreatedAt());
        vo.setIsFollowed(currentUserId != null && isFollowing(currentUserId, user.getId()));
        return vo;
    }
}
