package com.chengtan.common.cache;

/**
 * 缓存 key 集中管理，避免散落在业务代码里。
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    public static final String VENUE_TYPES = "cache:venue:types";
    public static final String VENUE_DETAIL = "cache:venue:detail:";
    public static final String SESSION_DETAIL = "cache:session:detail:";
    public static final String VENUE_GEO = "venue:geo:";

    public static final String SECKILL_STOCK = "ticket:stock:";
    public static final String SECKILL_USER_COUNT = "ticket:bought:";
    public static final String SECKILL_ATTENDEE = "ticket:attendee:";
    public static final String SECKILL_META = "ticket:session:";
    public static final String ORDER_CREATE_LOCK = "lock:order:create:";
    public static final String ORDER_REQUEST = "order:req:";
    public static final String WAITLIST_QUEUE = "waitlist:queue:";
    public static final String WAITLIST_OFFER_LOCK = "lock:waitlist:offer:";
    public static final String RESCHEDULE_LOCK = "lock:reschedule:";
}
