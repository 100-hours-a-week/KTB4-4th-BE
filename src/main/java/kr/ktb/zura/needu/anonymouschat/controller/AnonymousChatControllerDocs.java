package kr.ktb.zura.needu.anonymouschat.controller;

import java.util.List;
import kr.ktb.zura.needu.common.openapi.ControllerDocs;
import kr.ktb.zura.needu.common.openapi.OperationDoc;
import org.springframework.stereotype.Component;

@Component
public class AnonymousChatControllerDocs implements ControllerDocs {

    @Override
    public List<OperationDoc> operations() {
        return List.of(
                OperationDoc.of(AnonymousChatController.class, "findAllRooms").build(),
                OperationDoc.of(AnonymousChatController.class, "createRoom").build(),
                OperationDoc.of(AnonymousChatController.class, "findRoom").build(),
                OperationDoc.of(AnonymousChatController.class, "findAllMessages").build(),
                OperationDoc.of(AnonymousChatController.class, "readMessages").build(),
                OperationDoc.of(AnonymousChatController.class, "reportChat").build(),
                OperationDoc.of(AnonymousChatController.class, "leaveRoom").build()
        );
    }
}
