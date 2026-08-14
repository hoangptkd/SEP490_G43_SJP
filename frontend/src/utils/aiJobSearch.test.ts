import { describe, expect, it } from 'vitest';
import { filterAiJobSearchItems } from './aiJobSearch';
import type { AiJobSearchItem } from '../types/aiJobSearch';

function item(overrides: Partial<AiJobSearchItem['job']> = {}): AiJobSearchItem {
  return {
    rank: 1,
    matchScore: 90,
    matchedSkills: ['Java'],
    missingSkills: [],
    reason: 'Phù hợp.',
    job: {
      id: 'job-1',
      title: 'Backend Developer',
      description: '',
      location: 'Hà Nội',
      requirements: [],
      skills: ['Java'],
      company: { id: 'company-1', name: 'SRP' },
      status: 'PUBLISHED',
      saved: false,
      applied: false,
      salaryMin: 15_000_000,
      salaryMax: 25_000_000,
      jobType: 'full_time',
      workMode: 'hybrid',
      ...overrides,
    },
  };
}

describe('filterAiJobSearchItems', () => {
  it('lọc top AI theo địa điểm, lương, loại việc và hình thức', () => {
    const matching = item();
    const remote = item({ id: 'job-2', location: 'TP.HCM', workMode: 'remote' });

    expect(filterAiJobSearchItems([matching, remote], {
      location: 'hà nội',
      minSalary: 18_000_000,
      maxSalary: 30_000_000,
      jobType: 'full_time',
      workMode: 'hybrid',
    })).toEqual([matching]);
  });

  it('giữ nguyên thứ hạng và không áp dụng keyword của tìm kiếm thường', () => {
    const first = item({ id: 'job-1' });
    const second = { ...item({ id: 'job-2' }), rank: 2 };

    expect(filterAiJobSearchItems([first, second], { search: 'không liên quan' }))
      .toEqual([first, second]);
  });
});
