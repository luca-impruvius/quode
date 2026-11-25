/**
 * Calendar days from today to `iso`, both read in `timeZone` (the user's, from /api/me): 1 means
 * tomorrow. An unknown time zone falls back to the browser's.
 */
export function daysFromToday(iso: string, timeZone: string | undefined, now = new Date()): number {
  let format: Intl.DateTimeFormat;
  try {
    format = dayFormat(timeZone);
  } catch {
    format = dayFormat(undefined);
  }
  // The calendar date's parts, whatever order the locale writes them in, as midnight UTC.
  const day = (date: Date) => {
    const part = (type: string) =>
      Number(format.formatToParts(date).find((p) => p.type === type)?.value);
    return Date.UTC(part('year'), part('month') - 1, part('day'));
  };
  return Math.round((day(new Date(iso)) - day(now)) / 86_400_000);
}

function dayFormat(timeZone: string | undefined) {
  return new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
}
