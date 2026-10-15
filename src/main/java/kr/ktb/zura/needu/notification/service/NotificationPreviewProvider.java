package kr.ktb.zura.needu.notification.service;

import java.util.Collection;
import java.util.Map;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;

public interface NotificationPreviewProvider {

    NotificationResourceType resourceType();

    Map<Long, NotificationPreview> findAllPreviews(Long receiverUserId, Collection<Long> resourceIds);
}
