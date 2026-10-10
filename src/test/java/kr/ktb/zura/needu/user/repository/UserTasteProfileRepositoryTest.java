package kr.ktb.zura.needu.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import kr.ktb.zura.needu.user.entity.User;
import kr.ktb.zura.needu.user.entity.UserTasteProfile;
import kr.ktb.zura.needu.user.type.Gender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class UserTasteProfileRepositoryTest {

    @Autowired
    private UserTasteProfileRepository userTasteProfileRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void onboardingTastesSaved_findById_returnsCodeLists() {
        User user = new User(42L, "니듀", null, Gender.NONE, null);
        entityManager.persist(user);
        UserTasteProfile profile = new UserTasteProfile(user, Map.of());
        profile.updateOnboardingTastes(Map.of(
                "interestCategoryCodes", List.of("BEAUTY", "HOME_INTERIOR"),
                "allergyCodes", List.of(),
                "giftExclusionCodes", List.of("PERFUME")));
        userTasteProfileRepository.saveAndFlush(profile);
        entityManager.clear();

        UserTasteProfile found = userTasteProfileRepository.findById(user.getId()).orElseThrow();

        assertThat(found.hasOnboardingTastes()).isTrue();
        assertThat(found.getOnboardingTastes())
                .containsEntry("interestCategoryCodes", List.of("BEAUTY", "HOME_INTERIOR"))
                .containsEntry("allergyCodes", List.of())
                .containsEntry("giftExclusionCodes", List.of("PERFUME"));
    }

    @Test
    void analysisOnlyProfile_findById_hasNoOnboardingTastes() {
        User user = new User(43L, "니듀", null, Gender.NONE, null);
        entityManager.persist(user);
        userTasteProfileRepository.saveAndFlush(new UserTasteProfile(user, Map.of()));
        entityManager.clear();

        UserTasteProfile found = userTasteProfileRepository.findById(user.getId()).orElseThrow();

        assertThat(found.hasOnboardingTastes()).isFalse();
    }
}
