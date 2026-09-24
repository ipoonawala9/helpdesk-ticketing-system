import { afterEach, describe, expect, it, vi } from 'vitest'
import { project, rubberband, spring, velocityFrom } from './motion'

/** Drives a spring frame by frame on a controlled clock and records the path. */
function run(from: number, to: number, options?: Parameters<typeof spring>[3]) {
  const queue: FrameRequestCallback[] = []
  let now = 0
  vi.spyOn(performance, 'now').mockImplementation(() => now)
  vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => queue.push(callback))
  vi.stubGlobal('cancelAnimationFrame', () => {})

  const path: number[] = []
  spring(from, to, (value) => path.push(value), options)
  for (let frame = 0; frame < 400 && queue.length > 0; frame += 1) {
    now += 1000 / 60
    queue.shift()?.(now)
  }
  return path
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('spring', () => {
  it('settles exactly on the target without overshooting when critically damped', () => {
    const path = run(-300, 0, { damping: 1, response: 0.3 })
    expect(path.at(-1)).toBe(0)
    expect(Math.max(...path)).toBe(0)
    expect(path.length).toBeLessThan(60)
  })

  it('carries the release velocity, so a flick keeps going before it settles', () => {
    const thrown = run(-100, 0, { damping: 1, response: 0.3, velocity: 900 })
    const still = run(-100, 0, { damping: 1, response: 0.3 })
    expect(thrown[0]).toBeGreaterThan(still[0])
  })

  it('overshoots only when asked to bounce', () => {
    const bouncy = run(-100, 0, { damping: 0.7, response: 0.3, velocity: 900 })
    expect(Math.max(...bouncy)).toBeGreaterThan(0)
  })

  it('arrives immediately when motion is unwanted', () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }))
    const settled = vi.fn()
    const frames: number[] = []
    spring(-300, 0, (value) => frames.push(value), {}, settled)
    expect(frames).toEqual([0])
    expect(settled).toHaveBeenCalledOnce()
  })
})

describe('project', () => {
  it('throws further the faster the flick', () => {
    expect(project(500)).toBeCloseTo(249.5, 1)
    expect(project(1500)).toBeGreaterThan(project(500))
    expect(project(-500)).toBeCloseTo(-249.5, 1)
    expect(project(0)).toBe(0)
  })
})

describe('rubberband', () => {
  it('follows less and less the further past the edge it is pulled', () => {
    expect(rubberband(100, 400)).toBeLessThan(100)
    // However hard it is pulled, it never travels past the drawer's own width.
    expect(rubberband(10_000, 400)).toBeLessThan(400)
    // Twice the pull is nowhere near twice the movement.
    expect(rubberband(200, 400)).toBeLessThan(rubberband(100, 400) * 2)
    expect(rubberband(0, 400)).toBe(0)
  })
})

describe('velocityFrom', () => {
  it('measures the tail of the gesture, not the whole of it', () => {
    const samples = [
      { value: 0, time: 0 },
      { value: -40, time: 460 }, // a long slow drag, about 90px per second
      { value: -200, time: 540 }, // then a flick at the end
      { value: -260, time: 580 },
    ]
    // Only the last 100ms count: -200 to -260 in 40ms, not the slow lead-up.
    expect(velocityFrom(samples)).toBeCloseTo(-1500, 0)
  })

  it('reads as still when there is nothing to measure', () => {
    expect(velocityFrom([])).toBe(0)
    expect(velocityFrom([{ value: 4, time: 10 }])).toBe(0)
  })
})
