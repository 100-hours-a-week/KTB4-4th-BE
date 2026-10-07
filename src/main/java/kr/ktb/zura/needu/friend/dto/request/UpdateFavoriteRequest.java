package kr.ktb.zura.needu.friend.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateFavoriteRequest(@NotNull Boolean isFavorite) {
}
