package kr.ktb.zura.needu.user.type;

import java.util.Arrays;

import lombok.Getter;

@Getter
public enum ConsentType {
    PRIVACY_COLLECTION(1L, "개인정보 수집 및 이용 동의",
            "온보딩에서 입력한 성별·생년월일·관심 카테고리·알레르기·선물 제외 조건을 회원 관리와 맞춤 상품 추천에 이용합니다.", true, "1.0"),
    AI_CONVERSATION(2L, "AI 대화 정보 활용 동의",
            "AI와 나눈 대화 내용을 취향 분석에 활용하고, 분석한 취향 키워드와 요약으로 나에게 맞는 상품을 추천합니다.",
            true, "1.0"),
    FRIEND_TASTE_SHARING(3L, "친구에게 취향 정보 공개 동의",
            "카카오톡 친구 중 NeedU 회원에게 내 프로필, 생년월일, 취향·관심 키워드와 AI 요약, "
                    + "이를 바탕으로 한 선물 추천 목록을 보여 줍니다.", true, "1.0"),
    PRODUCT_ACTIVITY(4L, "상품 이용 기록 활용 동의",
            "추천 상품에 남긴 만족도(마음에 들어요·별로예요), 구매 링크 클릭, 구매 여부 응답을 "
                    + "추천 상품을 고르고 개선하는 데 활용합니다.", true, "1.0");

    private final Long id;
    private final String title;
    private final String content;
    private final boolean required;
    private final String version;

    ConsentType(Long id, String title, String content, boolean required, String version) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.required = required;
        this.version = version;
    }

    public static boolean existsById(Long id) {
        return Arrays.stream(values()).anyMatch(type -> type.id.equals(id));
    }

    public static ConsentType fromId(Long id) {
        return Arrays.stream(values())
                .filter(type -> type.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown consent id: " + id));
    }

    public boolean isCurrentVersion(String version) {
        return this.version.equals(version);
    }
}
