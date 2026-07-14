import { createStyles } from '@ead/antd-style';

const useStyles = createStyles(({ token, css }) => ({
  page: css`
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    padding: ${token.paddingMD}px;
    background: ${token.colorBgContainer};
  `,
  filter: css`
    margin-bottom: ${token.marginMD}px;
  `,
  table: css`
    flex: 1;
    overflow: auto;
  `,
}));

export default useStyles;
