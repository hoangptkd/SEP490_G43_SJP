package com.sjp.recruitment.service.audio;

import java.util.List;

public interface AudioDecoder {

    DecodedPcmAudio decode(List<AudioCaptureSegment> segments);

    AudioDecoderHealth health();
}
