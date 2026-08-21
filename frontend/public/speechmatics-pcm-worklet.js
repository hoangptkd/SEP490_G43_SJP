class SpeechmaticsPcmCaptureProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.targetSampleRate = 16000;
    this.pending = new Float32Array(0);
    this.readPosition = 0;
  }

  process(inputs) {
    const input = inputs[0]?.[0];
    if (!input?.length) return true;
    const combined = new Float32Array(this.pending.length + input.length);
    combined.set(this.pending);
    combined.set(input, this.pending.length);
    const ratio = sampleRate / this.targetSampleRate;
    const output = [];
    while (this.readPosition + 1 < combined.length) {
      const leftIndex = Math.floor(this.readPosition);
      const fraction = this.readPosition - leftIndex;
      const sample = combined[leftIndex] * (1 - fraction) + combined[leftIndex + 1] * fraction;
      output.push(Math.max(-1, Math.min(1, sample)));
      this.readPosition += ratio;
    }
    const consumed = Math.floor(this.readPosition);
    this.pending = combined.slice(consumed);
    this.readPosition -= consumed;
    if (output.length) {
      const pcm = new Int16Array(output.length);
      for (let index = 0; index < output.length; index += 1) {
        const sample = output[index];
        pcm[index] = sample < 0 ? Math.round(sample * 0x8000) : Math.round(sample * 0x7fff);
      }
      this.port.postMessage(pcm.buffer, [pcm.buffer]);
    }
    return true;
  }
}

registerProcessor('speechmatics-pcm-capture', SpeechmaticsPcmCaptureProcessor);
