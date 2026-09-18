package kr.ktb.zura.needu.product.service;

import java.util.List;

import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.product.dto.response.PersonalProductResponse;
import kr.ktb.zura.needu.product.entity.PersonalProduct;
import kr.ktb.zura.needu.product.repository.PersonalProductRepository;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonalProductService {

    private final UserService userService;
    private final PersonalProductRepository personalProductRepository;

    public CursorPageResponse<PersonalProductResponse> findAllPersonalProducts(Long userId, String cursor, int size) {
        userService.findUserSummary(userId);

        // 다음 페이지 존재 여부를 추가 count 쿼리 없이 판단하기 위해 한 건을 더 조회한다.
        List<PersonalProduct> personalProducts = findPersonalProducts(userId, cursor, Limit.of(size + 1));
        boolean hasNext = personalProducts.size() > size;
        List<PersonalProduct> pageItems = hasNext ? personalProducts.subList(0, size) : personalProducts;

        String nextCursor = hasNext ? PersonalProductCursor.from(pageItems.getLast()).encode() : null;
        return new CursorPageResponse<>(
                pageItems.stream().map(PersonalProductResponse::from).toList(),
                nextCursor,
                hasNext
        );
    }

    private List<PersonalProduct> findPersonalProducts(Long userId, String cursor, Limit limit) {
        if (cursor == null) {
            return personalProductRepository.findAllByUserId(userId, limit);
        }
        PersonalProductCursor decodedCursor = PersonalProductCursor.decode(cursor);
        return personalProductRepository.findAllByUserIdAfterCursor(
                userId, decodedCursor.score(), decodedCursor.id(), limit);
    }
}
