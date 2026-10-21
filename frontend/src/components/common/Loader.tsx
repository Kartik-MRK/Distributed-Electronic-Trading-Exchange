import type { CSSProperties } from 'react';
import styles from './Loader.module.css';

type LoaderProps = {
  /** An empty label makes the animation decorative, for use beside button text. */
  label?: string;
  variant?: 'inline' | 'panel' | 'page';
};

export function Loader({
  label = 'Loading...',
  variant = 'panel',
}: LoaderProps) {
  return (
    <span
      className={`${styles.loader} ${styles[variant]}`}
      role={label ? 'status' : undefined}
      aria-live={label ? 'polite' : undefined}
      aria-hidden={label ? undefined : true}
      data-loader={variant}
    >
      <span className={styles.animation} aria-hidden="true">
        {Array.from({ length: 5 }, (_, index) => (
          <span
            key={index}
            className={styles.circle}
            style={
              {
                '--delay': `${index * 0.3}s`,
                '--outline-delay': `${0.9 + index * 0.3}s`,
              } as CSSProperties
            }
          >
            <span className={styles.dot} />
            <span className={styles.outline} />
          </span>
        ))}
      </span>
      {label && <span className={styles.label}>{label}</span>}
    </span>
  );
}
