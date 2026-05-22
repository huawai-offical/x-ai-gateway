import '@testing-library/jest-dom/vitest'

const elementPrototype = globalThis.Element?.prototype as
  | {
      scrollIntoView?: (arg?: boolean | ScrollIntoViewOptions) => void
      hasPointerCapture?: (pointerId: number) => boolean
      setPointerCapture?: (pointerId: number) => void
      releasePointerCapture?: (pointerId: number) => void
    }
  | undefined

if (elementPrototype) {
  elementPrototype.scrollIntoView ??= () => {}
  elementPrototype.hasPointerCapture ??= () => false
  elementPrototype.setPointerCapture ??= () => {}
  elementPrototype.releasePointerCapture ??= () => {}
}

if (typeof window !== 'undefined' && !window.matchMedia) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }),
  })
}

if (typeof window !== 'undefined' && typeof window.ResizeObserver === 'undefined') {
  class ResizeObserverMock {
    observe() {}

    unobserve() {}

    disconnect() {}
  }

  window.ResizeObserver = ResizeObserverMock as typeof ResizeObserver
}
