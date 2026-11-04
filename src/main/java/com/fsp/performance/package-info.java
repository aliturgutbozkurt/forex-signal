@NullMarked
@ApplicationModule(
        displayName = "Performance & Backtesting",
        allowedDependencies = {"shared", "marketdata :: api", "analysis :: api", "signal :: api"}
)
package com.fsp.performance;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;

