import type { Metadata } from 'next';
import { LandingPage } from '@/components/landing/LandingPage';

export const metadata: Metadata = {
  title: 'DETE | The next generation of electronic trading',
  description:
    'Distributed trading infrastructure engineered for speed, precision, and reliability. Explore the DETE exchange.',
};

export default function HomePage() {
  return <LandingPage />;
}
