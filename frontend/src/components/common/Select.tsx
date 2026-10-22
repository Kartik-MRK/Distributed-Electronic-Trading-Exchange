'use client';

import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Check, ChevronDown } from 'lucide-react';
import styles from './Select.module.css';

export type SelectOption<T extends string = string> = {
  value: T;
  label: string;
};

export function Select<T extends string>({
  value,
  onChange,
  options,
  label,
  id,
  showLabel = false,
  variant = 'compact',
}: {
  value: T;
  onChange: (value: T) => void;
  options: readonly SelectOption<T>[];
  label: string;
  id?: string;
  showLabel?: boolean;
  variant?: 'compact' | 'field';
}) {
  const uid = useId();
  const listId = `${uid}-options`;
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const [position, setPosition] = useState({
    top: 0,
    left: 0,
    width: 210,
    maxHeight: 300,
  });
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const optionRefs = useRef<(HTMLButtonElement | null)[]>([]);
  const search = useRef({ value: '', time: 0 });
  const selected = options.find((option) => option.value === value);
  const contains = (target: Node | null) =>
    !!target &&
    (rootRef.current?.contains(target) || listRef.current?.contains(target));
  const close = () => {
    setOpen(false);
    triggerRef.current?.focus();
  };
  const show = () => {
    setActiveIndex(
      Math.max(
        0,
        options.findIndex((option) => option.value === value),
      ),
    );
    setOpen(true);
  };

  useLayoutEffect(() => {
    if (!open) return;
    const update = () => {
      const rect = triggerRef.current?.getBoundingClientRect();
      if (!rect) return;
      const below = window.innerHeight - rect.bottom - 20;
      const above = rect.top - 20;
      const desired = Math.min(300, options.length * 40 + 12);
      const flip = below < desired && above > below;
      const maxHeight = Math.max(40, Math.min(desired, flip ? above : below));
      const width = Math.min(Math.max(rect.width, 210), window.innerWidth - 24);
      setPosition({
        top: flip ? rect.top - maxHeight - 8 : rect.bottom + 8,
        left: Math.max(12, Math.min(rect.left, window.innerWidth - width - 12)),
        width,
        maxHeight,
      });
    };
    update();
    window.addEventListener('resize', update);
    window.addEventListener('scroll', update, true);
    return () => {
      window.removeEventListener('resize', update);
      window.removeEventListener('scroll', update, true);
    };
  }, [open, options.length]);

  useEffect(() => {
    if (open) optionRefs.current[activeIndex]?.focus({ preventScroll: true });
  }, [open, activeIndex]);
  useEffect(() => {
    if (!open) return;
    const dismiss = (event: PointerEvent) => {
      if (
        !rootRef.current?.contains(event.target as Node) &&
        !listRef.current?.contains(event.target as Node)
      )
        setOpen(false);
    };
    document.addEventListener('pointerdown', dismiss);
    return () => document.removeEventListener('pointerdown', dismiss);
  }, [open]);

  return (
    <div
      className={`${styles.root} ${variant === 'field' ? styles.field : ''}`}
      ref={rootRef}
      onBlur={(event) => {
        if (!contains(event.relatedTarget)) setOpen(false);
      }}
    >
      {showLabel && <span className="eyebrow">{label}</span>}
      <button
        ref={triggerRef}
        id={id}
        type="button"
        className={styles.trigger}
        aria-label={`${label}: ${selected?.label ?? value}`}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        onClick={() => (open ? setOpen(false) : show())}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            show();
          }
        }}
      >
        <span className={styles.value}>{selected?.label ?? value}</span>
        <ChevronDown size={14} aria-hidden="true" className={styles.chevron} />
      </button>
      {open &&
        createPortal(
          <div
            ref={listRef}
            id={listId}
            role="listbox"
            aria-label={label}
            className={styles.options}
            style={position}
            onBlur={(event) => {
              if (!contains(event.relatedTarget)) setOpen(false);
            }}
            onKeyDown={(event) => {
              const key = event.key;
              if (key === 'Escape') {
                event.preventDefault();
                close();
              } else if (key === 'Tab') {
                triggerRef.current?.focus();
                setOpen(false);
              } else if (
                ['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(key)
              ) {
                event.preventDefault();
                const next =
                  key === 'Home'
                    ? 0
                    : key === 'End'
                      ? options.length - 1
                      : (activeIndex +
                          (key === 'ArrowDown' ? 1 : -1) +
                          options.length) %
                        options.length;
                setActiveIndex(next);
                optionRefs.current[next]?.scrollIntoView({ block: 'nearest' });
              } else if (
                key.length === 1 &&
                key !== ' ' &&
                !event.ctrlKey &&
                !event.metaKey &&
                !event.altKey
              ) {
                event.preventDefault();
                const now = event.timeStamp;
                search.current = {
                  value:
                    (now - search.current.time < 600
                      ? search.current.value
                      : '') + key.toLowerCase(),
                  time: now,
                };
                const next = options.findIndex((option) =>
                  option.label.toLowerCase().startsWith(search.current.value),
                );
                if (next >= 0) {
                  setActiveIndex(next);
                  optionRefs.current[next]?.scrollIntoView({
                    block: 'nearest',
                  });
                }
              }
            }}
          >
            {options.map((option, index) => (
              <button
                key={option.value}
                type="button"
                role="option"
                aria-selected={value === option.value}
                className={styles.option}
                tabIndex={index === activeIndex ? 0 : -1}
                ref={(element) => {
                  optionRefs.current[index] = element;
                }}
                onClick={() => {
                  onChange(option.value);
                  close();
                }}
              >
                <span>{option.label}</span>
                {value === option.value && (
                  <Check size={14} aria-hidden="true" />
                )}
              </button>
            ))}
          </div>,
          document.body,
        )}
    </div>
  );
}
