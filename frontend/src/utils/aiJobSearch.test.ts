import { describe, expect, it } from 'vitest';
import { aiJobSearchInput } from './aiJobSearch';

describe('aiJobSearchInput', () => {
  it('sends the selected CV and hard filters to backend', () => {
    expect(aiJobSearchInput('cv-a', {
      location: ' Hà Nội ', minSalary: 15_000_000, maxSalary: 25_000_000,
      jobType: 'full_time', workMode: 'hybrid',
    })).toEqual({
      cvId: 'cv-a', filters: {
        location: 'Hà Nội', minSalary: 15_000_000, maxSalary: 25_000_000,
        jobType: 'full_time', workMode: 'hybrid',
      },
    });
  });

  it('does not forward ordinary search keywords or sort order', () => {
    expect(JSON.stringify(aiJobSearchInput('cv-a', { search: 'Java', sort: 'salary' })))
      .toBe(JSON.stringify(aiJobSearchInput('cv-a', {})));
  });

  it('isolates displayed results when CV or any hard filter changes', () => {
    const key = JSON.stringify(aiJobSearchInput('cv-a', {}));
    expect(JSON.stringify(aiJobSearchInput('cv-b', {}))).not.toBe(key);
    for (const filters of [
      { location: 'Hà Nội' }, { minSalary: 1 }, { maxSalary: 2 },
      { jobType: 'full_time' }, { workMode: 'remote' },
    ]) expect(JSON.stringify(aiJobSearchInput('cv-a', filters))).not.toBe(key);
  });
});
