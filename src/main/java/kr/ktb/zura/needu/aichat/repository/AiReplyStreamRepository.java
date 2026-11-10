package kr.ktb.zura.needu.aichat.repository;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import kr.ktb.zura.needu.common.redis.StreamRedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class AiReplyStreamRepository {

    static final String GROUP = "aichat-reply-workers";
    private static final String JOB_ID_FIELD = "jobId";
    private static final String DISPATCH_TOKEN_FIELD = "dispatchToken";
    private static final String BUSY_GROUP_ERROR = "BUSYGROUP";
    private static final String NO_GROUP_ERROR = "NOGROUP";
    private static final long FIRST_DELIVERY = 1;

    private static final DefaultRedisScript<List> CLAIM_SCRIPT = new DefaultRedisScript<>("""
            local cursor = '0-0'
            local deleted = {}
            repeat
                local result = redis.call('XAUTOCLAIM', KEYS[1], ARGV[1], ARGV[2], ARGV[3], cursor, 'COUNT', 1)
                cursor = result[1]
                for _, id in ipairs(result[3]) do
                    deleted[#deleted + 1] = id
                end
                local entries = result[2]
                if #entries > 0 then
                    local id = entries[1][1]
                    local pending = redis.call('XPENDING', KEYS[1], ARGV[1], id, id, 1)
                    return { id, entries[1][2], pending[1][4], deleted }
                end
            until cursor == '0-0'
            return { '', {}, 0, deleted }
            """, List.class);

    private static final DefaultRedisScript<Long> HEARTBEAT_SCRIPT = new DefaultRedisScript<>("""
            local pending = redis.call('XPENDING', KEYS[1], ARGV[1], ARGV[3], ARGV[3], 1)
            if #pending == 0 then
                return -1
            end
            if pending[1][2] ~= ARGV[2] then
                return 0
            end
            redis.call('XCLAIM', KEYS[1], ARGV[1], ARGV[2], 0, ARGV[3], 'JUSTID')
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> ACKNOWLEDGE_AND_DELETE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('XACK', KEYS[1], ARGV[1], ARGV[2])
            return redis.call('XDEL', KEYS[1], ARGV[2])
            """, Long.class);

    // 항목 ID 앞부분이 넣은 시각(ms) => 그룹이 마지막으로 할당한 ID 다음 항목이 가장 오래 기다린 항목
    private static final DefaultRedisScript<List> STATUS_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return { 0, 0, 0 }
            end
            local time = redis.call('TIME')
            local now = time[1] * 1000 + math.floor(time[2] / 1000)
            local pending = 0
            local start = '-'
            for _, group in ipairs(redis.call('XINFO', 'GROUPS', KEYS[1])) do
                local info = {}
                for i = 1, #group, 2 do
                    info[group[i]] = group[i + 1]
                end
                if info['name'] == ARGV[1] then
                    pending = info['pending']
                    start = '(' .. info['last-delivered-id']
                end
            end
            local waiting = redis.call('XRANGE', KEYS[1], start, '+', 'COUNT', 1)
            local oldestWaiting = 0
            if #waiting > 0 then
                local id = waiting[1][1]
                local addedAt = tonumber(string.sub(id, 1, string.find(id, '-', 1, true) - 1))
                oldestWaiting = math.max(now - addedAt, 0)
            end
            return { redis.call('XLEN', KEYS[1]), pending, oldestWaiting }
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final String key;

    public AiReplyStreamRepository(
            @Qualifier(StreamRedisConfig.STREAM_REDIS_TEMPLATE) StringRedisTemplate redisTemplate,
            RedisKeyGenerator keyGenerator
    ) {
        this.redisTemplate = redisTemplate;
        this.key = keyGenerator.generate("aichat", "reply-jobs");
    }

    // 처음부터(0) 읽는다 <- Redis가 데이터를 잃은 뒤 그룹보다 먼저 다시 넣은 작업도 처리되도록
    public void createGroupIfAbsent() {
        try {
            redisTemplate.opsForStream().createGroup(key, ReadOffset.from("0"), GROUP);
        } catch (DataAccessException e) {
            if (!isRedisError(e, BUSY_GROUP_ERROR)) {
                throw e;
            }
        }
    }

    public String add(Long jobId, String dispatchToken) {
        RecordId recordId = redisTemplate.opsForStream().add(key, Map.of(
                JOB_ID_FIELD, String.valueOf(jobId),
                DISPATCH_TOKEN_FIELD, dispatchToken));
        return recordId.getValue();
    }

    public Optional<AiReplyStreamEntry> readNew(String consumerName) {
        List<MapRecord<String, Object, Object>> records;
        try {
            records = redisTemplate.opsForStream().read(
                    Consumer.from(GROUP, consumerName),
                    StreamReadOptions.empty().count(1),
                    StreamOffset.create(key, ReadOffset.lastConsumed()));
        } catch (DataAccessException e) {
            if (!isRedisError(e, NO_GROUP_ERROR)) {
                throw e;
            }
            log.warn("AI reply stream group missing. Recreating group.");
            createGroupIfAbsent();
            return Optional.empty();
        }
        if (records == null || records.isEmpty()) {
            return Optional.empty();
        }
        MapRecord<String, Object, Object> record = records.getFirst();
        return Optional.of(toEntry(record.getId().getValue(), record.getValue(), FIRST_DELIVERY));
    }

    @SuppressWarnings("unchecked")
    public AiReplyStreamClaimResult claimIdle(String consumerName, Duration minIdle) {
        List<Object> result = redisTemplate.execute(CLAIM_SCRIPT, List.of(key),
                GROUP, consumerName, String.valueOf(minIdle.toMillis()));
        if (result == null || result.size() != 4) {
            throw new IllegalStateException("Invalid Redis stream claim result");
        }
        List<String> deletedEntryIds = ((List<Object>) result.get(3)).stream().map(String::valueOf).toList();
        String entryId = String.valueOf(result.get(0));
        if (entryId.isEmpty()) {
            return new AiReplyStreamClaimResult(null, deletedEntryIds);
        }
        Map<Object, Object> fields = toFieldMap((List<Object>) result.get(1));
        long deliveryCount = Long.parseLong(String.valueOf(result.get(2)));
        return new AiReplyStreamClaimResult(toEntry(entryId, fields, deliveryCount), deletedEntryIds);
    }

    public AiReplyHeartbeatResult heartbeat(String consumerName, String entryId) {
        Long result = redisTemplate.execute(HEARTBEAT_SCRIPT, List.of(key), GROUP, consumerName, entryId);
        if (result == null) {
            throw new IllegalStateException("Invalid Redis stream heartbeat result");
        }
        if (result == 1) {
            return AiReplyHeartbeatResult.OWNED;
        }
        return result == 0 ? AiReplyHeartbeatResult.CLAIMED_BY_OTHER : AiReplyHeartbeatResult.NOT_PENDING;
    }

    public void acknowledgeAndDelete(String entryId) {
        redisTemplate.execute(ACKNOWLEDGE_AND_DELETE_SCRIPT, List.of(key), GROUP, entryId);
    }

    public long size() {
        Long size = redisTemplate.opsForStream().size(key);
        return size == null ? 0 : size;
    }

    public AiReplyStreamStatus findStatus() {
        List<?> result = redisTemplate.execute(STATUS_SCRIPT, List.of(key), GROUP);
        if (result == null || result.size() != 3) {
            throw new IllegalStateException("Invalid Redis stream status result");
        }
        return new AiReplyStreamStatus(
                Long.parseLong(String.valueOf(result.get(0))),
                Long.parseLong(String.valueOf(result.get(1))),
                Duration.ofMillis(Long.parseLong(String.valueOf(result.get(2)))));
    }

    public boolean isPending(String entryId) {
        try {
            return !redisTemplate.opsForStream().pending(key, GROUP, Range.closed(entryId, entryId), 1).isEmpty();
        } catch (DataAccessException e) {
            if (!isRedisError(e, NO_GROUP_ERROR)) {
                throw e;
            }
            return false;
        }
    }

    public boolean exists(String entryId) {
        return !redisTemplate.opsForStream().range(key, Range.closed(entryId, entryId)).isEmpty();
    }

    private AiReplyStreamEntry toEntry(String entryId, Map<Object, Object> fields, long deliveryCount) {
        return new AiReplyStreamEntry(
                entryId,
                toJobId(fields.get(JOB_ID_FIELD)),
                fields.get(DISPATCH_TOKEN_FIELD) == null ? null : String.valueOf(fields.get(DISPATCH_TOKEN_FIELD)),
                deliveryCount);
    }

    private Long toJobId(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<Object, Object> toFieldMap(List<Object> flatFields) {
        Map<Object, Object> fields = new HashMap<>();
        for (int i = 0; i + 1 < flatFields.size(); i += 2) {
            fields.put(String.valueOf(flatFields.get(i)), flatFields.get(i + 1));
        }
        return fields;
    }

    private boolean isRedisError(DataAccessException e, String errorPrefix) {
        String message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
        return message != null && message.contains(errorPrefix);
    }
}
