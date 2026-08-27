import type { AdministrativeDivision } from '../types/location';

const API_BASE_URL = 'https://provinces.open-api.vn/api/v2';
const CACHE_PREFIX = 'sjp:provinces-open-api:v2';
const CACHE_TTL_MS = 30 * 24 * 60 * 60 * 1000;

interface ApiDivision {
  code?: number;
  name?: string;
  codename?: string;
  division_type?: string;
}

interface CacheEntry<T> {
  expiresAt: number;
  data: T;
}

const memoryCache = new Map<string, AdministrativeDivision[]>();
const pendingRequests = new Map<string, Promise<AdministrativeDivision[]>>();

function readCache(key: string): AdministrativeDivision[] | null {
  const memoryValue = memoryCache.get(key);
  if (memoryValue) return memoryValue;
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as CacheEntry<AdministrativeDivision[]>;
    if (!Array.isArray(parsed.data) || parsed.expiresAt <= Date.now()) {
      window.localStorage.removeItem(key);
      return null;
    }
    memoryCache.set(key, parsed.data);
    return parsed.data;
  } catch {
    return null;
  }
}

function writeCache(key: string, data: AdministrativeDivision[]) {
  memoryCache.set(key, data);
  try {
    const entry: CacheEntry<AdministrativeDivision[]> = {
      expiresAt: Date.now() + CACHE_TTL_MS,
      data,
    };
    window.localStorage.setItem(key, JSON.stringify(entry));
  } catch {
    // localStorage có thể bị chặn; cache trong bộ nhớ vẫn hoạt động.
  }
}

function normalizeResponse(payload: unknown): AdministrativeDivision[] {
  if (!Array.isArray(payload)) {
    throw new Error('Dữ liệu địa giới hành chính không hợp lệ.');
  }
  return payload.flatMap((item: ApiDivision) => {
    if (!Number.isInteger(item?.code) || !item?.name || !item?.codename) return [];
    return [{
      code: item.code as number,
      name: item.name.trim(),
      codename: item.codename,
      divisionType: item.division_type || '',
    }];
  });
}

async function fetchDivisions(url: string, cacheKey: string, forceRefresh: boolean) {
  if (!forceRefresh) {
    const cached = readCache(cacheKey);
    if (cached) return cached;
    const pending = pendingRequests.get(cacheKey);
    if (pending) return pending;
  } else {
    memoryCache.delete(cacheKey);
    try {
      window.localStorage.removeItem(cacheKey);
    } catch {
      // Ignore storage failures during an explicit retry.
    }
  }

  const request = fetch(url, { headers: { Accept: 'application/json' } })
    .then(async (response) => {
      if (!response.ok) throw new Error(`Không thể tải địa điểm (${response.status}).`);
      const data = normalizeResponse(await response.json());
      if (data.length === 0) throw new Error('Danh sách địa điểm đang trống.');
      writeCache(cacheKey, data);
      return data;
    })
    .finally(() => pendingRequests.delete(cacheKey));

  pendingRequests.set(cacheKey, request);
  return request;
}

export const provinceService = {
  getProvinces(forceRefresh = false) {
    return fetchDivisions(`${API_BASE_URL}/p/`, `${CACHE_PREFIX}:provinces`, forceRefresh);
  },

  getWards(provinceCode: number, forceRefresh = false) {
    if (!Number.isInteger(provinceCode) || provinceCode <= 0) {
      return Promise.reject(new Error('Mã tỉnh/thành phố không hợp lệ.'));
    }
    return fetchDivisions(
      `${API_BASE_URL}/w/?province=${encodeURIComponent(provinceCode)}`,
      `${CACHE_PREFIX}:wards:${provinceCode}`,
      forceRefresh,
    );
  },
};
