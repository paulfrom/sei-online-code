# Generated products use the team's EADP + SUID stack, not a neutral stack

The platform's agents build generated products with the **same enterprise stack
the team standardizes on**: frontend in `@ead/suid` + `@ead/antd-style` +
`@ead/suid-utils-react` (per the `suid` skill), with an API Contract shaped to
**EADP conventions** (`ResultData` envelope + ExtTable paging), mocked by MSW
until the EADP/sei-core backend is implemented later.

We rejected a neutral stack (Vite + Tailwind + shadcn) for the generated
products. Although a neutral stack is more "portable" in the abstract, this is an
internal rapid-app-dev tool for an EADP/SEI team: products must slot into the
existing platform, reuse `@ead/suid` business components (ExtTable, ComboList,
ExtModal…), and hand off to an EADP backend. A neutral stack would mean two
frontend skill sets, an MSW contract that doesn't match the future backend, and
rework at backend hand-off.

Consequences:
- The designated skill the agents use is `suid`; the template embeds the SUID
  stack + MSW.
- The MSW mock shape == the future EADP response shape, so backend hand-off is
  drop-in (ties ADR 0002).
- Generated products are NOT framework-agnostic by design. Supporting a neutral
  stack later would be a new, separate capability.
