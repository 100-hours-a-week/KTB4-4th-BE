package kr.ktb.zura.needu.product.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import javax.sql.DataSource;
import kr.ktb.zura.needu.product.repository.GiftProductRepository;
import kr.ktb.zura.needu.product.repository.PersonalProductRepository;
import kr.ktb.zura.needu.product.repository.ProductPriceRange;
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

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ProductPriceRangeCacheTransactionTest {

    private static final Long USER_ID = 1L;

    @Test
    void transactionCommitted_recommendationsUpdated_evictsAfterCommit() {
        try (AnnotationConfigApplicationContext context = context()) {
            PersonalProductRepository repository = context.getBean(PersonalProductRepository.class);
            ProductPriceRangeCacheService cacheService = context.getBean(ProductPriceRangeCacheService.class);
            ApplicationEventPublisher eventPublisher = context;
            TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);
            given(repository.findPriceRangeByUserId(USER_ID))
                    .willReturn(new ProductPriceRange(BigDecimal.ONE, BigDecimal.TEN));
            cacheService.findPersonalPriceRange(USER_ID);

            transactionTemplate.executeWithoutResult(status -> {
                eventPublisher.publishEvent(new ProductRecommendationsUpdatedEvent(USER_ID));
                cacheService.findPersonalPriceRange(USER_ID);
                verify(repository).findPriceRangeByUserId(USER_ID);
            });
            cacheService.findPersonalPriceRange(USER_ID);

            verify(repository, times(2)).findPriceRangeByUserId(USER_ID);
        }
    }

    @Test
    void transactionRolledBack_recommendationsUpdated_keepsCachedValue() {
        try (AnnotationConfigApplicationContext context = context()) {
            PersonalProductRepository repository = context.getBean(PersonalProductRepository.class);
            ProductPriceRangeCacheService cacheService = context.getBean(ProductPriceRangeCacheService.class);
            ApplicationEventPublisher eventPublisher = context;
            TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);
            given(repository.findPriceRangeByUserId(USER_ID))
                    .willReturn(new ProductPriceRange(BigDecimal.ONE, BigDecimal.TEN));
            cacheService.findPersonalPriceRange(USER_ID);

            transactionTemplate.executeWithoutResult(status -> {
                eventPublisher.publishEvent(new ProductRecommendationsUpdatedEvent(USER_ID));
                status.setRollbackOnly();
            });
            cacheService.findPersonalPriceRange(USER_ID);

            verify(repository).findPriceRangeByUserId(USER_ID);
        }
    }

    private AnnotationConfigApplicationContext context() {
        return new AnnotationConfigApplicationContext(TestConfig.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        PersonalProductRepository personalProductRepository() {
            return mock(PersonalProductRepository.class);
        }

        @Bean
        GiftProductRepository giftProductRepository() {
            return mock(GiftProductRepository.class);
        }

        @Bean
        ProductPriceRangeCacheService productPriceRangeCacheService(
                PersonalProductRepository personalProductRepository,
                GiftProductRepository giftProductRepository,
                MeterRegistry meterRegistry
        ) {
            return new ProductPriceRangeCacheService(
                    personalProductRepository, giftProductRepository, meterRegistry, Duration.ofMinutes(10), 100L);
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
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
