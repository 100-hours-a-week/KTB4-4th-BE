package kr.ktb.zura.needu.common.response;

import java.util.List;

public record ItemsResponse<T>(List<T> items) {
}
