import catalogJson from "../../../protocol/device-icons-v1.json" with { type: "json" };

export interface DeviceIconCatalogEntry {
  id: string;
  emoji: string;
  label: string;
}

function validateCatalog(value: unknown): readonly DeviceIconCatalogEntry[] {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("Invalid device icon catalog");
  const catalog = value as { schemaVersion?: unknown; icons?: unknown };
  if (catalog.schemaVersion !== 1 || !Array.isArray(catalog.icons) || catalog.icons.length < 1 || catalog.icons.length > 128) {
    throw new Error("Invalid device icon catalog");
  }
  const seen = new Set<string>();
  return Object.freeze(catalog.icons.map((raw): DeviceIconCatalogEntry => {
    if (!raw || typeof raw !== "object" || Array.isArray(raw)) throw new Error("Invalid device icon catalog entry");
    const entry = raw as Partial<DeviceIconCatalogEntry>;
    if (typeof entry.id !== "string" || !/^[a-z][a-z0-9-]{0,31}$/u.test(entry.id) || seen.has(entry.id)) {
      throw new Error("Invalid device icon catalog id");
    }
    if (typeof entry.emoji !== "string" || entry.emoji.length < 1 || entry.emoji.length > 16) throw new Error("Invalid device icon catalog emoji");
    if (typeof entry.label !== "string" || entry.label.length < 1 || entry.label.length > 40) throw new Error("Invalid device icon catalog label");
    seen.add(entry.id);
    return Object.freeze({ id: entry.id, emoji: entry.emoji, label: entry.label });
  }));
}

export const DEVICE_ICON_CATALOG = validateCatalog(catalogJson);
export const DEVICE_ICON_IDS = new Set(DEVICE_ICON_CATALOG.map((entry) => entry.id));

export function defaultDeviceIconId(userAgent: string, maxTouchPoints = 0): string {
  if (/iPhone|iPod|Mobile|Android/iu.test(userAgent)) return "phone";
  if (/iPad|Tablet|Silk/iu.test(userAgent) || (/Macintosh/iu.test(userAgent) && maxTouchPoints > 1)) return "phone";
  return "computer";
}
