import Link from 'next/link';
import { ArrowDown, ArrowRight, ArrowUpRight } from 'lucide-react';
import { Globe } from './Globe';
import { LandingNav } from './LandingNav';
import { ExchangeBrand } from '../common/ExchangeBrand';
import styles from './LandingPage.module.css';

const documentation =
  'https://github.com/Kartik-MRK/Distributed-Electronic-Trading-Exchange#readme';

export function LandingPage() {
  return (
    <main className={styles.landing}>
      <a className="skip-link" href="#hero-content">
        Skip to content
      </a>
      <section className={styles.hero} aria-labelledby="hero-title">
        <header className={styles.header}>
          <ExchangeBrand />
          <LandingNav documentation={documentation} />
          <div className={styles.headerActions}>
            <Link href="/login" className={styles.signIn}>
              Sign In
            </Link>
            <Link href="/login" className={styles.navLaunch}>
              Launch Exchange <ArrowUpRight size={14} />
            </Link>
          </div>
        </header>

        <div className={styles.heroContent} id="hero-content">
          <p className={styles.eyebrow}>
            <span /> DISTRIBUTED BY DESIGN
          </p>
          <h1 id="hero-title">
            The next generation
            <br />
            of electronic trading.
          </h1>
          <p className={styles.description}>
            Distributed trading infrastructure engineered
            <br className={styles.desktopBreak} /> for speed, precision, and
            reliability.
          </p>
          <div className={styles.actions}>
            <Link href="/login" className={styles.primary}>
              Launch Exchange <ArrowUpRight size={16} />
            </Link>
            <a href="#technology" className={styles.secondary}>
              Explore Technology <ArrowRight size={16} />
            </a>
          </div>
        </div>

        <div className={styles.visual} aria-hidden="true">
          <div className={styles.halo} />
          <Globe />
        </div>
        <div className={styles.heroFooter}>
          <span>ONE EXCHANGE. DISTRIBUTED INFRASTRUCTURE.</span>
          <a href="#technology" aria-label="Discover the exchange architecture">
            <span>DISCOVER THE ARCHITECTURE</span>
            <ArrowDown size={14} />
          </a>
        </div>
      </section>

      <section
        id="technology"
        className={styles.technology}
        aria-labelledby="technology-title"
      >
        <div className={styles.sectionIntro}>
          <div>
            <p className={styles.eyebrow}>THE ARCHITECTURE</p>
            <h2 id="technology-title">
              Built for the trade.
              <br />
              <span>Engineered for what follows.</span>
            </h2>
          </div>
          <p>
            From order entry to settlement, every step has a clear purpose and a
            traceable record.
          </p>
        </div>
        <div className={styles.principles}>
          <article>
            <span className={styles.index}>01 / EXECUTION</span>
            <h3>Deterministic matching.</h3>
            <p>
              A single-writer order book matches orders by price and time,
              keeping execution precise and predictable.
            </p>
          </article>
          <article>
            <span className={styles.index}>02 / DISTRIBUTION</span>
            <h3>Connected through events.</h3>
            <p>
              Kafka connects order processing, market data, and settlement
              through an ordered stream of exchange events.
            </p>
          </article>
          <article>
            <span className={styles.index}>03 / INTEGRITY</span>
            <h3>Accountable by design.</h3>
            <p>
              Fixed-point arithmetic and an append-only, double-entry ledger
              preserve precision throughout the trade lifecycle.
            </p>
          </article>
        </div>
        <a
          href={documentation}
          className={styles.documentation}
          target="_blank"
          rel="noreferrer"
        >
          Explore the technical documentation <ArrowUpRight size={15} />
        </a>
        <div id="markets" className={styles.markets}>
          <div>
            <p className={styles.eyebrow}>SPOT MARKETS</p>
            <h3>Your next trade starts here.</h3>
          </div>
          <div className={styles.pairs}>
            <span>
              BTC <small>/ USDT</small>
            </span>
            <span>
              ETH <small>/ USDT</small>
            </span>
            <span>
              SOL <small>/ USDT</small>
            </span>
          </div>
          <Link href="/login" className={styles.secondary}>
            Access the exchange <ArrowRight size={16} />
          </Link>
        </div>
      </section>
      <footer className={styles.footer}>
        <span>DETE · Distributed Electronic Trading Exchange</span>
        <Link href="/login">
          Enter the exchange <ArrowUpRight size={13} />
        </Link>
      </footer>
    </main>
  );
}
