package com.ohgiraffers.backendapi.domain.chapter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ohgiraffers.backendapi.domain.book.entity.Book;
import com.ohgiraffers.backendapi.domain.book.repository.BookRepository;
import com.ohgiraffers.backendapi.domain.chapter.dto.ChapterRequestDTO;
import com.ohgiraffers.backendapi.domain.chapter.dto.ChapterResponseDTO;
import com.ohgiraffers.backendapi.domain.chapter.dto.ChapterUrlRequestDTO;
import com.ohgiraffers.backendapi.domain.chapter.entity.Chapter;
import com.ohgiraffers.backendapi.domain.chapter.repository.ChapterRepository;
import com.ohgiraffers.backendapi.global.common.S3Service;
import com.ohgiraffers.backendapi.global.error.CustomException;
import com.ohgiraffers.backendapi.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final BookRepository bookRepository;
    private final ObjectMapper objectMapper; // JSON 파싱용

    private final S3Service s3Service;

    @Value("${file.upload-dir}")
    private String uploadDir;

    /* [1] [Local] 챕터 생성 (파일 업로드 + 메타데이터 추출) */

    @Transactional
    public ChapterResponseDTO createChapter(ChapterRequestDTO requestDTO) {
        // 1. 책 존재 여부 확인
        Book book = bookRepository.findById(requestDTO.getBookId())
                .orElseThrow(() -> new CustomException(ErrorCode.BOOK_NOT_FOUND));

        // 2. 파일 저장 (로컬 -> 추후 S3로 변경 포인트)
        String storedFilePath = saveFileToLocal(requestDTO.getFile());

        // 3. 메타데이터 결정 (수동 입력 vs 파일 자동 추출)
        Integer finalSequence = requestDTO.getSequence();
        String finalChapterName = requestDTO.getChapterName();

        // 4. 수동 입력이 하나라도 비어있으면 파일을 읽어서 추출 시도
        if (finalSequence == null || finalChapterName == null || finalChapterName.isEmpty()) {
            try {
                JsonNode rootNode = readFileToJsonNode(storedFilePath);

                // 순서 자동 추출
                if (finalSequence == null && rootNode.has("chapter")) {
                    finalSequence = rootNode.get("chapter").asInt();
                }

                // 이름 자동 추출 (예: 'book_name' + 'chapter')
                if ((finalChapterName == null || finalChapterName.isEmpty()) && rootNode.has("book_name")) {
                    String bookName = rootNode.get("book_name").asText();
                    int chapterNum = rootNode.has("chapter") ? rootNode.get("chapter").asInt() : 0;
                    finalChapterName = bookName + " Chapter " + chapterNum;
                }

            } catch (Exception e) {
                log.warn("메타데이터 추출 실패 (기본값 진행): {}", e.getMessage());
            }
        }

        // 기본값 방어 로직
        if (finalSequence == null)
            finalSequence = 1;
        if (finalChapterName == null)
            finalChapterName = "Untitled Chapter";

        // 4. 엔티티 생성 및 저장 (빌더 패턴 사용 가정)

        Chapter chapter = Chapter.builder()
                .book(book)
                .chapterName(finalChapterName)
                .sequence(finalSequence)
                .bookContentPath(storedFilePath)
                .isEmbedded(false) // 임베딩은 이번 단계 제외
                .build();

        Chapter savedChapter = chapterRepository.save(chapter);

        // 5. 응답 생성 (생성 시에는 내용을 굳이 다 안 내려줘도 되면 content는 null 처리 가능)
        return convertToResponseDTO(savedChapter, false);
    }

    /**
     * [AWS] 챕터 생성 (S3 파일 업로드)
     * 파일을 AWS S3에 업로드하고 챕터 정보를 DB에 저장합니다.
     *
     * @param requestDTO 챕터 생성 요청 데이터 (파일 포함)
     * @return 생성된 챕터 응답 DTO
     */
    @Transactional
    public ChapterResponseDTO createChapterS3(ChapterRequestDTO requestDTO) {
        // 1. 책 존재 여부 확인
        Book book = bookRepository.findById(requestDTO.getBookId())
                .orElseThrow(() -> new CustomException(ErrorCode.BOOK_NOT_FOUND));

        // 2. S3 파일 저장
        String s3Url = s3Service.uploadFile(requestDTO.getFile());

        // 3. 메타데이터 결정 (S3 방식은 파일 내용을 읽지 않고 입력받은 값 위주로 처리)
        // 만약 파일 내용을 읽어야 한다면, MultiPartFile 자체에서 InputStream으로 읽어서 처리 가능
        Integer finalSequence = requestDTO.getSequence() != null ? requestDTO.getSequence() : 1;
        String finalChapterName = requestDTO.getChapterName() != null ? requestDTO.getChapterName()
                : "Untitled Chapter";

        // 4. 엔티티 생성 및 저장
        Chapter chapter = Chapter.builder()
                .book(book)
                .chapterName(finalChapterName)
                .sequence(finalSequence)
                .bookContentPath(s3Url) // S3 URL 저장
                .isEmbedded(false)
                .build();

        Chapter savedChapter = chapterRepository.save(chapter);

        return convertToResponseDTO(savedChapter, false);
    }

    /* [1-3] 챕터 생성 (URL 기반) */
    @Transactional
    public ChapterResponseDTO createChapterByUrl(ChapterUrlRequestDTO requestDTO) {
        // 1. 책 존재 여부 확인
        Book book = bookRepository.findById(requestDTO.getBookId())
                .orElseThrow(() -> new CustomException(ErrorCode.BOOK_NOT_FOUND));

        // 2. URL 유효성 검사 (간단한 null 체크)
        if (requestDTO.getContentUrl() == null || requestDTO.getContentUrl().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "contentUrl은 필수입니다.");
        }

        // 3. 메타데이터 기본값 설정
        Integer finalSequence = requestDTO.getSequence() != null ? requestDTO.getSequence() : 1;
        String finalChapterName = requestDTO.getChapterName() != null ? requestDTO.getChapterName()
                : "Untitled Chapter";
        Integer finalParagraphs = requestDTO.getParagraphs() != null ? requestDTO.getParagraphs() : -1;

        // 4. 엔티티 생성 및 저장
        Chapter chapter = Chapter.builder()
                .book(book)
                .chapterName(finalChapterName)
                .sequence(finalSequence)
                .bookContentPath(requestDTO.getContentUrl()) // URL을 경로로 저장
                .paragraphs(finalParagraphs)
                .build();

        Chapter savedChapter = chapterRepository.save(chapter);

        // 5. 응답 생성
        return convertToResponseDTO(savedChapter, false);
    }

    /* [2] 챕터 조회 (파일 내용을 읽어서 반환) */

    public ChapterResponseDTO getChapter(Long chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAPTER_NOT_FOUND));

        // 내용을 포함하여 DTO 반환
        return convertToResponseDTO(chapter, true);
    }

    /**
     * 챕터 URL 조회
     * 
     * 파일 내용을 읽지 않고 저장된 경로(URL)만 포함하여 반환합니다.
     *
     * @param chapterId 조회할 챕터 ID
     * @return 챕터 응답 DTO (content=null)
     */
    public ChapterResponseDTO getChapterUrl(Long chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAPTER_NOT_FOUND));

        // 내용을 포함하지 않고 DTO 반환 (bookContentPath만 제공)
        return convertToResponseDTO(chapter, false);
    }

    /* [3] [Local] 챕터 수정 (파일 변경 시 is_embedded -> false) */
    @Transactional
    public ChapterResponseDTO updateChapter(Long chapterId, ChapterRequestDTO requestDTO) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAPTER_NOT_FOUND));

        // 1. 파일이 수정된 경우 처리
        if (requestDTO.getFile() != null && !requestDTO.getFile().isEmpty()) {
            // 기존 파일 삭제 (로컬 스토리지 관리)
            deleteLocalFile(chapter.getBookContentPath());

            // 새 파일 저장
            String newFilePath = saveFileToLocal(requestDTO.getFile());

            // 엔티티 업데이트 (경로 변경 + isEmbedded = false 초기화)
            chapter.updateFile(newFilePath);

            // (선택) 여기서 바로 임베딩 재요청 이벤트를 발행할 수도 있음.
        }

        // 2. 메타데이터(이름, 순서) 수정
        chapter.updateMetadata(requestDTO.getChapterName(), requestDTO.getSequence());

        // 3. 변경사항 저장 (JPA Dirty Checking으로 자동 저장되지만 명시적 save도 무관)
        return convertToResponseDTO(chapterRepository.save(chapter), false);
    }

    /**
     * [AWS] 챕터 수정 (S3 파일 업로드)
     * 
     * 기존 파일을 S3에서 삭제하고 새 파일을 업로드하여 챕터 정보를 수정합니다.
     *
     * @param chapterId  수정할 챕터 ID
     * @param requestDTO 수정할 요청 데이터 (파일 포함)
     * @return 수정된 챕터 응답 DTO
     */
    @Transactional
    public ChapterResponseDTO updateChapterS3(Long chapterId, ChapterRequestDTO requestDTO) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAPTER_NOT_FOUND));

        // 1. 파일이 수정된 경우 처리
        if (requestDTO.getFile() != null && !requestDTO.getFile().isEmpty()) {
            // 기존 파일이 S3 파일인지 확인은 어렵지만, 일단 URL 형태라면 삭제 시도
            // (주의: 기존에 로컬 파일이었다면 S3 삭제 로직이 실패할 수도 있으나 예외처리 되어있음)
            s3Service.deleteFile(chapter.getBookContentPath());

            // 새 파일 저장
            String s3Url = s3Service.uploadFile(requestDTO.getFile());

            // 엔티티 업데이트
            chapter.updateFile(s3Url);
        }

        // 2. 메타데이터 수정
        chapter.updateMetadata(requestDTO.getChapterName(), requestDTO.getSequence());

        return convertToResponseDTO(chapterRepository.save(chapter), false);
    }

    /* [3-3] 챕터 수정 (URL 기반) */
    @Transactional
    public ChapterResponseDTO updateChapterByUrl(Long chapterId, ChapterUrlRequestDTO requestDTO) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAPTER_NOT_FOUND));

        // 1. URL이 수정된 경우 처리
        if (requestDTO.getContentUrl() != null && !requestDTO.getContentUrl().isEmpty()) {
            chapter.updateUrl(requestDTO.getContentUrl());
        }

        // 2. 메타데이터(이름, 순서) 수정
        chapter.updateMetadata(requestDTO.getChapterName(), requestDTO.getSequence());

        // 3. 문단 개수 수정
        chapter.updateParagraphs(requestDTO.getParagraphs());

        // 4. 변경사항 저장
        return convertToResponseDTO(chapterRepository.save(chapter), false);
    }

    /* [4] 챕터 삭제 */
    @Transactional
    public void deleteChapter(Long chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAPTER_NOT_FOUND));

        // 1. 로컬 파일 삭제 (경로가 로컬 경로일 때만 동작하도록 되어 있음)
        deleteLocalFile(chapter.getBookContentPath());

        // 2. S3 파일 삭제 시도 (경로가 S3 URL일 때 동작)
        s3Service.deleteFile(chapter.getBookContentPath());

        // 3. DB 데이터 삭제
        chapterRepository.delete(chapter);
    }

    /* [5] [관리자] 모든 챕터 조회 */
    public java.util.List<ChapterResponseDTO> getAllChapters() {
        return chapterRepository.findAll().stream()
                .map(chapter -> convertToResponseDTO(chapter, false))
                .collect(java.util.stream.Collectors.toList());
    }

    /* [6] [공통] 책 ID로 챕터 목록 조회 */
    public java.util.List<ChapterResponseDTO> getChaptersByBookId(Long bookId) {
        return chapterRepository.findByBook_BookIdOrderBySequenceAsc(bookId).stream()
                .map(chapter -> convertToResponseDTO(chapter, false))
                .collect(java.util.stream.Collectors.toList());
    }

    /* ------------- 내부 헬퍼 메서드 ------------- */

    // 파일 로컬 저장 로직 (추후 AWS S3 Service로 대체될 부분)
    private String saveFileToLocal(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }

        try {
            // 1. 상대 경로(./uploads...)를 절대 경로로 변환하여 명확하게 처리
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            File directory = uploadPath.toFile();

            // 2. 디렉토리가 없으면 생성 (프로젝트 폴더 내에 uploads/chapters 폴더가 생김)
            if (!directory.exists()) {
                boolean created = directory.mkdirs();
                if (created) {
                    log.info("디렉토리가 생성되었습니다: {}", directory.getAbsolutePath());
                }
            }

            // 3. 유니크한 파일명 생성
            String originalFilename = file.getOriginalFilename();
            String storeFileName = UUID.randomUUID() + "_" + originalFilename;

            // 4. 저장 경로 결합
            Path targetPath = uploadPath.resolve(storeFileName);

            // 5. 파일 저장
            file.transferTo(targetPath.toFile());

            log.info("파일이 저장되었습니다: {}", targetPath);

            // DB에 저장할 경로 반환 (나중에 읽을 때 사용)
            return targetPath.toString();

        } catch (IOException e) {
            throw new RuntimeException("파일 저장 중 오류가 발생했습니다.", e);
        }
    }

    // 저장된 파일 경로에서 JSON Node 읽기 (로컬 파일 또는 외부 URL)
    private JsonNode readFileToJsonNode(String filePath) {
        // [Optimized] AWS S3 URL인 경우 백엔드에서 다운로드하지 않고 건너뜀 (프론트엔드 직접 다운로드 유도)
        if (filePath != null && filePath.contains("amazonaws.com")) {
            log.info("S3 URL 접근 감지 - 백엔드 다운로드 스킵: {}", filePath);
            return null;
        }

        // HTTP URL인 경우 (Google Drive 등)
        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            try {
                log.info("외부 URL에서 콘텐츠 다운로드 시도: {}", filePath);

                // RestTemplate을 UTF-8 인코딩으로 설정
                org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

                // UTF-8 인코딩을 위한 StringHttpMessageConverter 설정
                java.util.List<org.springframework.http.converter.HttpMessageConverter<?>> messageConverters = new java.util.ArrayList<>();
                org.springframework.http.converter.StringHttpMessageConverter stringConverter = new org.springframework.http.converter.StringHttpMessageConverter(
                        java.nio.charset.StandardCharsets.UTF_8);
                stringConverter.setWriteAcceptCharset(false); // Accept-Charset 헤더 비활성화
                messageConverters.add(stringConverter);
                messageConverters
                        .add(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter());
                restTemplate.setMessageConverters(messageConverters);

                // Google Drive URL 변환 처리
                String downloadUrl = filePath;
                if (filePath.contains("drive.google.com")) {
                    downloadUrl = convertGoogleDriveUrl(filePath);
                    log.info("Google Drive URL 변환: {} -> {}", filePath, downloadUrl);
                }

                // UTF-8로 인코딩된 응답 받기
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.setAccept(java.util.Arrays.asList(org.springframework.http.MediaType.APPLICATION_JSON));
                headers.set("Accept-Charset", "UTF-8");

                org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
                org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                        downloadUrl,
                        org.springframework.http.HttpMethod.GET,
                        entity,
                        String.class);

                String jsonContent = response.getBody();
                if (jsonContent == null || jsonContent.isEmpty()) {
                    throw new CustomException(ErrorCode.FILE_NOT_FOUND, "URL에서 콘텐츠를 가져올 수 없습니다.");
                }

                log.info("콘텐츠 다운로드 성공: {} bytes", jsonContent.length());
                return objectMapper.readTree(jsonContent);
            } catch (IOException e) {
                log.error("URL 콘텐츠 파싱 실패: {}", filePath, e);
                throw new CustomException(ErrorCode.FILE_READ_ERROR, "URL 콘텐츠 파싱 오류: " + e.getMessage());
            } catch (Exception e) {
                log.error("URL 콘텐츠 다운로드 실패: {}", filePath, e);
                throw new CustomException(ErrorCode.FILE_NOT_FOUND, "URL에서 파일을 다운로드할 수 없습니다: " + e.getMessage());
            }
        }

        // 로컬 파일인 경우
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                throw new CustomException(ErrorCode.FILE_NOT_FOUND, "Path: " + filePath);
            }
            return objectMapper.readTree(path.toFile());
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_READ_ERROR, e.getMessage());
        }
    }

    /**
     * Google Drive 공유 URL을 직접 다운로드 URL로 변환
     * 
     * @param url Google Drive 공유 URL
     * @return 변환된 다운로드 URL
     */
    private String convertGoogleDriveUrl(String url) {
        // 형식: https://drive.google.com/file/d/{FILE_ID}/view?usp=sharing
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("/d/([a-zA-Z0-9_-]+)");
        java.util.regex.Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            String fileId = matcher.group(1);
            return "https://drive.google.com/uc?export=download&id=" + fileId;
        }

        // 형식: https://drive.google.com/open?id={FILE_ID}
        pattern = java.util.regex.Pattern.compile("[?&]id=([a-zA-Z0-9_-]+)");
        matcher = pattern.matcher(url);
        if (matcher.find()) {
            String fileId = matcher.group(1);
            return "https://drive.google.com/uc?export=download&id=" + fileId;
        }

        return url; // 변환 불가시 원본 반환
    }

    // 엔티티 -> Response DTO 변환
    private ChapterResponseDTO convertToResponseDTO(Chapter chapter, boolean includeContent) {
        Object content = null;
        if (includeContent) {
            try {
                // 저장된 경로에서 파일을 읽어 Object(Map/List) 형태로 변환
                content = readFileToJsonNode(chapter.getBookContentPath());
            } catch (CustomException e) {
                log.error("컨텐츠 로드 실패: {}", e.getMessage());
                content = "Error: 내용을 불러올 수 없습니다. (URL 기반이거나 파일 없음)";
            }
        }
        return ChapterResponseDTO.builder()
                .chapterId(chapter.getChapterId())
                .bookId(chapter.getBook().getBookId())
                .chapterName(chapter.getChapterName())
                .sequence(chapter.getSequence())
                // [Optimized] S3 URL인 경우 Presigned URL로 변환하여 전달 (보안 접근 허용)
                .bookContentPath(s3Service.getPresignedUrl(chapter.getBookContentPath()))
                .bookContent(content)
                .paragraphs(chapter.getParagraphs())
                .build();
    }

    // LocalFile 삭제 메서드
    private void deleteLocalFile(String filePath) {
        if (filePath != null && !filePath.isEmpty() && !filePath.startsWith("http")) { // URL은 로컬 파일이 아님
            try {
                Files.deleteIfExists(Paths.get(filePath));
            } catch (IOException e) {
                // 파일 삭제 실패가 DB 트랜잭션을 롤백시키지 않도록 로그만 남김 (Orphan file 정책)
                log.warn("파일 삭제 실패: {}", filePath, e);
            }
        }
    }
}