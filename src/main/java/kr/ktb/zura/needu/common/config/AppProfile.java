package kr.ktb.zura.needu.common.config;

// 같은 이미지를 역할별로 띄울 때 쓰는 프로필
public final class AppProfile {

    // AI 답변 워커로만 띄운 인스턴스. 다른 배치와 스트림 소비는 API 인스턴스가 맡는다
    public static final String AI_WORKER = "ai-worker";
    public static final String NOT_AI_WORKER = "!" + AI_WORKER;

    private AppProfile() {
    }
}
