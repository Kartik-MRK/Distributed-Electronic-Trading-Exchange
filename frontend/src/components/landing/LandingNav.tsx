'use client';

import { useEffect, useState } from 'react';
import { ArrowUpRight } from 'lucide-react';
import styles from './LandingPage.module.css';

export function LandingNav({ documentation }: { documentation: string }) {
  const [active, setActive] = useState('');
  useEffect(() => {
    let frame = 0;
    const update = () => {
      frame = 0;
      if (
        window.scrollY > 0 &&
        window.scrollY + window.innerHeight >=
          document.documentElement.scrollHeight - 2
      ) {
        setActive('markets');
        return;
      }
      const current = ['technology', 'markets']
        .filter((id) => {
          const rect = document.getElementById(id)?.getBoundingClientRect();
          return rect && rect.top <= window.innerHeight * 0.65;
        })
        .at(-1);
      setActive(current ?? '');
    };
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update);
    };
    update();
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', onScroll);
    };
  }, []);
  return (
    <nav className={styles.nav} aria-label="Landing navigation">
      {['markets', 'technology'].map((id) => (
        <a
          key={id}
          href={`#${id}`}
          aria-current={active === id ? 'location' : undefined}
          onClick={() => setActive(id)}
        >
          {id === 'markets' ? 'Markets' : 'Technology'}
        </a>
      ))}
      <a href={documentation} target="_blank" rel="noreferrer">
        Documentation <ArrowUpRight size={12} />
      </a>
    </nav>
  );
}
