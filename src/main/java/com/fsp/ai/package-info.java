@NullMarked
@ApplicationModule(
        displayName = "AI Insights",
        allowedDependencies = {"shared", "marketdata :: api", "signal :: api", "performance :: api"}
)
package com.fsp.ai;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;

