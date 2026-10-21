'use client';

import { Suspense, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Loader } from '@/components/common/Loader';

export default function RegisterRedirect() {
  const router = useRouter();

  useEffect(() => {
    router.replace('/login?tab=signup');
  }, [router]);

  return (
    <Suspense fallback={null}>
      <Loader variant="page" label="Opening registration..." />
    </Suspense>
  );
}
