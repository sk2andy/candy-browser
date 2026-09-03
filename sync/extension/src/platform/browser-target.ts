declare const __CANDY_SYNC_FIREFOX__: boolean;

export const IS_FIREFOX_BUILD =
  typeof __CANDY_SYNC_FIREFOX__ !== "undefined" && __CANDY_SYNC_FIREFOX__;
