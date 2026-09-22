package kr.ktb.zura.needu.aichat.dto.response;

import java.util.Map;

public record AiServerRanking(String mode, Map<String, Double> factors, String rankerVersion) {
}
