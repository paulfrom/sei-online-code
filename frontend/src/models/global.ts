import { setLocale, history as umiHistory } from 'umi';
import { message } from '@ead/suid';
import { qs, SEI_CONSTANTS, storage } from '@ead/suid-utils-react';
import { getAuthorizedFeatures, getVerifyCode, login } from '@/services/api';
import { formatVerifyCodeSrc } from '@/utils/verifyCodeFormat';
import type { AnyAction, EffectsCommandMap, ReducersMapObject, SubscriptionAPI } from 'umi';

const { stringify } = qs;

const { CONST_GLOBAL } = SEI_CONSTANTS;

export default {
  namespace: 'global',
  state: {
    locationPathName: '/',
    locationQuery: {},
    showTenant: false,
    locale: 'zh-CN',
    verifyCode: '',
    showVertifCode: true,
  },
  subscriptions: {
    setupHistory({ dispatch, history }: SubscriptionAPI) {
      history.listen((his: any) => {
        dispatch({
          type: 'updateState',
          payload: {
            locationPathName: his.location.pathname,
            locationQuery: his.location.query,
          },
        });
      });
    },
  },
  effects: {
    *redirectLogin({}: AnyAction, { select }: EffectsCommandMap): any {
      const global = yield select((sel: any) => sel.global);
      const { locationPathName, locationQuery } = global;
      let location = locationPathName;
      if (location.indexOf('/user/login') !== -1) {
        location = locationQuery.from || '/';
      }
      umiHistory.replace({
        pathname: '/user/login',
        search: stringify({
          from: location,
        }),
      });
    },
    *getAuthorizedFeatures(
      { payload, callback = () => {} }: AnyAction,
      { call }: EffectsCommandMap,
    ): any {
      const res = yield call(getAuthorizedFeatures, payload);
      callback(res);
    },
    *login({ payload, callback = () => {} }: AnyAction, { call }: EffectsCommandMap): any {
      const res = yield call(login, payload);
      callback(res);
    },
    *getVerifyCode({ payload }: AnyAction, { call, put }: EffectsCommandMap): any {
      const result = yield call(getVerifyCode, payload.reqId);
      const { success, data, message: msg } = result || {};
      const verifyCodeSrc = formatVerifyCodeSrc(data);
      if (success && verifyCodeSrc) {
        yield put({
          type: 'updateState',
          payload: {
            verifyCode: verifyCodeSrc,
            showVertifCode: true,
          },
        });
      } else if (success) {
        message.error(msg || '验证码数据无效');
      } else {
        message.error(msg);
      }
    },
    *changeLocale({ payload }: AnyAction, { put }: EffectsCommandMap) {
      const { locale } = payload;
      storage.sessionStorage.set(CONST_GLOBAL.CURRENT_LOCALE, locale);
      yield put({
        type: 'updateState',
        payload: {
          locale,
        },
      });
      setLocale(locale, true);
      umiHistory.replace('/user/login');
    },
  },
  reducers: {
    updateState(state: any, { payload }: ReducersMapObject<any>) {
      return {
        ...state,
        ...payload,
      };
    },
  },
};
