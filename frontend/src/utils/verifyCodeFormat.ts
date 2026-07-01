/**
 * Normalize the login verify-code payload into an <img src>.
 * The backend may return either a ready-to-use data/URL string or a bare
 * base64 PNG body; this returns a usable src or empty string when absent.
 */
export function formatVerifyCodeSrc(data?: string | null): string {
  if (!data) return '';
  if (/^(data:|https?:\/\/|\/)/.test(data)) return data;
  return `data:image/png;base64,${data}`;
}
