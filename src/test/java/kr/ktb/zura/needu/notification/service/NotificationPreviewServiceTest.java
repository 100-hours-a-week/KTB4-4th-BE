package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import kr.ktb.zura.needu.notification.entity.Notification;
import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationPreviewServiceTest {

    private static final Long RECEIVER_USER_ID = 1L;

    private NotificationPreviewProvider purchaseCheckProvider;
    private NotificationPreviewService notificationPreviewService;

    @BeforeEach
    void setUp() {
        purchaseCheckProvider = mock(NotificationPreviewProvider.class);
        given(purchaseCheckProvider.resourceType()).willReturn(NotificationResourceType.PURCHASE_CHECK);
        notificationPreviewService = new NotificationPreviewService(List.of(purchaseCheckProvider));
    }

    @Test
    void notificationsWithProvider_findAllPreviews_queriesProviderOnceAndMapsByNotificationId() {
        Notification first = notification(101L, NotificationResourceType.PURCHASE_CHECK, 56L);
        Notification second = notification(102L, NotificationResourceType.PURCHASE_CHECK, 57L);
        NotificationPreview preview = new TestPreview("섬유유연제");
        given(purchaseCheckProvider.findAllPreviews(RECEIVER_USER_ID, List.of(56L, 57L)))
                .willReturn(Map.of(56L, preview));

        Map<Long, NotificationPreview> previews =
                notificationPreviewService.findAllPreviews(RECEIVER_USER_ID, List.of(first, second));

        assertThat(previews).containsExactly(Map.entry(101L, preview));
        verify(purchaseCheckProvider).findAllPreviews(RECEIVER_USER_ID, List.of(56L, 57L));
    }

    @Test
    void notificationsWithoutProvider_findAllPreviews_returnsEmptyWithoutQuery() {
        Notification userNotification = notification(101L, NotificationResourceType.USER, 321L);
        Notification noResourceNotification = notification(102L, null, null);

        Map<Long, NotificationPreview> previews = notificationPreviewService.findAllPreviews(
                RECEIVER_USER_ID, List.of(userNotification, noResourceNotification));

        assertThat(previews).isEmpty();
        verify(purchaseCheckProvider, never()).findAllPreviews(any(), any());
    }

    @Test
    void providerFails_findAllPreviews_returnsEmptyInsteadOfThrowing() {
        Notification notification = notification(101L, NotificationResourceType.PURCHASE_CHECK, 56L);
        given(purchaseCheckProvider.findAllPreviews(RECEIVER_USER_ID, List.of(56L)))
                .willThrow(new IllegalStateException("database unavailable"));

        Map<Long, NotificationPreview> previews =
                notificationPreviewService.findAllPreviews(RECEIVER_USER_ID, List.of(notification));

        assertThat(previews).isEmpty();
    }

    @Test
    void resourceWithProvider_findPreview_returnsPreview() {
        NotificationPreview preview = new TestPreview("섬유유연제");
        given(purchaseCheckProvider.findAllPreviews(RECEIVER_USER_ID, List.of(56L))).willReturn(Map.of(56L, preview));

        assertThat(notificationPreviewService.findPreview(
                RECEIVER_USER_ID, NotificationResourceType.PURCHASE_CHECK, 56L)).isEqualTo(preview);
    }

    @Test
    void resourceWithoutProvider_findPreview_returnsNullWithoutQuery() {
        assertThat(notificationPreviewService.findPreview(
                RECEIVER_USER_ID, NotificationResourceType.USER, 321L)).isNull();
        assertThat(notificationPreviewService.findPreview(RECEIVER_USER_ID, null, null)).isNull();
        verify(purchaseCheckProvider, never()).findAllPreviews(any(), any());
    }

    @Test
    void duplicateResourceTypeProviders_createService_throwsIllegalState() {
        NotificationPreviewProvider duplicate = mock(NotificationPreviewProvider.class);
        given(duplicate.resourceType()).willReturn(NotificationResourceType.PURCHASE_CHECK);

        assertThatThrownBy(() -> new NotificationPreviewService(List.of(purchaseCheckProvider, duplicate)))
                .isInstanceOf(IllegalStateException.class);
    }

    private Notification notification(Long id, NotificationResourceType resourceType, Long resourceId) {
        NotificationEvent event = mock(NotificationEvent.class);
        given(event.getResourceType()).willReturn(resourceType);
        given(event.getResourceId()).willReturn(resourceId);
        Notification notification = mock(Notification.class);
        given(notification.getId()).willReturn(id);
        given(notification.getEvent()).willReturn(event);
        return notification;
    }

    private record TestPreview(String productName) implements NotificationPreview {
    }
}
