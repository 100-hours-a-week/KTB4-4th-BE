package kr.ktb.zura.needu.aichat.client.dto.request;

import java.util.List;

public record AiServerAnalysisKeywordsRequest(List<String> taste, List<String> interest) {
}
