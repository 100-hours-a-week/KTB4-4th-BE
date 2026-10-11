package kr.ktb.zura.needu.notification.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
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
                                    && event.data().get("notificationId").asLong() == 10L));
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

    private NotificationsCreatedEvent createdEvent() {
        return new NotificationsCreatedEvent(List.of(new NotificationsCreatedEvent.CreatedNotification(
                10L,
                RECEIVER_USER_ID,
                NotificationCategory.CHAT,
                NotificationType.CHAT,
                "새로운 답변이 도착했어요.",
                "친구가 상품에 대한 의견을 남겼어요.",
                NotificationResourceType.AI_CONVERSATION,
                20L,
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
        NotificationCreatedEventListener notificationCreatedEventListener(
                NotificationRepository notificationRepository,
                NotificationEventPublisher notificationEventPublisher,
                JsonMapper jsonMapper
        ) {
            return new NotificationCreatedEventListener(
                    notificationRepository,
                    notificationEventPublisher,
                    jsonMapper
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
}
