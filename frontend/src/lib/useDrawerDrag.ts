import { useCallback, useEffect, useLayoutEffect, useRef } from 'react'
import { project, rubberband, spring, velocityFrom } from './motion'

const THRESHOLD = 10 // px of horizontal travel before this counts as a drag
const SHADOW_GAP = 8 // matches the closed transform in the stylesheet

/**
 * Lets the narrow-screen navigation drawer be pushed shut with a finger.
 *
 * The drawer tracks the pointer one to one, resists being dragged past open,
 * and on release is thrown to wherever the flick was heading rather than to
 * whichever edge happens to be nearer. A spring finishes the movement at the
 * speed the finger left it, so there is no seam between dragging and
 * animating, and it can be caught again mid-flight.
 */
export function useDrawerDrag(open: boolean, onClose: () => void) {
  const drawerRef = useRef<HTMLElement>(null)
  const shellRef = useRef<HTMLDivElement>(null)
  const busy = useRef(false)

  const paint = useCallback((x: number, width: number) => {
    const progress = Math.min(1, Math.max(0, 1 + x / width))
    drawerRef.current?.style.setProperty('--drawer-x', `${x}px`)
    shellRef.current?.style.setProperty('--drawer-progress', progress.toFixed(3))
  }, [])

  const clear = useCallback(() => {
    drawerRef.current?.removeAttribute('data-dragging')
    drawerRef.current?.style.removeProperty('--drawer-x')
    shellRef.current?.removeAttribute('data-dragging')
    shellRef.current?.style.removeProperty('--drawer-progress')
  }, [])

  // Once React has taken the drawer down, the drag's own offsets can go: the
  // stylesheet's closed position is exactly where the spring left it.
  useLayoutEffect(() => {
    if (!busy.current) clear()
  }, [open, clear])

  useEffect(() => {
    const shell = shellRef.current
    const drawer = drawerRef.current
    if (!shell || !drawer || !open) return

    let pointer: number | null = null
    let startX = 0
    let startY = 0
    let origin = 0
    let width = 0
    let dragging = false
    let samples: { value: number; time: number }[] = []
    let stopSpring: (() => void) | null = null

    const narrow = () => window.matchMedia('(max-width: 900px)').matches
    const liveX = () => {
      const value = drawer.style.getPropertyValue('--drawer-x')
      return value ? Number.parseFloat(value) : 0
    }

    const reset = () => {
      pointer = null
      dragging = false
      samples = []
    }

    const onPointerDown = (event: PointerEvent) => {
      if (pointer !== null || !narrow()) return
      if (event.pointerType === 'mouse' && event.button !== 0) return
      // A spring in flight is caught where it is, not where it was heading.
      stopSpring?.()
      stopSpring = null
      origin = drawer.hasAttribute('data-dragging') ? liveX() : 0
      width = drawer.offsetWidth + SHADOW_GAP
      pointer = event.pointerId
      startX = event.clientX
      startY = event.clientY
      samples = [{ value: origin, time: event.timeStamp }]
    }

    const onPointerMove = (event: PointerEvent) => {
      if (event.pointerId !== pointer) return
      const dx = event.clientX - startX
      const dy = event.clientY - startY

      if (!dragging) {
        // Wait to see which way this is going before committing to either.
        if (Math.abs(dy) > THRESHOLD && Math.abs(dy) > Math.abs(dx)) return reset()
        if (Math.abs(dx) < THRESHOLD) return
        dragging = true
        busy.current = true
        // Keeps tracking once the finger leaves the drawer. A pointer that has
        // already gone cannot be captured, which is not worth failing over.
        try {
          shell.setPointerCapture(event.pointerId)
        } catch {
          /* the gesture still works from the shell's own listeners */
        }
        drawer.setAttribute('data-dragging', 'true')
        shell.setAttribute('data-dragging', 'true')
      }

      event.preventDefault()
      const wanted = origin + dx
      // Open is the far edge; past it the drawer follows less and less.
      const x = wanted > 0 ? rubberband(wanted, width) : Math.max(wanted, -width)
      samples.push({ value: x, time: event.timeStamp })
      if (samples.length > 8) samples.shift()
      paint(x, width)
    }

    const onPointerUp = (event: PointerEvent) => {
      if (event.pointerId !== pointer) return
      if (!dragging) return reset()
      reset()

      const blockClick = (click: MouseEvent) => {
        click.preventDefault()
        click.stopPropagation()
      }
      shell.addEventListener('click', blockClick, true)
      window.setTimeout(() => shell.removeEventListener('click', blockClick, true), 0)

      const from = liveX()
      const velocity = velocityFrom(samples)
      const closing = from + project(velocity) < -width / 2
      stopSpring = spring(
        from,
        closing ? -width : 0,
        (x) => paint(x, width),
        // A throw keeps a little bounce; a drag that barely moved should not.
        { damping: Math.abs(velocity) > 300 ? 0.85 : 1, response: 0.3, velocity },
        () => {
          stopSpring = null
          busy.current = false
          if (closing) onClose()
          else clear()
        },
      )
    }

    shell.addEventListener('pointerdown', onPointerDown)
    shell.addEventListener('pointermove', onPointerMove)
    shell.addEventListener('pointerup', onPointerUp)
    shell.addEventListener('pointercancel', onPointerUp)
    return () => {
      stopSpring?.()
      busy.current = false
      shell.removeEventListener('pointerdown', onPointerDown)
      shell.removeEventListener('pointermove', onPointerMove)
      shell.removeEventListener('pointerup', onPointerUp)
      shell.removeEventListener('pointercancel', onPointerUp)
    }
  }, [open, onClose, paint, clear])

  return { drawerRef, shellRef }
}
