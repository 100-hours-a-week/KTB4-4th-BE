package kr.ktb.zura.needu.user.service;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.user.dto.request.ConsentAgreementRequest;
import kr.ktb.zura.needu.user.dto.request.UpdateConsentRequest;
import kr.ktb.zura.needu.user.dto.response.ConsentResponse;
import kr.ktb.zura.needu.user.dto.response.ConsentsResponse;
import kr.ktb.zura.needu.user.entity.UserConsent;
import kr.ktb.zura.needu.user.repository.UserConsentRepository;
import kr.ktb.zura.needu.user.type.ConsentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsentService {

    private final UserConsentRepository userConsentRepository;
    private final UserService userService;

    public ConsentsResponse findConsents(Long userId) {
        userService.validateAuthenticatableUser(userId);
        Map<ConsentType, UserConsent> latestConsents = findLatestConsents(userId);
        return new ConsentsResponse(Arrays.stream(ConsentType.values())
                .map(type -> ConsentResponse.from(type, latestConsents.get(type)))
                .toList());
    }

    @Transactional
    public void updateConsents(Long userId, UpdateConsentRequest request) {
        userService.validateAuthenticatableUser(userId);
        Map<ConsentType, Boolean> agreements = toAgreements(request.consents());
        validateRequiredAgreed(agreements);

        Map<ConsentType, UserConsent> latestConsents = findLatestConsents(userId);
        List<UserConsent> changedConsents = agreements.entrySet().stream()
                .filter(agreement -> isChanged(latestConsents.get(agreement.getKey()), agreement.getValue()))
                .map(agreement -> UserConsent.create(userId, agreement.getKey(), agreement.getValue()))
                .toList();
        userConsentRepository.saveAll(changedConsents);
    }

    public boolean hasAgreedToRequiredConsents(Long userId) {
        Map<ConsentType, UserConsent> latestConsents = findLatestConsents(userId);
        return Arrays.stream(ConsentType.values())
                .filter(ConsentType::isRequired)
                .allMatch(type -> isAgreedToCurrentVersion(latestConsents.get(type)));
    }

    public boolean hasAgreedToAiConversation(Long userId) {
        return userConsentRepository
                .findFirstByUserIdAndConsentTypeOrderByIdDesc(userId, ConsentType.AI_CONVERSATION)
                .map(UserConsent::isAgreedToCurrentVersion)
                .orElse(false);
    }

    private Map<ConsentType, UserConsent> findLatestConsents(Long userId) {
        return userConsentRepository.findAllLatestByUserId(userId).stream()
                .collect(Collectors.toMap(UserConsent::getConsentType, Function.identity()));
    }

    private Map<ConsentType, Boolean> toAgreements(List<ConsentAgreementRequest> consents) {
        Map<ConsentType, Boolean> agreements = new EnumMap<>(ConsentType.class);
        for (ConsentAgreementRequest consent : consents) {
            if (!ConsentType.existsById(consent.id())) {
                throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
            }

            if (agreements.putIfAbsent(ConsentType.fromId(consent.id()), consent.agreed()) != null) {
                throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
            }
        }
        return agreements;
    }

    private void validateRequiredAgreed(Map<ConsentType, Boolean> agreements) {
        boolean allRequiredAgreed = Arrays.stream(ConsentType.values())
                .filter(ConsentType::isRequired)
                .allMatch(type -> Boolean.TRUE.equals(agreements.get(type)));
        if (!allRequiredAgreed) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }
    }

    private boolean isChanged(UserConsent latestConsent, boolean agreed) {
        return latestConsent == null || !latestConsent.isSameChoiceInCurrentVersion(agreed);
    }

    private boolean isAgreedToCurrentVersion(UserConsent latestConsent) {
        return latestConsent != null && latestConsent.isAgreedToCurrentVersion();
    }
}
