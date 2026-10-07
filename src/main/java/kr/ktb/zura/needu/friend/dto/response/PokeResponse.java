package kr.ktb.zura.needu.friend.dto.response;

import java.time.LocalDateTime;

public record PokeResponse(
        Long pokeId,
        Long friendUserId,
        LocalDateTime createdAt,
        LocalDateTime nextPokeAvailableAt
) {
}
