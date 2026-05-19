package io.github.fantasticname.mybilibili.controller;

import io.github.fantasticname.mybilibili.annotation.*;
import io.github.fantasticname.mybilibili.common.Result;
import io.github.fantasticname.mybilibili.context.UserContext;
import io.github.fantasticname.mybilibili.entity.User;
import io.github.fantasticname.mybilibili.service.FileServiceImpl;
import io.github.fantasticname.mybilibili.service.PostServiceImpl;
import io.github.fantasticname.mybilibili.util.IdempotentUtil;
import io.github.fantasticname.mybilibili.util.SentinelUtil;
import io.github.fantasticname.mybilibili.vo.PostVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态控制器，处理动态发布、查看、删除等接口
 *
 * <p>接口列表：</p>
 * <ul>
 *   <li>POST /post - 发布动态（multipart/form-data，支持多图上传）</li>
 *   <li>GET /post/user/{userId} - 获取用户动态列表（游标分页）</li>
 *   <li>GET /post/{postId} - 获取动态详情</li>
 *   <li>DELETE /post/{postId} - 删除动态</li>
 * </ul>
 *
 * @author FantasticName
 */
@RestController
@io.github.fantasticname.mybilibili.annotation.RequestMapping("/post")
public class PostController {

    private static final Logger log = LoggerFactory.getLogger(PostController.class);

    /**
     * 动态最多允许上传的图片数量
     */
    private static final int MAX_IMAGE_COUNT = 9;

    @io.github.fantasticname.mybilibili.annotation.Autowired
    private PostServiceImpl postService;

    @io.github.fantasticname.mybilibili.annotation.Autowired
    private FileServiceImpl fileService;

    public PostController() {
    }

    /**
     * 发布动态（支持多图上传）
     *
     * <p>使用multipart/form-data格式提交，支持纯文字、纯图片、或文字+图片。
     * 图片通过files字段上传，最多9张。图片先通过FileService保存到服务器，
     * 然后将文件名以逗号分隔存入数据库的images字段。</p>
     *
     * @param request HTTP请求（包含multipart数据）
     * @return 动态VO
     */
    @PostMapping("")
    @RequirePermission("post:create")
    public Result<PostVO> create(HttpServletRequest request) {
        User currentUser = UserContext.get();
        log.info("发布动态: userId={}", currentUser.getId());

        // 获取文字内容（非必填）
        String content = request.getParameter("content");

        // 获取上传的图片文件（可选，支持多文件）
        List<String> savedFilenames = new ArrayList<>();
        try {
            // getParts()获取所有multipart部件，筛选名为"images"的文件部件
            for (Part part : request.getParts()) {
                if ("images".equals(part.getName()) && part.getSize() > 0) {
                    // 校验图片数量上限
                    if (savedFilenames.size() >= MAX_IMAGE_COUNT) {
                        return Result.error(40000, "最多上传" + MAX_IMAGE_COUNT + "张图片");
                    }
                    // 读取文件数据
                    String originalFilename = getSubmittedFileName(part);
                    String contentType = part.getContentType();
                    byte[] fileBytes = new byte[(int) part.getSize()];
                    part.getInputStream().read(fileBytes);
                    // 通过FileService上传封面图片（复用封面上传逻辑）
                    String filename = fileService.uploadCover(fileBytes, originalFilename, contentType);
                    savedFilenames.add(filename);
                }
            }
        } catch (IOException | ServletException e) {
            log.error("上传动态图片失败", e);
            return Result.error(50002, "图片上传失败");
        } catch (IllegalArgumentException e) {
            return Result.error(40000, e.getMessage());
        }

        // 将图片文件名用逗号拼接成字符串
        String imagesStr = savedFilenames.isEmpty() ? null : String.join(",", savedFilenames);

        // 幂等性校验：从Header获取Token，防止重复提交
        String idempotentToken = request.getHeader("X-Idempotent-Token");
        if (idempotentToken != null && !idempotentToken.isEmpty()) {
            if (!IdempotentUtil.consumeToken(idempotentToken)) {
                return Result.error(40000, "请勿重复提交");
            }
        }

        return SentinelUtil.executeWithProtection("post-create", () -> {
            PostVO vo = postService.create(currentUser.getId(), content, imagesStr);
            return vo;
        });
    }

    /**
     * 获取用户动态列表（游标分页）
     *
     * <p>瀑布流场景使用游标分页，避免数据重复或遗漏。
     * cursor为上一页最后一条动态的createdAt时间戳，首次请求不传。</p>
     *
     * @param userId 用户ID
     * @param cursor 游标（上一页最后一条的createdAt，首次不传）
     * @param size   每页数量（默认10）
     * @return 动态列表
     */
    @GetMapping("/user/{userId}")
    public Result<Map<String, Object>> listByUser(
            @PathVariable("userId") Long userId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        log.info("获取用户动态列表: userId={}, cursor={}, size={}", userId, cursor, size);

        // 使用游标分页查询
        List<PostVO> list = postService.listByUserCursor(userId, cursor, size);

        // 计算下一页游标（最后一条动态的createdAt）
        String nextCursor = null;
        if (!list.isEmpty()) {
            PostVO last = list.get(list.size() - 1);
            nextCursor = last.getCreatedAt() != null ? last.getCreatedAt().toString() : null;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("nextCursor", nextCursor);
        return Result.success(result);
    }

    /**
     * 获取动态详情
     *
     * <p>展示动态内容、图片、发布者信息、点赞数、评论数。
     * 如果当前用户已登录，还会返回是否已点赞的状态。</p>
     *
     * @param postId 动态ID
     * @return 动态详情VO
     */
    @GetMapping("/{postId}")
    public Result<PostVO> getDetail(@PathVariable("postId") Long postId) {
        User currentUser = UserContext.get();
        Long currentUserId = currentUser != null ? currentUser.getId() : null;
        log.info("获取动态详情: postId={}, currentUserId={}", postId, currentUserId);
        PostVO vo = postService.getDetail(postId, currentUserId);
        return Result.success(vo);
    }

    /**
     * 删除动态
     *
     * @param postId 动态ID
     * @return 操作结果
     */
    @DeleteMapping("/{postId}")
    @RequirePermission("post:delete")
    public Result<Void> delete(@PathVariable("postId") Long postId) {
        User currentUser = UserContext.get();
        log.info("删除动态: postId={}, userId={}", currentUser.getId(), postId);
        postService.delete(postId, currentUser.getId());
        return Result.success(null);
    }

    /**
     * 编辑动态（支持修改文字内容和增删图片）
     *
     * <p>使用multipart/form-data格式提交。
     * existingImages字段：保留的已有图片文件名（逗号分隔，不含/upload/前缀）。
     * images字段：新上传的图片文件（多文件）。
     * 最终图片 = existingImages + 新上传的图片。</p>
     *
     * @param postId  动态ID
     * @param request HTTP请求
     * @return 更新后的动态VO
     */
    @PostMapping("/{postId}/edit")
    @RequirePermission("post:update")
    public Result<PostVO> update(@PathVariable("postId") Long postId, HttpServletRequest request) {
        User currentUser = UserContext.get();
        log.info("编辑动态: postId={}, userId={}", postId, currentUser.getId());

        // 获取修改后的文字内容
        String content = request.getParameter("content");

        // 获取保留的已有图片文件名（前端传来的是完整URL，需要提取文件名）
        String existingImagesParam = request.getParameter("existingImages");
        String existingFilenames = extractFilenames(existingImagesParam);

        // 获取新上传的图片文件
        List<String> newFilenames = new ArrayList<>();
        try {
            for (Part part : request.getParts()) {
                if ("images".equals(part.getName()) && part.getSize() > 0) {
                    if (newFilenames.size() >= MAX_IMAGE_COUNT) {
                        return Result.error(40000, "最多上传" + MAX_IMAGE_COUNT + "张图片");
                    }
                    String originalFilename = getSubmittedFileName(part);
                    String contentType = part.getContentType();
                    byte[] fileBytes = new byte[(int) part.getSize()];
                    part.getInputStream().read(fileBytes);
                    String filename = fileService.uploadCover(fileBytes, originalFilename, contentType);
                    newFilenames.add(filename);
                }
            }
        } catch (IOException | ServletException e) {
            log.error("上传动态图片失败", e);
            return Result.error(50002, "图片上传失败");
        } catch (IllegalArgumentException e) {
            return Result.error(40000, e.getMessage());
        }

        String newImagesStr = newFilenames.isEmpty() ? null : String.join(",", newFilenames);
        PostVO vo = postService.update(postId, currentUser.getId(), content, existingFilenames, newImagesStr);
        return Result.success(vo);
    }

    /**
     * 从逗号分隔的图片URL中提取文件名
     *
     * <p>前端传来的existingImages可能是完整URL如 "/upload/abc.jpg,/upload/def.png"，
     * 也可能只是文件名如 "abc.jpg,def.png"。此方法统一提取文件名部分。</p>
     *
     * @param imagesStr 逗号分隔的图片URL或文件名
     * @return 逗号分隔的文件名
     */
    private String extractFilenames(String imagesStr) {
        if (imagesStr == null || imagesStr.trim().isEmpty()) {
            return null;
        }
        String[] parts = imagesStr.split(",");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            // 如果包含路径分隔符，取最后一段作为文件名
            if (trimmed.contains("/")) {
                trimmed = trimmed.substring(trimmed.lastIndexOf('/') + 1);
            }
            if (result.length() > 0) result.append(",");
            result.append(trimmed);
        }
        return result.length() > 0 ? result.toString() : null;
    }

    /**
     * 从Part中提取原始文件名
     *
     * @param part multipart部件
     * @return 原始文件名
     */
    private String getSubmittedFileName(Part part) {
        String contentDisposition = part.getHeader("content-disposition");
        if (contentDisposition == null) {
            return null;
        }
        for (String token : contentDisposition.split(";")) {
            token = token.trim();
            if (token.startsWith("filename")) {
                String filename = token.substring(token.indexOf('=') + 1).trim();
                return filename.replace("\"", "");
            }
        }
        return null;
    }
}
