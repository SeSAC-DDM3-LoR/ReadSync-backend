package com.ohgiraffers.backendapi.global.common;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Template s3Template;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    public String uploadFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String s3FileName = UUID.randomUUID().toString().substring(0, 10) + originalFilename;

        try (InputStream inputStream = file.getInputStream()) {
            s3Template.upload(bucket, s3FileName, inputStream,
                    ObjectMetadata.builder().contentType(file.getContentType()).build());
            String fileUrl = s3Template.download(bucket, s3FileName).getURL().toString();
            log.info("S3 Upload Success: {}", fileUrl);
            return fileUrl;
        } catch (IOException e) {
            log.error("S3 Upload Failed: {}", e.getMessage());
            throw new RuntimeException("S3 파일 업로드에 실패했습니다.", e);
        }
    }

    public void deleteFile(String fileUrl) {
        try {
            String splitStr = ".com/";
            if (fileUrl.contains(splitStr)) {
                String fileName = fileUrl.substring(fileUrl.lastIndexOf(splitStr) + splitStr.length());
                s3Template.delete(bucket, fileName);
                log.info("S3 Delete Success: {}", fileName);
            }
        } catch (Exception e) {
            log.error("S3 Delete Failed: {}", e.getMessage());
        }
    }
}
