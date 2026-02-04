package com.ohgiraffers.backendapi.global.service;

import com.ohgiraffers.backendapi.global.error.CustomException;
import com.ohgiraffers.backendapi.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

/**
 * S3 이미지 업로드 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private final S3Client s3Client;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    /**
     * 채팅 이미지를 S3에 업로드
     * 
     * @param file   업로드할 이미지 파일
     * @param roomId 채팅방 ID
     * @return S3 공개 URL
     */
    public String uploadChatImage(MultipartFile file, Long roomId) {
        validateImage(file);

        String fileName = generateFileName(file, roomId);
        String s3Key = "chat-images/" + roomId + "/" + fileName;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            // S3 URL 생성 (공개 URL 형식)
            String s3Url = String.format("https://%s.s3.ap-northeast-2.amazonaws.com/%s", bucketName, s3Key);
            log.info("Image uploaded to S3: {}", s3Url);
            return s3Url;

        } catch (IOException e) {
            log.error("Failed to upload image to S3", e);
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    /**
     * 이미지 파일 유효성 검증
     */
    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_FILE);
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }

        // 파일 크기 제한 (10MB)
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new CustomException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
    }

    /**
     * 고유한 파일명 생성
     */
    private String generateFileName(MultipartFile file, Long roomId) {
        String originalFilename = file.getOriginalFilename();
        String extension = "";

        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        return UUID.randomUUID().toString() + extension;
    }
}
