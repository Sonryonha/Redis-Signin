package com.son.signin.server;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;


@Service
public class SignService {

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 用户签到，可以补签
     *
     * @param userId  用户ID
     * @param dateStr 查询的日期，默认当天 yyyy-MM-dd
     * @return 连续签到次数和总签到次数
     */
    public Map<String, Object> doSign(Integer userId, String dateStr) {
        Map<String, Object> result = new HashMap<>();
        // 获取日期
        Date date = getDate(dateStr);
        // 获取日期对应的天数，多少号
        int day = DateUtil.dayOfMonth(date) - 1; // 从 0 开始
        // 构建 Redis Key
        String signKey = buildSignKey(userId, date);
        // 查看指定日期是否已签到
        boolean isSigned = redisTemplate.opsForValue().getBit(signKey, day);
        if (isSigned) {
            result.put("message", "当前日期已完成签到，无需再签");
            result.put("code", 400);
            return result;
        }
        // 签到
        redisTemplate.opsForValue().setBit(signKey, day, true);
        // 根据当前日期统计签到次数
        Date today = new Date();
        // 统计连续签到次数
        int continuous = getContinuousSignCount(userId, today);
        // 统计总签到次数
        long count = getSumSignCount(userId, today);
        result.put("message", "签到成功");
        result.put("code", 200);
        result.put("continuous", continuous);
        result.put("count", count);
        return result;
    }

    /**
     * 统计连续签到次数
     *
     * @param userId 用户ID
     * @param date   查询的日期
     * @return
     */
    private int getContinuousSignCount(Integer userId, Date date) {
        // 获取日期对应的天数，多少号，假设是 31
        int dayOfMonth = DateUtil.dayOfMonth(date);
        // 构建 Redis Key
        String signKey = buildSignKey(userId, date);
        // e.g. bitfield user:sign:5:202103 u31 0
        BitFieldSubCommands bitFieldSubCommands = BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                .valueAt(0);
        // 获取用户从当前日期开始到 1 号的所有签到状态
        List<Long> list = redisTemplate.opsForValue().bitField(signKey, bitFieldSubCommands);
        if (list == null || list.isEmpty()) {
            return 0;
        }
        // 连续签到计数器
        int signCount = 0;
        long v = list.get(0) == null ? 0 : list.get(0);
        // 位移计算连续签到次数
        for (int i = dayOfMonth; i > 0; i--) {// i 表示位移操作次数
            // 右移再左移，如果等于自己说明最低位是 0，表示未签到
            if (v >> 1 << 1 == v) {
                // 用户可能当前还未签到，所以要排除是否是当天的可能性
                // 低位 0 且非当天说明连续签到中断了
                if (i != dayOfMonth) {
                    break;
                }
            } else {
                // 右移再左移，如果不等于自己说明最低位是 1，表示签到
                signCount++;
            }
            // 右移一位并重新赋值，相当于把最低位丢弃一位然后重新计算
            v >>= 1;
        }
        return signCount;
    }

    /**
     * 统计总签到次数
     *
     * @param userId 用户ID
     * @param date   查询的日期
     * @return
     */
    private Long getSumSignCount(Integer userId, Date date) {
        // 构建 Redis Key
        String signKey = buildSignKey(userId, date);
        // e.g. BITCOUNT user:sign:5:202103
        return (Long) redisTemplate.execute(
                (RedisCallback<Long>) con -> con.bitCount(signKey.getBytes())
        );
    }

    /**
     * 获取日期
     *
     * @param dateStr yyyy-MM-dd
     * @return
     */
    private Date getDate(String dateStr) {
        return StrUtil.isBlank(dateStr) ?
                new Date() : DateUtil.parseDate(dateStr);
    }

    /**
     * 构建 Redis Key - user:sign:userId:yyyyMM
     *
     * @param userId 用户ID
     * @param date   日期
     * @return
     */
    private String buildSignKey(Integer userId, Date date) {
        return String.format("user:sign:%d:%s", userId,
                DateUtil.format(date, "yyyyMM"));
    }

    /**
     * 获取用户当天签到情况
     *
     * @param userId  用户ID
     * @param dateStr 查询的日期，默认当天 yyyy-MM-dd
     * @return 当天签到情况，连续签到次数和总签到次数
     */
    public Map<String, Object> getSignByDate(Integer userId, String dateStr) {
        Map<String, Object> result = new HashMap<>();
        // 获取日期
        Date date = getDate(dateStr);
        // 获取日期对应的天数，多少号
        int day = DateUtil.dayOfMonth(date) - 1; // 从 0 开始
        // 构建 Redis Key
        String signKey = buildSignKey(userId, date);
        // 查看是否已签到
        boolean isSigned = redisTemplate.opsForValue().getBit(signKey, day);
        // 根据当前日期统计签到次数
        Date today = new Date();
        // 统计连续签到次数
        int continuous = getContinuousSignCount(userId, today);
        // 统计总签到次数
        long count = getSumSignCount(userId, today);
        result.put("today", isSigned);
        result.put("continuous", continuous);
        result.put("count", count);
        return result;
    }

    /**
     * 获取用户当月签到情况
     *
     * @param userId  用户ID
     * @param dateStr 查询的日期，默认当月 yyyy-MM
     * @return
     */
    public Map<String, Object> getSignInfo(Integer userId, String dateStr) {
        // 获取日期
        Date date = getDate(dateStr);
        // 构建 Redis Key
        String signKey = buildSignKey(userId, date);
        // 构建一个自动排序的 Map
        Map<String, Object> signInfo = new TreeMap<>();
        // 获取某月的总天数（考虑闰年）
        int dayOfMonth = DateUtil.lengthOfMonth(DateUtil.month(date) + 1,
                DateUtil.isLeapYear(DateUtil.dayOfYear(date)));
        // e.g. bitfield user:sign:5:202103 u31 0
        BitFieldSubCommands bitFieldSubCommands = BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                .valueAt(0);
        // 获取用户从当前日期开始到 1 号的所有签到数据
        List<Long> list = redisTemplate.opsForValue().bitField(signKey, bitFieldSubCommands);
        if (list == null || list.isEmpty()) {
            return signInfo;
        }
        long v = list.get(0) == null ? 0 : list.get(0);
        // 从低位到高位进行遍历，为 0 表示未签到，为 1 表示已签到
        for (int i = dayOfMonth; i > 0; i--) {
            /*
                Map 存储格式：
                    签到：  yyyy-MM-01 "✅"
                    未签到：yyyy-MM-02 不做任何处理
             */
            // 获取日期
            LocalDateTime localDateTime = LocalDateTimeUtil.of(date).withDayOfMonth(i);
            // 右移再左移，如果不等于自己说明最低位是 1，表示已签到
            boolean flag = v >> 1 << 1 != v;
            // 如果已签到，添加标记
            if (flag) {
                signInfo.put(DateUtil.format(localDateTime, "yyyy-MM-dd"), "✅");
            }
            // 右移一位并重新赋值，相当于把最低位丢弃一位然后重新计算
            v >>= 1;
        }
        return signInfo;
    }

}
