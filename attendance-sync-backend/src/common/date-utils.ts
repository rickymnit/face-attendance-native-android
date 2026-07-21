import { BadRequestException } from '@nestjs/common';

export function parseIsoDateTime(value: string | number, fieldName: string): Date {
  const date = typeof value === 'number' || /^\d+$/.test(String(value))
    ? new Date(Number(value))
    : new Date(value);
  if (Number.isNaN(date.getTime())) {
    throw new BadRequestException(`${fieldName} must be a valid ISO date/time or epoch millis`);
  }
  return date;
}

export function parseAttendanceDate(value: string): Date {
  const date = new Date(`${value}T00:00:00.000Z`);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || Number.isNaN(date.getTime())) {
    throw new BadRequestException('attendanceDate must be YYYY-MM-DD');
  }
  return date;
}
