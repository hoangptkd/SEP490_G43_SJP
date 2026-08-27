import { describe, expect, it } from 'vitest';
import { pcm16LeToFloat32, SseEventParser } from './pcmAudioStreamPlayer';

describe('pcm16LeToFloat32', () => {
  it('converts signed 16-bit little-endian PCM to normalized samples', () => {
    const result = pcm16LeToFloat32(new Uint8Array([
      0x00, 0x00,
      0xff, 0x7f,
      0x00, 0x80,
    ]));

    expect(Array.from(result.samples)).toEqual([0, 1, -1]);
    expect(result.trailingByte).toBeUndefined();
  });

  it('preserves an odd trailing byte for the next network chunk', () => {
    const first = pcm16LeToFloat32(new Uint8Array([0x34]));
    const second = pcm16LeToFloat32(new Uint8Array([0x12, 0x78]), first.trailingByte);

    expect(first.samples).toHaveLength(0);
    expect(second.samples[0]).toBeCloseTo(0x1234 / 0x7fff, 6);
    expect(second.trailingByte).toBe(0x78);
  });
});

describe('SseEventParser', () => {
  it('reassembles an event split across arbitrary network chunks', () => {
    const parser = new SseEventParser();
    const encoder = new TextEncoder();

    expect(parser.push(encoder.encode('event: aud'))).toEqual([]);
    expect(parser.push(encoder.encode('io\ndata: {"sequence":0}\n'))).toEqual([]);
    expect(parser.push(encoder.encode('\nevent: done\ndata: {"chunks":1}\n\n'))).toEqual([
      { event: 'audio', data: '{"sequence":0}' },
      { event: 'done', data: '{"chunks":1}' },
    ]);
    expect(parser.finish()).toEqual([]);
  });

  it('rejects EOF with an incomplete SSE event', () => {
    const parser = new SseEventParser();
    parser.push(new TextEncoder().encode('event: audio\ndata: {}'));

    expect(() => parser.finish()).toThrow('incomplete SSE event');
  });
});
