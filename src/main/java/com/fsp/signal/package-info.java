@NullMarked
@ApplicationModule(
        displayName = "Signal Generation",
        allowedDependencies = {"shared", "marketdata :: api", "analysis :: api", "watchlist :: api"}
)
package com.fsp.signal;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;

