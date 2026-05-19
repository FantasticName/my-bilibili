package io.github.fantasticname.mybilibili.service;

/**
 * 文件上传服务接口
 *
 * @author FantasticName
 */
public interface FileService {

    /**
     * 上传视频文件
     *
     * @param fileBytes 文件字节数组
     * @param originalFilename 原始文件名
     * @param contentType 文件MIME类型
     * @return 保存后的文件名
     */
    String uploadVideo(byte[] fileBytes, String originalFilename, String contentType);

    /**
     * 上传封面图片
     *
     * @param fileBytes 文件字节数组
     * @param originalFilename 原始文件名
     * @param contentType 文件MIME类型
     * @return 保存后的文件名
     */
    String uploadCover(byte[] fileBytes, String originalFilename, String contentType);
}