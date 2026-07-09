/**
 * Reusable markdown editor with live preview.
 * - readOnly: renders a styled markdown preview only.
 * - editable: split view with raw Input.TextArea on the left and ReactMarkdown preview on the right.
 */
import React, { useEffect, useState } from 'react';
import { createStyles } from '@ead/antd-style';
import { Input } from '@ead/suid';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

const useStyles = createStyles(({ token, css }) => ({
  container: css`
    border: 1px solid ${token.colorBorder};
    border-radius: ${token.borderRadius}px;
    background: ${token.colorBgContainer};
    overflow: hidden;
    display: flex;
    flex-direction: column;
  `,
  header: css`
    display: flex;
    align-items: center;
    gap: ${token.marginLG}px;
    padding: ${token.paddingSM}px ${token.paddingMD}px;
    border-bottom: 1px solid ${token.colorBorderSecondary};
    background: ${token.colorFillAlter};
  `,
  headerLabel: css`
    color: ${token.colorTextSecondary};
    font-size: ${token.fontSizeSM}px;
    font-weight: ${token.fontWeightStrong};
  `,
  body: css`
    display: flex;
    flex: 1;
    min-height: 0;
  `,
  pane: css`
    flex: 1 1 50%;
    min-width: 0;
    overflow: auto;
    padding: ${token.paddingMD}px;
  `,
  leftPane: css`
    border-right: 1px solid ${token.colorBorderSecondary};
  `,
  textarea: css`
    resize: none;
    border: none;
    outline: none;
    box-shadow: none;
    padding: 0;
    &:focus {
      box-shadow: none;
    }
  `,
  preview: css`
    color: ${token.colorText};
    font-size: ${token.fontSize}px;
    line-height: ${token.lineHeight};

    h1,
    h2,
    h3,
    h4,
    h5,
    h6 {
      color: ${token.colorTextHeading};
      margin-top: ${token.marginMD}px;
      margin-bottom: ${token.marginSM}px;
    }

    p {
      margin: 0 0 ${token.marginSM}px;
    }

    ul,
    ol {
      padding-left: ${token.paddingLG}px;
      margin-bottom: ${token.marginSM}px;
    }

    li {
      margin-bottom: ${token.marginXXS}px;
    }

    code {
      background: ${token.colorFillTertiary};
      padding: ${token.paddingXXS}px ${token.paddingXS}px;
      border-radius: ${token.borderRadiusSM}px;
      font-family: ${token.fontFamilyCode};
    }

    pre {
      background: ${token.colorFillTertiary};
      padding: ${token.paddingSM}px;
      border-radius: ${token.borderRadius}px;
      overflow: auto;
      margin-bottom: ${token.marginSM}px;
    }

    pre code {
      background: transparent;
      padding: 0;
    }

    blockquote {
      border-left: 4px solid ${token.colorBorderSecondary};
      padding-left: ${token.paddingMD}px;
      margin: 0 0 ${token.marginSM}px;
      color: ${token.colorTextSecondary};
    }

    table {
      width: 100%;
      border-collapse: collapse;
      margin-bottom: ${token.marginSM}px;
    }

    th,
    td {
      border: 1px solid ${token.colorBorderSecondary};
      padding: ${token.paddingXS}px ${token.paddingSM}px;
      text-align: left;
    }

    th {
      background: ${token.colorFillAlter};
    }

    hr {
      border: 0;
      border-top: 1px solid ${token.colorBorderSecondary};
      margin: ${token.marginMD}px 0;
    }
  `,
  previewOnly: css`
    padding: ${token.paddingMD}px;
  `,
  empty: css`
    color: ${token.colorTextDisabled};
  `,
}));

export interface MarkdownEditorProps {
  value?: string;
  onChange?: (value: string) => void;
  readOnly?: boolean;
  height?: number | string;
  placeholder?: string;
}

const MarkdownEditor: React.FC<MarkdownEditorProps> = ({
  value,
  onChange,
  readOnly = false,
  height = 320,
  placeholder,
}) => {
  const { styles } = useStyles();
  const isControlled = value !== undefined;
  const [draft, setDraft] = useState(value ?? '');

  useEffect(() => {
    if (isControlled) {
      setDraft(value ?? '');
    }
  }, [value, isControlled]);

  const displayValue = isControlled ? (value ?? '') : draft;
  const containerHeight = typeof height === 'number' ? `${height}px` : height;

  const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const next = e.target.value;
    if (!isControlled) {
      setDraft(next);
    }
    onChange?.(next);
  };

  const renderPreview = () => {
    if (!displayValue) {
      return <div className={styles.empty}>{placeholder ?? '暂无内容'}</div>;
    }
    return (
      <div className={styles.preview}>
        <ReactMarkdown remarkPlugins={[remarkGfm as any]}>
          {displayValue}
        </ReactMarkdown>
      </div>
    );
  };

  if (readOnly) {
    return (
      <div className={styles.container} style={{ height: containerHeight }}>
        <div className={styles.previewOnly}>{renderPreview()}</div>
      </div>
    );
  }

  return (
    <div className={styles.container} style={{ height: containerHeight }}>
      <div className={styles.header}>
        <span className={styles.headerLabel}>编辑</span>
        <span className={styles.headerLabel}>预览</span>
      </div>
      <div className={styles.body}>
        <div className={`${styles.pane} ${styles.leftPane}`}>
          <Input.TextArea
            value={displayValue}
            onChange={handleChange}
            placeholder={placeholder}
            className={styles.textarea}
            autoSize={false}
            style={{ height: '100%' }}
          />
        </div>
        <div className={styles.pane}>{renderPreview()}</div>
      </div>
    </div>
  );
};

export default MarkdownEditor;
