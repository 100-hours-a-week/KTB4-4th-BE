package kr.ktb.zura.needu.common.openapi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import kr.ktb.zura.needu.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

// 컨트롤러 메서드 하나의 문서 정보. 컨트롤러 메서드 정보만으로는 알 수 없는 것만 담는다.
public record OperationDoc(
        Class<?> controllerType,
        String methodName,
        boolean isPublic,
        List<HttpStatus> successStatuses,
        List<ErrorDoc> errors
) {

    public static Builder of(Class<?> controllerType, String methodName) {
        return new Builder(controllerType, methodName);
    }

    String key() {
        return keyOf(controllerType, methodName);
    }

    static String keyOf(Class<?> controllerType, String methodName) {
        return controllerType.getName() + "#" + methodName;
    }

    public record ErrorDoc(ErrorCode errorCode, Object data) {
    }

    public static final class Builder {

        private final Class<?> controllerType;
        private final String methodName;
        private final List<HttpStatus> successStatuses = new ArrayList<>();
        private final List<ErrorDoc> errors = new ArrayList<>();
        private boolean isPublic;

        private Builder(Class<?> controllerType, String methodName) {
            this.controllerType = controllerType;
            this.methodName = methodName;
        }

        // SecurityConfig에서 permitAll인 API. 문서에서 인증 요구와 401을 뺀다.
        public Builder publicApi() {
            this.isPublic = true;
            return this;
        }

        // ResponseEntity.status(...)로 정한 상태 코드는 문서 생성기가 알 수 없어 200으로 표시되므로 직접 지정한다.
        public Builder successStatus(HttpStatus... statuses) {
            successStatuses.addAll(Arrays.asList(statuses));
            return this;
        }

        public Builder errors(ErrorCode... errorCodes) {
            return errors(Arrays.asList(errorCodes));
        }

        public Builder errors(List<? extends ErrorCode> errorCodes) {
            errorCodes.forEach(errorCode -> errors.add(new ErrorDoc(errorCode, null)));
            return this;
        }

        // 오류 응답 data가 있는 경우
        public Builder error(ErrorCode errorCode, Object data) {
            errors.add(new ErrorDoc(errorCode, data));
            return this;
        }

        public OperationDoc build() {
            return new OperationDoc(controllerType, methodName, isPublic,
                    List.copyOf(successStatuses), List.copyOf(errors));
        }
    }
}
