package kr.ktb.zura.needu.product.service;

import java.util.Set;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.notification.service.NotificationCreationService;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import kr.ktb.zura.needu.product.dto.response.PurchaseCheckResponse;
import kr.ktb.zura.needu.product.entity.PurchaseCheck;
import kr.ktb.zura.needu.product.exception.ProductErrorCode;
import kr.ktb.zura.needu.product.repository.ProductLinkClickRepository;
import kr.ktb.zura.needu.product.repository.PurchaseCheckRepository;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseCheckService {

    private static final String NOTIFICATION_TITLE = "추천받은 상품, 구매하셨나요?";
    private static final String NOTIFICATION_BODY = "구매 여부를 알려주시면 추천이 더 정확해져요.";
    private static final String NOTIFICATION_DEDUP_KEY_PREFIX = "PURCHASE_CHECK:";
    private static final long NOTIFICATION_EXPIRATION_DAYS = 7;

    private final UserService userService;
    private final NotificationCreationService notificationCreationService;
    private final PurchaseCheckRepository purchaseCheckRepository;
    private final ProductLinkClickRepository productLinkClickRepository;

    @Transactional
    public PurchaseCheckResponse answerPurchaseCheck(Long userId, Long purchaseCheckId, boolean purchased) {
        userService.validateActiveUser(userId);

        PurchaseCheck purchaseCheck = purchaseCheckRepository.findByIdAndUserId(purchaseCheckId, userId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PURCHASE_CHECK_NOT_FOUND));
        if (purchaseCheck.isAnswered()) {
            throw new BusinessException(ProductErrorCode.PURCHASE_CHECK_ALREADY_ANSWERED);
        }

        purchaseCheck.answer(purchased);
        try {
            purchaseCheckRepository.saveAndFlush(purchaseCheck);
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException(ProductErrorCode.PURCHASE_CHECK_ALREADY_ANSWERED);
        }
        return PurchaseCheckResponse.from(purchaseCheck);
    }

    @Transactional
    public void createPurchaseCheck(Long productLinkClickId) {
        PurchaseCheck purchaseCheck = purchaseCheckRepository.save(
                PurchaseCheck.create(productLinkClickRepository.getReferenceById(productLinkClickId)));

        notificationCreationService.createNotifications(
                Set.of(purchaseCheck.getUserId()),
                NotificationType.PURCHASE_STATUS,
                null,
                NOTIFICATION_TITLE,
                NOTIFICATION_BODY,
                NotificationResourceType.PURCHASE_CHECK,
                purchaseCheck.getId(),
                NOTIFICATION_DEDUP_KEY_PREFIX + purchaseCheck.getId(),
                purchaseCheck.getCreatedAt().plusDays(NOTIFICATION_EXPIRATION_DAYS)
        );
    }
}
