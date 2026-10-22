import * as THREE from 'three';
import { createMeteorShower } from './meteor-shower';

/** A decorative, asset-free globe. No per-frame React work or postprocessing. */
export function createGlobe(
  host: HTMLDivElement,
  onReady: () => void,
  onFailure: () => void,
) {
  const renderer = new THREE.WebGLRenderer({
    alpha: true,
    antialias: true,
    powerPreference: 'low-power',
  });
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 40);
  camera.position.set(0, 0, 9);
  const resources: { dispose: () => void }[] = [];
  const keep = <T extends { dispose: () => void }>(resource: T): T => {
    resources.push(resource);
    return resource;
  };
  const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
  let inView = true;
  let lost = false;
  let disposed = false;
  let previousTime = 0;
  let resizeObserver: ResizeObserver | undefined;
  let intersectionObserver: IntersectionObserver | undefined;
  const globe = new THREE.Group();
  let elapsed = 0;
  const signals: {
    mesh: THREE.Mesh;
    from: THREE.Vector3;
    to: THREE.Vector3;
    phase: number;
  }[] = [];
  const nodeMeshes: THREE.Mesh[] = [];
  let meteors: ReturnType<typeof createMeteorShower> | undefined;

  const render = () => {
    if (!disposed && !lost) renderer.render(scene, camera);
  };
  const animate = (time: number) => {
    if (previousTime && time - previousTime < 1000 / 30) return;
    const delta = previousTime
      ? Math.min((time - previousTime) / 1000, 0.1)
      : 0;
    previousTime = time;
    elapsed += delta;
    globe.rotation.y += delta * 0.035;
    signals.forEach(({ mesh, from, to, phase }) => {
      const t = (elapsed * 0.085 + phase) % 1;
      mesh.position
        .copy(from)
        .lerp(to, t)
        .normalize()
        .multiplyScalar(2.875 + Math.sin(t * Math.PI) * 0.28);
      mesh.scale.setScalar(0.55 + Math.sin(t * Math.PI) * 0.45);
    });
    nodeMeshes.forEach((node, index) =>
      node.scale.setScalar(1 + Math.sin(elapsed * 1.1 + index) * 0.12),
    );
    meteors?.update(elapsed);
    render();
  };
  const syncMotion = () => {
    if (disposed || lost) return;
    previousTime = 0;
    const animateScene = !motion.matches && inView && !document.hidden;
    if (!animateScene) meteors?.hide();
    renderer.setAnimationLoop(animateScene ? animate : null);
    host.dataset.motion = animateScene ? 'rotating' : 'still';
    render();
  };
  const contextLost = (event: Event) => {
    event.preventDefault();
    lost = true;
    renderer.setAnimationLoop(null);
    host.dataset.motion = 'still';
    onFailure();
  };
  const dispose = () => {
    disposed = true;
    renderer.setAnimationLoop(null);
    resizeObserver?.disconnect();
    intersectionObserver?.disconnect();
    motion.removeEventListener('change', syncMotion);
    document.removeEventListener('visibilitychange', syncMotion);
    renderer.domElement.removeEventListener('webglcontextlost', contextLost);
    resources.forEach((resource) => resource.dispose());
    renderer.dispose();
    renderer.forceContextLoss();
    renderer.domElement.remove();
  };

  try {
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.5));
    renderer.setClearColor(0x000000, 0);
    renderer.domElement.setAttribute('aria-hidden', 'true');
    host.appendChild(renderer.domElement);
    scene.add(globe);
    globe.rotation.set(0.15, 0.35, -0.18);
    const radius = 2.85;
    const surface = new THREE.Mesh(
      keep(new THREE.SphereGeometry(radius, 64, 48)),
      keep(
        new THREE.MeshPhongMaterial({
          color: 0x08131e,
          shininess: 9,
          specular: 0x1c3c53,
        }),
      ),
    );
    globe.add(surface);
    // A thin atmospheric rim adds depth without a postprocessing pass.
    globe.add(
      new THREE.Mesh(
        keep(new THREE.SphereGeometry(radius + 0.035, 64, 48)),
        keep(
          new THREE.ShaderMaterial({
            transparent: true,
            depthWrite: false,
            vertexShader: `
          varying vec3 vNormal;
          varying vec3 vView;
          void main() {
            vec4 viewPosition = modelViewMatrix * vec4(position, 1.0);
            vNormal = normalize(normalMatrix * normal);
            vView = -viewPosition.xyz;
            gl_Position = projectionMatrix * viewPosition;
          }`,
            fragmentShader: `
          varying vec3 vNormal;
          varying vec3 vView;
          void main() {
            float rim = pow(1.0 - max(dot(normalize(vNormal), normalize(vView)), 0.0), 4.0);
            gl_FragColor = vec4(0.31, 0.61, 0.78, rim * 0.24);
          }`,
          }),
        ),
      ),
    );
    scene.add(new THREE.AmbientLight(0x517088, 1.5));
    const light = new THREE.DirectionalLight(0x86c9ef, 2.5);
    light.position.set(-3, 5, 4);
    scene.add(light);

    // Latitude/longitude lines sit just above an opaque sphere, hiding the
    // rear hemisphere naturally rather than drawing a transparent wire ball.
    const lines: number[] = [];
    const point = (lat: number, lon: number, r = radius + 0.008) =>
      new THREE.Vector3(
        r * Math.cos(lat) * Math.sin(lon),
        r * Math.sin(lat),
        r * Math.cos(lat) * Math.cos(lon),
      );
    const segment = (a: THREE.Vector3, b: THREE.Vector3) => {
      lines.push(...a.toArray(), ...b.toArray());
    };
    for (let lat = -75; lat <= 75; lat += 15) {
      for (let step = 0; step < 96; step++)
        segment(
          point((lat * Math.PI) / 180, (step * Math.PI) / 48),
          point((lat * Math.PI) / 180, ((step + 1) * Math.PI) / 48),
        );
    }
    for (let lon = 0; lon < 360; lon += 15) {
      for (let step = 0; step < 48; step++)
        segment(
          point(-Math.PI / 2 + (step * Math.PI) / 48, (lon * Math.PI) / 180),
          point(
            -Math.PI / 2 + ((step + 1) * Math.PI) / 48,
            (lon * Math.PI) / 180,
          ),
        );
    }
    const grid = keep(new THREE.BufferGeometry());
    grid.setAttribute('position', new THREE.Float32BufferAttribute(lines, 3));
    globe.add(
      new THREE.LineSegments(
        grid,
        keep(
          new THREE.LineBasicMaterial({
            color: 0x396884,
            transparent: true,
            opacity: 0.24,
            depthWrite: false,
          }),
        ),
      ),
    );

    const dots: number[] = [];
    const count = 4000;
    const goldenAngle = Math.PI * (3 - Math.sqrt(5));
    for (let i = 0; i < count; i++) {
      const y = 1 - (i / (count - 1)) * 2;
      const ring = Math.sqrt(1 - y * y);
      const angle = goldenAngle * i;
      dots.push(
        Math.cos(angle) * ring * (radius + 0.012),
        y * (radius + 0.012),
        Math.sin(angle) * ring * (radius + 0.012),
      );
    }
    const dotGeometry = keep(new THREE.BufferGeometry());
    dotGeometry.setAttribute(
      'position',
      new THREE.Float32BufferAttribute(dots, 3),
    );
    globe.add(
      new THREE.Points(
        dotGeometry,
        keep(
          new THREE.PointsMaterial({
            color: 0x5e9ebc,
            size: 0.012,
            transparent: true,
            opacity: 0.48,
            depthWrite: false,
          }),
        ),
      ),
    );

    // Illustrative network connections, not a claim of deployment locations.
    const locations = [
      [42, -65],
      [48, -15],
      [20, 38],
      [38, 80],
      [-15, -30],
      [-26, 95],
    ];
    const nodes = locations.map(([lat, lon]) =>
      point((lat * Math.PI) / 180, (lon * Math.PI) / 180, radius + 0.025),
    );
    const nodeGeometry = keep(new THREE.SphereGeometry(0.023, 10, 8));
    const nodeMaterial = keep(new THREE.MeshBasicMaterial({ color: 0x96d8ee }));
    nodes.forEach((position) => {
      const node = new THREE.Mesh(nodeGeometry, nodeMaterial);
      node.position.copy(position);
      nodeMeshes.push(node);
      globe.add(node);
    });
    const arcMaterial = keep(
      new THREE.LineBasicMaterial({
        color: 0x55aaca,
        transparent: true,
        opacity: 0.5,
      }),
    );
    const signalGeometry = keep(new THREE.SphereGeometry(0.018, 8, 6));
    const signalMaterial = keep(
      new THREE.MeshBasicMaterial({ color: 0xc5edff }),
    );
    [
      [0, 1],
      [1, 2],
      [2, 3],
      [0, 4],
      [2, 5],
    ].forEach(([from, to], index) => {
      const points = Array.from({ length: 49 }, (_, i) => {
        const t = i / 48;
        return nodes[from]
          .clone()
          .lerp(nodes[to], t)
          .normalize()
          .multiplyScalar(radius + 0.025 + Math.sin(t * Math.PI) * 0.28);
      });
      globe.add(
        new THREE.Line(
          keep(new THREE.BufferGeometry().setFromPoints(points)),
          arcMaterial,
        ),
      );
      const signal = new THREE.Mesh(signalGeometry, signalMaterial);
      const phase = index / 5;
      signal.position
        .copy(nodes[from])
        .lerp(nodes[to], phase)
        .normalize()
        .multiplyScalar(radius + 0.025 + Math.sin(phase * Math.PI) * 0.28);
      globe.add(signal);
      signals.push({ mesh: signal, from: nodes[from], to: nodes[to], phase });
    });

    meteors = createMeteorShower(scene, host, keep);

    const resize = () => {
      if (disposed || lost) return;
      const { width, height } = host.getBoundingClientRect();
      if (!width || !height) return;
      renderer.setSize(width, height, false);
      camera.aspect = width / height;
      camera.updateProjectionMatrix();
      render();
    };
    resizeObserver = new ResizeObserver(resize);
    resizeObserver.observe(host);
    intersectionObserver = new IntersectionObserver((entries) => {
      inView = entries[0].isIntersecting;
      syncMotion();
    });
    intersectionObserver.observe(host);
    motion.addEventListener('change', syncMotion);
    document.addEventListener('visibilitychange', syncMotion);
    renderer.domElement.addEventListener('webglcontextlost', contextLost);
    resize();
    syncMotion();
    onReady();
  } catch (error) {
    dispose();
    throw error;
  }
  return dispose;
}
