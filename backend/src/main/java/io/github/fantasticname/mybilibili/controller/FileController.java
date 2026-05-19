package io.github.fantasticname.mybilibili.controller;

import io.github.fantasticname.mybilibili.annotation.Autowired;
import io.github.fantasticname.mybilibili.annotation.PostMapping;
import io.github.fantasticname.mybilibili.annotation.RequestMapping;
import io.github.fantasticname.mybilibili.annotation.RequirePermission;
import io.github.fantasticname.mybilibili.annotation.RestController;
import io.github.fantasticname.mybilibili.common.Result;
import io.github.fantasticname.mybilibili.service.FileService;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import java.io.IOException;

/**
 * 文件上传控制器
 *
 * @author FantasticName
 */
@RestController
@RequestMapping("/file")
public class FileController {

    @Autowired
    private FileService fileService;

    /**
     * 上传封面图片
     */
    @PostMapping("/upload/cover")
    @RequirePermission("file:upload")
    public Result<String> uploadCover(HttpServletRequest request) {
        try {
            Part filePart = request.getPart("file");
            if (filePart == null || filePart.getSize() == 0) {
                return Result.error(400, "请选择封面图片");
            }
            String originalFilename = getSubmittedFileName(filePart);
            String contentType = filePart.getContentType();
            byte[] fileBytes = new byte[(int) filePart.getSize()];
            filePart.getInputStream().read(fileBytes);
            String filename = fileService.uploadCover(fileBytes, originalFilename, contentType);
            return Result.success(filename);
        } catch (IOException | ServletException e) {
            return Result.error(500, "封面上传失败: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 上传视频文件
     */
    @PostMapping("/upload/video")
    @RequirePermission("file:upload")
    public Result<String> uploadVideo(HttpServletRequest request) {
        try {
            Part filePart = request.getPart("file");
            if (filePart == null || filePart.getSize() == 0) {
                return Result.error(400, "请选择视频文件");
            }
            String originalFilename = getSubmittedFileName(filePart);
            String contentType = filePart.getContentType();
            byte[] fileBytes = new byte[(int) filePart.getSize()];
            filePart.getInputStream().read(fileBytes);
            String filename = fileService.uploadVideo(fileBytes, originalFilename, contentType);
            return Result.success(filename);
        } catch (IOException | ServletException e) {
            return Result.error(500, "视频上传失败: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

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