package kr.ktb.zura.needu.guidance.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GuidanceContent {

    TASTE_ANALYSIS_REQUIRED(
            "아직 니즈 분석을 진행하지 않았어요.",
            "AI와 대화를 통해 나에게 필요한게 뭔지 알아보세요."
    ),
    TASTE_ANALYSIS_COMPLETED(
            "오늘의 추천",
            "취향 분석 결과를 바탕으로 고른 상품을 확인해 보세요."
    );

    private final String title;
    private final String description;
}
