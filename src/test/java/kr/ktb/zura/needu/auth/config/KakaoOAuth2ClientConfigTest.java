package kr.ktb.zura.needu.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

class KakaoOAuth2ClientConfigTest {

    @Test
    void kakaoClientRegistrationRepository_registersKakaoRefreshClient() {
        ClientRegistrationRepository repository = new KakaoOAuth2ClientConfig()
                .kakaoClientRegistrationRepository(
                        "client-id", "client-secret", "https://needu.example/friends/callback");

        ClientRegistration registration = repository.findByRegistrationId(
                KakaoOAuth2ClientConfig.REGISTRATION_ID);

        assertThat(registration).isNotNull();
        assertThat(registration.getClientAuthenticationMethod())
                .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_POST);
        assertThat(registration.getAuthorizationGrantType())
                .isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(registration.getRedirectUri())
                .isEqualTo("https://needu.example/friends/callback");
        assertThat(registration.getScopes()).containsExactly("friends");
        assertThat(registration.getProviderDetails().getTokenUri())
                .isEqualTo("https://kauth.kakao.com/oauth/token");
    }
}
