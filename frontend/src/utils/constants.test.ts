import { describe, expect, it } from 'vitest';
import {
  APPLICATION_STATUS,
  ASSESSMENT_STATUS,
  ASSESSMENT_TYPE,
  DIFFICULTY_LEVEL,
  INTERVIEW_STATUS,
  INTERVIEW_TYPE,
  JOB_STATUS,
  QUESTION_TYPE,
  USER_ROLE,
} from './constants';

describe('constantsIntegrity', () => {
  it('keeps job statuses unique and complete', () => {
    expect(JOB_STATUS).toEqual({
      ACTIVE: 'ACTIVE',
      CLOSED: 'CLOSED',
      DRAFT: 'DRAFT',
    });
    expect(new Set(Object.values(JOB_STATUS)).size).toBe(3);
  });

  it('keeps application statuses used by the hiring pipeline', () => {
    expect(APPLICATION_STATUS.PENDING).toBe('PENDING');
    expect(APPLICATION_STATUS.UNDER_REVIEW).toBe('UNDER_REVIEW');
    expect(APPLICATION_STATUS.INTERVIEW_SCHEDULED).toBe('INTERVIEW_SCHEDULED');
    expect(APPLICATION_STATUS.REJECTED).toBe('REJECTED');
    expect(APPLICATION_STATUS.ACCEPTED).toBe('ACCEPTED');
  });

  it('keeps interview statuses and types aligned', () => {
    expect(Object.values(INTERVIEW_STATUS)).toEqual([
      'SCHEDULED',
      'IN_PROGRESS',
      'COMPLETED',
      'CANCELLED',
    ]);
    expect(INTERVIEW_TYPE.AI_INTERVIEW).toBe('AI_INTERVIEW');
    expect(INTERVIEW_TYPE.HUMAN_INTERVIEW).toBe('HUMAN_INTERVIEW');
  });

  it('keeps assessment constants stable', () => {
    expect(ASSESSMENT_STATUS.DRAFT).toBe('DRAFT');
    expect(ASSESSMENT_STATUS.EXPIRED).toBe('EXPIRED');
    expect(ASSESSMENT_TYPE.MULTIPLE_CHOICE).toBe('MULTIPLE_CHOICE');
    expect(QUESTION_TYPE.CODING).toBe('CODING');
    expect(DIFFICULTY_LEVEL.HARD).toBe('HARD');
  });

  it('covers the three portal roles', () => {
    expect(USER_ROLE).toEqual({
      CANDIDATE: 'CANDIDATE',
      EMPLOYER: 'EMPLOYER',
      ADMIN: 'ADMIN',
    });
  });

  it('does not allow accidental duplicate role values', () => {
    const values = Object.values(USER_ROLE);
    expect(new Set(values).size).toBe(values.length);
  });

  it('uses uppercase token values for every exported map', () => {
    const maps = [
      JOB_STATUS,
      APPLICATION_STATUS,
      INTERVIEW_STATUS,
      INTERVIEW_TYPE,
      ASSESSMENT_STATUS,
      ASSESSMENT_TYPE,
      QUESTION_TYPE,
      DIFFICULTY_LEVEL,
      USER_ROLE,
    ];
    for (const map of maps) {
      for (const value of Object.values(map)) {
        expect(value).toBe(value.toUpperCase());
      }
    }
  });

  it('keeps assessment type count at four', () => {
    expect(Object.keys(ASSESSMENT_TYPE)).toHaveLength(4);
  });

  it('keeps question types at three', () => {
    expect(Object.keys(QUESTION_TYPE)).toHaveLength(3);
  });

  it('keeps difficulty levels at three', () => {
    expect(Object.keys(DIFFICULTY_LEVEL)).toHaveLength(3);
  });
});
