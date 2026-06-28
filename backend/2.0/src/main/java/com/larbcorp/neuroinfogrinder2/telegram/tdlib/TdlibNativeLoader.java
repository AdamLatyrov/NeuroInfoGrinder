package com.larbcorp.neuroinfogrinder2.telegram.tdlib;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TdlibNativeLoader {
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);

    private TdlibNativeLoader() {
    }

    public static void load(TdlibProperties properties) {
        if (LOADED.get()) {
            return;
        }
        synchronized (TdlibNativeLoader.class) {
            if (LOADED.get()) {
                return;
            }
            String configuredName = properties.getLibraryName();
            String libraryName = configuredName == null || configuredName.isBlank() ? "tdjni" : configuredName;
            String resolvedFileName = resolveFileName(libraryName);
            try {
                if (properties.getLibraryPath() != null && !properties.getLibraryPath().isBlank()) {
                    Path path = Path.of(properties.getLibraryPath()).toAbsolutePath();
                    Path libraryFile = Files.isDirectory(path) ? path.resolve(resolvedFileName) : path;
                    if (Files.exists(libraryFile)) {
                        System.load(libraryFile.toString());
                        LOADED.set(true);
                        return;
                    }
                }
                System.loadLibrary(stripExtension(libraryName));
                LOADED.set(true);
            } catch (UnsatisfiedLinkError error) {
                throw new TdlibException("Failed to load TDLib JNI library: " + resolvedFileName, error);
            }
        }
    }

    public static boolean isLoaded() {
        return LOADED.get();
    }

    private static String resolveFileName(String libraryName) {
        String lower = libraryName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".dll") || lower.endsWith(".so") || lower.endsWith(".dylib")) {
            return libraryName;
        }
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return libraryName + ".dll";
        }
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            return "lib" + libraryName + ".dylib";
        }
        return "lib" + libraryName + ".so";
    }

    private static String stripExtension(String libraryName) {
        int dotIndex = libraryName.lastIndexOf('.');
        return dotIndex < 0 ? libraryName : libraryName.substring(0, dotIndex);
    }
}
