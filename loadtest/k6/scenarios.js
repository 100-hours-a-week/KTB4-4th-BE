import { check, fail, sleep } from 'k6';
import k6Crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import http from 'k6/http';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_SECRET = __ENV.JWT_SECRET;
const READ_VUS = Number(__ENV.READ_VUS || 20);
const AI_VUS = Number(__ENV.AI_VUS || 10);
const AI_TURNS = Number(__ENV.AI_TURNS || 2);
const MOCK_MAX_TURNS = Number(__ENV.AI_SERVER_MOCK_MAX_TURNS || 2);
const SCENARIO = __ENV.SCENARIO || 'read';
const AI_REPLY_TIMEOUT_SECONDS = Number(__ENV.AI_REPLY_TIMEOUT_SECONDS || 30);
const AI_REPLY_POLL_INTERVAL_SECONDS = Number(__ENV.AI_REPLY_POLL_INTERVAL_SECONDS || 0.5);

// 메시지 전송은 접수(202)만 하고 답변은 백그라운드에서 만든다 => 접수부터 답변 저장까지 걸린 시간을 따로 잰다
const aiReplyDuration = new Trend('ai_reply_duration', true);

if (!JWT_SECRET) {
    throw new Error('JWT_SECRET is required. Export loadtest/.env before running k6.');
}
if (READ_VUS < 1 || READ_VUS > 200 || AI_VUS < 1 || AI_VUS > 200) {
    throw new Error('READ_VUS and AI_VUS must be between 1 and the seeded user limit, 200.');
}
if (AI_TURNS !== MOCK_MAX_TURNS) {
    throw new Error('AI_TURNS must equal AI_SERVER_MOCK_MAX_TURNS.');
}
if (!['read', 'ai'].includes(SCENARIO)) {
    throw new Error('SCENARIO must be read or ai.');
}

const readScenarioOptions = {
    executor: 'ramping-vus',
    exec: 'readScenario',
    startVUs: 1,
    stages: [
        { duration: __ENV.READ_RAMP_UP || '30s', target: READ_VUS },
        { duration: __ENV.READ_HOLD || '2m', target: READ_VUS },
        { duration: __ENV.READ_RAMP_DOWN || '15s', target: 0 },
    ],
    gracefulRampDown: '10s',
};

const aiScenarioOptions = {
    executor: 'per-vu-iterations',
    exec: 'aiFlowScenario',
    vus: AI_VUS,
    iterations: 1,
    maxDuration: __ENV.AI_MAX_DURATION || '5m',
};

export const options = {
    discardResponseBodies: false,
    scenarios: SCENARIO === 'read'
        ? { read: readScenarioOptions }
        : { ai_flow: aiScenarioOptions },
    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate<0.01'],
        dropped_iterations: ['count==0'],
    },
};

export function readScenario() {
    const userId = 1 + ((__VU - 1) % 200);
    const friendUserId = (userId % 200) + 1;
    authenticate(userId);

    requestTwoPages(
        `${BASE_URL}/api/v1/friends?sort=birthday&size=20`,
        'friends',
    );
    requestTwoPages(
        `${BASE_URL}/api/v1/users/me/personal-recommendations?size=20`,
        'personal_recommendations',
    );
    requestTwoPages(
        `${BASE_URL}/api/v1/users/${friendUserId}/gift-recommendations?size=20`,
        'gift_recommendations',
    );
    sleep(Number(__ENV.READ_THINK_TIME || 0.2));
}

export function aiFlowScenario() {
    const userId = 200 + __VU;
    authenticate(userId);
    const csrfToken = fetchCsrfToken();
    const requestParams = {
        headers: {
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': csrfToken,
        },
        timeout: '30s',
    };

    const startResponse = http.post(
        `${BASE_URL}/api/v1/ai/conversations`,
        null,
        named(requestParams, 'ai_start'),
    );
    requireStatus(startResponse, [200, 201], 'AI conversation starts');
    const conversationId = startResponse.json('data.conversationId');
    if (!conversationId) {
        fail('AI conversation response did not contain conversationId');
    }

    for (let turn = 1; turn <= AI_TURNS; turn += 1) {
        const messageResponse = http.post(
            `${BASE_URL}/api/v1/ai/conversations/${conversationId}/messages`,
            JSON.stringify({
                clientMessageId: crypto.randomUUID(),
                content: `load test message ${turn}`,
            }),
            named(requestParams, 'ai_message'),
        );
        requireStatus(messageResponse, [202], `AI message ${turn} is accepted`);
        const userMessageId = messageResponse.json('data.userMessageId');
        if (!userMessageId) {
            fail('AI message response did not contain userMessageId');
        }
        waitForReply(conversationId, userMessageId, requestParams, turn);
    }

    const analysisResponse = http.post(
        `${BASE_URL}/api/v1/ai/conversations/${conversationId}/analysis`,
        null,
        named(requestParams, 'ai_analysis'),
    );
    requireStatus(analysisResponse, [200], 'AI analysis succeeds');

    const confirmResponse = http.post(
        `${BASE_URL}/api/v1/ai/conversations/${conversationId}/confirm`,
        null,
        named(requestParams, 'ai_confirm'),
    );
    requireStatus(confirmResponse, [200], 'AI analysis confirmation succeeds');
}

function waitForReply(conversationId, userMessageId, requestParams, turn) {
    const acceptedAt = Date.now();
    const replied = pollUntilReplied(conversationId, userMessageId, requestParams, turn);
    check(replied, { [`AI message ${turn} is replied`]: (value) => value });
    if (!replied) {
        fail(`AI message ${turn} was not replied within ${AI_REPLY_TIMEOUT_SECONDS}s`);
    }
    aiReplyDuration.add(Date.now() - acceptedAt);
}

// 답변은 SSE로 오지만 k6에는 SSE 클라이언트가 없다 => 가장 최근 메시지가 이 메시지 이후의 AI 답변이 될 때까지 목록을 조회한다
function pollUntilReplied(conversationId, userMessageId, requestParams, turn) {
    const deadline = Date.now() + AI_REPLY_TIMEOUT_SECONDS * 1000;
    while (Date.now() < deadline) {
        const response = http.get(
            `${BASE_URL}/api/v1/ai/conversations/${conversationId}/messages?size=1`,
            named(requestParams, 'ai_reply_poll'),
        );
        requireStatus(response, [200], `AI message ${turn} history is readable`);
        const latest = response.json('data.items.0');
        if (latest && latest.role === 'AI' && latest.messageId > userMessageId) {
            return true;
        }
        sleep(AI_REPLY_POLL_INTERVAL_SECONDS);
    }
    return false;
}

function requestTwoPages(firstUrl, requestName) {
    const firstResponse = http.get(firstUrl, named({}, `${requestName}_first`));
    requireStatus(firstResponse, [200], `${requestName} first page succeeds`);
    const cursor = firstResponse.json('nextCursor');
    check(cursor, { [`${requestName} has next page`]: (value) => Boolean(value) });
    if (!cursor) {
        fail(`${requestName} did not return a next cursor`);
    }
    const separator = firstUrl.includes('?') ? '&' : '?';
    const secondResponse = http.get(
        `${firstUrl}${separator}cursor=${encodeURIComponent(cursor)}`,
        named({}, `${requestName}_second`),
    );
    requireStatus(secondResponse, [200], `${requestName} second page succeeds`);
}

function authenticate(userId) {
    http.cookieJar().set(BASE_URL, 'NEEDU_ACCESS_TOKEN', createAccessToken(userId), {
        path: '/',
    });
}

function fetchCsrfToken() {
    const response = http.get(`${BASE_URL}/api/v1/auth/csrf`, named({}, 'csrf'));
    requireStatus(response, [200], 'CSRF token is issued');
    const token = response.json('data.token');
    if (!token) {
        fail('CSRF response did not contain a token');
    }
    return token;
}

function createAccessToken(userId) {
    const now = Math.floor(Date.now() / 1000);
    const header = encoding.b64encode(JSON.stringify({ alg: 'HS256' }), 'rawurl');
    const payload = encoding.b64encode(JSON.stringify({
        userId,
        iat: now,
        exp: now + 3600,
    }), 'rawurl');
    const unsignedToken = `${header}.${payload}`;
    const decodedSecret = encoding.b64decode(JWT_SECRET, 'std');
    const signature = k6Crypto.hmac('sha256', decodedSecret, unsignedToken, 'base64rawurl');
    return `${unsignedToken}.${signature}`;
}

function named(params, name) {
    return { ...params, tags: { ...(params.tags || {}), name } };
}

function requireStatus(response, expectedStatuses, message) {
    const succeeded = check(response, {
        [message]: (result) => expectedStatuses.includes(result.status),
    });
    if (!succeeded) {
        fail(`${message}: status=${response.status}, body=${response.body}`);
    }
}
