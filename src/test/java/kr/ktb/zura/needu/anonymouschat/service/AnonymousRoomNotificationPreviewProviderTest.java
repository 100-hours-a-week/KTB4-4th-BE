package kr.ktb.zura.needu.anonymouschat.service;

import java.util.List;
import java.util.Map;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomRepository;
import kr.ktb.zura.needu.notification.service.NotificationPreview;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.product.dto.response.ProductSummaryResponse;
import kr.ktb.zura.needu.product.service.ProductService;
import kr.ktb.zura.needu.product.type.ProductCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AnonymousRoomNotificationPreviewProviderTest {

    private static final Long ROOM_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long RECEIVER_USER_ID = 20L;

    @Mock
    private AnonymousRoomRepository anonymousRoomRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private AnonymousRoomNotificationPreviewProvider provider;

    @Test
    void resourceType_returnsAnonymousRoom() {
        assertThat(provider.resourceType()).isEqualTo(NotificationResourceType.ANONYMOUS_ROOM);
    }

    @Test
    void receivedRoom_findAllPreviews_returnsProduct() {
        AnonymousRoom room = new AnonymousRoom(PRODUCT_ID, 15L, RECEIVER_USER_ID, "익명의 수달");
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        ProductSummaryResponse product = new ProductSummaryResponse(
                PRODUCT_ID, "무선 헤드폰", 129000L, ProductCategory.DIGITAL,
                "https://example.com/product.png");
        given(anonymousRoomRepository.findAllByIdInAndRecipientUserId(List.of(ROOM_ID), RECEIVER_USER_ID))
                .willReturn(List.of(room));
        given(productService.findAllProductSummaries(List.of(PRODUCT_ID)))
                .willReturn(Map.of(PRODUCT_ID, product));

        Map<Long, NotificationPreview> previews =
                provider.findAllPreviews(RECEIVER_USER_ID, List.of(ROOM_ID));

        assertThat(previews).containsExactly(Map.entry(ROOM_ID, new AnonymousRoomNotificationPreview(
                PRODUCT_ID, "무선 헤드폰", "https://example.com/product.png")));
    }

    @Test
    void emptyResourceIds_findAllPreviews_returnsEmpty() {
        assertThat(provider.findAllPreviews(RECEIVER_USER_ID, List.of())).isEmpty();
        verifyNoInteractions(anonymousRoomRepository, productService);
    }
}
