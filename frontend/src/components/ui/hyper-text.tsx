'use client';

import { motion, useReducedMotion, type Variants } from 'framer-motion';
import { Fragment, useEffect, useState } from 'react';
import { cn } from '@/lib/utils';

interface HyperTextProps {
  text: string;
  duration?: number;
  framerProps?: Variants;
  className?: string;
  animateOnLoad?: boolean;
}

const alphabets = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
const defaultVariants: Variants = {
  initial: { opacity: 0, y: -10 },
  animate: { opacity: 1, y: 0 },
  exit: { opacity: 0, y: 3 },
};

export function HyperText({
  text,
  duration = 1600,
  framerProps = defaultVariants,
  className,
  animateOnLoad = true,
}: HyperTextProps) {
  const reducedMotion = useReducedMotion();
  const [frame, setFrame] = useState<{ source: string; value: string } | null>(null);

  useEffect(() => {
    if (
      reducedMotion ||
      window.matchMedia('(prefers-reduced-motion: reduce)').matches ||
      !animateOnLoad ||
      !text ||
      duration <= 0
    ) return;

    let request = 0;
    let lastUpdate = 0;
    const startedAt = performance.now();

    const tick = (now: number) => {
      const progress = Math.min((now - startedAt) / duration, 1);
      // Cap the scramble at 30 fps; elapsed time keeps long copy within duration.
      if (now - lastUpdate >= 1000 / 30 || progress === 1) {
        const resolved = Math.floor(progress * text.length);
        const value = Array.from(text, (letter, index) => {
          if (index < resolved || !/[a-z]/i.test(letter)) return letter;
          const random = alphabets[Math.floor(Math.random() * alphabets.length)];
          return letter === letter.toLowerCase() ? random.toLowerCase() : random;
        }).join('');
        setFrame({ source: text, value });
        lastUpdate = now;
      }
      if (progress < 1) request = requestAnimationFrame(tick);
    };

    request = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(request);
  }, [text, duration, animateOnLoad, reducedMotion]);

  const displayText = !reducedMotion && duration > 0 && frame?.source === text
    ? frame.value
    : text;
  const words = [...text.matchAll(/\S+|\s+/g)];

  return (
    <span
      className={cn('inline-block max-w-full cursor-default', className)}
      data-hyper-text=""
    >
      {/* Assistive technology gets stable copy, never the random letters. */}
      <span className="sr-only">{text}</span>
      <span aria-hidden="true">
        {words.map((match) => {
          const word = match[0];
          const start = match.index;
          if (/^\s+$/.test(word)) return <Fragment key={start}>{word}</Fragment>;
          return (
            <span key={start} className="inline-block whitespace-nowrap">
              {Array.from(word, (letter, index) => (
                <span key={index} className="relative inline-grid">
                  {/* Original glyphs reserve space so scrambling cannot reflow. */}
                  <span className="invisible">{letter}</span>
                  <motion.span
                    className="absolute inset-0 text-center"
                    initial={false}
                    animate="animate"
                    variants={reducedMotion ? undefined : framerProps}
                  >
                    {displayText[start + index]}
                  </motion.span>
                </span>
              ))}
            </span>
          );
        })}
      </span>
    </span>
  );
}
