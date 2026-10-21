'use client';

import { useEffect, useId, useRef, useState } from 'react';
import styles from './LandingPage.module.css';

export function Globe({ className = '' }: { className?: string }) {
  const shadingId = useId();
  const hostRef = useRef<HTMLDivElement>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let cancelled = false;
    let dispose: (() => void) | undefined;
    // The headline and links render immediately; only the decorative scene
    // downloads the graphics library after hydration.
    import('./globe-scene')
      .then(({ createGlobe }) => {
        if (cancelled || !hostRef.current) return;
        dispose = createGlobe(
          hostRef.current,
          () => setReady(true),
          () => setReady(false),
        );
      })
      .catch(() => {
        if (!cancelled) setReady(false);
      });
    return () => {
      cancelled = true;
      dispose?.();
    };
  }, []);

  return (
    <div
      className={`${styles.globe} ${className}`}
      aria-hidden="true"
      data-renderer={ready ? 'webgl' : 'fallback'}
    >
      <svg
        className={styles.fallback}
        viewBox="0 0 1000 1000"
        focusable="false"
      >
        <defs>
          <radialGradient id={shadingId} cx="50%" cy="12%" r="80%">
            <stop offset="0" stopColor="#102534" />
            <stop offset="1" stopColor="#060a10" />
          </radialGradient>
        </defs>
        <circle
          cx="500"
          cy="500"
          r="420"
          fill={`url(#${shadingId})`}
          stroke="#2a6379"
          strokeWidth="1"
        />
        <g fill="none" stroke="#28536a" strokeWidth="0.7" opacity="0.6">
          {[100, 220, 330, 400].map((radius) => (
            <ellipse key={radius} cx="500" cy="500" rx={radius} ry="420" />
          ))}
          {[-300, -200, -100, 0, 100, 200, 300].map((offset) => (
            <ellipse
              key={offset}
              cx="500"
              cy={500 + offset}
              rx={Math.sqrt(420 ** 2 - offset ** 2)}
              ry={35 + Math.abs(offset) * 0.12}
            />
          ))}
        </g>
      </svg>
      <div ref={hostRef} className={styles.canvasHost} />
    </div>
  );
}
