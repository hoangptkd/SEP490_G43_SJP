export interface PcmConversionResult {
  samples: Float32Array;
  trailingByte?: number;
}

export interface ParsedSseEvent {
  event: string;
  data: string;
}

export class SseEventParser {
  private readonly decoder = new TextDecoder('utf-8');
  private buffer = '';

  push(bytes: Uint8Array): ParsedSseEvent[] {
    this.buffer += this.decoder.decode(bytes, { stream: true });
    return this.drain();
  }

  finish(): ParsedSseEvent[] {
    this.buffer += this.decoder.decode();
    const events = this.drain();
    if (this.buffer.trim()) throw new Error('TTS stream ended with an incomplete SSE event');
    return events;
  }

  private drain(): ParsedSseEvent[] {
    const events: ParsedSseEvent[] = [];
    while (true) {
      const separator = /\r?\n\r?\n/.exec(this.buffer);
      if (!separator || separator.index == null) break;
      const block = this.buffer.slice(0, separator.index);
      this.buffer = this.buffer.slice(separator.index + separator[0].length);
      if (!block || block.startsWith(':')) continue;

      let event = 'message';
      const data: string[] = [];
      block.split(/\r?\n/).forEach((line) => {
        if (line.startsWith('event:')) event = line.slice(6).trim();
        if (line.startsWith('data:')) data.push(line.slice(5).trimStart());
      });
      if (data.length) events.push({ event, data: data.join('\n') });
    }
    return events;
  }
}

export function pcm16LeToFloat32(bytes: Uint8Array, leadingByte?: number): PcmConversionResult {
  const combinedLength = bytes.length + (leadingByte == null ? 0 : 1);
  const completeLength = combinedLength - (combinedLength % 2);
  const samples = new Float32Array(completeLength / 2);
  let byteIndex = 0;
  const byteAt = (index: number) => {
    if (leadingByte == null) return bytes[index];
    return index === 0 ? leadingByte : bytes[index - 1];
  };
  for (let sampleIndex = 0; sampleIndex < samples.length; sampleIndex += 1) {
    const low = byteAt(byteIndex) ?? 0;
    const high = byteAt(byteIndex + 1) ?? 0;
    let value = low | (high << 8);
    if (value >= 0x8000) value -= 0x10000;
    samples[sampleIndex] = value < 0 ? value / 0x8000 : value / 0x7fff;
    byteIndex += 2;
  }
  return {
    samples,
    trailingByte: completeLength < combinedLength ? byteAt(combinedLength - 1) : undefined,
  };
}

export class PcmAudioStreamPlayer {
  private context?: AudioContext;
  private controller?: AbortController;
  private sources = new Set<AudioBufferSourceNode>();
  private timers = new Set<number>();
  private generation = 0;

  async play(url: string, onPlaying: () => void): Promise<void> {
    this.stop();
    const generation = ++this.generation;
    const context = this.context || new AudioContext({ sampleRate: 24_000 });
    this.context = context;
    if (context.state !== 'running') await context.resume();

    const controller = new AbortController();
    this.controller = controller;
    const response = await fetch(url, {
      method: 'GET',
      signal: controller.signal,
      cache: 'no-store',
    });
    if (!response.ok || !response.body) {
      throw new Error(`TTS stream failed with HTTP ${response.status}`);
    }
    const contentType = response.headers.get('content-type')?.toLowerCase() || '';
    if (!contentType.startsWith('text/event-stream')) {
      throw new Error(`TTS stream returned unsupported content type: ${contentType || 'unknown'}`);
    }

    const reader = response.body.getReader();
    const parser = new SseEventParser();
    let nextStart = context.currentTime + 0.04;
    let trailingByte: number | undefined;
    let started = false;
    let totalSamples = 0;
    let expectedSequence = 0;
    let receivedDone = false;

    const schedulePcm = (bytes: Uint8Array) => {
      const converted = pcm16LeToFloat32(bytes, trailingByte);
      trailingByte = converted.trailingByte;
      if (!converted.samples.length) return;

      const audioBuffer = context.createBuffer(1, converted.samples.length, 24_000);
      const channel = audioBuffer.getChannelData(0);
      for (let index = 0; index < converted.samples.length; index += 1) {
        channel[index] = converted.samples[index];
      }
      const source = context.createBufferSource();
      source.buffer = audioBuffer;
      source.connect(context.destination);
      this.sources.add(source);
      source.onended = () => this.sources.delete(source);

      const startAt = Math.max(nextStart, context.currentTime + 0.025);
      source.start(startAt);
      nextStart = startAt + audioBuffer.duration;
      totalSamples += converted.samples.length;
      if (!started) {
        started = true;
        const delay = Math.max(0, (startAt - context.currentTime) * 1_000);
        const timer = window.setTimeout(() => {
          this.timers.delete(timer);
          if (generation === this.generation) onPlaying();
        }, delay);
        this.timers.add(timer);
      }
    };

    const processEvent = (event: ParsedSseEvent) => {
      if (event.event === 'audio') {
        if (receivedDone) throw new Error('TTS stream sent audio after done');
        const payload = JSON.parse(event.data) as { sequence?: unknown; mimeType?: unknown; data?: unknown };
        if (payload.sequence !== expectedSequence) throw new Error('TTS stream sequence is invalid');
        if (typeof payload.mimeType !== 'string' || !payload.mimeType.toLowerCase().startsWith('audio/l16')) {
          throw new Error('TTS stream audio format is unsupported');
        }
        if (typeof payload.data !== 'string' || !payload.data) throw new Error('TTS stream audio is empty');
        let binary: string;
        try {
          binary = atob(payload.data);
        } catch {
          throw new Error('TTS stream audio base64 is invalid');
        }
        const bytes = new Uint8Array(binary.length);
        for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
        expectedSequence += 1;
        schedulePcm(bytes);
        return;
      }
      if (event.event === 'done') {
        if (receivedDone) throw new Error('TTS stream sent more than one done event');
        receivedDone = true;
        return;
      }
      if (event.event === 'error') {
        let message = 'Không thể phát trọn vẹn giọng đọc. Hãy đọc câu hỏi trên màn hình.';
        try {
          const payload = JSON.parse(event.data) as { message?: unknown };
          if (typeof payload.message === 'string' && payload.message.trim()) message = payload.message;
        } catch { /* keep the safe provider-neutral message */ }
        throw new Error(message);
      }
    };

    try {
      while (generation === this.generation) {
        const { done, value } = await reader.read();
        if (done) {
          parser.finish().forEach(processEvent);
          break;
        }
        if (value?.length) parser.push(value).forEach(processEvent);
      }
      if (generation !== this.generation) return;
      if (!receivedDone) throw new Error('TTS stream ended before done');
      if (trailingByte != null || totalSamples === 0) throw new Error('TTS stream returned invalid PCM audio');
    } catch (error) {
      if (generation === this.generation) this.stop();
      throw error;
    } finally {
      reader.releaseLock();
    }

    if (generation !== this.generation) return;
    const remainingMs = Math.max(0, (nextStart - context.currentTime) * 1_000);
    await new Promise<void>((resolve) => {
      const timer = window.setTimeout(() => {
        this.timers.delete(timer);
        resolve();
      }, remainingMs + 20);
      this.timers.add(timer);
    });
  }

  stop() {
    this.generation += 1;
    this.controller?.abort();
    this.controller = undefined;
    this.timers.forEach((timer) => window.clearTimeout(timer));
    this.timers.clear();
    this.sources.forEach((source) => {
      source.onended = null;
      try { source.stop(); } catch { /* source already ended */ }
      try { source.disconnect(); } catch { /* source already detached */ }
    });
    this.sources.clear();
  }

  dispose() {
    this.stop();
    const context = this.context;
    this.context = undefined;
    if (context && context.state !== 'closed') void context.close();
  }
}
