package kr.ktb.zura.needu.product.service;

import java.util.List;
import java.util.Set;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.friend.service.FriendService;
import kr.ktb.zura.needu.notification.service.NotificationCreationService;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import kr.ktb.zura.needu.product.dto.response.PurchaseCheckDetailResponse;
import kr.ktb.zura.needu.product.dto.response.PurchaseCheckFriendResponse;
import kr.ktb.zura.needu.product.dto.response.PurchaseCheckResponse;
import kr.ktb.zura.needu.product.entity.ProductLinkClick;
import kr.ktb.zura.needu.product.entity.PurchaseCheck;
import kr.ktb.zura.needu.product.exception.ProductErrorCode;
import kr.ktb.zura.needu.product.repository.ProductLinkClickRepository;
import kr.ktb.zura.needu.product.repository.PurchaseCheckRepository;
import kr.ktb.zura.needu.product.type.ProductContext;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseCheckService {

    private static final String PERSONAL_NOTIFICATION_TITLE = "추천받은 상품, 구매하셨나요?";
    private static final String MY_GIFT_NOTIFICATION_TITLE = "내 선물 추천에서 본 상품, 구매하셨나요?";
    private static final String FRIEND_GIFT_NOTIFICATION_TITLE = "친구 선물로 본 상품, 구매하셨나요?";
    private static final String NOTIFICATION_BODY = "구매 여부를 알려주시면 추천이 더 정확해져요.";
    private static final String NOTIFICATION_DEDUP_KEY_PREFIX = "PURCHASE_CHECK:";
    private static final long NOTIFICATION_EXPIRATION_DAYS = 7;

    private final UserService userService;
    private final FriendService friendService;
    private final NotificationCreationService notificationCreationService;
    private final PurchaseCheckRepository purchaseCheckRepository;
    private final ProductLinkClickRepository productLinkClickRepository;

    public PurchaseCheckDetailResponse findPurchaseCheck(Long userId, Long purchaseCheckId) {
        userService.validateActiveUser(userId);

        PurchaseCheck purchaseCheck = purchaseCheckRepository.findWithProductByIdAndUserId(purchaseCheckId, userId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PURCHASE_CHECK_NOT_FOUND));
        PurchaseCheckFriendResponse friend = findFriend(userId, purchaseCheck.getProductLinkClick());
        return PurchaseCheckDetailResponse.from(purchaseCheck, friend);
    }

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
        ProductLinkClick click = productLinkClickRepository.getReferenceById(productLinkClickId);
        PurchaseCheck purchaseCheck = purchaseCheckRepository.save(PurchaseCheck.create(click));

        notificationCreationService.createNotifications(
                Set.of(purchaseCheck.getUserId()),
                NotificationType.PURCHASE_STATUS,
                null,
                toNotificationTitle(click.getContext()),
                NOTIFICATION_BODY,
                NotificationResourceType.PURCHASE_CHECK,
                purchaseCheck.getId(),
                NOTIFICATION_DEDUP_KEY_PREFIX + purchaseCheck.getId(),
                purchaseCheck.getCreatedAt().plusDays(NOTIFICATION_EXPIRATION_DAYS)
        );
    }

    private String toNotificationTitle(ProductContext context) {
        return switch (context) {
            case PERSONAL -> PERSONAL_NOTIFICATION_TITLE;
            case MY_GIFT -> MY_GIFT_NOTIFICATION_TITLE;
            case FRIEND_GIFT -> FRIEND_GIFT_NOTIFICATION_TITLE;
        };
    }

    private PurchaseCheckFriendResponse findFriend(Long userId, ProductLinkClick click) {
        if (click.getContext() != ProductContext.FRIEND_GIFT || click.getFriendUserId() == null) {
            return null;
        }
        String name = friendService.findAllFriendNicknames(userId, List.of(click.getFriendUserId()))
                .get(click.getFriendUserId());
        return name == null ? null : new PurchaseCheckFriendResponse(click.getFriendUserId(), name);
    }
}
