import * as THREE from 'three';

/** Three reusable trails share a texture and the globe's animation loop. */
export function createMeteorShower(
  scene: THREE.Scene,
  host: HTMLDivElement,
  keep: <T extends { dispose: () => void }>(resource: T) => T,
) {
  const textureCanvas = document.createElement('canvas');
  textureCanvas.width = textureCanvas.height = 64;
  const context = textureCanvas.getContext('2d');
  if (!context) return { update: () => {}, hide: () => {} };

  const gradient = context.createRadialGradient(32, 32, 0, 32, 32, 32);
  gradient.addColorStop(0, 'rgba(225, 239, 247, 0.9)');
  gradient.addColorStop(0.15, 'rgba(186, 213, 230, 0.5)');
  gradient.addColorStop(0.45, 'rgba(133, 180, 206, 0.12)');
  gradient.addColorStop(1, 'rgba(133, 180, 206, 0)');
  context.fillStyle = gradient;
  context.fillRect(0, 0, 64, 64);
  const texture = keep(new THREE.CanvasTexture(textureCanvas));
  texture.colorSpace = THREE.SRGBColorSpace;

  const meteors = Array.from({ length: 3 }, (_, index) => {
    const glowMaterial = keep(
      new THREE.SpriteMaterial({
        map: texture,
        transparent: true,
        opacity: 0,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
      }),
    );
    const glow = new THREE.Sprite(glowMaterial);
    const samples = 18;
    const positions = new THREE.Float32BufferAttribute(
      new Float32Array(samples * 3),
      3,
    );
    positions.setUsage(THREE.DynamicDrawUsage);
    const colors = new Float32Array(samples * 3);
    for (let i = 0; i < samples; i++) {
      const fade = (1 - i / (samples - 1)) ** 1.8;
      colors.set([0.72 * fade, 0.83 * fade, 0.9 * fade], i * 3);
    }
    const geometry = keep(new THREE.BufferGeometry());
    geometry.setAttribute('position', positions);
    geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    const trailMaterial = keep(
      new THREE.LineBasicMaterial({
        vertexColors: true,
        transparent: true,
        opacity: 0,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
      }),
    );
    const trail = new THREE.Line(geometry, trailMaterial);
    // Its position buffer moves each frame; avoid a stale bounding sphere.
    trail.frustumCulled = false;
    const group = new THREE.Group();
    group.add(trail, glow);
    group.visible = false;
    scene.add(group);

    const paths = [
      { x: -1.65, y: 2.08 },
      { x: 1.35, y: 2.24 },
      { x: -0.95, y: 2.32 },
    ];
    const from = new THREE.Vector3();
    const to = new THREE.Vector3();
    const head = new THREE.Vector3();
    const direction = new THREE.Vector3();
    const radius = 2.89;
    const impactDuration = 1.4;
    let nextAt = 2.4 + index * 1.15 + Math.random() * 0.35;
    let startedAt = -1;
    let pathIndex = index;
    let distance = 1;
    let duration = 4;

    return {
      hide() {
        group.visible = false;
      },
      update(elapsed: number) {
        const path = paths[pathIndex];
        if (startedAt < 0) {
          if (elapsed < nextAt) return;
          // Keep the impact inside the visible slice of the globe on phones.
          const viewportWidth =
            host.closest('section')?.clientWidth ?? host.clientWidth;
          const spread = Math.min(
            1,
            viewportWidth / Math.max(host.clientWidth, 1),
          );
          const x = (path.x + (Math.random() - 0.5) * 0.24) * spread;
          const y = path.y + (Math.random() - 0.5) * 0.1;
          to.set(x, y, Math.sqrt(radius ** 2 - x ** 2 - y ** 2));
          from.set(
            x + Math.sign(x) * (0.75 + Math.random() * 0.3) * spread,
            3.1 + Math.random() * 0.1,
            0.4,
          );
          direction.copy(from).sub(to).normalize();
          distance = from.distanceTo(to);
          duration = 3.8 + Math.random() * 0.8;
          startedAt = elapsed;
        }

        const age = elapsed - startedAt;
        group.visible = true;
        if (age < duration) {
          const progress = age / duration;
          const fadeIn = THREE.MathUtils.smoothstep(progress, 0, 0.22);
          const fadeOut = 1 - THREE.MathUtils.smoothstep(progress, 0.88, 1);
          head.copy(from).lerp(to, progress);
          const length = Math.min(0.55, progress * distance);
          for (let i = 0; i < samples; i++) {
            const offset = (i / (samples - 1)) * length;
            positions.setXYZ(
              i,
              head.x + direction.x * offset,
              head.y + direction.y * offset,
              head.z + direction.z * offset,
            );
          }
          positions.needsUpdate = true;
          trail.visible = true;
          trailMaterial.opacity = 0.55 * fadeIn * fadeOut;
          glow.position.copy(head);
          glow.scale.setScalar(0.085);
          glowMaterial.opacity = 0.65 * fadeIn * fadeOut;
        } else if (age < duration + impactDuration) {
          const progress = (age - duration) / impactDuration;
          trail.visible = false;
          glow.position.copy(to);
          glow.scale.setScalar(0.1 + progress * 0.35);
          glowMaterial.opacity = Math.sin(progress * Math.PI) * 0.36;
        } else {
          group.visible = false;
          startedAt = -1;
          nextAt = elapsed + 0.8 + Math.random() * 1.5;
          pathIndex = (pathIndex + 1) % paths.length;
        }
      },
    };
  });

  return {
    hide() {
      meteors.forEach((meteor) => meteor.hide());
    },
    update(elapsed: number) {
      meteors.forEach((meteor) => meteor.update(elapsed));
    },
  };
}
