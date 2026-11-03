package kr.ktb.zura.needu.aichat.entity;

import java.time.LocalDateTime;

import kr.ktb.zura.needu.aichat.type.AiReplyJobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiReplyJobTest {

    private static final Long USER_MESSAGE_ID = 201L;
    private static final Long USER_ID = 1L;
    private static final String LOCK_TOKEN = "lock-token-1";
    private static final String NEW_LOCK_TOKEN = "lock-token-2";
    private static final String ERROR_CODE = "AICHAT_REQUEST_TIMEOUT";
    private static final LocalDateTime FINISHED_AT = LocalDateTime.of(2026, 11, 3, 12, 0);

    @Test
    void newJob_create_isQueuedWithFirstAttemptNotTakenAndDispatchToken() {
        AiChatRoom room = AiChatRoom.reserve(USER_ID);

        AiReplyJob job = AiReplyJob.create(room, USER_MESSAGE_ID, USER_ID, LOCK_TOKEN);

        assertThat(job.getAiChatRoom()).isSameAs(room);
        assertThat(job.getUserMessageId()).isEqualTo(USER_MESSAGE_ID);
        assertThat(job.getUserId()).isEqualTo(USER_ID);
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.QUEUED);
        assertThat(job.getAttempt()).isZero();
        assertThat(job.getDispatchToken()).isNotBlank();
        assertThat(job.getLockToken()).isEqualTo(LOCK_TOKEN);
        assertThat(job.getFinishedAt()).isNull();
    }

    @Test
    void queuedJob_start_becomesRunning() {
        AiReplyJob job = queuedJob();

        job.start();

        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.RUNNING);
    }

    @Test
    void runningJob_start_throws() {
        AiReplyJob job = runningJob();

        assertThatThrownBy(job::start).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void runningJob_succeed_becomesSucceededWithFinishedAt() {
        AiReplyJob job = runningJob();

        job.succeed(FINISHED_AT);

        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.SUCCEEDED);
        assertThat(job.getFinishedAt()).isEqualTo(FINISHED_AT);
        assertThat(job.isFinished()).isTrue();
    }

    @Test
    void queuedJob_succeed_throws() {
        AiReplyJob job = queuedJob();

        assertThatThrownBy(() -> job.succeed(FINISHED_AT)).isInstanceOf(IllegalStateException.class);
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.QUEUED);
    }

    @Test
    void succeededJob_succeed_throws() {
        AiReplyJob job = succeededJob();

        assertThatThrownBy(() -> job.succeed(FINISHED_AT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedJob_succeed_throwsAndStaysFailed() {
        AiReplyJob job = failedJob();

        assertThatThrownBy(() -> job.succeed(FINISHED_AT)).isInstanceOf(IllegalStateException.class);
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.FAILED);
    }

    @Test
    void runningJob_fail_becomesFailedWithErrorCodeAndFinishedAt() {
        AiReplyJob job = runningJob();

        job.fail(ERROR_CODE, FINISHED_AT);

        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.FAILED);
        assertThat(job.getLastErrorCode()).isEqualTo(ERROR_CODE);
        assertThat(job.getFinishedAt()).isEqualTo(FINISHED_AT);
        assertThat(job.isFinished()).isTrue();
    }

    @Test
    void queuedJob_fail_becomesFailed() {
        AiReplyJob job = queuedJob();

        job.fail(ERROR_CODE, FINISHED_AT);

        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.FAILED);
    }

    @Test
    void succeededJob_fail_throwsAndStaysSucceeded() {
        AiReplyJob job = succeededJob();

        assertThatThrownBy(() -> job.fail(ERROR_CODE, FINISHED_AT)).isInstanceOf(IllegalStateException.class);
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.SUCCEEDED);
        assertThat(job.getLastErrorCode()).isNull();
    }

    @Test
    void failedJob_fail_throws() {
        AiReplyJob job = failedJob();

        assertThatThrownBy(() -> job.fail(ERROR_CODE, FINISHED_AT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedJob_restart_requeuesWithNewDispatchAndLockTokens() {
        AiReplyJob job = failedJob();
        String previousDispatchToken = job.getDispatchToken();

        job.restart(NEW_LOCK_TOKEN);

        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.QUEUED);
        assertThat(job.getDispatchToken()).isNotBlank().isNotEqualTo(previousDispatchToken);
        assertThat(job.getLockToken()).isEqualTo(NEW_LOCK_TOKEN);
        assertThat(job.getFinishedAt()).isNull();
        assertThat(job.isFinished()).isFalse();
    }

    @Test
    void failedJobWithAttempts_restart_keepsAttempt() {
        AiReplyJob job = failedJob();
        // 가져가기(attempt 증가)는 다음 작업에서 추가되므로 이미 두 번 가져간 상태를 직접 만든다
        ReflectionTestUtils.setField(job, "attempt", 2);

        job.restart(NEW_LOCK_TOKEN);

        assertThat(job.getAttempt()).isEqualTo(2);
    }

    @Test
    void runningJob_restart_throws() {
        AiReplyJob job = runningJob();

        assertThatThrownBy(() -> job.restart(NEW_LOCK_TOKEN)).isInstanceOf(IllegalStateException.class);
        assertThat(job.getLockToken()).isEqualTo(LOCK_TOKEN);
    }

    @Test
    void succeededJob_restart_throws() {
        AiReplyJob job = succeededJob();

        assertThatThrownBy(() -> job.restart(NEW_LOCK_TOKEN)).isInstanceOf(IllegalStateException.class);
    }

    private static AiReplyJob queuedJob() {
        return AiReplyJob.create(AiChatRoom.reserve(USER_ID), USER_MESSAGE_ID, USER_ID, LOCK_TOKEN);
    }

    private static AiReplyJob runningJob() {
        AiReplyJob job = queuedJob();
        job.start();
        return job;
    }

    private static AiReplyJob succeededJob() {
        AiReplyJob job = runningJob();
        job.succeed(FINISHED_AT);
        return job;
    }

    private static AiReplyJob failedJob() {
        AiReplyJob job = runningJob();
        job.fail(ERROR_CODE, FINISHED_AT);
        return job;
    }
}
