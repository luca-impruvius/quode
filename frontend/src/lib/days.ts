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
  // en-CA writes dates as YYYY-MM-DD, which Date.parse reads as midnight UTC.
  const day = (date: Date) => Date.parse(format.format(date));
  return Math.round((day(new Date(iso)) - day(now)) / 86_400_000);
}

function dayFormat(timeZone: string | undefined) {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
}
