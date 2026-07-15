package com.changhong.onlinecode.exception;

import com.changhong.onlinecode.dto.enums.StudentGender;
import com.changhong.onlinecode.dto.enums.StudentStatus;

import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * 学生管理字段校验工具（PRD §5.4 / §8.1 AC-05/AC-06/AC-07）。
 *
 * <p>所有校验方法返回 {@link CheckResult}，由调用方按业务需求抛出对应
 * {@link StudentErrorCode}，避免校验语义与 service/exception 形态绑定死。</p>
 *
 * @author sei-online-code
 */
public final class StudentValidator {

    /** 18 位中国大陆身份证号：前 17 位数字 + 末位数字或 X。 */
    private static final Pattern IDCARD_PATTERN = Pattern.compile("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$");

    /** 加权因子：身份证号 GB 11643-1999 标准。 */
    private static final int[] IDCARD_WEIGHT = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};

    /** 校验位对应映射：0-10 对应字符。 */
    private static final char[] IDCARD_CHECKSUM_MAP = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    /** 中国大陆 11 位手机号：以 1 开头，第二位 3-9。 */
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private StudentValidator() {
        // 禁止实例化
    }

    /**
     * 校验身份证号。
     *
     * <p>校验项：长度 18、字符格式合法（区分 18 位含校验位与历史 15 位）、出生日期段合法
     * 且不晚于今天、ISO 加权因子校验位匹配。</p>
     *
     * @param idCard 身份证号
     * @return true=合法；false=非法
     */
    public static boolean isValidIdCard(String idCard) {
        if (idCard == null || !IDCARD_PATTERN.matcher(idCard).matches()) {
            return false;
        }
        // 出生日期段合法性：年/月/日。
        try {
            int year = Integer.parseInt(idCard.substring(6, 10));
            int month = Integer.parseInt(idCard.substring(10, 12));
            int day = Integer.parseInt(idCard.substring(12, 14));
            LocalDate.parse(String.format("%04d-%02d-%02d", year, month, day));
        } catch (Exception ex) {
            return false;
        }
        // 加权因子校验位。
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (idCard.charAt(i) - '0') * IDCARD_WEIGHT[i];
        }
        char expected = IDCARD_CHECKSUM_MAP[sum % 11];
        char actual = Character.toUpperCase(idCard.charAt(17));
        return expected == actual;
    }

    /**
     * 校验中国大陆手机号：以 1 开头，第二位 3-9，余 9 位数字。
     *
     * @param mobile 手机号
     * @return true=合法；false=非法
     */
    public static boolean isValidMobile(String mobile) {
        return mobile != null && MOBILE_PATTERN.matcher(mobile).matches();
    }

    /**
     * 校验出生日期：必填且不得晚于今天（PRD §5.1 / §8.1 AC-07）。
     *
     * @param birthDate 出生日期
     * @return true=合法；false=非法
     */
    public static boolean isValidBirthDate(LocalDate birthDate) {
        return birthDate != null && !birthDate.isAfter(LocalDate.now());
    }

    /**
     * 校验性别枚举：必须为 {@link StudentGender} 合法取值。
     *
     * @param gender 性别
     * @return true=合法；false=非法
     */
    public static boolean isValidGender(StudentGender gender) {
        return gender != null;
    }

    /**
     * 校验状态枚举。
     *
     * @param status 状态
     * @return true=合法；false=非法
     */
    public static boolean isValidStatus(StudentStatus status) {
        return status != null;
    }

    /**
     * 校验学号：长度 1-32。
     *
     * <p>全局唯一性由 Service 校验，DB 唯一索引兜底，本方法不重复断言唯一。</p>
     *
     * @param studentNo 学号
     * @return true=合法；false=非法
     */
    public static boolean isValidStudentNo(String studentNo) {
        return studentNo != null && !studentNo.isEmpty() && studentNo.length() <= 32;
    }

    /**
     * 校验姓名：长度 1-64。
     *
     * @param name 姓名
     * @return true=合法；false=非法
     */
    public static boolean isValidName(String name) {
        return name != null && !name.isEmpty() && name.length() <= 64;
    }
}
