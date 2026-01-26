package com.ohgiraffers.backendapi.global.common;

import com.ohgiraffers.backendapi.global.error.CustomException;
import com.ohgiraffers.backendapi.global.error.ErrorCode;
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

/**
 * AWS S3 파일 서비스
 * <p>
 * 파일 업로드 및 삭제 기능을 제공합니다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Template s3Template;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    /**
     * S3에 파일 업로드
     *
     * @param file 업로드할 멀티파트 파일
     * @return 업로드된 파일의 S3 URL
     * @throws CustomException FILE_UPLOAD_ERROR 업로드 실패 시
     */
    public String uploadFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String s3FileName = UUID.randomUUID().toString().substring(0, 10) + "_" + originalFilename;

        try (InputStream inputStream = file.getInputStream()) {
            s3Template.upload(bucket, s3FileName, inputStream,
                    ObjectMetadata.builder().contentType(file.getContentType()).build());
            String fileUrl = s3Template.download(bucket, s3FileName).getURL().toString();
            log.info("S3 Upload Success: {}", fileUrl);
            return fileUrl;
        } catch (IOException e) {
            log.error("S3 Upload Failed: {}", e.getMessage());
            throw new CustomException(ErrorCode.FILE_UPLOAD_ERROR, e.getMessage());
        }
    }

    /**
     * S3 파일 삭제
     *
     * @param fileUrl 삭제할 파일의 전체 URL
     */
    public void deleteFile(String fileUrl) {
        try {
            String splitStr = ".com/";
            if (fileUrl.contains(splitStr)) {
                String fileName = fileUrl.substring(fileUrl.lastIndexOf(splitStr) + splitStr.length());
                s3Template.deleteObject(bucket, fileName);
                log.info("S3 Delete Success: {}", fileName);
            }
        } catch (Exception e) {
            log.error("S3 Delete Failed: {}", e.getMessage());
            // 삭제 실패는 로그만 남기고 예외를 던지지 않음 (필요 시 FILE_DELETE_ERROR 사용 가능)
        }
    }
}