This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/app/api-reference/cli/create-next-app).

## Getting Started

Install the dependencies with `npm ci` before starting the development server.

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

You can start editing the page by modifying `src/app/page.tsx`. The page auto-updates as you edit the file.

This project uses `next/font` to load Manrope throughout the interface.

## Shared UI components

TypeScript is already configured. Tailwind CSS v4 is installed through the PostCSS
plugin in `postcss.config.mjs`. Global styles live in `src/app/globals.css`; existing
component styles remain in CSS modules. Tailwind's theme and utilities are enabled
without its Preflight reset, preserving the site's existing base styles.

Reusable components live in `src/components/ui`, imported as `@/components/ui`.
The `@/` alias resolves to `src/`, so a second root-level `components/ui` directory
is unnecessary. This folder keeps shared UI primitives separate from page-specific
components and gives the shadcn CLI a consistent installation target.
`components.json` configures those aliases, and `src/lib/utils.ts` supplies `cn`.
This project has been configured manually; running `shadcn init` is not required.
To add a future component from the `frontend` directory, use:

```bash
npx shadcn@latest add <component-name>
```

`src/components/ui/hyper-text.tsx` uses `framer-motion` for the hero's text effect.
It preserves text case, punctuation, Manrope, and responsive word wrapping. It
runs once on mount over 1.6 seconds (with no hover replay), cancels pending frames on unmount, and disables scrambling
for reduced-motion users. Screen readers receive the unchanged original text.

Setup references: [Tailwind PostCSS](https://tailwindcss.com/docs/installation/using-postcss),
[optional Preflight](https://tailwindcss.com/docs/preflight), and
[shadcn configuration](https://ui.shadcn.com/docs/components-json).

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
