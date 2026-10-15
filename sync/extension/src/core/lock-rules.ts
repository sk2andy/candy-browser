import type { StoredSettings } from "./models.js";

export type LockPolicy = "browser-restart" | "idle" | "never";
export const DEFAULT_IDLE_LOCK_MINUTES = 30;
export const MAX_IDLE_LOCK_MINUTES = 1_440;

export function isLockPolicy(value: unknown): value is LockPolicy {
  return value === "browser-restart" || value === "idle" || value === "never";
}

export function isIdleLockMinutes(value: unknown): value is number {
  return typeof value === "number" && Number.isInteger(value) && value >= 1 && value <= MAX_IDLE_LOCK_MINUTES;
}

export function lockPolicy(settings: StoredSettings | null): LockPolicy {
  return isLockPolicy(settings?.lockPolicy) ? settings.lockPolicy : "browser-restart";
}

export function idleLockSeconds(settings: StoredSettings): number {
  return (isIdleLockMinutes(settings.idleLockMinutes) ? settings.idleLockMinutes : DEFAULT_IDLE_LOCK_MINUTES) * 60;
}
