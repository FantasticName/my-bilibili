package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.entity.FavoriteFolder;
import io.github.fantasticname.mybilibili.vo.VideoVO;

import java.util.List;
import java.util.Map;

/**
 * 收藏服务接口
 *
 * @author FantasticName
 */
public interface FavoriteService {

    /**
     * 创建收藏夹
     *
     * @param userId 用户ID
     * @param name   收藏夹名称
     * @return 收藏夹对象
     */
    FavoriteFolder createFolder(Long userId, String name);

    /**
     * 获取用户的收藏夹列表
     *
     * @param userId 用户ID
     * @return 收藏夹列表
     */
    List<FavoriteFolder> listFolders(Long userId);

    /**
     * 获取用户的收藏夹列表，并标记每个收藏夹是否已收藏指定目标
     *
     * @param userId     用户ID
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @return 收藏夹列表（含isFavorited标记），已收藏的置顶
     */
    List<Map<String, Object>> listFoldersWithStatus(Long userId, Integer targetType, Long targetId);

    /**
     * 批量更新收藏状态
     *
     * @param userId      用户ID
     * @param folderIds   目标收藏夹ID列表
     * @param targetType  目标类型
     * @param targetId    目标ID
     * @return 是否至少在一个收藏夹中（即整体是否已收藏）
     */
    boolean batchUpdate(Long userId, List<Long> folderIds, Integer targetType, Long targetId);

    /**
     * 删除收藏夹
     *
     * @param folderId 收藏夹ID
     * @param userId   用户ID
     */
    void deleteFolder(Long folderId, Long userId);

    /**
     * 重命名收藏夹
     *
     * @param folderId 收藏夹ID
     * @param userId   用户ID
     * @param newName  新名称
     */
    void renameFolder(Long folderId, Long userId, String newName);

    /**
     * 获取收藏夹列表（含视频数量）
     *
     * @param userId 用户ID
     * @return 收藏夹列表（含videoCount字段）
     */
    List<Map<String, Object>> listFoldersWithCount(Long userId);

    /**
     * 收藏/取消收藏（Toggle模式）
     *
     * @param userId     用户ID
     * @param folderId   收藏夹ID
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @return true-已收藏，false-已取消
     */
    boolean toggle(Long userId, Long folderId, Integer targetType, Long targetId);

    /**
     * 判断是否已收藏
     *
     * @param userId     用户ID
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @return true-已收藏
     */
    boolean isFavorited(Long userId, Integer targetType, Long targetId);

    /**
     * 获取收藏夹中的视频列表（含归属校验）
     *
     * @param folderId 收藏夹ID
     * @param userId   当前用户ID，用于校验收藏夹归属
     * @param page     页码
     * @param size     每页数量
     * @return 视频VO列表
     */
    List<VideoVO> listVideos(Long folderId, Long userId, int page, int size);

    /**
     * 获取收藏夹中的视频总数
     *
     * @param folderId 收藏夹ID
     * @return 总数
     */
    int countVideos(Long folderId);
}
