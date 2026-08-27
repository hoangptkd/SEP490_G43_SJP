package com.sjp.recruitment.service.vad;

import java.util.List;

public record SileroModelContract(
        List<String> inputNames,
        List<String> outputNames,
        String inputShape,
        String stateShape,
        String sampleRateShape,
        String outputShape,
        String nextStateShape
) {
}
