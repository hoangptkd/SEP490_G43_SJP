import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('provinceService', () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    vi.resetModules();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it('getProvinces fetches from external API and maps divisions', async () => {
    globalThis.fetch = vi.fn().mockResolvedValueOnce({
      ok: true,
      json: () =>
        Promise.resolve([
          { code: 1, name: 'Hà Nội', codename: 'ha_noi', division_type: 'thành phố' },
        ]),
    });

    const { provinceService } = await import('./provinceService');
    const result = await provinceService.getProvinces();

    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
    expect(result).toHaveLength(1);
    expect(result[0].name).toBe('Hà Nội');
  });

  it('getWards fetches wards for a given province code', async () => {
    globalThis.fetch = vi.fn().mockResolvedValueOnce({
      ok: true,
      json: () =>
        Promise.resolve([
          { code: 101, name: 'Phường 1', codename: 'phuong_1', division_type: 'phường' },
        ]),
    });

    const { provinceService } = await import('./provinceService');
    const result = await provinceService.getWards(1);

    expect(result).toHaveLength(1);
    expect(result[0].name).toBe('Phường 1');
  });

  it('getWards rejects invalid province code', async () => {
    const { provinceService } = await import('./provinceService');
    await expect(provinceService.getWards(-1)).rejects.toThrow('Mã tỉnh/thành phố không hợp lệ');
  });
});
