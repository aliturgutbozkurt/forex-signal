package com.fsp.marketdata.application;

import com.fsp.marketdata.api.ScannerIndicatorValues;
import com.fsp.marketdata.domain.InstrumentId;
import com.fsp.shared.Timeframe;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface IndicatorSnapshotPort {

    Map<InstrumentId, ScannerIndicatorValues> fetchBatchSnapshots(
            String market,
            List<InstrumentId> instruments,
            Timeframe timeframe
    );

    boolean isCircuitOpen();
}

