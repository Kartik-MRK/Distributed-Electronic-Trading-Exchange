import Link from 'next/link';
import { Activity } from 'lucide-react';
import styles from './ExchangeBrand.module.css';

export function ExchangeBrand() {
  return (
    <Link href="/" className={styles.brand} aria-label="DETE home">
      <span className={styles.mark}>
        <Activity size={21} strokeWidth={1.5} />
      </span>
      DETE<span className={styles.caption}>EXCHANGE</span>
    </Link>
  );
}
