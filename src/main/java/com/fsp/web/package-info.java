@NullMarked
@ApplicationModule(
        displayName = "Web UI",
        allowedDependencies = {
                "shared",
                "marketdata :: api",
                "analysis :: api",
                "watchlist :: api",
                "signal :: api",
                "performance :: api",
                "ai :: api",
                "alerting :: api",
                "identity :: api"
        }
)
package com.fsp.web;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;

