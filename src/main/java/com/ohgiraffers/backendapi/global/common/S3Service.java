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

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final S3Client s3Client; // AWS SDK v2 Client 주입
    private final software.amazon.awssdk.services.s3.presigner.S3Presigner s3Presigner; // Presigner 주입

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    /**
     * S3 Presigned URL 생성 (비공개 객체 접근용)
     *
     * @param fileUrl 원본 S3 URL (전체 경로)
     * @return 10분간 유효한 서명된 URL
     */
    public String getPresignedUrl(String fileUrl) {
        try {
            String splitStr = ".com/";
            if (!fileUrl.contains(splitStr)) {
                return fileUrl; // S3 URL이 아니면 원본 반환
            }
            String key = fileUrl.substring(fileUrl.lastIndexOf(splitStr) + splitStr.length());

            // [Fix] URL 디코딩 추가 (한글 파일명의 경우 %EC... 형태로 들어오면 S3에서 찾지 못함)
            key = java.net.URLDecoder.decode(key, java.nio.charset.StandardCharsets.UTF_8);

            software.amazon.awssdk.services.s3.model.GetObjectRequest getObjectRequest = software.amazon.awssdk.services.s3.model.GetObjectRequest
                    .builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest presignRequest = software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
                    .builder()
                    .signatureDuration(java.time.Duration.ofMinutes(10)) // 10분 유효
                    .getObjectRequest(getObjectRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();

        } catch (Exception e) {
            log.error("Presigned URL 생성 실패: {}", e.getMessage());
            return fileUrl; // 실패 시 원본 반환 (클라이언트에서 403 받도록)
        }
    }

    /**
     * S3에 파일 업로드
     *
     * S3에 파일 업로드
     *
     * @param file       업로드할 멀티파트 파일
     * @param folderName 저장할 폴더 이름 (예: "book")
     * @return 업로드된 파일의 S3 URL
     * @throws CustomException FILE_UPLOAD_ERROR 업로드 실패 시
     */
    public String uploadFile(MultipartFile file, String folderName) {
        String originalFilename = file.getOriginalFilename();
        String s3FileName = folderName + "/" + UUID.randomUUID().toString().substring(0, 10) + "_" + originalFilename;

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

    /**
     * S3에서 파일 내용을 문자열로 다운로드 (인증된 접근)
     *
     * @param fileUrl S3 파일 URL
     * @return 파일 내용 (문자열)
     */
    public String downloadFileAsString(String fileUrl) {
        try {
            String splitStr = ".com/";
            if (!fileUrl.contains(splitStr)) {
                throw new CustomException(ErrorCode.FILE_NOT_FOUND, "유효하지 않은 S3 URL: " + fileUrl);
            }
            String key = fileUrl.substring(fileUrl.lastIndexOf(splitStr) + splitStr.length());

            // URL 디코딩 (한글 파일명 처리)
            key = java.net.URLDecoder.decode(key, java.nio.charset.StandardCharsets.UTF_8);
            log.info("S3 파일 다운로드 시도: bucket={}, key={}", bucket, key);

            // AWS SDK를 사용하여 파일 다운로드
            software.amazon.awssdk.services.s3.model.GetObjectRequest getObjectRequest = software.amazon.awssdk.services.s3.model.GetObjectRequest
                    .builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            software.amazon.awssdk.core.ResponseInputStream<software.amazon.awssdk.services.s3.model.GetObjectResponse> s3Object = s3Client
                    .getObject(getObjectRequest);

            String content = new String(s3Object.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            log.info("S3 파일 다운로드 성공: {} bytes", content.length());
            return content;

        } catch (Exception e) {
            log.error("S3 파일 다운로드 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.FILE_NOT_FOUND, "S3 파일을 다운로드할 수 없습니다: " + e.getMessage());
        }
    }

    /**
     * 현재 계정의 모든 S3 버킷 목록 조회
     *
     * @return 버킷 이름 목록
     */
    public List<String> listBuckets() {
        return s3Client.listBuckets().buckets().stream()
                .map(Bucket::name)
                .collect(Collectors.toList());
    }

    /**
     * 설정된 버킷 내의 모든 파일 목록 조회
     *
     * @return 파일 이름(Key) 목록
     */
    public List<String> listFiles() {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .build();

        ListObjectsV2Response result = s3Client.listObjectsV2(request);
        return result.contents().stream()
                .map(S3Object::key)
                .collect(Collectors.toList());
    }
}