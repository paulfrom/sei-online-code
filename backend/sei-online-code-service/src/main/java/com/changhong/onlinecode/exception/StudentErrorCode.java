package com.changhong.onlinecode.exception;

/**
 * 学生管理业务错误码（PRD §5.2 / §5.4）。
 *
 * <p>作为 {@code OperateResultWithData.operationFailure(...)} 与全局异常处理器统一透传的
 * 业务错误码字面量；中文文案对应翻译键写入 {@code messages.properties}，便于前端 i18n
 * 与既有 ErrorCode 体系对齐。</p>
 *
 * <p>命名约定：{@code student.<businessKey>}；本类常量即 messages.properties 键。</p>
 *
 * @author sei-online-code
 */
public final class StudentErrorCode {

    /** 学生不存在。 */
    public static final String STUDENT_NOT_FOUND = "student.notFound";

    /** 学号已存在。参数：{0}=学号。 */
    public static final String STUDENT_DUPLICATE_NO = "student.duplicateNo";

    /** 身份证号已存在。参数：{0}=身份证号。 */
    public static final String STUDENT_DUPLICATE_IDCARD = "student.duplicateIdCard";

    /** 手机号已存在。参数：{0}=手机号。 */
    public static final String STUDENT_DUPLICATE_MOBILE = "student.duplicateMobile";

    /** 身份证号校验位不合法。参数：{0}=身份证号。 */
    public static final String STUDENT_INVALID_IDCARD = "student.invalidIdCard";

    /** 手机号格式不合法。参数：{0}=手机号。 */
    public static final String STUDENT_INVALID_MOBILE = "student.invalidMobile";

    /** 出生日期不合法（晚于今天）。参数：{0}=出生日期。 */
    public static final String STUDENT_INVALID_BIRTHDATE = "student.invalidBirthDate";

    /** 性别枚举不合法。参数：{0}=值。 */
    public static final String STUDENT_INVALID_GENDER = "student.invalidGender";

    /** 状态枚举不合法。参数：{0}=值。 */
    public static final String STUDENT_INVALID_STATUS = "student.invalidStatus";

    /** 参数校验失败：必填字段缺失。参数：{0}=字段名。 */
    public static final String STUDENT_FIELD_REQUIRED = "student.fieldRequired";

    private StudentErrorCode() {
        // 禁止实例化
    }
}
