package io.github.fantasticname.mybilibili.service;

import io.github.fantasticname.mybilibili.util.FileUtil;
import io.github.fantasticname.mybilibili.annotation.Service;

/**
 * 文件上传服务实现
 *
 * @author FantasticName
 */
@Service
public class FileServiceImpl implements FileService {

    private static final long MAX_VIDEO_SIZE = 500 * 1024 * 1024;
    private static final long MAX_COVER_SIZE = 5 * 1024 * 1024;

    @Override
    public String uploadVideo(byte[] fileBytes, String originalFilename, String contentType) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("视频文件不能为空");
        }
        if (fileBytes.length > MAX_VIDEO_SIZE) {
            throw new IllegalArgumentException("视频文件大小不能超过500MB");
        }
        String ext = getExtension(originalFilename, contentType, "video");
        if (!isValidVideoExtension(ext)) {
            throw new IllegalArgumentException("不支持的视频格式，支持: mp4, avi, mov, mkv, flv, wmv, webm");
        }
        return FileUtil.saveFile(fileBytes, ext);
    }

    @Override
    public String uploadCover(byte[] fileBytes, String originalFilename, String contentType) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("封面图片不能为空");
        }
        if (fileBytes.length > MAX_COVER_SIZE) {
            throw new IllegalArgumentException("封面图片大小不能超过5MB");
        }
        String ext = getExtension(originalFilename, contentType, "image");
        if (!isValidImageExtension(ext)) {
            throw new IllegalArgumentException("不支持的图片格式，支持: jpg, jpeg, png, gif, webp, avif");
        }
        return FileUtil.saveFile(fileBytes, ext);
    }

    private String getExtension(String filename, String contentType, String fileType) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        }
        if (contentType != null) {
            String ext = contentTypeToExtension(contentType);
            if (ext != null) {
                return ext;
            }
        }
        return fileType.equals("video") ? "mp4" : "jpg";
    }

    private String contentTypeToExtension(String contentType) {
        switch (contentType.toLowerCase()) {
            case "image/jpeg":
                return "jpg";
            case "image/png":
                return "png";
            case "image/gif":
                return "gif";
            case "image/webp":
                return "webp";
            case "image/avif":
                return "avif";
            case "video/mp4":
                return "mp4";
            case "video/webm":
                return "webm";
            case "video/x-msvideo":
                return "avi";
            case "video/quicktime":
                return "mov";
            case "video/x-matroska":
                return "mkv";
            case "video/x-flv":
                return "flv";
            case "video/x-ms-wmv":
                return "wmv";
            default:
                if (contentType.startsWith("image/")) {
                    return contentType.substring(6).toLowerCase();
                }
                if (contentType.startsWith("video/")) {
                    return contentType.substring(6).toLowerCase();
                }
                return null;
        }
    }

    private boolean isValidVideoExtension(String ext) {
        return "mp4".equals(ext) || "avi".equals(ext) || "mov".equals(ext)
                || "mkv".equals(ext) || "flv".equals(ext) || "wmv".equals(ext)
                || "webm".equals(ext);
    }

    private boolean isValidImageExtension(String ext) {
        return "jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext)
                || "gif".equals(ext) || "webp".equals(ext) || "avif".equals(ext);
    }
}