/**
 * Shared page-shell primitives for the OnlineCode module.
 *
 * Unifies the three repeated patterns every business page used to hand-roll:
 *   - PageContainer: consistent padding / gap / full-height flex column
 *   - PageHeader:    BannerTitle (+ optional back) on the left, extra + actions
 *                    clustered on the right with a single gap token
 *   - PageState:     full-page loading / empty / error, centered once
 *
 * Styling derives only from @ead/antd-style tokens so the micro-app keeps
 * inheriting the qiankun parent theme (no parallel palette).
 */
import React from 'react';
import { history } from 'umi';
import { createStyles } from '@ead/antd-style';
import { BannerTitle, Button, Empty, Spin } from '@ead/suid';
import { ArrowLeftOutlined } from '@ead/suid-icons';

const useContainerStyles = createStyles(({ token, css }) => ({
  page: css`
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    padding: ${token.paddingMD}px;
    gap: ${token.marginMD}px;
  `,
  scroll: css`
    overflow: auto;
  `,
}));

interface PageContainerProps extends React.HTMLAttributes<HTMLDivElement> {
  /** enable page-level scrolling for long content (Spec/Settings/PlanTab) */
  scroll?: boolean;
}

export const PageContainer: React.FC<PageContainerProps> = ({
  scroll,
  className,
  children,
  ...rest
}) => {
  const { styles } = useContainerStyles();
  const cls = [styles.page, scroll ? styles.scroll : '', className]
    .filter(Boolean)
    .join(' ');
  return (
    <div className={cls} {...rest}>
      {children}
    </div>
  );
};

const useHeaderStyles = createStyles(({ token, css }) => ({
  header: css`
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: ${token.marginSM}px;
  `,
  left: css`
    display: flex;
    align-items: center;
    gap: ${token.marginSM}px;
    min-width: 0;
  `,
  right: css`
    display: flex;
    align-items: center;
    gap: ${token.marginSM}px;
    flex-wrap: wrap;
    justify-content: flex-end;
  `,
}));

interface PageHeaderProps {
  title: string;
  subTitle?: string;
  /** renders a back button that pushes to `to` */
  back?: { to: string; text?: string };
  /** status badges / indicators placed left of the actions */
  extra?: React.ReactNode;
  /** right-aligned action buttons */
  actions?: React.ReactNode;
}

export const PageHeader: React.FC<PageHeaderProps> = ({
  title,
  subTitle,
  back,
  extra,
  actions,
}) => {
  const { styles } = useHeaderStyles();
  return (
    <div className={styles.header}>
      <div className={styles.left}>
        {back && (
          <Button
            icon={<ArrowLeftOutlined />}
            onClick={() => history.push(back.to)}
          >
            {back.text ?? '返回'}
          </Button>
        )}
        <BannerTitle title={title} subTitle={subTitle} />
      </div>
      {(extra || actions) && (
        <div className={styles.right}>
          {extra}
          {actions}
        </div>
      )}
    </div>
  );
};

const useStateStyles = createStyles(({ css }) => ({
  state: css`
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 200px;
  `,
}));

interface PageStateProps {
  loading?: boolean;
  /** empty-state description; shown when not loading and no error */
  empty?: React.ReactNode;
  /** error description; shown when not loading */
  error?: string;
}

export const PageState: React.FC<PageStateProps> = ({ loading, empty, error }) => {
  const { styles } = useStateStyles();
  if (loading) {
    return (
      <div className={styles.state}>
        <Spin spinning />
      </div>
    );
  }
  return (
    <div className={styles.state}>
      <Empty description={error ?? empty} />
    </div>
  );
};
