import { useCallback, useEffect, useRef } from 'react';
import { connect, getIntl, history as umiHistory, useDispatch } from 'umi';
import { createStyles } from '@ead/antd-style';
import type { InputRef } from '@ead/suid';
import { App, Button, Flex, Form, Input, Select, Spin } from '@ead/suid';
import type { ResponseResult } from '@ead/suid-utils-react';
import { getUUID, md5, useUserContext } from '@ead/suid-utils-react';
import { constants as localConstants } from '@/utils';
import { title } from '../../../package.json';

const FormItem = Form.Item;
const { Option } = Select;
const { formatMessage } = getIntl();
const { LOGIN_STATUS } = localConstants;

const useStyles = createStyles(({ prefixCls, token }) => {
  return {
    'login-form-box': {
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      height: '100%',
      backgroundColor: token.colorBgContainer,
      ['.login-form']: {
        width: 320,
        height: 400,
        padding: token.padding,
        background: token.colorBgContainer,
        borderRadius: token.borderRadius,
        boxShadow: token.boxShadow,
        [`${prefixCls}-row.${prefixCls}.form-item`]: {
          height: 'auto',
          marginBlockEnd: token.margin,
        },
      },
      '.login-name': {
        padding: token.paddingSM,
        fontWeight: 700,
        fontSize: token.fontSizeLG,
        textAlign: 'center',
      },
    },
    verifyCodeImg: {
      flexShrink: 0,
      width: 100,
      height: 40,
      cursor: 'pointer',
      objectFit: 'contain',
      borderRadius: token.borderRadius,
      border: `1px solid ${token.colorBorder}`,
      backgroundColor: token.colorBgContainer,
    },
    verifyCodePlaceholder: {
      flexShrink: 0,
      width: 100,
      height: 40,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      cursor: 'pointer',
      borderRadius: token.borderRadius,
      border: `1px solid ${token.colorBorder}`,
      backgroundColor: token.colorFillAlter,
    },
  };
});

const LoginForm = (props: any) => {
  const { loading, global } = props;
  const { message } = App.useApp();
  const { styles } = useStyles();
  const [form] = Form.useForm();
  const loginReqIdRef = useRef(getUUID());

  const userInputRef = useRef<InputRef>(null);

  const { locationQuery, showTenant, locale, verifyCode, showVertifCode } = global || {};
  const { setCurrentAuth, setCurrentUser, setSessionId, setCurrentPolicy } = useUserContext();

  const dispatch = useDispatch();
  const loadingVerifyCode = loading.effects['global/getVerifyCode'];

  /** 请求图形验证码（点击验证码图或进入页面时触发） */
  const handleVertify = useCallback(() => {
    dispatch({
      type: 'global/getVerifyCode',
      payload: {
        reqId: loginReqIdRef.current,
      },
    });
  }, [dispatch]);

  useEffect(() => {
    handleVertify();
    const timer = window.setTimeout(() => userInputRef.current?.focus(), 0);
    return () => window.clearTimeout(timer);
  }, [handleVertify]);

  const setAuthorizedFeatures = (userId: string) => {
    if (process.env.NODE_ENV !== 'production') {
      dispatch({
        type: 'global/getAuthorizedFeatures',
        payload: userId,
        callback: (res: ResponseResult) => {
          if (res.success) {
            setCurrentAuth(res.data);
          } else {
            message.error('获取权限失败');
          }
        },
      });
    }
  };

  const setRequireTenant = (payload: any) => {
    dispatch({
      type: 'global/updateState',
      payload,
    });
  };

  const handlerSubmit = (formData: any) => {
    const user = { ...formData };
    user.password = md5(user.password);
    user.reqId = loginReqIdRef.current;
    dispatch({
      type: 'global/login',
      payload: {
        ...user,
      },
      callback: (res: ResponseResult) => {
        const msg = res.message;
        if (res.success) {
          const { loginStatus, authorityPolicy, sessionId, userId } = res.data || {};
          const { from } = locationQuery;
          switch (loginStatus) {
            case LOGIN_STATUS.SUCCESS:
              message.success('登录成功');
              setCurrentUser(res.data);
              setSessionId(sessionId);
              setCurrentPolicy(authorityPolicy);
              setAuthorizedFeatures(userId);
              if (from && from.indexOf('/user/login') === -1) {
                if (from === '/') {
                  umiHistory.push('/dashboard');
                } else {
                  umiHistory.push(from);
                }
              } else {
                umiHistory.push('/');
              }
              break;
            case LOGIN_STATUS.MULTI_TENANT:
              message.warning('需要输入租户账号');
              setRequireTenant({
                showTenant: true,
              });
              break;
            case LOGIN_STATUS.CAPTCHA_ERROR:
              message.error(msg || '验证码错误');
              setRequireTenant({
                showVertifCode: true,
              });
              handleVertify();
              break;
            case LOGIN_STATUS.FROZEN:
              message.error('账号被冻结');
              handleVertify();
              break;
            case LOGIN_STATUS.LOCKED:
              message.error('账号被锁定');
              handleVertify();
              break;
            case LOGIN_STATUS.FAILURE:
              message.error('账号或密码错误');
              handleVertify();
              break;
            default:
              message.error(msg || '登录失败');
              handleVertify();
          }
        } else {
          message.error(msg || '登录失败');
          handleVertify();
        }
      },
    });
  };

  const handlerLocaleChange = (localeValue: string) => {
    dispatch({
      type: 'global/changeLocale',
      payload: {
        locale: localeValue,
      },
    });
  };

  return (
    <div className={styles['login-form-box']}>
      <div className="login-form">
        <div className="login-logo">
          <div className="login-name">{`${title}-用户登录`}</div>
        </div>
        <Form
          form={form}
          initialValues={{ locale, password: 'EadpAdmin@2026!@#' }}
          style={{ maxWidth: '300px' }}
          onFinish={handlerSubmit}
        >
          {showTenant && (
            <FormItem name="tenant" rules={[{ required: true, message: '请输入租户账号' }]}>
              <Input autoFocus size="large" placeholder="租户账号" />
            </FormItem>
          )}
          <FormItem name="account" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input
              ref={userInputRef}
              size="large"
              placeholder={formatMessage({
                id: 'login.account',
                defaultMessage: '用户名',
              })}
            />
          </FormItem>
          <FormItem name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input
              size="large"
              type="password"
              placeholder={formatMessage({
                id: 'login.password',
                defaultMessage: '密码',
              })}
            />
          </FormItem>
          {showVertifCode ? (
            <FormItem name="verifyCode" rules={[{ required: true, message: '请输入验证码' }]}>
              <Flex align="center" gap={8}>
                <Input
                  size="large"
                  disabled={!!loadingVerifyCode}
                  placeholder={formatMessage({
                    id: 'login.verifyCode',
                    defaultMessage: '验证码',
                  })}
                />
                {verifyCode ? (
                  <img
                    className={styles.verifyCodeImg}
                    alt={formatMessage({
                      id: 'login.verifyCode',
                      defaultMessage: '验证码',
                    })}
                    src={verifyCode}
                    onClick={handleVertify}
                  />
                ) : (
                  <div
                    className={styles.verifyCodePlaceholder}
                    onClick={handleVertify}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        handleVertify();
                      }
                    }}
                  >
                    {loadingVerifyCode ? <Spin size="small" /> : null}
                  </div>
                )}
              </Flex>
            </FormItem>
          ) : null}
          <FormItem name="locale" rules={[{ required: true, message: '请选择语言' }]}>
            <Select size="large" onChange={handlerLocaleChange}>
              <Option value="zh-CN">简体中文</Option>
              <Option value="en-US">English</Option>
            </Select>
          </FormItem>
          <FormItem label={null}>
            <Button
              type="primary"
              htmlType="submit"
              size="large"
              className="login-form-button"
              block
              disabled={!!loadingVerifyCode}
              loading={loading.effects['global/login']}
            >
              登录
            </Button>
          </FormItem>
        </Form>
      </div>
    </div>
  );
};

const ConnectedLoginForm: any = connect(({ global, loading }) => ({
  global,
  loading,
}))(LoginForm);

export default ConnectedLoginForm;
