import Link from 'next/link';
import { ExchangeLogo } from './ExchangeLogo';
import { ExchangeWordmark } from './ExchangeWordmark';
import styles from './ExchangeBrand.module.css';

export function ExchangeBrand() {
  return (
    <Link href="/" className={styles.brand} aria-label="DETE home">
      <ExchangeLogo className={styles.mark} />
      <ExchangeWordmark className={styles.wordmark} />
      <span className={styles.caption}>EXCHANGE</span>
    </Link>
  );
}
