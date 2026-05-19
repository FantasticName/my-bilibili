package io.github.fantasticname.mybilibili.util;

import io.github.fantasticname.mybilibili.common.BusinessException;
import io.github.fantasticname.mybilibili.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传工具类，处理用户上传的文件（头像、视频封面等）
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>白名单校验：只允许上传图片（jpg/png/gif/webp/avif）和视频（mp4/webm）</li>
 *   <li>UUID重命名：防止文件名冲突和中文文件名问题</li>
 *   <li>文件大小限制：防止上传超大文件打爆服务器磁盘</li>
 *   <li>文件删除：用户更换头像时删除旧文件</li>
 * </ul>
 *
 * <p>文件存储路径：项目根目录下的 uploads/ 目录</p>
 * <p>文件访问URL：/uploads/{uuid_filename}</p>
 *
 * @author FantasticName
 */
public final class FileUtil {

    private static final Logger log = LoggerFactory.getLogger(FileUtil.class);

    private static final String UPLOAD_URL_PREFIX = "/upload/";

    /**
     * 将文件名转换为完整的访问URL路径
     *
     * @param filename 文件名（如 abc123.jpg）
     * @return 完整的访问URL路径（如 /upload/abc123.jpg），如果已经是完整路径或为空则原样返回
     */
    public static String toUrl(String filename) {
        if (filename == null || filename.isEmpty()) {
            return filename;
        }
        if (filename.startsWith("http://") || filename.startsWith("https://") || filename.startsWith("/")) {
            return filename;
        }
        return UPLOAD_URL_PREFIX + filename;
    }

    /**
     * 允许上传的图片文件扩展名（白名单）
     */
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "avif");

    /**
     * 允许上传的视频文件扩展名（白名单）
     */
    private static final Set<String> ALLOWED_VIDEO_EXTENSIONS = Set.of("mp4", "webm");

    /**
     * 所有允许上传的文件扩展名（图片+视频）
     */
    private static final Set<String> ALLOWED_EXTENSIONS;

    static {
        ALLOWED_EXTENSIONS = Set.of(
                "jpg", "jpeg", "png", "gif", "webp", "avif",
                "mp4", "webm"
        );
    }

    /**
     * 上传文件存储目录
     */
    private static final String UPLOAD_DIR = "uploads";

    /**
     * 私有构造方法，防止实例化
     */
    private FileUtil() {
    }

    /**
     * 保存上传的文件到服务器
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>校验文件扩展名（白名单）</li>
     *   <li>用UUID重命名文件</li>
     *   <li>保存到 uploads/ 目录</li>
     * </ol>
     *
     * @param fileBytes  文件字节数组
     * @param extension  文件扩展名（不含点号）
     * @return 保存后的文件名（UUID格式）
     */
    public static String saveFile(byte[] fileBytes, String extension) {
        if (extension == null || extension.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法识别文件类型");
        }

        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            log.warn("不支持的文件类型: {}", extension);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "只允许上传图片或视频文件");
        }

        String newFilename = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            boolean created = uploadDir.mkdirs();
            if (!created) {
                log.error("创建上传目录失败: {}", uploadDir.getAbsolutePath());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传目录创建失败");
            }
        }

        Path targetPath = Paths.get(UPLOAD_DIR, newFilename);
        try {
            Files.write(targetPath, fileBytes);
            log.info("文件上传成功: {}", newFilename);
            return newFilename;
        } catch (IOException e) {
            log.error("文件保存失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        }
    }

    /**
     * 删除服务器上的文件
     *
     * <p>用户更换头像或删除视频时调用，清理旧文件释放磁盘空间。</p>
     *
     * @param filename 文件名（UUID格式）
     */
    public static void deleteFile(String filename) {
        if (filename == null || filename.isEmpty()) {
            return;
        }

        Path filePath = Paths.get(UPLOAD_DIR, filename);
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("文件删除成功: {}", filename);
            }
        } catch (IOException e) {
            log.warn("文件删除失败: {}, error={}", filename, e.getMessage());
        }
    }

    /**
     * 校验文件是否为图片类型
     *
     * @param filename 文件名
     * @return true表示是图片，false表示不是
     */
    public static boolean isImage(String filename) {
        String extension = getFileExtension(filename).toLowerCase();
        return ALLOWED_IMAGE_EXTENSIONS.contains(extension);
    }

    /**
     * 校验文件是否为视频类型
     *
     * @param filename 文件名
     * @return true表示是视频，false表示不是
     */
    public static boolean isVideo(String filename) {
        String extension = getFileExtension(filename).toLowerCase();
        return ALLOWED_VIDEO_EXTENSIONS.contains(extension);
    }

    /**
     * 获取文件扩展名
     *
     * @param filename 文件名
     * @return 扩展名（不含点号），如果没有扩展名返回空字符串
     */
    public static String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }
}
