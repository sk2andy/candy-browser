package dev.sk2andy.materialbrowser.browser

internal object AntiFingerprintingScript {
    private val validSeed = Regex("[A-Za-z0-9_-]{16,64}")

    fun create(sessionSeed: String): String {
        require(validSeed.matches(sessionSeed)) { "Invalid anti-fingerprinting seed" }
        return """
            (() => {
              "use strict";
              const defineGetter = (owner, name, getter) => {
                if (!owner) return;
                try {
                  const descriptor = Object.getOwnPropertyDescriptor(owner, name);
                  if (descriptor && !descriptor.configurable) return;
                  Object.defineProperty(owner, name, {
                    configurable: true,
                    enumerable: descriptor?.enumerable ?? true,
                    get: getter,
                  });
                } catch (_) {}
              };
              const fixedGetter = (value) => () => value;

              defineGetter(globalThis.Navigator?.prototype, "hardwareConcurrency", fixedGetter(4));
              defineGetter(globalThis.Navigator?.prototype, "deviceMemory", fixedGetter(4));
              defineGetter(globalThis.Navigator?.prototype, "maxTouchPoints", fixedGetter(5));

              const bucket = (value) => Math.max(100, Math.round(Number(value || 0) / 100) * 100);
              const screenWidth = bucket(globalThis.screen?.width);
              const screenHeight = bucket(globalThis.screen?.height);
              defineGetter(globalThis.Screen?.prototype, "width", fixedGetter(screenWidth));
              defineGetter(globalThis.Screen?.prototype, "height", fixedGetter(screenHeight));
              defineGetter(globalThis.Screen?.prototype, "availWidth", fixedGetter(screenWidth));
              defineGetter(globalThis.Screen?.prototype, "availHeight", fixedGetter(screenHeight));
              defineGetter(globalThis.Screen?.prototype, "colorDepth", fixedGetter(24));
              defineGetter(globalThis.Screen?.prototype, "pixelDepth", fixedGetter(24));

              const uaDataPrototype = globalThis.NavigatorUAData?.prototype;
              const originalHighEntropyValues = uaDataPrototype?.getHighEntropyValues;
              if (originalHighEntropyValues) {
                try {
                  Object.defineProperty(uaDataPrototype, "getHighEntropyValues", {
                    configurable: true,
                    value: async function(hints) {
                      const values = await originalHighEntropyValues.call(this, hints);
                      const reduced = { ...values };
                      for (const name of ["architecture", "bitness", "model", "platformVersion", "wow64"]) {
                        if (Object.prototype.hasOwnProperty.call(reduced, name)) {
                          reduced[name] = name === "wow64" ? false : "";
                        }
                      }
                      return reduced;
                    },
                    writable: true,
                  });
                } catch (_) {}
              }

              let siteSeed = 2166136261;
              const seedInput = "$sessionSeed|" + (globalThis.location?.hostname || "opaque");
              for (let index = 0; index < seedInput.length; index += 1) {
                siteSeed ^= seedInput.charCodeAt(index);
                siteSeed = Math.imul(siteSeed, 16777619);
              }
              const nextNoise = (value) => {
                value ^= value << 13;
                value ^= value >>> 17;
                value ^= value << 5;
                return value >>> 0;
              };
              const perturbPixels = (pixels) => {
                if (!pixels || typeof pixels.length !== "number") return pixels;
                let noise = siteSeed;
                for (let index = 0; index + 3 < pixels.length; index += 4) {
                  if (pixels[index + 3] === 0) continue;
                  noise = nextNoise(noise + index);
                  pixels[index + (noise % 3)] ^= 1;
                }
                return pixels;
              };

              const canvas2d = globalThis.CanvasRenderingContext2D?.prototype;
              const originalGetImageData = canvas2d?.getImageData;
              const originalPutImageData = canvas2d?.putImageData;
              if (originalGetImageData) {
                try {
                  Object.defineProperty(canvas2d, "getImageData", {
                    configurable: true,
                    value: function(...args) {
                      const image = originalGetImageData.apply(this, args);
                      perturbPixels(image.data);
                      return image;
                    },
                    writable: true,
                  });
                } catch (_) {}
              }

              const canvas = globalThis.HTMLCanvasElement?.prototype;
              const originalToDataUrl = canvas?.toDataURL;
              const originalToBlob = canvas?.toBlob;
              const protectedCanvas = (source) => {
                if (
                  !source.width ||
                  !source.height ||
                  source.width * source.height > 4 * 1024 * 1024
                ) return source;
                const copy = document.createElement("canvas");
                copy.width = source.width;
                copy.height = source.height;
                const context = copy.getContext("2d");
                if (!context) return source;
                context.drawImage(source, 0, 0);
                const image = originalGetImageData.call(context, 0, 0, copy.width, copy.height);
                perturbPixels(image.data);
                originalPutImageData.call(context, image, 0, 0);
                return copy;
              };
              if (originalToDataUrl && originalGetImageData && originalPutImageData) {
                try {
                  Object.defineProperty(canvas, "toDataURL", {
                    configurable: true,
                    value: function(...args) {
                      return originalToDataUrl.apply(protectedCanvas(this), args);
                    },
                    writable: true,
                  });
                } catch (_) {}
              }
              if (originalToBlob && originalGetImageData && originalPutImageData) {
                try {
                  Object.defineProperty(canvas, "toBlob", {
                    configurable: true,
                    value: function(callback, ...args) {
                      return originalToBlob.call(protectedCanvas(this), callback, ...args);
                    },
                    writable: true,
                  });
                } catch (_) {}
              }

              const protectWebGl = (prototype) => {
                const originalGetParameter = prototype?.getParameter;
                if (!originalGetParameter) return;
                try {
                  Object.defineProperty(prototype, "getParameter", {
                    configurable: true,
                    value: function(parameter) {
                      if (parameter === 37445) return "Google Inc. (Google)";
                      if (parameter === 37446) return "ANGLE (Google, Vulkan)";
                      return originalGetParameter.call(this, parameter);
                    },
                    writable: true,
                  });
                } catch (_) {}
              };
              protectWebGl(globalThis.WebGLRenderingContext?.prototype);
              protectWebGl(globalThis.WebGL2RenderingContext?.prototype);
            })();
        """.trimIndent()
    }
}
