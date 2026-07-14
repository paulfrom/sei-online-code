/**
 * CommentStream — full transcript view (spec §5).
 *
 * `comments` are grouped by `loopId` (`null` normalised to `'(no-loop)'`),
 * each group sorted ascending by `createdDate`, and rendered as a
 * `React.memo`-wrapped `LoopGroup`. The group whose `loopId` matches
 * `activeLoopId` (or `'(no-loop)'` when both are null) is `active`; every
 * other group is collapsed historical context.
 *
 * `onJumpPlan` / `onHighlightTask` are forwarded transparently to every
 * `LoopGroup` so actions surfaced inside a comment (view execution plan /
 * jump to task) work uniformly regardless of which loop the comment lives in.
 *
 * The composer is rendered at the bottom and owns the interrupt-aware send
 * flow; the stream only listens to `comments.length` to keep the bottom in
 * view after a successful post (the container刷新 is what appends the new
 * comment, so we track length rather than the send promise here).
 *
 * Virtualisation: when the list grows past 100 comments we want to recall
 * the `React.memo` boundary alone is O(n); up to that bound the cost is
 * fine. The TODO below marks the point at which a windowed list should be
 * introduced.
 */
import React, { useEffect, useMemo, useRef } from 'react';
import LoopGroup from './LoopGroup';
import CommentComposer from './CommentComposer';

/** Normalised key for comments that predate the loop concept. */
const NO_LOOP = '(no-loop)';

const planVersionOf = (c) => {
  if (!c.metadataJson) return null;
  try {
    const parsed = JSON.parse(c.metadataJson);
    return parsed && typeof parsed === 'object' && 'planVersion' in parsed
      ? Number(parsed.planVersion)
      : null;
  } catch {
    return null;
  }
};

/**
 * Group and sort the flat comment list into ordered `LoopGroupData` rows.
 * Active group is placed first so the user lands on the live conversation
 * without scrolling past history. Within a group, comments stay in
 * `createdDate` ascending order (LoopGroup re-sorts too, but we keep the
 * sort local so the active-group placement logic reads from a canonical list).
 */
function groupComments(comments, activeLoopId) {
  const groups = new Map();
  for (const c of comments) {
    const key = c.loopId ?? NO_LOOP;
    const list = groups.get(key);
    if (list) list.push(c);
    else groups.set(key, [c]);
  }
  const activeKey = activeLoopId ?? NO_LOOP;
  const result = [];
  for (const [loopId, list] of groups) {
    const sorted = [...list].sort((a, b) =>
      a.createdDate.localeCompare(b.createdDate),
    );
    result.push({ loopId, comments: sorted, active: loopId === activeKey });
  }
  // Active loop first, then the rest in earliest-comment order.
  result.sort((a, b) => {
    if (a.active !== b.active) return a.active ? -1 : 1;
    const a0 = a.comments[0]?.createdDate ?? '';
    const b0 = b.comments[0]?.createdDate ?? '';
    return a0.localeCompare(b0);
  });
  return result;
}

const CommentStream = ({
  comments,
  activeLoopId,
  requirement,
  onSend,
  sending,
  onJumpPlan,
  onHighlightTask,
}) => {
  const bottomRef = useRef(null);
  const groups = useMemo(
    () => groupComments(comments, activeLoopId),
    [comments, activeLoopId],
  );
  const lastLengthRef = useRef(comments.length);

  // Keep the bottom in view after a successful post. The container refreshes
  // comments on send-resolve, so we watch length growth as the signal.
  useEffect(() => {
    if (comments.length > lastLengthRef.current) {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
    }
    lastLengthRef.current = comments.length;
  }, [comments.length]);

  // TODO: virtualize when >100 comments — until then React.memo on the group
  // (see LoopGroup default export) keeps re-renders bounded.
  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {groups.map((g) => (
        <LoopGroup
          key={g.loopId}
          loopId={g.loopId}
          comments={g.comments}
          active={g.active}
          planVersion={(() => {
            for (const c of g.comments) {
              const v = planVersionOf(c);
              if (v !== null) return v;
            }
            return null;
          })()}
          onJumpPlan={onJumpPlan}
          onHighlightTask={onHighlightTask}
        />
      ))}
      <div ref={bottomRef} />
      <div style={{ flexShrink: 0, borderTop: '1px solid rgba(0,0,0,0.06)', paddingTop: 8, marginTop: 8 }}>
        <CommentComposer
          requirement={requirement}
          onSend={onSend}
          sending={sending}
        />
      </div>
    </div>
  );
};

export default CommentStream;
