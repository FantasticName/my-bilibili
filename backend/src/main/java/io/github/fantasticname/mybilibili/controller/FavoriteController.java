package io.github.fantasticname.mybilibili.controller;

import io.github.fantasticname.mybilibili.annotation.*;
import io.github.fantasticname.mybilibili.common.Result;
import io.github.fantasticname.mybilibili.context.UserContext;
import io.github.fantasticname.mybilibili.dto.FavoriteRequestDTO;
import io.github.fantasticname.mybilibili.entity.FavoriteFolder;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.service.FavoriteServiceImpl;
import io.github.fantasticname.mybilibili.vo.VideoVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收藏控制器，处理收藏夹管理、收藏/取消收藏等接口
 *
 * @author FantasticName
 */
@RestController
@io.github.fantasticname.mybilibili.annotation.RequestMapping("/favorite")
public class FavoriteController {

    private static final Logger log = LoggerFactory.getLogger(FavoriteController.class);

    @io.github.fantasticname.mybilibili.annotation.Autowired
    private FavoriteServiceImpl favoriteService;

    public FavoriteController() {
    }

    /**
     * 创建收藏夹
     *
     * @param body 包含name的请求体
     * @return 收藏夹对象
     */
    @PostMapping("/folder/create")
    @RequirePermission("favorite:manage")
    public Result<FavoriteFolder> createFolder(@RequestBody Map<String, String> body) {
        User currentUser = UserContext.get();
        String name = body.get("name");
        log.info("创建收藏夹: userId={}, name={}", currentUser.getId(), name);
        FavoriteFolder folder = favoriteService.createFolder(currentUser.getId(), name);
        return Result.success(folder);
    }

    /**
     * 获取收藏夹列表
     *
     * @return 收藏夹列表
     */
    @GetMapping("/folders")
    @RequirePermission("favorite:manage")
    public Result<List<FavoriteFolder>> listFolders() {
        User currentUser = UserContext.get();
        log.info("获取收藏夹列表: userId={}", currentUser.getId());
        List<FavoriteFolder> folders = favoriteService.listFolders(currentUser.getId());
        return Result.success(folders);
    }

    /**
     * 获取收藏夹列表（含视频数量）
     *
     * @return 收藏夹列表（含videoCount字段）
     */
    @GetMapping("/folders-with-count")
    @RequirePermission("favorite:manage")
    public Result<List<Map<String, Object>>> listFoldersWithCount() {
        User currentUser = UserContext.get();
        log.info("获取收藏夹列表(含数量): userId={}", currentUser.getId());
        List<Map<String, Object>> folders = favoriteService.listFoldersWithCount(currentUser.getId());
        return Result.success(folders);
    }

    /**
     * 重命名收藏夹
     *
     * @param body 包含folderId和name的请求体
     * @return 操作结果
     */
    @PutMapping("/folder/rename")
    @RequirePermission("favorite:manage")
    public Result<Void> renameFolder(@RequestBody Map<String, Object> body) {
        User currentUser = UserContext.get();
        Number folderIdNum = (Number) body.get("folderId");
        Long folderId = folderIdNum != null ? folderIdNum.longValue() : null;
        String name = (String) body.get("name");
        log.info("重命名收藏夹: folderId={}, userId={}, newName={}", folderId, currentUser.getId(), name);
        favoriteService.renameFolder(folderId, currentUser.getId(), name);
        return Result.success(null);
    }

    /**
     * 删除收藏夹
     *
     * @param folderId 收藏夹ID
     * @return 操作结果
     */
    @DeleteMapping("/folder/{folderId}")
    @RequirePermission("favorite:manage")
    public Result<Void> deleteFolder(@PathVariable("folderId") Long folderId) {
        User currentUser = UserContext.get();
        log.info("删除收藏夹: folderId={}, userId={}", folderId, currentUser.getId());
        favoriteService.deleteFolder(folderId, currentUser.getId());
        return Result.success(null);
    }

    /**
     * 收藏/取消收藏
     *
     * @param dto 收藏请求DTO
     * @return 操作结果
     */
    @PostMapping("/toggle")
    @RequirePermission("favorite:manage")
    public Result<Map<String, Object>> toggle(@RequestBody FavoriteRequestDTO dto) {
        User currentUser = UserContext.get();
        log.info("收藏/取消收藏: userId={}, folderId={}, targetId={}", currentUser.getId(), dto.getFolderId(), dto.getTargetId());
        boolean favorited = favoriteService.toggle(currentUser.getId(), dto.getFolderId(), dto.getTargetType(), dto.getTargetId());
        Map<String, Object> result = new HashMap<>();
        result.put("favorited", favorited);
        return Result.success(result);
    }

    /**
     * 获取收藏夹列表（含收藏状态）
     *
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @return 收藏夹列表（含isFavorited标记），已收藏的置顶
     */
    @GetMapping("/folders-with-status")
    @RequirePermission("favorite:manage")
    public Result<List<Map<String, Object>>> listFoldersWithStatus(
            @RequestParam("targetType") Integer targetType,
            @RequestParam("targetId") Long targetId) {
        User currentUser = UserContext.get();
        log.info("获取收藏夹列表(含状态): userId={}, targetType={}, targetId={}", currentUser.getId(), targetType, targetId);
        List<Map<String, Object>> folders = favoriteService.listFoldersWithStatus(currentUser.getId(), targetType, targetId);
        return Result.success(folders);
    }

    /**
     * 批量更新收藏状态
     *
     * @param body 包含folderIds、targetType、targetId的请求体
     * @return 操作结果
     */
    @PostMapping("/batch-update")
    @RequirePermission("favorite:manage")
    public Result<Map<String, Object>> batchUpdate(@RequestBody Map<String, Object> body) {
        User currentUser = UserContext.get();
        @SuppressWarnings("unchecked")
        List<Number> folderIdNumbers = (List<Number>) body.get("folderIds");
        List<Long> folderIds = new ArrayList<>();
        if (folderIdNumbers != null) {
            for (Number n : folderIdNumbers) {
                folderIds.add(n.longValue());
            }
        }
        Integer targetType = (Integer) body.get("targetType");
        Number targetIdNum = (Number) body.get("targetId");
        Long targetId = targetIdNum != null ? targetIdNum.longValue() : null;

        log.info("批量更新收藏: userId={}, folderIds={}, targetType={}, targetId={}", currentUser.getId(), folderIds, targetType, targetId);
        boolean favorited = favoriteService.batchUpdate(currentUser.getId(), folderIds, targetType, targetId);
        Map<String, Object> result = new HashMap<>();
        result.put("favorited", favorited);
        return Result.success(result);
    }

    /**
     * 获取收藏夹中的视频列表
     *
     * @param folderId 收藏夹ID
     * @param page     页码
     * @param size     每页数量
     * @return 视频列表和总数
     */
    @GetMapping("/videos/{folderId}")
    @RequirePermission("favorite:manage")
    public Result<Map<String, Object>> listVideos(
            @PathVariable("folderId") Long folderId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        User currentUser = UserContext.get();
        log.info("获取收藏夹视频: folderId={}, userId={}, page={}, size={}", folderId, currentUser.getId(), page, size);
        List<VideoVO> list = favoriteService.listVideos(folderId, currentUser.getId(), page, size);
        int total = favoriteService.countVideos(folderId);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return Result.success(result);
    }
}
