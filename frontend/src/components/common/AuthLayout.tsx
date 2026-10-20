import Link from 'next/link';
import { Activity } from 'lucide-react';
import { ReactNode } from 'react';
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <main className="auth-page">
      <aside className="auth-intro">
        <Link className="brand" href="/terminal">
          <span className="brand-mark">
            <Activity size={20} />
          </span>
          DETE<span className="brand-caption">EXCHANGE</span>
        </Link>
        <div>
          <span className="eyebrow">
            Distributed electronic trading exchange
          </span>
          <h2>
            A clear view.
            <br />A considered trade.
          </h2>
          <p>
            Your markets, orders, and account activity in one focused workspace.
          </p>
          <div className="auth-feature" style={{ marginTop: 36 }}>
            Market depth<span>01</span>
          </div>
          <div className="auth-feature">
            Order management<span>02</span>
          </div>
          <div className="auth-feature">
            Execution history<span>03</span>
          </div>
        </div>
        <footer>DETE / Trading infrastructure</footer>
      </aside>
      <section className="auth-content">
        <div className="auth-card">{children}</div>
      </section>
    </main>
  );
}
