import { expect, test, type Page } from '@playwright/test';

// Exercise the actual production page and components. All exchange traffic is
// intercepted: these layout checks never create accounts, orders or simulations.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('dete_access_token', 'layout-test');
    sessionStorage.setItem('dete_user_info', JSON.stringify({ userId: 'layout-test', username: 'Layout test', roles: ['ROLE_TRADER'] }));
  });
  await page.routeWebSocket(/.*/, socket => socket.close());
  await page.route(/\/(market-simulation|market-data|accounts|orders|auth\/me)(\/|\?|$)/, async route => {
    const path = new URL(route.request().url()).pathname;
    let data: unknown = [];
    if (path.endsWith('/status')) {
      data = { status: 'COMPLETED', clock: {
        epochId: 'layout-test', initializedAt: '2026-11-01T00:00:00Z',
        serverNow: new Date().toISOString(), status: 'COMPLETED', fence: 1,
        elapsedMs: 300000, remainingMs: 0, tradingAllowed: false,
      } };
    } else if (path.endsWith('/orderbook')) {
      data = { bids: [], asks: [], instrument: 'BTC-USD' };
    } else if (path === '/orders' || path === '/orders/history') {
      data = Array.from({ length: 50 }, (_, i) => ({
        orderId: `layout-${i}`, accountId: 'layout-test', instrument: 'BTC-USD',
        side: 'BUY', orderType: 'LIMIT', price: 5000000000000, originalQty: 100000000,
        remainingQty: 100000000, filledQty: 0, status: 'ACCEPTED', createdAt: '2026-11-01T00:00:00Z',
      }));
    }
    await route.fulfill({ json: data });
  });
});

async function loadWorkspace(page: Page) {
  await page.goto('/terminal');
  await expect(page.locator('[data-panel-id]')).toHaveCount(6);
  await expect(page.getByRole('button', { name: 'Start simulation', exact: true })).toBeEnabled();
  await expect(page.locator('.open-orders-scroll tbody tr')).toHaveCount(50);
}

async function geometry(page: Page) {
  return page.locator('#workspace').evaluate(root => {
    const rect = (element: Element) => {
      const r = element.getBoundingClientRect();
      return { x: r.x, y: r.y, width: r.width, height: r.height, bottom: r.bottom, right: r.right };
    };
    return Object.fromEntries([...root.querySelectorAll<HTMLElement>('[data-panel-id]')]
      .map(panel => [panel.dataset.panelId!, rect(panel)]));
  });
}

async function aligned(page: Page) {
  await expect.poll(async () => {
    const g = await geometry(page);
    return Math.max(Math.abs(g.activity.bottom - g.book.bottom), Math.abs(g.activity.bottom - g.balances.bottom));
  }).toBeLessThan(1);
  const g = await geometry(page);
  expect(g.activity.y - g.chart.bottom).toBeCloseTo(12, 0);
  expect(g.orderEntry.y - g.simulation.bottom).toBeCloseTo(12, 0);
  expect(g.balances.y - g.orderEntry.bottom).toBeCloseTo(12, 0);
  expect(g.simulation.x).toBeCloseTo(g.orderEntry.x, 0);
  expect(g.simulation.width).toBeCloseTo(g.orderEntry.width, 0);
  const overflow = await page.evaluate(() => ({
    x: document.documentElement.scrollWidth - window.innerWidth,
    y: document.documentElement.scrollHeight - window.innerHeight,
  }));
  expect(overflow.x).toBeLessThanOrEqual(1);
  expect(overflow.y).toBeLessThanOrEqual(1);
}

test('desktop stacks stay aligned through vertical window resizing; content scrolls internally', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await loadWorkspace(page);
  await expect(page.getByText('Shared across all traders')).toHaveCount(0);
  for (const height of [900, 650, 600, 1050, 760]) {
    await page.setViewportSize({ width: 1440, height });
    await aligned(page);
    const button = page.getByRole('button', { name: 'Start simulation', exact: true });
    await button.scrollIntoViewIfNeeded();
    expect(await button.evaluate(el => {
      const style = getComputedStyle(el);
      return el.clientHeight >= parseFloat(style.lineHeight) + parseFloat(style.paddingTop) + parseFloat(style.paddingBottom) - 1;
    })).toBe(true);
    const scroll = page.locator('.open-orders-scroll');
    expect(await scroll.evaluate(el => el.scrollHeight > el.clientHeight)).toBe(true);
    await scroll.evaluate(el => { el.scrollTop = el.scrollHeight; });
    expect(await scroll.evaluate(el => el.scrollTop)).toBeGreaterThan(0);
    if (height === 900 || height === 600) await page.screenshot({ path: testInfo.outputPath(`desktop-${height}.png`) });
  }
});

async function drag(page: Page, panel: string, edge: string, dx: number, dy: number) {
  const handle = page.locator(`[data-panel-id="${panel}"] .workspace-resize-grip--${edge}`);
  const box = (await handle.boundingBox())!;
  const x = box.x + box.width / 2;
  const y = box.y + box.height / 2;
  await page.mouse.move(x, y);
  await page.mouse.down();
  await page.mouse.move(x + dx, y + dy);
  await page.mouse.up();
}

test('dividers move pixel by pixel, affect adjacent panels only, and survive reload', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await loadWorkspace(page);
  const initial = await geometry(page);
  await drag(page, 'chart', 'height', 0, -1);
  const first = await geometry(page);
  expect(initial.chart.height - first.chart.height).toBeCloseTo(1, 0);
  expect(first.activity.height - initial.activity.height).toBeCloseTo(1, 0);
  expect(first.simulation.height).toBeCloseTo(initial.simulation.height, 0);
  await drag(page, 'simulation', 'height', 0, 5);
  const second = await geometry(page);
  expect(second.simulation.height - first.simulation.height).toBeCloseTo(5, 0);
  expect(first.orderEntry.height - second.orderEntry.height).toBeCloseTo(5, 0);
  expect(second.chart.height).toBeCloseTo(first.chart.height, 0);
  await drag(page, 'book', 'width', 10, 0);
  const third = await geometry(page);
  expect(third.book.width - second.book.width).toBeCloseTo(10, 0);
  expect(second.simulation.width - third.simulation.width).toBeCloseTo(10, 0);
  await aligned(page);
  await page.reload();
  await expect(page.locator('.open-orders-scroll tbody tr')).toHaveCount(50);
  const restored = await geometry(page);
  for (const id of ['chart', 'simulation', 'orderEntry', 'balances']) {
    expect(restored[id].height).toBeCloseTo(third[id].height, 0);
  }
  await aligned(page);
});

test('minimum heights and extreme saved layouts remain contained', async ({ page }) => {
  await page.setViewportSize({ width: 1200, height: 600 });
  await page.addInitScript(() => localStorage.setItem('dete-terminal-layout-v1', JSON.stringify({
    chart: { rows: 8 }, simulation: { rows: 3 }, orderEntry: { rows: 6 }, balances: { rows: 6 },
  })));
  await loadWorkspace(page);
  await aligned(page);
  await drag(page, 'chart', 'height', 0, -2000);
  await drag(page, 'orderEntry', 'height', 0, 2000);
  await aligned(page);
  const g = await geometry(page);
  expect(g.chart.height).toBeGreaterThanOrEqual(159);
  expect(g.balances.height).toBeGreaterThanOrEqual(79);
});

test('corner and keyboard resizing keep alternate activity tabs scrollable', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await loadWorkspace(page);
  const before = await geometry(page);
  await drag(page, 'chart', 'both', 6, -7);
  const corner = await geometry(page);
  expect(corner.chart.width - before.chart.width).toBeCloseTo(6, 0);
  expect(before.chart.height - corner.chart.height).toBeCloseTo(7, 0);
  await page.locator('[data-panel-id="chart"] .workspace-resize-grip--height').press('ArrowDown');
  const keyboard = await geometry(page);
  expect(keyboard.chart.height - corner.chart.height).toBeCloseTo(8, 0);
  await page.getByRole('button', { name: 'Order history', exact: true }).click();
  await expect(page.locator('.activity-body tbody tr')).toHaveCount(50);
  const history = page.locator('.activity-body > div');
  await history.evaluate(el => { el.scrollTop = el.scrollHeight; });
  expect(await history.evaluate(el => el.scrollTop)).toBeGreaterThan(0);
  await page.getByRole('button', { name: 'Historical replay', exact: true }).click();
  await expect(page.locator('.replay-viewer')).toBeVisible();
  await aligned(page);
});

test('mobile and tablet retain readable stacked panels and hidden grips', async ({ page }) => {
  for (const width of [390, 1000]) {
    await page.setViewportSize({ width, height: 844 });
    await loadWorkspace(page);
    await expect(page.locator('.workspace-resize-controls').first()).toBeHidden();
    const g = await geometry(page);
    expect(g.chart.height).toBeGreaterThan(200);
    for (const rect of Object.values(g)) {
      expect(rect.width).toBeGreaterThan(100);
      expect(rect.right).toBeLessThanOrEqual(width);
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
  }
});
