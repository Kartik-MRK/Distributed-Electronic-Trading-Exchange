'use client';

import { Suspense, useEffect } from 'react';
import { useRouter } from 'next/navigation';

export default function RegisterRedirect() {
  const router = useRouter();

  useEffect(() => {
    router.replace('/login?tab=signup');
  }, [router]);

  return (
    <Suspense fallback={null}>
      <div className="loading-screen" role="status">
        Redirecting to registration...
      </div>
    </Suspense>
  );
}
