package kr.ktb.zura.needu.common.redis;

import static org.assertj.core.api.Assertions.assertThat;

import kr.ktb.zura.needu.aichat.config.AiConversationLockConfig;
import kr.ktb.zura.needu.aichat.config.AiReplyQueueConfig;
import kr.ktb.zura.needu.aichat.repository.AiConversationLockRepository;
import kr.ktb.zura.needu.aichat.repository.AiGenerationStateRepository;
import kr.ktb.zura.needu.aichat.repository.AiReplyPermitRepository;
import kr.ktb.zura.needu.aichat.repository.AiReplyStreamRepository;
import kr.ktb.zura.needu.aichat.service.AiChatEventPublisher;
import kr.ktb.zura.needu.anonymouschat.service.AnonymousMessageEventPublisher;
import kr.ktb.zura.needu.notification.service.NotificationEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class StreamRedisConfigTest {

    private static final String CACHE_HOST = "cache-redis";
    private static final String STREAM_HOST = "stream-redis";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            // @Value로 받는 Duration("120s")을 운영과 같이 변환한다
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withConfiguration(AutoConfigurations.of(
                    PropertyPlaceholderAutoConfiguration.class,
                    DataRedisAutoConfiguration.class
            ))
            .withUserConfiguration(StreamRedisConfig.class, AiConversationLockConfig.class, AiReplyQueueConfig.class)
            .withBean(JsonMapper.class, JsonMapper::new)
            .withBean(RedisKeyGenerator.class, () -> new RedisKeyGenerator("backend:test"))
            .withBean(AiReplyStreamRepository.class)
            .withBean(AiGenerationStateRepository.class)
            .withBean(AiConversationLockRepository.class)
            .withBean(AiReplyPermitRepository.class)
            .withBean(AiChatEventPublisher.class)
            .withBean(NotificationEventPublisher.class)
            .withBean(AnonymousMessageEventPublisher.class)
            .withPropertyValues(
                    "spring.data.redis.host=" + CACHE_HOST,
                    "needu.stream-redis.host=" + STREAM_HOST,
                    "needu.stream-redis.port=6380",
                    "needu.stream-redis.connect-timeout=10s",
                    "needu.stream-redis.timeout=2s",
                    "ai.chat.generation-ttl=120s",
                    "ai.chat.lock.ttl=120s",
                    "ai.chat.lock.key-prefix=backend:test:aichat:lock",
                    "ai.chat.reply-queue.global-max-concurrency=96",
                    "ai.chat.reply-queue.permit-ttl=30s",
                    "ai.chat.reply-queue.max-deliveries=3",
                    "ai.chat.reply-queue.heartbeat-interval=5s",
                    "ai.chat.reply-queue.claim-min-idle=30s",
                    "ai.chat.reply-queue.claim-interval=5s",
                    "ai.chat.reply-queue.poll-interval=200ms",
                    "ai.chat.reply-queue.turn-in-progress-retry-delay=2s"
            );

    @Test
    void 스트림_Redis를_추가해도_기본_StringRedisTemplate은_캐싱용_Redis에_연결된다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            // 자동 구성이 물러나지 않고 기본 템플릿을 그대로 만든다
            assertThat(context).hasBean("stringRedisTemplate");
            assertThat(hostOf(context.getBean("stringRedisTemplate", StringRedisTemplate.class)))
                    .isEqualTo(CACHE_HOST);
        });
    }

    @Test
    void 스트림_Redis는_스트림_설정의_접속_정보를_쓴다() {
        contextRunner.run(context -> {
            LettuceConnectionFactory connectionFactory = context.getBean(
                    StreamRedisConfig.STREAM_REDIS_CONNECTION_FACTORY, LettuceConnectionFactory.class);

            assertThat(connectionFactory.getHostName()).isEqualTo(STREAM_HOST);
            assertThat(connectionFactory.getPort()).isEqualTo(6380);
            assertThat(connectionFactory.getClientConfiguration().getCommandTimeout()).hasSeconds(2);
        });
    }

    @Test
    void AI_대화와_알림_Redis_사용처는_스트림_Redis를_주입받는다() {
        contextRunner.run(context -> {
            assertThat(injectedHost(context, AiReplyStreamRepository.class)).isEqualTo(STREAM_HOST);
            assertThat(injectedHost(context, AiGenerationStateRepository.class)).isEqualTo(STREAM_HOST);
            assertThat(injectedHost(context, AiConversationLockRepository.class)).isEqualTo(STREAM_HOST);
            assertThat(injectedHost(context, AiReplyPermitRepository.class)).isEqualTo(STREAM_HOST);
            assertThat(injectedHost(context, AiChatEventPublisher.class)).isEqualTo(STREAM_HOST);
            assertThat(injectedHost(context, NotificationEventPublisher.class)).isEqualTo(STREAM_HOST);
        });
    }

    @Test
    void 캐싱용_Redis_사용처는_그대로_캐싱용_Redis를_주입받는다() {
        contextRunner.run(context ->
                assertThat(injectedHost(context, AnonymousMessageEventPublisher.class)).isEqualTo(CACHE_HOST));
    }

    private String injectedHost(AssertableApplicationContext context, Class<?> beanType) {
        StringRedisTemplate redisTemplate =
                (StringRedisTemplate) ReflectionTestUtils.getField(context.getBean(beanType), "redisTemplate");
        return hostOf(redisTemplate);
    }

    private String hostOf(StringRedisTemplate redisTemplate) {
        return ((LettuceConnectionFactory) redisTemplate.getConnectionFactory()).getHostName();
    }
}
