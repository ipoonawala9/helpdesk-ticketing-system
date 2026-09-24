/**
 * Motion primitives, described the way a designer describes motion rather than
 * the way physics does: how bouncy it is, and roughly how long it takes.
 *
 * A spring is used instead of a fixed-duration curve because anything a finger
 * can touch has to be interruptible — a new target simply changes where the
 * spring is going, and the value keeps moving from wherever it currently is.
 */

export interface SpringOptions {
  /** 1 settles without overshoot. Below 1 overshoots; reserve it for flicks. */
  damping?: number
  /** Roughly how long the value takes to arrive, in seconds. */
  response?: number
  /** The value's speed as the spring starts, in units per second. */
  velocity?: number
}

const prefersReducedMotion = () =>
  typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

/**
 * Run a spring from `from` to `to`, reporting every frame. Returns a stop
 * function. Where motion is unwanted the value arrives immediately, which is
 * the still equivalent rather than a faster version of the same movement.
 */
export function spring(
  from: number,
  to: number,
  onFrame: (value: number) => void,
  { damping = 1, response = 0.35, velocity = 0 }: SpringOptions = {},
  onSettle?: () => void,
): () => void {
  if (response <= 0 || prefersReducedMotion()) {
    onFrame(to)
    onSettle?.()
    return () => {}
  }

  const omega = (2 * Math.PI) / response
  const zeta = Math.min(Math.max(damping, 0.1), 1)
  const offset = from - to
  const started = performance.now()
  let previous = offset
  let frame = requestAnimationFrame(function tick(now) {
    const t = (now - started) / 1000
    const decay = Math.exp(-zeta * omega * t)
    const current =
      zeta === 1
        ? (offset + (velocity + omega * offset) * t) * decay
        : decay *
          (offset * Math.cos(omega * Math.sqrt(1 - zeta * zeta) * t) +
            ((velocity + zeta * omega * offset) / (omega * Math.sqrt(1 - zeta * zeta))) *
              Math.sin(omega * Math.sqrt(1 - zeta * zeta) * t))

    // Settled once it is both near the target and barely moving.
    if (Math.abs(current) < 0.5 && Math.abs(current - previous) < 0.5) {
      onFrame(to)
      onSettle?.()
      return
    }
    previous = current
    onFrame(to + current)
    frame = requestAnimationFrame(tick)
  })

  return () => cancelAnimationFrame(frame)
}

/**
 * Where a flick would come to rest if it were left to decelerate on its own.
 * Snapping from the release point ignores how hard the throw was; snapping
 * from the projected point is what makes a flick feel thrown.
 */
export function project(velocity: number, decelerationRate = 0.998) {
  return ((velocity / 1000) * decelerationRate) / (1 - decelerationRate)
}

/**
 * Progressive resistance past an edge. A hard stop reads as frozen; resistance
 * reads as responsive, with nothing more to find in that direction.
 */
export function rubberband(overshoot: number, dimension: number, constant = 0.55) {
  return (overshoot * dimension * constant) / (dimension + constant * Math.abs(overshoot))
}

/** Speed in units per second over the tail of a gesture, for the handoff. */
export function velocityFrom(samples: { value: number; time: number }[], window = 100) {
  const last = samples.at(-1)
  if (!last) return 0
  const first = samples.find((sample) => last.time - sample.time <= window) ?? samples[0]
  const elapsed = last.time - first.time
  return elapsed > 0 ? ((last.value - first.value) / elapsed) * 1000 : 0
}
