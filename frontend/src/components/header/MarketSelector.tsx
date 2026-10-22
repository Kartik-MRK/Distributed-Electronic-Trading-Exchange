'use client';

import { Instrument } from '@/types';
import { INSTRUMENT_METAS } from '@/lib/api';
import { Select } from '@/components/common/Select';

export const MARKET_OPTIONS = (
  Object.keys(INSTRUMENT_METAS) as Instrument[]
).map((value) => ({ value, label: value.replace('_', ' / ') }));

export function MarketSelector({
  value,
  onChange,
  label = 'Spot market',
  showLabel = true,
}: {
  value: Instrument;
  onChange: (instrument: Instrument) => void;
  label?: string;
  showLabel?: boolean;
}) {
  return (
    <Select
      value={value}
      onChange={onChange}
      options={MARKET_OPTIONS}
      label={label}
      showLabel={showLabel}
    />
  );
}
