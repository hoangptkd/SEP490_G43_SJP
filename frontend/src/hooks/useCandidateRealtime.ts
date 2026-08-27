import { useEffect, useRef } from 'react';
import {
  candidateRealtime,
  type CandidateRealtimeEvent,
} from '../services/candidateRealtime';

export function useCandidateRealtime(listener: (event: CandidateRealtimeEvent) => void) {
  const listenerRef = useRef(listener);
  useEffect(() => {
    listenerRef.current = listener;
  }, [listener]);

  useEffect(() => candidateRealtime.subscribe((event) => listenerRef.current(event)), []);
}
