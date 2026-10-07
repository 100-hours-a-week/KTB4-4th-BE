package kr.ktb.zura.needu.notification.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class NotificationStreamService {

    // TODO: emitter 등록(사용자당 5개 초과 시 NOTIFICATION_STREAM_LIMIT_EXCEEDED)
    //  connected 이벤트(unreadCount, serverTime), 25초 :ping, 30분 timeout, Redis Pub/Sub 수신 구현
    //  연결 전 오류는 JSON으로 응답해야 하므로 Accept: text/event-stream 요청의 오류 응답 형식도 함께 확인
    public SseEmitter connect(Long userId) {
        throw new UnsupportedOperationException("알림 스트림 연결 미구현");
    }
}
