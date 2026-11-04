@NullMarked
@ApplicationModule(
        displayName = "Alerting & Voice",
        allowedDependencies = {"shared", "marketdata :: api", "watchlist :: api", "signal :: api", "ai :: api"}
)
package com.fsp.alerting;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;

