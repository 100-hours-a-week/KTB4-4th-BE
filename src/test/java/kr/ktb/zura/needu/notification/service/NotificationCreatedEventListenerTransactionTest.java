package kr.ktb.zura.needu.notification.service;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import javax.sql.DataSource;
import kr.ktb.zura.needu.notification.repository.NotificationRepository;
import kr.ktb.zura.needu.notification.repository.NotificationUnreadCount;
import kr.ktb.zura.needu.notification.sse.NotificationRedisEvent;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class NotificationCreatedEventListenerTransactionTest {

    private static final Long RECEIVER_USER_ID = 1L;
    private static final Long PURCHASE_CHECK_ID = 56L;

    @Test
    void transactionCommitted_notificationsCreated_publishesAfterCommit() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            NotificationRepository notificationRepository = context.getBean(NotificationRepository.class);
            NotificationEventPublisher notificationEventPublisher =
                    context.getBean(NotificationEventPublisher.class);
            ApplicationEventPublisher eventPublisher = context;
            TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);
            NotificationUnreadCount unreadCount = mock(NotificationUnreadCount.class);
            given(unreadCount.getReceiverUserId()).willReturn(RECEIVER_USER_ID);
            given(unreadCount.getUnreadCount()).willReturn(3L);
            given(notificationRepository.findAllUnreadCountsByReceiverUserIdIn(List.of(RECEIVER_USER_ID)))
                    .willReturn(List.of(unreadCount));

            transactionTemplate.executeWithoutResult(status -> {
                eventPublisher.publishEvent(createdEvent());
                verify(notificationEventPublisher, never())
                        .publish(org.mockito.ArgumentMatchers.any(NotificationRedisEvent.class));
            });

            verify(notificationEventPublisher)
                    .publish(org.mockito.ArgumentMatchers.argThat(event ->
                            event.receiverUserId().equals(RECEIVER_USER_ID)
                                    && event.unreadCount() == 3L
                                    && event.data().get("notificationId").asLong() == 10L
                                    && event.data().get("targetAvailable").asBoolean()));
        }
    }

    @Test
    void transactionRolledBack_notificationsCreated_doesNotPublish() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            NotificationEventPublisher notificationEventPublisher =
                    context.getBean(NotificationEventPublisher.class);
            ApplicationEventPublisher eventPublisher = context;
            TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);

            transactionTemplate.executeWithoutResult(status -> {
                eventPublisher.publishEvent(createdEvent());
                status.setRollbackOnly();
            });

            verify(notificationEventPublisher, never())
                    .publish(org.mockito.ArgumentMatchers.any(NotificationRedisEvent.class));
        }
    }

    @Test
    void previewProviderExists_notificationsCreated_publishesPreviewInData() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            NotificationEventPublisher notificationEventPublisher =
                    context.getBean(NotificationEventPublisher.class);
            ApplicationEventPublisher eventPublisher = context;
            TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);

            transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(
                    new NotificationsCreatedEvent(List.of(new NotificationsCreatedEvent.CreatedNotification(
                            11L,
                            RECEIVER_USER_ID,
                            NotificationCategory.PURCHASE_STATUS,
                            NotificationType.PURCHASE_STATUS,
                            "추천받은 상품, 구매하셨나요?",
                            "구매 여부를 알려주시면 추천이 더 정확해져요.",
                            NotificationResourceType.PURCHASE_CHECK,
                            PURCHASE_CHECK_ID,
                            LocalDateTime.of(2026, 10, 10, 12, 0)
                    )))));

            verify(notificationEventPublisher)
                    .publish(org.mockito.ArgumentMatchers.argThat(event ->
                            event.data().get("notificationId").asLong() == 11L
                                    && event.data().get("preview").get("productName").asString()
                                            .equals("섬유유연제")));
        }
    }

    @Test
    void receiverOffline_notificationsCreated_skipsLookupAndPublish() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            NotificationRepository notificationRepository = context.getBean(NotificationRepository.class);
            NotificationEventPublisher notificationEventPublisher =
                    context.getBean(NotificationEventPublisher.class);
            NotificationPresenceRegistry presenceRegistry = context.getBean(NotificationPresenceRegistry.class);
            given(presenceRegistry.findOnlineUserIds(anyCollection())).willReturn(Set.of());
            ApplicationEventPublisher eventPublisher = context;
            TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);

            transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(createdEvent()));

            verify(notificationRepository, never()).findAllUnreadCountsByReceiverUserIdIn(anyCollection());
            verify(notificationEventPublisher, never())
                    .publish(org.mockito.ArgumentMatchers.any(NotificationRedisEvent.class));
        }
    }

    private NotificationsCreatedEvent createdEvent() {
        return new NotificationsCreatedEvent(List.of(new NotificationsCreatedEvent.CreatedNotification(
                10L,
                RECEIVER_USER_ID,
                NotificationCategory.POKE,
                NotificationType.POKE,
                "친구가 콕 찔렀어요.",
                "AI대화를 통해 취향 분석을 해보세요!",
                NotificationResourceType.AI_CONVERSATION,
                null,
                LocalDateTime.of(2026, 10, 10, 12, 0)
        )));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        NotificationRepository notificationRepository() {
            return mock(NotificationRepository.class);
        }

        @Bean
        NotificationEventPublisher notificationEventPublisher() {
            return mock(NotificationEventPublisher.class);
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        NotificationPreviewService notificationPreviewService() {
            return new NotificationPreviewService(List.of(new NotificationPreviewProvider() {
                @Override
                public NotificationResourceType resourceType() {
                    return NotificationResourceType.PURCHASE_CHECK;
                }

                @Override
                public Map<Long, NotificationPreview> findAllPreviews(
                        Long receiverUserId, Collection<Long> resourceIds) {
                    return Map.of(PURCHASE_CHECK_ID, new TestPreview("섬유유연제"));
                }
            }));
        }

        @Bean
        NotificationPresenceRegistry notificationPresenceRegistry() {
            NotificationPresenceRegistry presenceRegistry = mock(NotificationPresenceRegistry.class);
            given(presenceRegistry.findOnlineUserIds(anyCollection()))
                    .willAnswer(invocation -> new HashSet<Long>(invocation.getArgument(0)));
            return presenceRegistry;
        }

        @Bean
        NotificationCreatedEventListener notificationCreatedEventListener(
                NotificationRepository notificationRepository,
                NotificationEventPublisher notificationEventPublisher,
                NotificationPreviewService notificationPreviewService,
                JsonMapper jsonMapper,
                NotificationPresenceRegistry presenceRegistry
        ) {
            return new NotificationCreatedEventListener(
                    notificationRepository,
                    notificationEventPublisher,
                    notificationPreviewService,
                    jsonMapper,
                    new NotificationStageTimer(new SimpleMeterRegistry()),
                    presenceRegistry
            );
        }

        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .generateUniqueName(true)
                    .setType(EmbeddedDatabaseType.H2)
                    .build();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }

    private record TestPreview(String productName) implements NotificationPreview {
    }
}
