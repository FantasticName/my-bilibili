package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.annotation.Autowired;
import io.github.fantasticname.mybilibili.annotation.Service;
import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import io.github.fantasticname.mybilibili.dao.FavoriteFolderDao;
import io.github.fantasticname.mybilibili.dao.FavoriteRecordDao;
import io.github.fantasticname.mybilibili.dao.UserBehaviorDao;
import io.github.fantasticname.mybilibili.dao.UserDao;
import io.github.fantasticname.mybilibili.dao.VideoDao;
import io.github.fantasticname.mybilibili.entity.FavoriteFolder;
import io.github.fantasticname.mybilibili.entity.FavoriteRecord;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.entity.UserBehavior;
import io.github.fantasticname.mybilibili.entity.Video;
import io.github.fantasticname.mybilibili.util.FileUtil;
import io.github.fantasticname.mybilibili.vo.VideoVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收藏服务实现类
 *
 * @author FantasticName
 */
@Service
public class FavoriteServiceImpl implements FavoriteService {

    private static final Logger log = LoggerFactory.getLogger(FavoriteServiceImpl.class);

    @Autowired
    private FavoriteFolderDao favoriteFolderDao;

    @Autowired
    private FavoriteRecordDao favoriteRecordDao;

    @Autowired
    private VideoDao videoDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private LikeService likeService;

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

    public FavoriteServiceImpl() {
    }

    @Override
    public FavoriteFolder createFolder(Long userId, String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "收藏夹名称不能为空");
        }
        if (name.length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "收藏夹名称过长");
        }
        if (favoriteFolderDao.existsByName(userId, name.trim())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "已存在同名收藏夹");
        }

        FavoriteFolder folder = new FavoriteFolder();
        folder.setUserId(userId);
        folder.setName(name.trim());
        folder.setIsDefault(0);
        long folderId = favoriteFolderDao.insert(folder);
        log.info("收藏夹创建成功: folderId={}, name={}", folderId, name);
        return favoriteFolderDao.findById(folderId);
    }

    @Override
    public List<FavoriteFolder> listFolders(Long userId) {
        List<FavoriteFolder> folders = favoriteFolderDao.listByUserId(userId);
        if (folders.isEmpty()) {
            FavoriteFolder defaultFolder = new FavoriteFolder();
            defaultFolder.setUserId(userId);
            defaultFolder.setName("默认收藏夹");
            defaultFolder.setIsDefault(1);
            long folderId = favoriteFolderDao.insert(defaultFolder);
            defaultFolder.setId(folderId);
            folders.add(defaultFolder);
        }
        return folders;
    }

    @Override
    public List<Map<String, Object>> listFoldersWithStatus(Long userId, Integer targetType, Long targetId) {
        List<FavoriteFolder> folders = listFolders(userId);
        List<Long> favoritedFolderIds = favoriteRecordDao.findFavoritedFolderIds(userId, targetType, targetId);

        List<Map<String, Object>> favoritedList = new ArrayList<>();
        List<Map<String, Object>> notFavoritedList = new ArrayList<>();

        for (FavoriteFolder folder : folders) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", folder.getId());
            item.put("name", folder.getName());
            item.put("isDefault", folder.getIsDefault());
            item.put("createdAt", folder.getCreatedAt());
            boolean isFav = favoritedFolderIds.contains(folder.getId());
            item.put("isFavorited", isFav);
            if (isFav) {
                favoritedList.add(item);
            } else {
                notFavoritedList.add(item);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        result.addAll(favoritedList);
        result.addAll(notFavoritedList);
        return result;
    }

    @Override
    public boolean batchUpdate(Long userId, List<Long> folderIds, Integer targetType, Long targetId) {
        List<Long> currentFavoritedFolderIds = favoriteRecordDao.findFavoritedFolderIds(userId, targetType, targetId);

        for (Long folderId : currentFavoritedFolderIds) {
            if (!folderIds.contains(folderId)) {
                favoriteRecordDao.deleteByFolderAndTarget(folderId, targetType, targetId);
                log.info("批量更新-取消收藏: userId={}, folderId={}, targetId={}", userId, folderId, targetId);
            }
        }

        for (Long folderId : folderIds) {
            if (!currentFavoritedFolderIds.contains(folderId)) {
                FavoriteRecord record = new FavoriteRecord();
                record.setFolderId(folderId);
                record.setUserId(userId);
                record.setTargetType(targetType);
                record.setTargetId(targetId);
                favoriteRecordDao.insert(record);
                log.info("批量更新-新增收藏: userId={}, folderId={}, targetId={}", userId, folderId, targetId);
            }
        }

        return !folderIds.isEmpty();
    }

    @Override
    public void deleteFolder(Long folderId, Long userId) {
        FavoriteFolder folder = favoriteFolderDao.findById(folderId);
        if (folder == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "收藏夹不存在");
        }
        if (!folder.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "只能删除自己的收藏夹");
        }
        if (folder.getIsDefault() == 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能删除默认收藏夹");
        }
        favoriteFolderDao.deleteRecordsByFolderId(folderId);
        favoriteFolderDao.delete(folderId);
        log.info("收藏夹删除成功: folderId={}", folderId);
    }

    @Override
    public void renameFolder(Long folderId, Long userId, String newName) {
        FavoriteFolder folder = favoriteFolderDao.findById(folderId);
        if (folder == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "收藏夹不存在");
        }
        if (!folder.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "只能重命名自己的收藏夹");
        }
        if (folder.getIsDefault() == 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "默认收藏夹不可重命名");
        }
        if (newName == null || newName.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "收藏夹名称不能为空");
        }
        if (favoriteFolderDao.existsByName(userId, newName.trim()) && !folder.getName().equals(newName.trim())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "已存在同名收藏夹");
        }
        favoriteFolderDao.rename(folderId, newName.trim());
        log.info("收藏夹重命名成功: folderId={}, newName={}", folderId, newName);
    }

    @Override
    public List<Map<String, Object>> listFoldersWithCount(Long userId) {
        List<FavoriteFolder> folders = listFolders(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (FavoriteFolder folder : folders) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", folder.getId());
            item.put("name", folder.getName());
            item.put("isDefault", folder.getIsDefault());
            item.put("createdAt", folder.getCreatedAt());
            item.put("videoCount", favoriteFolderDao.countVideosByFolderId(folder.getId()));
            result.add(item);
        }
        return result;
    }

    @Override
    public boolean toggle(Long userId, Long folderId, Integer targetType, Long targetId) {
        if (folderId == null) {
            FavoriteFolder defaultFolder = favoriteFolderDao.findDefaultByUserId(userId);
            if (defaultFolder == null) {
                defaultFolder = new FavoriteFolder();
                defaultFolder.setUserId(userId);
                defaultFolder.setName("默认收藏夹");
                defaultFolder.setIsDefault(1);
                long fid = favoriteFolderDao.insert(defaultFolder);
                defaultFolder.setId(fid);
            }
            folderId = defaultFolder.getId();
        }

        FavoriteRecord existing = favoriteRecordDao.findByFolderAndTarget(folderId, targetType, targetId);
        if (existing != null) {
            favoriteRecordDao.deleteByFolderAndTarget(folderId, targetType, targetId);
            log.info("取消收藏: userId={}, folderId={}, targetId={}", userId, folderId, targetId);
            return false;
        }

        FavoriteRecord record = new FavoriteRecord();
        record.setFolderId(folderId);
        record.setUserId(userId);
        record.setTargetType(targetType);
        record.setTargetId(targetId);
        favoriteRecordDao.insert(record);
        // 【行为埋点】异步记录用户收藏行为（推荐系统数据源）
        BEHAVIOR_EXECUTOR.submit(() -> {
            try {
                UserBehavior behavior = new UserBehavior();
                behavior.setUserId(userId);
                behavior.setBehaviorType("FAVORITE");
                behavior.setTargetId(targetId);
                behavior.setTargetType(targetType);
                behavior.setWeight(3);
                userBehaviorDao.insert(behavior);
            } catch (Exception ex) {
                log.warn("行为埋点失败(FAVORITE): userId={}, targetId={}, error={}", userId, targetId, ex.getMessage());
            }
        });
        log.info("收藏成功: userId={}, folderId={}, targetId={}", userId, folderId, targetId);
        return true;
    }

    @Override
    public boolean isFavorited(Long userId, Integer targetType, Long targetId) {
        if (userId == null) return false;
        return favoriteRecordDao.findByUserAndTarget(userId, targetType, targetId) != null;
    }

    @Override
    public List<VideoVO> listVideos(Long folderId, Long userId, int page, int size) {
        FavoriteFolder folder = favoriteFolderDao.findById(folderId);
        if (folder == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "收藏夹不存在");
        }
        if (!folder.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "只能查看自己的收藏夹");
        }
        int offset = (page - 1) * size;
        List<FavoriteRecord> records = favoriteRecordDao.listByFolderId(folderId, offset, size);
        List<VideoVO> result = new ArrayList<>();
        for (FavoriteRecord r : records) {
            Video video = videoDao.findById(r.getTargetId());
            if (video != null && video.getStatus() == 0) {
                VideoVO vo = new VideoVO();
                vo.setId(video.getId());
                vo.setTitle(video.getTitle());
                vo.setDescription(video.getDescription());
                vo.setCoverUrl(FileUtil.toUrl(video.getCoverUrl()));
                vo.setVideoUrl(FileUtil.toUrl(video.getVideoUrl()));
                vo.setCategory(video.getCategory());
                vo.setUserId(video.getUserId());
                vo.setViewCount(video.getViewCount());
                vo.setLikeCount(video.getLikeCount());
                vo.setCreatedAt(video.getCreatedAt());

                User user = userDao.findById(video.getUserId());
                if (user != null) {
                    vo.setNickname(user.getNickname());
                    vo.setAvatar(FileUtil.toUrl(user.getAvatar()));
                }
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    public int countVideos(Long folderId) {
        return favoriteRecordDao.countByFolderId(folderId);
    }
}
