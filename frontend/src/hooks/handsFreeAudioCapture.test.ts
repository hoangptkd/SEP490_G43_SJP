import { beforeAll, describe, expect, it, vi } from 'vitest';
import { CandidateAudioCapture } from './handsFreeAudioCapture';

class FakeRecorder {
  state: RecordingState = 'inactive';
  mimeType = 'audio/webm;codecs=opus';
  ondataavailable: ((event: BlobEvent) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  onstop: ((event: Event) => void) | null = null;

  start() { this.state = 'recording'; }
  stop() {
    if (this.state === 'inactive') return;
    this.state = 'inactive';
    this.ondataavailable?.({ data: new Blob(['candidate'], { type: this.mimeType }) } as BlobEvent);
    this.onstop?.(new Event('stop'));
  }
}

describe('CandidateAudioCapture', () => {
  beforeAll(() => {
    Object.defineProperty(globalThis, 'MediaRecorder', {
      configurable: true,
      value: { isTypeSupported: () => true },
    });
  });

  it('creates one independently decodable segment and reuses the microphone stream', async () => {
    const stopTrack = vi.fn();
    const stream = { getTracks: () => [{ readyState: 'live', stop: stopTrack }] } as unknown as MediaStream;
    const getUserMedia = vi.fn().mockResolvedValue(stream);
    let now = 1_000;
    const capture = new CandidateAudioCapture({
      getUserMedia,
      createRecorder: () => new FakeRecorder() as unknown as MediaRecorder,
      now: () => now,
    });

    await capture.startSegment(0);
    expect(capture.recording).toBe(true);
    await expect(capture.startSegment(1)).rejects.toThrow('đang được ghi');
    now = 2_500;
    const first = await capture.stopSegment();
    expect(first?.sequence).toBe(0);
    expect(first?.file.type).toBe('audio/webm;codecs=opus');
    expect(first?.durationSeconds).toBe(1.5);

    await capture.startSegment(1);
    now = 3_000;
    const second = await capture.stopSegment();
    expect(second?.sequence).toBe(1);
    expect(getUserMedia).toHaveBeenCalledTimes(1);
    capture.dispose();
    expect(stopTrack).toHaveBeenCalledTimes(1);
  });

  it('discards an active candidate segment during cleanup', async () => {
    const stopTrack = vi.fn();
    const stream = { getTracks: () => [{ readyState: 'live', stop: stopTrack }] } as unknown as MediaStream;
    const capture = new CandidateAudioCapture({
      getUserMedia: async () => stream,
      createRecorder: () => new FakeRecorder() as unknown as MediaRecorder,
      now: () => 1_000,
    });
    await capture.startSegment(0);
    capture.dispose();
    expect(capture.recording).toBe(false);
    expect(stopTrack).toHaveBeenCalledOnce();
  });
});
