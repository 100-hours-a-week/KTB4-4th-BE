package kr.ktb.zura.needu.user.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.user.dto.request.ConsentAgreementRequest;
import kr.ktb.zura.needu.user.dto.request.UpdateConsentRequest;
import kr.ktb.zura.needu.user.dto.response.ConsentResponse;
import kr.ktb.zura.needu.user.dto.response.ConsentsResponse;
import kr.ktb.zura.needu.user.entity.UserConsent;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.repository.UserConsentRepository;
import kr.ktb.zura.needu.user.type.ConsentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsentServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDateTime AGREED_AT = LocalDateTime.of(2026, 10, 1, 9, 0);

    private UserConsentRepository userConsentRepository;
    private UserService userService;
    private ConsentService consentService;

    @BeforeEach
    void setUp() {
        userConsentRepository = mock(UserConsentRepository.class);
        userService = mock(UserService.class);
        consentService = new ConsentService(userConsentRepository, userService);
    }

    @Test
    void noConsentHistory_findConsents_returnsAllItemsNotAgreed() {
        when(userConsentRepository.findAllLatestByUserId(USER_ID)).thenReturn(List.of());

        ConsentsResponse response = consentService.findConsents(USER_ID);

        assertThat(response.consents())
                .extracting(ConsentResponse::id)
                .containsExactly(1L, 2L, 3L, 4L);
        assertThat(response.consents()).allSatisfy(consent -> {
            assertThat(consent.agreed()).isFalse();
            assertThat(consent.agreedAt()).isNull();
        });
    }

    @Test
    void agreedToCurrentVersion_findConsents_returnsAgreedWithAgreedAt() {
        when(userConsentRepository.findAllLatestByUserId(USER_ID))
                .thenReturn(List.of(consent(ConsentType.PRIVACY_COLLECTION, true)));

        ConsentResponse privacy = consentService.findConsents(USER_ID).consents().getFirst();

        assertThat(privacy.title()).isEqualTo(ConsentType.PRIVACY_COLLECTION.getTitle());
        assertThat(privacy.required()).isTrue();
        assertThat(privacy.version()).isEqualTo(ConsentType.PRIVACY_COLLECTION.getVersion());
        assertThat(privacy.agreed()).isTrue();
        assertThat(privacy.agreedAt()).isEqualTo(AGREED_AT);
    }

    @Test
    void agreedToOldVersion_findConsents_returnsNotAgreed() {
        when(userConsentRepository.findAllLatestByUserId(USER_ID))
                .thenReturn(List.of(oldVersionConsent(ConsentType.PRIVACY_COLLECTION)));

        ConsentResponse privacy = consentService.findConsents(USER_ID).consents().getFirst();

        assertThat(privacy.agreed()).isFalse();
        assertThat(privacy.agreedAt()).isNull();
    }

    @Test
    void blockedUser_findConsents_throwsUserBlocked() {
        doThrow(new BusinessException(UserErrorCode.USER_BLOCKED))
                .when(userService).validateAuthenticatableUser(USER_ID);

        assertThatThrownBy(() -> consentService.findConsents(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_BLOCKED);
    }

    @Test
    void noConsentHistory_updateConsents_savesAllItemsWithCurrentVersion() {
        when(userConsentRepository.findAllLatestByUserId(USER_ID)).thenReturn(List.of());

        consentService.updateConsents(USER_ID, allAgreedRequest());

        assertThat(savedConsents())
                .extracting(UserConsent::getConsentType, UserConsent::isAgreed, UserConsent::getVersion)
                .containsExactlyInAnyOrder(
                        tuple(ConsentType.PRIVACY_COLLECTION, true, "1.0"),
                        tuple(ConsentType.AI_CONVERSATION, true, "1.0"),
                        tuple(ConsentType.FRIEND_TASTE_SHARING, true, "1.0"),
                        tuple(ConsentType.PRODUCT_ACTIVITY, true, "1.0"));
    }

    @Test
    void sameChoiceInCurrentVersion_updateConsents_savesOnlyChangedItems() {
        when(userConsentRepository.findAllLatestByUserId(USER_ID)).thenReturn(List.of(
                consent(ConsentType.PRIVACY_COLLECTION, true),
                consent(ConsentType.AI_CONVERSATION, true),
                consent(ConsentType.FRIEND_TASTE_SHARING, true),
                oldVersionConsent(ConsentType.PRODUCT_ACTIVITY)));

        consentService.updateConsents(USER_ID, allAgreedRequest());

        assertThat(savedConsents())
                .extracting(UserConsent::getConsentType)
                .containsExactly(ConsentType.PRODUCT_ACTIVITY);
    }

    @Test
    void requiredItemDisagreed_updateConsents_throwsInvalidInput() {
        UpdateConsentRequest request = new UpdateConsentRequest(List.of(
                new ConsentAgreementRequest(1L, true),
                new ConsentAgreementRequest(2L, false),
                new ConsentAgreementRequest(3L, true),
                new ConsentAgreementRequest(4L, true)));

        assertInvalidInput(request);
    }

    @Test
    void requiredItemMissing_updateConsents_throwsInvalidInput() {
        UpdateConsentRequest request = new UpdateConsentRequest(List.of(
                new ConsentAgreementRequest(1L, true),
                new ConsentAgreementRequest(2L, true),
                new ConsentAgreementRequest(3L, true)));

        assertInvalidInput(request);
    }

    @Test
    void unknownConsentId_updateConsents_throwsInvalidInput() {
        UpdateConsentRequest request = new UpdateConsentRequest(List.of(
                new ConsentAgreementRequest(1L, true),
                new ConsentAgreementRequest(2L, true),
                new ConsentAgreementRequest(3L, true),
                new ConsentAgreementRequest(4L, true),
                new ConsentAgreementRequest(99L, true)));

        assertInvalidInput(request);
    }

    @Test
    void duplicatedConsentId_updateConsents_throwsInvalidInput() {
        UpdateConsentRequest request = new UpdateConsentRequest(List.of(
                new ConsentAgreementRequest(1L, true),
                new ConsentAgreementRequest(1L, false),
                new ConsentAgreementRequest(2L, true),
                new ConsentAgreementRequest(3L, true),
                new ConsentAgreementRequest(4L, true)));

        assertInvalidInput(request);
    }

    @Test
    void allRequiredAgreedToCurrentVersion_hasAgreedToRequiredConsents_returnsTrue() {
        when(userConsentRepository.findAllLatestByUserId(USER_ID)).thenReturn(List.of(
                consent(ConsentType.PRIVACY_COLLECTION, true),
                consent(ConsentType.AI_CONVERSATION, true),
                consent(ConsentType.FRIEND_TASTE_SHARING, true),
                consent(ConsentType.PRODUCT_ACTIVITY, true)));

        assertThat(consentService.hasAgreedToRequiredConsents(USER_ID)).isTrue();
    }

    @Test
    void requiredWithdrawnOrOldVersion_hasAgreedToRequiredConsents_returnsFalse() {
        when(userConsentRepository.findAllLatestByUserId(USER_ID)).thenReturn(List.of(
                consent(ConsentType.PRIVACY_COLLECTION, true),
                consent(ConsentType.AI_CONVERSATION, false),
                consent(ConsentType.FRIEND_TASTE_SHARING, true),
                oldVersionConsent(ConsentType.PRODUCT_ACTIVITY)));

        assertThat(consentService.hasAgreedToRequiredConsents(USER_ID)).isFalse();
    }

    @Test
    void agreedToAiConversation_hasAgreedToAiConversation_returnsTrue() {
        when(userConsentRepository.findFirstByUserIdAndConsentTypeOrderByIdDesc(
                USER_ID, ConsentType.AI_CONVERSATION))
                .thenReturn(Optional.of(consent(ConsentType.AI_CONVERSATION, true)));

        assertThat(consentService.hasAgreedToAiConversation(USER_ID)).isTrue();
    }

    @Test
    void notAgreedOrOldVersionAiConversation_hasAgreedToAiConversation_returnsFalse() {
        when(userConsentRepository.findFirstByUserIdAndConsentTypeOrderByIdDesc(
                USER_ID, ConsentType.AI_CONVERSATION))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(consent(ConsentType.AI_CONVERSATION, false)))
                .thenReturn(Optional.of(oldVersionConsent(ConsentType.AI_CONVERSATION)));

        assertThat(consentService.hasAgreedToAiConversation(USER_ID)).isFalse();
        assertThat(consentService.hasAgreedToAiConversation(USER_ID)).isFalse();
        assertThat(consentService.hasAgreedToAiConversation(USER_ID)).isFalse();
    }

    private void assertInvalidInput(UpdateConsentRequest request) {
        assertThatThrownBy(() -> consentService.updateConsents(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_INPUT);
        verify(userConsentRepository, never()).saveAll(any());
    }

    @SuppressWarnings("unchecked")
    private List<UserConsent> savedConsents() {
        ArgumentCaptor<List<UserConsent>> captor = ArgumentCaptor.forClass(List.class);
        verify(userConsentRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private static UpdateConsentRequest allAgreedRequest() {
        return new UpdateConsentRequest(List.of(
                new ConsentAgreementRequest(1L, true),
                new ConsentAgreementRequest(2L, true),
                new ConsentAgreementRequest(3L, true),
                new ConsentAgreementRequest(4L, true)));
    }

    private static UserConsent consent(ConsentType type, boolean agreed) {
        UserConsent consent = UserConsent.create(USER_ID, type, agreed);
        ReflectionTestUtils.setField(consent, "createdAt", AGREED_AT);
        return consent;
    }

    private static UserConsent oldVersionConsent(ConsentType type) {
        UserConsent consent = consent(type, true);
        ReflectionTestUtils.setField(consent, "version", "0.9");
        return consent;
    }
}
