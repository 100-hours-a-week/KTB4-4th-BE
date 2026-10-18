package kr.ktb.zura.needu.product.service;

import java.time.Duration;
import java.time.LocalDateTime;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.product.dto.request.CreateProductLinkClickRequest;
import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.entity.ProductLinkClick;
import kr.ktb.zura.needu.product.repository.ProductLinkClickRepository;
import kr.ktb.zura.needu.product.repository.ProductRepository;
import kr.ktb.zura.needu.product.type.ProductContext;
import kr.ktb.zura.needu.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductLinkClickService {

    private final UserService userService;
    private final ProductRepository productRepository;
    private final ProductLinkClickRepository productLinkClickRepository;
    private final Duration duplicateClickWindow;

    public ProductLinkClickService(
            UserService userService,
            ProductRepository productRepository,
            ProductLinkClickRepository productLinkClickRepository,
            @Value("${needu.product.link-click.duplicate-window:7d}") Duration duplicateClickWindow
    ) {
        this.userService = userService;
        this.productRepository = productRepository;
        this.productLinkClickRepository = productLinkClickRepository;
        this.duplicateClickWindow = duplicateClickWindow;
    }

    @Transactional
    public void createLinkClick(Long userId, CreateProductLinkClickRequest request) {
        userService.validateActiveUser(userId);

        ProductContext context = ProductContext.valueOf(request.context());
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT));

        LocalDateTime since = LocalDateTime.now().minus(duplicateClickWindow);

        if (productLinkClickRepository.existsRecentClick(userId, product.getId(), context, since)) {
            return;
        }

        productLinkClickRepository.save(
                new ProductLinkClick(userId, product, context, toFriendUserId(context, request.friendUserId())));
    }

    private Long toFriendUserId(ProductContext context, Long friendUserId) {
        return context == ProductContext.FRIEND_GIFT ? friendUserId : null;
    }
}
