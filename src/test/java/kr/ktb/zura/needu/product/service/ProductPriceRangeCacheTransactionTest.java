package kr.ktb.zura.needu.product.service;

import java.time.Duration;
import javax.sql.DataSource;
import kr.ktb.zura.needu.common.redis.RedisCacheStore;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import kr.ktb.zura.needu.product.repository.GiftProductRepository;
import kr.ktb.zura.needu.product.repository.PersonalProductRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ProductPriceRangeCacheTransactionTest {

    private static final Long USER_ID = 1L;
    private static final String CACHE_NAME = "product-price-range";
    private static final String PERSONAL_KEY = "backend:test:product:price-range:personal:1";
    private static final String GIFT_KEY = "backend:test:product:price-range:gift:1";

    @Test
    void transactionCommitted_recommendationsUpdated_evictsAfterCommit() {
        try (AnnotationConfigApplicationContext context = context()) {
            RedisCacheStore redisCacheStore = context.getBean(RedisCacheStore.class);
            ApplicationEventPublisher eventPublisher = context;
            TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);

            transactionTemplate.executeWithoutResult(status -> {
                eventPublisher.publishEvent(new ProductRecommendationsUpdatedEvent(USER_ID));
                verify(redisCacheStore, never()).delete(anyString(), any(String[].class));
            });

            verify(redisCacheStore).delete(CACHE_NAME, PERSONAL_KEY, GIFT_KEY);
        }
    }

    @Test
    void transactionRolledBack_recommendationsUpdated_keepsCachedValue() {
        try (AnnotationConfigApplicationContext context = context()) {
            RedisCacheStore redisCacheStore = context.getBean(RedisCacheStore.class);
            ApplicationEventPublisher eventPublisher = context;
            TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);

            transactionTemplate.executeWithoutResult(status -> {
                eventPublisher.publishEvent(new ProductRecommendationsUpdatedEvent(USER_ID));
                status.setRollbackOnly();
            });

            verify(redisCacheStore, never()).delete(anyString(), any(String[].class));
        }
    }

    private AnnotationConfigApplicationContext context() {
        return new AnnotationConfigApplicationContext(TestConfig.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        RedisCacheStore redisCacheStore() {
            return mock(RedisCacheStore.class);
        }

        @Bean
        ProductPriceRangeCacheService productPriceRangeCacheService(RedisCacheStore redisCacheStore) {
            return new ProductPriceRangeCacheService(
                    mock(PersonalProductRepository.class),
                    mock(GiftProductRepository.class),
                    redisCacheStore,
                    new RedisKeyGenerator("backend:test"),
                    Duration.ofMinutes(10)
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
