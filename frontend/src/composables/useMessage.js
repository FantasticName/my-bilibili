import { inject } from 'vue'

export function useMessage() {
  const message = inject('message', () => {})

  return {
    success: (text, duration) => message(text, 'success', duration),
    error: (text, duration) => message(text, 'error', duration),
    info: (text, duration) => message(text, 'info', duration),
  }
}
