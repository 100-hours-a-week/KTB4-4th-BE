package kr.ktb.zura.needu.notification.client;

import com.zerodeplibs.webpush.VAPIDKeyPair;
import com.zerodeplibs.webpush.httpclient.StandardHttpClientRequestPreparer;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;
import kr.ktb.zura.needu.notification.config.WebPushProperties;
import kr.ktb.zura.needu.notification.entity.PushSubscription;
import kr.ktb.zura.needu.notification.service.NotificationsCreatedEvent;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

public class WebPushClient {

    private static final int JWT_EXPIRES_MINUTES = 15;

    private final HttpClient httpClient;
    private final VAPIDKeyPair vapidKeyPair;
    private final WebPushProperties properties;
    private final JsonMapper jsonMapper;

    public WebPushClient(
            HttpClient httpClient,
            VAPIDKeyPair vapidKeyPair,
            WebPushProperties properties,
            JsonMapper jsonMapper
    ) {
        this.httpClient = httpClient;
        this.vapidKeyPair = vapidKeyPair;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    public WebPushResult send(
            PushSubscription subscription,
            NotificationsCreatedEvent.CreatedNotification notification
    ) {
        try {
            HttpResponse<Void> response = httpClient.send(
                    createRequest(subscription, notification),
                    HttpResponse.BodyHandlers.discarding()
            );
            return WebPushResult.fromStatusCode(response.statusCode());
        } catch (IOException | RuntimeException exception) {
            return WebPushResult.FAILED;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return WebPushResult.FAILED;
        }
    }

    private HttpRequest createRequest(
            PushSubscription target,
            NotificationsCreatedEvent.CreatedNotification notification
    ) throws JacksonException {
        return StandardHttpClientRequestPreparer.getBuilder()
                .pushSubscription(toLibrarySubscription(target))
                .vapidJWTExpiresAfter(JWT_EXPIRES_MINUTES, TimeUnit.MINUTES)
                .vapidJWTSubject(properties.subject())
                .pushMessage(jsonMapper.writeValueAsBytes(WebPushPayload.from(notification)))
                .ttl(properties.ttl().toSeconds(), TimeUnit.SECONDS)
                .urgencyNormal()
                .build(vapidKeyPair)
                .toRequestBuilder()
                .timeout(properties.requestTimeout())
                .build();
    }

    private com.zerodeplibs.webpush.PushSubscription toLibrarySubscription(PushSubscription target) {
        com.zerodeplibs.webpush.PushSubscription.Keys keys =
                new com.zerodeplibs.webpush.PushSubscription.Keys();
        keys.setP256dh(target.getP256dh());
        keys.setAuth(target.getAuth());

        com.zerodeplibs.webpush.PushSubscription subscription =
                new com.zerodeplibs.webpush.PushSubscription();
        subscription.setEndpoint(target.getEndpoint());
        subscription.setKeys(keys);
        return subscription;
    }

    private record WebPushPayload(
            Long notificationId,
            NotificationCategory category,
            String title,
            String body,
            NotificationResourceType resourceType,
            Long resourceId
    ) {

        private static WebPushPayload from(NotificationsCreatedEvent.CreatedNotification notification) {
            return new WebPushPayload(
                    notification.notificationId(),
                    notification.category(),
                    notification.title(),
                    notification.body(),
                    notification.resourceType(),
                    notification.resourceId()
            );
        }
    }
}
