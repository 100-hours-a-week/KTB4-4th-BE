package kr.ktb.zura.needu.common.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.resource.ClientResources;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

// 캐싱용 Redis는 Boot 자동 구성(spring.data.redis.*)을 그대로 쓰고, 스트림용 Redis만 여기서 따로 만든다.
// defaultCandidate = false라 자동 구성의 @ConditionalOnMissingBean 판단과 타입 주입에서 빠진다
// => 기존 코드는 계속 캐싱용을 받고, 스트림용은 @Qualifier로 이름을 지정한 곳에만 주입된다.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StreamRedisProperties.class)
public class StreamRedisConfig {

    public static final String STREAM_REDIS_CONNECTION_FACTORY = "streamRedisConnectionFactory";
    public static final String STREAM_REDIS_TEMPLATE = "streamRedisTemplate";

    @Bean(name = STREAM_REDIS_CONNECTION_FACTORY, defaultCandidate = false)
    public LettuceConnectionFactory streamRedisConnectionFactory(
            StreamRedisProperties properties,
            ClientResources clientResources
    ) {
        RedisStandaloneConfiguration serverConfiguration =
                new RedisStandaloneConfiguration(properties.host(), properties.port());
        serverConfiguration.setDatabase(properties.database());
        if (StringUtils.hasText(properties.username())) {
            serverConfiguration.setUsername(properties.username());
        }
        if (StringUtils.hasText(properties.password())) {
            serverConfiguration.setPassword(RedisPassword.of(properties.password()));
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfiguration =
                LettuceClientConfiguration.builder()
                        .clientResources(clientResources)
                        .commandTimeout(properties.timeout())
                        .clientOptions(ClientOptions.builder()
                                .socketOptions(SocketOptions.builder()
                                        .connectTimeout(properties.connectTimeout())
                                        .build())
                                .build());
        if (properties.sslEnabled()) {
            clientConfiguration.useSsl();
        }
        return new LettuceConnectionFactory(serverConfiguration, clientConfiguration.build());
    }

    @Bean(name = STREAM_REDIS_TEMPLATE, defaultCandidate = false)
    public StringRedisTemplate streamRedisTemplate(
            @Qualifier(STREAM_REDIS_CONNECTION_FACTORY) RedisConnectionFactory connectionFactory
    ) {
        return new StringRedisTemplate(connectionFactory);
    }
}
