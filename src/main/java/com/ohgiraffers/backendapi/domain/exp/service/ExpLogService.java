package com.ohgiraffers.backendapi.domain.exp.service;

import com.ohgiraffers.backendapi.domain.exp.dto.ExpLogRequestDTO;
import com.ohgiraffers.backendapi.domain.exp.dto.ExpLogResponseDTO;
import com.ohgiraffers.backendapi.domain.exp.entity.ExpLog;
import com.ohgiraffers.backendapi.domain.exp.entity.ExpRule;
import com.ohgiraffers.backendapi.domain.exp.enums.ActivityType;
import com.ohgiraffers.backendapi.domain.exp.repository.ExpLogRepository;
import com.ohgiraffers.backendapi.domain.exp.repository.ExpRuleRepository;
import com.ohgiraffers.backendapi.domain.user.entity.User;
import com.ohgiraffers.backendapi.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpLogService {

    private final ExpLogRepository expLogRepository;
    private final ExpRuleRepository expRuleRepository;
    private final UserRepository userRepository;

    private ExpRule findAppropriateRule(ActivityType type, Long categoryId) {
        if (categoryId != null) {
            return expRuleRepository.findByActivityTypeAndCategory_CategoryId(type, categoryId)
                    .orElseGet(() -> expRuleRepository.findByActivityTypeAndCategoryIsNull(type)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    String.format("[%s] 활동에 대한 일반 규칙이 존재하지 않습니다.", type))));
        }

        return expRuleRepository.findByActivityTypeAndCategoryIsNull(type)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("[%s] 활동에 대한 규칙이 존재하지 않습니다.", type)));
    }

    @Transactional
    public ExpLogResponseDTO giveExperience(ExpLogRequestDTO requestDTO) {
        ExpRule rule = findAppropriateRule(requestDTO.getActivityType(), requestDTO.getCategoryId());

        if (expLogRepository.existsByUser_IdAndExpRule_ExpRuleIdAndReferenceId(
                requestDTO.getUserId(), rule.getExpRuleId(), requestDTO.getReferenceId())) {
            throw new IllegalArgumentException("이미 처리된 활동입니다.");
        }

        User user = userRepository.findById(requestDTO.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        ExpLog expLog = requestDTO.toEntity(user, rule);
        ExpLog savedLog = expLogRepository.save(expLog);

        user.getUserInformation().addExperience(rule.getExp());

        return ExpLogResponseDTO.from(savedLog);
    }

    // 유저별 경험치 로그 페이징 조회
    public Page<ExpLogResponseDTO> findAllByUser(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("존재하지 않는 유저입니다.");
        }

        return expLogRepository.findAllByUser_Id(userId, pageable)
                .map(ExpLogResponseDTO::from);
    }
}