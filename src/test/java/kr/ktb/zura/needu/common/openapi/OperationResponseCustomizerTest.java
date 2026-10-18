package kr.ktb.zura.needu.common.openapi;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationResponseCustomizerTest {

    @Test
    void unknownControllerMethodName_throwsIllegalStateException() {
        ControllerDocs docs = () -> List.of(OperationDoc.of(SampleController.class, "renamedMethod").build());

        assertThatThrownBy(() -> new OperationResponseCustomizer(List.of(docs)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("renamedMethod");
    }

    static class SampleController {

        public void findSample() {
        }
    }
}
