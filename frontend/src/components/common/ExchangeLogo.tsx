import Image from 'next/image';

export function ExchangeLogo({ className }: { className?: string }) {
  return (
    <Image
      src="/exchange-logo.svg"
      alt=""
      aria-hidden="true"
      width={48}
      height={48}
      className={className}
      unoptimized
    />
  );
}
