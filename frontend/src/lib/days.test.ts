import { describe, expect, it } from 'vitest';
import { daysFromToday } from './days';

const now = new Date('2026-11-25T21:30:00Z'); // 23:30 in Bucharest, 21:30 in London

describe('daysFromToday', () => {
  it('counts calendar days in the given time zone', () => {
    expect(daysFromToday('2026-11-25T22:00:00Z', 'Europe/Bucharest', now)).toBe(1);
    expect(daysFromToday('2026-11-25T22:00:00Z', 'Europe/London', now)).toBe(0);
    expect(daysFromToday('2026-11-28T22:00:00Z', 'Europe/Bucharest', now)).toBe(4);
  });

  it('crosses a daylight-saving change without drifting', () => {
    const spring = new Date('2027-03-27T12:00:00Z');
    // 28 March 2027 starts in Bucharest at 22:00Z the day before; 29 March at 21:00Z (summer time).
    expect(daysFromToday('2027-03-28T21:00:00Z', 'Europe/Bucharest', spring)).toBe(2);
  });

  it('falls back to the browser’s zone for an unknown one', () => {
    expect(() => daysFromToday('2026-11-27T00:00:00Z', 'Not/AZone', now)).not.toThrow();
  });
});
