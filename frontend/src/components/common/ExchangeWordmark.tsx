import Image from 'next/image';

export function ExchangeWordmark({ className }: { className?: string }) {
  return (
    <Image
      src="/dete-wordmark.svg"
      alt=""
      aria-hidden="true"
      width={420}
      height={160}
      className={className}
      unoptimized
    />
  );
}
