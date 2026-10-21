'use client';

import { useEffect, useId, useRef, useState } from 'react';
import { Check, ChevronDown } from 'lucide-react';
import { Instrument } from '@/types';
import { INSTRUMENT_METAS } from '@/lib/api';
import styles from './MarketSelector.module.css';

const instruments = Object.keys(INSTRUMENT_METAS) as Instrument[];

export function MarketSelector({
  value,
  onChange,
}: {
  value: Instrument;
  onChange: (instrument: Instrument) => void;
}) {
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const optionRefs = useRef<(HTMLButtonElement | null)[]>([]);
  const id = useId();

  useEffect(() => {
    if (!open) return;
    optionRefs.current[activeIndex]?.focus();
  }, [open, activeIndex]);

  useEffect(() => {
    if (!open) return;
    const dismiss = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('pointerdown', dismiss);
    return () => document.removeEventListener('pointerdown', dismiss);
  }, [open]);

  const showOptions = () => {
    setActiveIndex(instruments.indexOf(value));
    setOpen(true);
  };

  return (
    <div
      className={styles.root}
      ref={rootRef}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false);
      }}
    >
      <span className="eyebrow" id={`${id}-label`}>
        Spot market
      </span>
      <button
        ref={triggerRef}
        type="button"
        className={styles.trigger}
        aria-label={`Spot market: ${value.replace('_', ' / ')}`}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? `${id}-options` : undefined}
        onClick={() => (open ? setOpen(false) : showOptions())}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            showOptions();
          }
        }}
      >
        <span>{value.replace('_', ' / ')}</span>
        <ChevronDown size={14} aria-hidden="true" className={styles.chevron} />
      </button>
      {open && (
        <div
          id={`${id}-options`}
          role="listbox"
          aria-labelledby={`${id}-label`}
          className={styles.options}
          onKeyDown={(event) => {
            const key = event.key;
            if (key === 'Escape') {
              event.preventDefault();
              setOpen(false);
              triggerRef.current?.focus();
            } else if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(key)) {
              event.preventDefault();
              setActiveIndex((index) =>
                key === 'Home'
                  ? 0
                  : key === 'End'
                    ? instruments.length - 1
                    : (index +
                        (key === 'ArrowDown' ? 1 : -1) +
                        instruments.length) %
                      instruments.length,
              );
            }
          }}
        >
          {instruments.map((instrument, index) => (
            <button
              key={instrument}
              type="button"
              role="option"
              aria-selected={value === instrument}
              className={styles.option}
              tabIndex={activeIndex === index ? 0 : -1}
              ref={(element) => {
                optionRefs.current[index] = element;
              }}
              onClick={() => {
                onChange(instrument);
                setOpen(false);
                triggerRef.current?.focus();
              }}
            >
              <span>
                {INSTRUMENT_METAS[instrument].baseAsset}
                <span className={styles.quote}> / USDT</span>
              </span>
              {value === instrument && <Check size={14} aria-hidden="true" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
