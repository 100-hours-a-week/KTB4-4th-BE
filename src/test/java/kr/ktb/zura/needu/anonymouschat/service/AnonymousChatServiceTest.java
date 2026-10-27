package kr.ktb.zura.needu.anonymouschat.service;

import java.time.LocalDate;
import java.util.Optional;

import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousRoomDetailResponse;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.exception.AnonymousChatErrorCode;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomRepository;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.product.dto.response.ProductSummaryResponse;
import kr.ktb.zura.needu.product.service.ProductService;
import kr.ktb.zura.needu.product.type.ProductCategory;
import kr.ktb.zura.needu.user.dto.response.UserDetailResponse;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AnonymousChatServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long QUESTIONER_ID = 100L;
    private static final Long RECIPIENT_ID = 200L;
    private static final Long OUTSIDER_ID = 300L;
    private static final String QUESTIONER_ALIAS = "익명의 수달";
    private static final ProductSummaryResponse PRODUCT = new ProductSummaryResponse(
            PRODUCT_ID, "무선 헤드폰", 129000L, ProductCategory.DIGITAL, "https://example.com/product.png");

    @Mock
    private AnonymousRoomRepository anonymousRoomRepository;

    @Mock
    private UserService userService;

    @Mock
    private ProductService productService;

    @InjectMocks
    private AnonymousChatService anonymousChatService;

    @Test
    void questioner_findRoom_returnsRecipientNicknameAndProduct() {
        givenRoom();
        given(productService.findProductSummary(PRODUCT_ID)).willReturn(PRODUCT);
        given(userService.findUserById(RECIPIENT_ID)).willReturn(Optional.of(recipient()));

        AnonymousRoomDetailResponse response = anonymousChatService.findRoom(QUESTIONER_ID, ROOM_ID);

        verify(userService).validateActiveUser(QUESTIONER_ID);
        assertThat(response.roomId()).isEqualTo(ROOM_ID);
        assertThat(response.askedByMe()).isTrue();
        assertThat(response.targetName()).isEqualTo("지호");
        assertThat(response.product()).isEqualTo(PRODUCT);
    }

    @Test
    void recipient_findRoom_returnsQuestionerAliasInsteadOfName() {
        givenRoom();
        given(productService.findProductSummary(PRODUCT_ID)).willReturn(PRODUCT);

        AnonymousRoomDetailResponse response = anonymousChatService.findRoom(RECIPIENT_ID, ROOM_ID);

        assertThat(response.askedByMe()).isFalse();
        assertThat(response.targetName()).isEqualTo(QUESTIONER_ALIAS);
        verify(userService, never()).findUserById(anyLong());
    }

    @Test
    void recipientWithdrawn_findRoom_returnsNullTargetName() {
        givenRoom();
        given(productService.findProductSummary(PRODUCT_ID)).willReturn(PRODUCT);
        given(userService.findUserById(RECIPIENT_ID)).willReturn(Optional.empty());

        AnonymousRoomDetailResponse response = anonymousChatService.findRoom(QUESTIONER_ID, ROOM_ID);

        assertThat(response.targetName()).isNull();
    }

    @Test
    void missingRoom_findRoom_throwsNotFound() {
        given(anonymousRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> anonymousChatService.findRoom(QUESTIONER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_NOT_FOUND);

        verifyNoInteractions(productService);
    }

    @Test
    void nonParticipant_findRoom_throwsForbiddenWithoutLoadingProduct() {
        givenRoom();

        assertThatThrownBy(() -> anonymousChatService.findRoom(OUTSIDER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_FORBIDDEN);

        verifyNoInteractions(productService);
    }

    @Test
    void inactiveUser_findRoom_throwsWithoutQueryingRoom() {
        willThrow(new BusinessException(UserErrorCode.USER_ONBOARDING_REQUIRED))
                .given(userService).validateActiveUser(QUESTIONER_ID);

        assertThatThrownBy(() -> anonymousChatService.findRoom(QUESTIONER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_ONBOARDING_REQUIRED);

        verifyNoInteractions(anonymousRoomRepository, productService);
    }

    private void givenRoom() {
        AnonymousRoom room = new AnonymousRoom(PRODUCT_ID, QUESTIONER_ID, RECIPIENT_ID, QUESTIONER_ALIAS);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        given(anonymousRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room));
    }

    private UserDetailResponse recipient() {
        return new UserDetailResponse(RECIPIENT_ID, "지호", "https://image.test/profile.png", true,
                LocalDate.of(2000, 1, 1));
    }
}
