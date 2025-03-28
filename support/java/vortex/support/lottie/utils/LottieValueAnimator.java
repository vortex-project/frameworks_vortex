package vortex.support.lottie.utils;

import android.animation.ValueAnimator;
import android.support.annotation.FloatRange;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.view.Choreographer;

import vortex.support.lottie.LottieComposition;

/**
 * This is a slightly modified {@link ValueAnimator} that allows us to update start and end values
 * easily optimizing for the fact that we know that it's a value animator with 2 floats.
 */
public class LottieValueAnimator extends BaseLottieAnimator implements Choreographer.FrameCallback {


  private float speed = 1f;
  private boolean speedReversedForRepeatMode = false;
  private long lastFrameTimeNs = 0;
  private float frame = 0;
  private int repeatCount = 0;
  private float minFrame = Integer.MIN_VALUE;
  private float maxFrame = Integer.MAX_VALUE;
  @Nullable private LottieComposition composition;
  @VisibleForTesting protected boolean isRunning = false;

  public LottieValueAnimator() {
  }

  /**
   * Returns a float representing the current value of the animation from 0 to 1
   * regardless of the animation speed, direction, or min and max frames.
   */
  @Override public Object getAnimatedValue() {
    return getAnimatedValueAbsolute();
  }

  /**
   * Returns the current value of the animation from 0 to 1 regardless
   * of the animation speed, direction, or min and max frames.
   */
  @FloatRange(from = 0f, to = 1f) public float getAnimatedValueAbsolute() {
    if (composition == null) {
      return 0;
    }
    return (frame - composition.getStartFrame()) / (composition.getEndFrame() - composition.getStartFrame());

  }

  /**
   * Returns the current value of the currently playing animation taking into
   * account direction, min and max frames.
   */
  @Override @FloatRange(from = 0f, to = 1f) public float getAnimatedFraction() {
    if (composition == null) {
      return 0;
    }
    if (isReversed()) {
      return (getMaxFrame() - frame) / (getMaxFrame() - getMinFrame());
    } else {
      return (frame - getMinFrame()) / (getMaxFrame() - getMinFrame());
    }
  }

  @Override public long getDuration() {
    return composition == null ? 0 : (long) composition.getDuration();
  }

  public float getFrame() {
    return frame;
  }

  @Override public boolean isRunning() {
    return isRunning;
  }

  @Override public void doFrame(long frameTimeNanos) {
    postFrameCallback();
    if (composition == null || !isRunning()) {
      return;
    }

    long now = System.nanoTime();
    long timeSinceFrame = now - lastFrameTimeNs;
    float frameDuration = getFrameDurationNs();
    float dFrames = timeSinceFrame / frameDuration;

    frame += isReversed() ? -dFrames : dFrames;
    boolean ended = !MiscUtils.contains(frame, getMinFrame(), getMaxFrame());
    frame = MiscUtils.clamp(frame, getMinFrame(), getMaxFrame());

    lastFrameTimeNs = now;

    notifyUpdate();
    if (ended) {
      if (getRepeatCount() != INFINITE && repeatCount >= getRepeatCount()) {
        frame = getMaxFrame();
        removeFrameCallback();
        notifyEnd(isReversed());
      } else {
        notifyRepeat();
        repeatCount++;
        if (getRepeatMode() == REVERSE) {
          speedReversedForRepeatMode = !speedReversedForRepeatMode;
          reverseAnimationSpeed();
        } else {
          frame = isReversed() ? getMaxFrame() : getMinFrame();
        }
        lastFrameTimeNs = now;
      }
    }

    verifyFrame();
  }

  private float getFrameDurationNs() {
    if (composition == null) {
      return Float.MAX_VALUE;
    }
    return Utils.SECOND_IN_NANOS / composition.getFrameRate() / Math.abs(speed);
  }

  public void setComposition(LottieComposition composition) {
    this.composition = composition;

    setMinAndMaxFrames(
        (int) Math.max(this.minFrame, composition.getStartFrame()),
        (int) Math.min(this.maxFrame, composition.getEndFrame())
    );
    setFrame((int) frame);
    lastFrameTimeNs = System.nanoTime();
  }

  public void setFrame(int frame) {
    if (this.frame == frame) {
      return;
    }
    this.frame = MiscUtils.clamp(frame, getMinFrame(), getMaxFrame());
    lastFrameTimeNs = System.nanoTime();
    notifyUpdate();
  }

  public void setMinFrame(int minFrame) {
    setMinAndMaxFrames(minFrame, (int) maxFrame);
  }

  public void setMaxFrame(int maxFrame) {
    setMinAndMaxFrames((int) minFrame, maxFrame);
  }

  public void setMinAndMaxFrames(int minFrame, int maxFrame) {
    float compositionMinFrame = composition == null ? Float.MIN_VALUE : composition.getStartFrame();
    float compositionMaxFrame = composition == null ? Float.MAX_VALUE : composition.getEndFrame();
    this.minFrame = MiscUtils.clamp(minFrame, compositionMinFrame, compositionMaxFrame);
    this.maxFrame = MiscUtils.clamp(maxFrame, compositionMinFrame, compositionMaxFrame);
    setFrame((int) MiscUtils.clamp(frame, minFrame, maxFrame));
  }

  public void reverseAnimationSpeed() {
    setSpeed(-getSpeed());
  }

  public void setSpeed(float speed) {
    this.speed = speed;
  }

  /**
   * Returns the current speed. This will be affected by repeat mode REVERSE.
   */
  public float getSpeed() {
    return speed;
  }

  @Override public void setRepeatMode(int value) {
    super.setRepeatMode(value);
    if (value != REVERSE && speedReversedForRepeatMode) {
      speedReversedForRepeatMode = false;
      reverseAnimationSpeed();
    }
  }

  public void playAnimation() {
    notifyStart(isReversed());
    setFrame((int) (isReversed() ? getMaxFrame() : getMinFrame()));
    lastFrameTimeNs = System.nanoTime();
    repeatCount = 0;
    postFrameCallback();
  }

  public void endAnimation() {
    removeFrameCallback();
    notifyEnd(isReversed());
  }

  public void pauseAnimation() {
    removeFrameCallback();
  }

  public void resumeAnimation() {
    postFrameCallback();
    lastFrameTimeNs = System.nanoTime();
    if (isReversed() && getFrame() == getMinFrame()) {
      frame = getMaxFrame();
    } else if (!isReversed() && getFrame() == getMaxFrame()) {
      frame = getMinFrame();
    }
  }

  @Override public void cancel() {
    notifyCancel();
    removeFrameCallback();
  }

  private boolean isReversed() {
    return getSpeed() < 0;
  }

  public float getMinFrame() {
    if (composition == null) {
      return 0;
    }
    return minFrame == Integer.MIN_VALUE ? composition.getStartFrame() : minFrame;
  }

  public float getMaxFrame() {
    if (composition == null) {
      return 0;
    }
    return maxFrame == Integer.MAX_VALUE ? composition.getEndFrame() : maxFrame;
  }

  protected void postFrameCallback() {
    removeFrameCallback();
    Choreographer.getInstance().postFrameCallback(this);
    isRunning = true;
  }

  protected void removeFrameCallback() {
    Choreographer.getInstance().removeFrameCallback(this);
    isRunning = false;
  }

  private void verifyFrame() {
    if (composition == null) {
      return;
    }
    if (frame < minFrame || frame > maxFrame) {
      throw new IllegalStateException(String.format("Frame must be [%f,%f]. It is %f", minFrame, maxFrame, frame));
    }
  }
}
