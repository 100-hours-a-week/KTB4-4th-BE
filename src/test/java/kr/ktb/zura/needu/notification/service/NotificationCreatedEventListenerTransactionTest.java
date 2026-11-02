package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import kr.ktb.zura.needu.notification.repository.NotificationRepository;
import kr.ktb.zura.needu.notification.repository.NotificationUnreadCount;
import kr.ktb.zura.needu.notification.sse.NotificationRedisEvent;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class NotificationCreatedEventListenerTransactionTest {

    private static final Long RECEIVER_USER_ID = 1L;
    private static final Long PURCHASE_CHECK_ID = 56L;
    private static final int PUBLISH_CHUNK_SIZE = 2;

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
                            event.data().get("targetAvailable").asBoolean()
                                    && event.recipients().size() == 1
                                    && event.recipients().getFirst().receiverUserId().equals(RECEIVER_USER_ID)
                                    && event.recipients().getFirst().unreadCount() == 3L
                                    && event.recipients().getFirst().data().get("notificationId").asLong() == 10L));
            assertThat(context.getBean(MeterRegistry.class).get("needu.notification.creation")
                    .tag("stage", "publish")
                    .tag("type", NotificationType.POKE.name())
                    .timer()
                    .count()).isEqualTo(1);
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
                    .publish(org.mockito.ArgumentMatchers.argThat(event -> {
                        JsonNode recipientData = event.recipients().getFirst().data();
                        return recipientData.get("notificationId").asLong() == 11L
                                && recipientData.get("preview").get("productName").asString().equals("섬유유연제");
                    }));
        }
    }

    @Test
    void receiversExceedChunkSize_notificationsCreated_publishesPerChunk() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            NotificationEventPublisher notificationEventPublisher =
                    context.getBean(NotificationEventPublisher.class);
            ApplicationEventPublisher eventPublisher = context;
            TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);

            transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(
                    new NotificationsCreatedEvent(List.of(
                            birthdayNotification(20L, 1L),
                            birthdayNotification(21L, 2L),
                            birthdayNotification(22L, 3L)
                    ))));

            ArgumentCaptor<NotificationRedisEvent> eventCaptor = ArgumentCaptor.forClass(NotificationRedisEvent.class);
            verify(notificationEventPublisher, times(2)).publish(eventCaptor.capture());
            assertThat(eventCaptor.getAllValues())
                    .extracting(event -> event.recipients().stream()
                            .map(NotificationRedisEvent.Recipient::receiverUserId)
                            .toList())
                    .containsExactly(List.of(1L, 2L), List.of(3L));
            assertThat(eventCaptor.getAllValues())
                    .allSatisfy(event -> assertThat(event.data().get("title").asString())
                            .isEqualTo("친구의 생일이 7일 남았어요."));
        }
    }

    @Test
    void notificationsWithDifferentContent_notificationsCreated_publishesSeparately() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            NotificationEventPublisher notificationEventPublisher =
                    context.getBean(NotificationEventPublisher.class);
            ApplicationEventPublisher eventPublisher = context;
            TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);

            transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(
                    new NotificationsCreatedEvent(List.of(
                            birthdayNotification(20L, 1L),
                            createdEvent().notifications().getFirst()
                    ))));

            ArgumentCaptor<NotificationRedisEvent> eventCaptor = ArgumentCaptor.forClass(NotificationRedisEvent.class);
            verify(notificationEventPublisher, times(2)).publish(eventCaptor.capture());
            assertThat(eventCaptor.getAllValues())
                    .extracting(event -> event.data().get("type").asString())
                    .containsExactly("FRIEND_BIRTHDAY", "POKE");
        }
    }

    @Test
    void nonPositiveChunkSize_create_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new NotificationCreatedEventListener(
                mock(NotificationRepository.class),
                mock(NotificationEventPublisher.class),
                new NotificationPreviewService(List.of()),
                JsonMapper.builder().build(),
                new SimpleMeterRegistry(),
                0
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private NotificationsCreatedEvent.CreatedNotification birthdayNotification(
            Long notificationId, Long receiverUserId) {
        return new NotificationsCreatedEvent.CreatedNotification(
                notificationId,
                receiverUserId,
                NotificationCategory.EVENT,
                NotificationType.FRIEND_BIRTHDAY,
                "친구의 생일이 7일 남았어요.",
                "지금 선물을 준비하면 여유 있게 도착해요.",
                NotificationResourceType.USER,
                99L,
                LocalDateTime.of(2026, 10, 10, 9, 0)
        );
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
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        NotificationCreatedEventListener notificationCreatedEventListener(
                NotificationRepository notificationRepository,
                NotificationEventPublisher notificationEventPublisher,
                NotificationPreviewService notificationPreviewService,
                JsonMapper jsonMapper,
                MeterRegistry meterRegistry
        ) {
            return new NotificationCreatedEventListener(
                    notificationRepository,
                    notificationEventPublisher,
                    notificationPreviewService,
                    jsonMapper,
                    meterRegistry,
                    PUBLISH_CHUNK_SIZE
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
