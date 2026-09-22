package kr.ktb.zura.needu.aichat.client.dto.response;

import java.util.Map;

public record AiServerRanking(String mode, Map<String, Double> factors, String rankerVersion) {
}
