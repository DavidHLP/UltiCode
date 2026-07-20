import { describe, expect, it } from 'vitest'
import { applyTargets, PRISTINE } from '../polyhedron'

describe('applyTargets', () => {
  /**
   * Regression test: applyTargets must reset tgtBuf to PRISTINE on every call,
   * so channels the current state doesn't touch remain at their pristine defaults.
   *
   * Two-state sequence: 'opened' sets openBlend=1, portalVis=1, wireOpacity=0.
   * Then 'squashed' touches only scaleX/Y/Z, wireOpacity, jitter — it does NOT
   * touch openBlend or portalVis. Without Object.assign(tgtBuf, PRISTINE) inside
   * applyTargets, squashed would leave openBlend=1 and portalVis=1 — stale bleed.
   */
  it('resets untouched channels to PRISTINE on every call', () => {
    const buf = { ...PRISTINE }
    applyTargets('opened', 0, null, buf, false, 0)
    expect(buf.openBlend).toBe(1)
    expect(buf.portalVis).toBe(1)
    expect(buf.wireOpacity).toBe(0)

    applyTargets('squashed', 0, null, buf, false, 0)
    // squashed must not have touched openBlend or portalVis — reset brings them to 0
    expect(buf.openBlend).toBe(0)
    expect(buf.portalVis).toBe(0)
    // squashed's own channels are correct
    expect(buf.scaleX).toBe(1.15)
    expect(buf.wireOpacity).toBe(1)
  })

  it('squashed sets its specific channels and leaves others at PRISTINE', () => {
    const buf = { ...PRISTINE }
    applyTargets('squashed', 0, null, buf, false, 0)
    expect(buf.scaleX).toBe(1.15)
    expect(buf.scaleY).toBe(1.15)
    expect(buf.scaleZ).toBe(0.6)
    expect(buf.wireOpacity).toBe(1)
    expect(buf.jitter).toBe(1)
  })

  it('opened hides the device and shows the portal', () => {
    const buf = { ...PRISTINE }
    applyTargets('opened', 0, null, buf, false, 0)
    expect(buf.openBlend).toBe(1)
    expect(buf.portalVis).toBe(1)
    expect(buf.wireOpacity).toBe(0)
    expect(buf.idleSpin).toBe(0)
  })

  it('broken in harmony mode eases back toward PRISTINE', () => {
    const buf = { ...PRISTINE }
    applyTargets('broken', 0, null, buf, true, 0.5) // halfway through reverse tween
    // At t=0.5, lerp(1, 0, 0.5) = 0.5
    expect(buf.brokenBlend).toBeCloseTo(0.5)
    expect(buf.rotX).toBeCloseTo(0.1) // lerp(0.2, 0, 0.5)
    expect(buf.wireOpacity).toBeCloseTo(0.85)
  })

  it('broken outside harmony mode sets full lean', () => {
    const buf = { ...PRISTINE }
    applyTargets('broken', 0, null, buf, false, 0)
    expect(buf.brokenBlend).toBe(1)
    expect(buf.rotX).toBe(0.2)
    expect(buf.rotZ).toBe(-0.15)
    expect(buf.magnetic).toBe(1)
    expect(buf.wireOpacity).toBe(0.85)
  })
})
