package kr.ktb.zura.needu.common.openapi;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import jakarta.validation.Constraint;
import jakarta.validation.Valid;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;

// 컨트롤러 메서드 시그니처로 알 수 있는 오류(요청 형식·입력값)와 *ControllerDocs에 등록한 정보(성공 상태 코드, 도메인 오류)를 문서에 추가한다.
@Component
public class OperationResponseCustomizer implements OperationCustomizer {

    private static final String DEFAULT_SUCCESS_STATUS = String.valueOf(HttpStatus.OK.value());

    private final Map<String, OperationDoc> operationDocs;

    public OperationResponseCustomizer(List<ControllerDocs> controllerDocs) {
        this.operationDocs = controllerDocs.stream()
                .flatMap(docs -> docs.operations().stream())
                .peek(this::validateMethodExists)
                .collect(Collectors.toMap(OperationDoc::key, Function.identity()));
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        OperationDoc doc = operationDocs.get(OperationDoc.keyOf(
                ClassUtils.getUserClass(handlerMethod.getBeanType()), handlerMethod.getMethod().getName()));
        if (doc != null && doc.isPublic()) {
            operation.setSecurity(List.of());
        }
        if (doc != null && !doc.successStatuses().isEmpty()) {
            replaceSuccessResponses(operation, doc.successStatuses());
        }
        if (Arrays.stream(handlerMethod.getMethodParameters()).anyMatch(this::canFailToBind)) {
            ErrorResponseDocs.addError(operation, CommonErrorCode.COMMON_INVALID_REQUEST, null);
        }
        if (Arrays.stream(handlerMethod.getMethodParameters()).anyMatch(this::isValidated)) {
            ErrorResponseDocs.addError(operation, CommonErrorCode.COMMON_INVALID_INPUT, null);
        }
        if (doc != null) {
            doc.errors().forEach(error -> ErrorResponseDocs.addError(operation, error.errorCode(), error.data()));
        }
        return operation;
    }

    // 메서드 이름은 문자열이라 컨트롤러 메서드 이름을 바꾸면 문서 정보가 조용히 빠진다. 문서 생성 시점에 실패시켜 잡는다.
    private void validateMethodExists(OperationDoc doc) {
        boolean exists = Arrays.stream(doc.controllerType().getMethods())
                .anyMatch(method -> method.getName().equals(doc.methodName()));
        if (!exists) {
            throw new IllegalStateException("OperationDoc refers to unknown controller method. key=" + doc.key());
        }
    }

    private void replaceSuccessResponses(Operation operation, List<HttpStatus> statuses) {
        ApiResponses responses = operation.getResponses();
        ApiResponse defaultResponse = responses.remove(DEFAULT_SUCCESS_STATUS);
        for (HttpStatus status : statuses) {
            ApiResponse response = new ApiResponse().description(status.getReasonPhrase());
            if (status.is3xxRedirection()) {
                response.addHeaderObject(HttpHeaders.LOCATION,
                        new Header().description("이동할 주소").schema(new StringSchema()));
            } else if (status != HttpStatus.NO_CONTENT && defaultResponse != null) {
                response.content(defaultResponse.getContent());
            }
            responses.addApiResponse(String.valueOf(status.value()), response);
        }
    }

    // 값이 빠지거나 타입이 맞지 않으면 바인딩 단계에서 400이 된다. 선택 문자열 파라미터는 실패할 수 없다.
    private boolean canFailToBind(MethodParameter parameter) {
        if (parameter.hasParameterAnnotation(RequestBody.class)
                || parameter.hasParameterAnnotation(PathVariable.class)) {
            return true;
        }
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            return requestParam.required() || parameter.getParameterType() != String.class;
        }
        RequestHeader requestHeader = parameter.getParameterAnnotation(RequestHeader.class);
        if (requestHeader != null) {
            return requestHeader.required() || parameter.getParameterType() != String.class;
        }
        return false;
    }

    private boolean isValidated(MethodParameter parameter) {
        return Arrays.stream(parameter.getParameterAnnotations()).anyMatch(this::isValidationAnnotation);
    }

    private boolean isValidationAnnotation(Annotation annotation) {
        return annotation instanceof Valid
                || annotation instanceof Validated
                || annotation.annotationType().isAnnotationPresent(Constraint.class);
    }
}
