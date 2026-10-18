package kr.ktb.zura.needu.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import kr.ktb.zura.needu.user.entity.UserConsent;
import kr.ktb.zura.needu.user.type.ConsentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class UserConsentRepositoryTest {

    @Autowired
    private UserConsentRepository userConsentRepository;

    @Test
    void consentHistoryExists_findAllLatestByUserId_returnsLatestPerTypeOfUser() {
        userConsentRepository.save(UserConsent.create(1L, ConsentType.PRIVACY_COLLECTION, true));
        userConsentRepository.save(UserConsent.create(1L, ConsentType.AI_CONVERSATION, true));
        userConsentRepository.save(UserConsent.create(1L, ConsentType.AI_CONVERSATION, false));
        userConsentRepository.save(UserConsent.create(2L, ConsentType.PRODUCT_ACTIVITY, true));

        assertThat(userConsentRepository.findAllLatestByUserId(1L))
                .extracting(UserConsent::getConsentType, UserConsent::isAgreed)
                .containsExactlyInAnyOrder(
                        tuple(ConsentType.PRIVACY_COLLECTION, true),
                        tuple(ConsentType.AI_CONVERSATION, false));
    }

    @Test
    void noConsentHistory_findAllLatestByUserId_returnsEmpty() {
        userConsentRepository.save(UserConsent.create(2L, ConsentType.PRIVACY_COLLECTION, true));

        assertThat(userConsentRepository.findAllLatestByUserId(1L)).isEmpty();
    }

    @Test
    void consentChangedSeveralTimes_findFirstByUserIdAndConsentTypeOrderByIdDesc_returnsLatest() {
        userConsentRepository.save(UserConsent.create(1L, ConsentType.AI_CONVERSATION, false));
        userConsentRepository.save(UserConsent.create(1L, ConsentType.AI_CONVERSATION, true));
        userConsentRepository.save(UserConsent.create(1L, ConsentType.PRIVACY_COLLECTION, false));

        assertThat(userConsentRepository.findFirstByUserIdAndConsentTypeOrderByIdDesc(
                1L, ConsentType.AI_CONVERSATION))
                .get()
                .extracting(UserConsent::isAgreed)
                .isEqualTo(true);
    }
}
