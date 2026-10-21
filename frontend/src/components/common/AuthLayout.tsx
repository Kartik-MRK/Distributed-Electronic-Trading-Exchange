import Link from 'next/link';
import { ArrowLeft } from 'lucide-react';
import { ReactNode } from 'react';
import { ExchangeBrand } from './ExchangeBrand';
import { Globe } from '../landing/Globe';
import styles from './AuthLayout.module.css';

export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <ExchangeBrand />
        <Link href="/" className={styles.back}>
          <ArrowLeft size={14} /> Back to overview
        </Link>
      </header>
      <div className={styles.workspace}>
        <aside className={styles.intro}>
          <div className={styles.introCopy}>
            <p className={styles.eyebrow}>DISTRIBUTED BY DESIGN</p>
            <h2>
              Access your
              <br />
              trading workspace.
            </h2>
            <p>
              Follow the market. Manage your orders.
              <br />
              See every trade through.
            </p>
          </div>
          <div className={styles.visual} aria-hidden="true">
            <Globe className={styles.globe} />
          </div>
          <span className={styles.caption}>
            DETE / Distributed Electronic Trading Exchange
          </span>
        </aside>
        <section className={styles.content} aria-label="Exchange access">
          <div className={styles.form}>{children}</div>
        </section>
      </div>
    </main>
  );
}
