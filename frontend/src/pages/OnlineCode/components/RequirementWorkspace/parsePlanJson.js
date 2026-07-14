/**
 * Parse an ExecutionPlan planJson string into a structured object.
 * Pure function, safe for malformed/empty input.
 *
 * @param {string|null|undefined} raw
 * @returns {{ goal: string|null, tasks: any[], risks: string[], validation: string|null }}
 */
export function parsePlanJson(raw) {
  if (!raw) {
    return { goal: null, tasks: [], risks: [], validation: null };
  }
  try {
    const parsed = JSON.parse(raw);
    return {
      goal: typeof parsed.goal === 'string' ? parsed.goal : null,
      tasks: Array.isArray(parsed.tasks) ? parsed.tasks : [],
      risks: Array.isArray(parsed.risks) ? parsed.risks : [],
      validation: typeof parsed.validation === 'string'
        ? parsed.validation
        : parsed.validation && typeof parsed.validation === 'object'
          ? JSON.stringify(parsed.validation, null, 2)
          : null,
    };
  } catch {
    return { goal: null, tasks: [], risks: [], validation: null };
  }
}
