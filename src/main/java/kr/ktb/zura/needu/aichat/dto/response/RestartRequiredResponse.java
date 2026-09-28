package kr.ktb.zura.needu.aichat.dto.response;

// 같은 상태 코드의 다른 오류와 구분해 FE가 새 대화 시작으로 안내하도록 오류 응답 data에 담는다
public record RestartRequiredResponse(boolean restartRequired) {
}
