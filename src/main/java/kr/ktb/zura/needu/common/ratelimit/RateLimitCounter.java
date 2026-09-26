package kr.ktb.zura.needu.common.ratelimit;

public interface RateLimitCounter {

    RateLimitUsage increment(String policyName, String userId);
}
