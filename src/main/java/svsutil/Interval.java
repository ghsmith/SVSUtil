/**
 * Java Modular Image Synthesis Toolkit (JMIST)
 * Copyright (C) 2018 Bradley W. Kimmel
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
package svsutil;

import java.io.Serializable;

/**
 * An closed interval [a, b] on the real number line.
 * This class is immutable.
 * @author Brad Kimmel
 */
public final class Interval implements Serializable {

  /** Serialization version ID. */
  private static final long serialVersionUID = -4034510279908046892L;

  /**
   * The entire real line (-infinity, infinity).
   * {@code Interval.UNIVERSE.contains(t)} will return true for all t.
   */
  public static final Interval UNIVERSE = new Interval(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

  /** The unit interval: [0, 1]. */
  public static final Interval UNIT = new Interval(0.0, 1.0);

  /**
   * The empty set.
   * {@code Interval.EMPTY.contains(t)} will return false for all t.
   */
  public static final Interval EMPTY = new Interval();

  /**
   * The interval containing the non-negative real numbers: [0, infinity).
   */
  public static final Interval POSITIVE = new Interval(0.0, Double.POSITIVE_INFINITY);

  /**
   * The interval containing the non-positive real numbers: (-infinity, 0].
   */
  public static final Interval NEGATIVE = new Interval(Double.NEGATIVE_INFINITY, 0.0);

  /** The lower bound of this interval. */
  private final double minimum;

  /** The upper bound of this interval. */
  private final double maximum;

  /**
   * Initializes the endpoints of the interval.
   * Requires {@code minimum <= maximum}
   * @param minimum The lower bound of the interval.
   * @param maximum The upper bound of the interval.
   */
  public Interval(double minimum, double maximum) {
    assert(minimum <= maximum);
    this.minimum = minimum;
    this.maximum = maximum;
  }

  /** Default constructor (private). */
  private Interval() {
    minimum = maximum = Double.NaN;
  }

  /**
   * Gets the interval between the specified values.
   * @param a The first value.
   * @param b The second value.
   * @return The interval [a, b], if a &lt;= b. The interval [b, a], if
   *     b &lt; a.
   */
  public static Interval between(double a, double b) {
    return new Interval(Math.min(a, b), Math.max(a, b));
  }

  /**
   * Creates an <code>Interval</code> representing [a, infinity).
   * @param a The lower bound of the interval.
   * @return The new <code>Interval</code>.
   */
  public static Interval greaterThan(double a) {
    return new Interval(a, Double.POSITIVE_INFINITY);
  }

  /**
   * Creates an <code>Interval</code> representing (-infinity, b].
   * @param b The lower bound of the interval.
   * @return The new <code>Interval</code>.
   */
  public static Interval lessThan(double b) {
    return new Interval(Double.NEGATIVE_INFINITY, b);
  }

  /**
   * Gets the lower bound of this interval.
   * @return The lower bound of this interval.
   */
  public double minimum() {
    return minimum;
  }

  /**
   * Gets the upper bound of this interval.
   * @return The upper bound of this interval.
   */
  public double maximum() {
    return maximum;
  }

  /**
   * Gets the length of this interval (i.e., the distance
   * between the endpoints).
   * @return The length of the interval.
   */
  public double length() {
    return maximum - minimum;
  }

  /**
   * Determines if the interval contains a particular value.
   * @param t The value to check for containment.
   * @return True if {@code this.minimum() <= t <= this.maximum()}, false otherwise.
   */
  public boolean contains(double t) {
    return minimum <= t && t <= maximum;
  }

  /**
   * Determines if this interval contains a given interval.
   * @param I The <code>Interval</code> to check for containment.
   * @return True if <code>I</code> is empty or if
   *     {@code this.minimum() <= I.minimum() < I.maximum() <= this.maximum()},
   *     false otherwise.
   */
  public boolean contains(Interval I) {
    return I.isEmpty() || (minimum <= I.minimum && I.maximum <= maximum);
  }

  /**
   * Determines if this interval contains the interval
   * <code>(t - epsilon, t + epsilon)</code>.
   * @param t The center of the interval to check for containment.
   * @param epsilon Half the width of the interval to check for containment.
   * @return True if {@code minimum() <= t - epsilon < t + epsilon <= maximum()},
   *     false otherwise.
   */
  public boolean contains(double t, double epsilon) {
    return (minimum <= t - epsilon) && (t + epsilon <= maximum);
  }

  /**
   * Determines if this interval is the empty interval.
   * @return A value indicating if this interval is empty.
   */
  public boolean isEmpty() {
    return Double.isNaN(minimum);
  }

  /**
   * Determines if this interval is infinite.
   * @return A value indicating if this interval is infinite.
   */
  public boolean isInfinite() {
    return !isEmpty() && (Double.isInfinite(minimum) || Double.isInfinite(maximum));
  }

  /**
   * Extends this interval to include the specified value.
   * Guarantees that {@code this.contains(t)} after this method is called.
   * @param t The value to include in this interval.
   * @return The extended interval.
   * @see #contains(double)
   */
  public Interval extendTo(double t) {
    if (isEmpty()) {
      return new Interval(t, t);
    } else if (t < minimum) {
      return new Interval(t, maximum);
    } else if (t > maximum) {
      return new Interval(minimum, maximum);
    }

    return this;
  }

  /**
   * Expands this interval by the specified amount.
   * @param amount The amount to expand this interval by.
   * @return The expanded interval.
   */
  public Interval expand(double amount) {
    double newMinimum = minimum - amount;
    double newMaximum = maximum + amount;

    if (newMinimum > newMaximum) {
      return Interval.EMPTY;
    } else {
      return new Interval(newMinimum, newMaximum);
    }
  }

  /**
   * Computes the intersection of this interval with another.
   * @param I The interval to intersect with this one.
   * @return The intersection of this interval with I.
   */
  public Interval intersect(Interval I) {
    return new Interval(Math.max(minimum, I.minimum), Math.min(maximum, I.maximum));
  }

  /**
   * Determines whether this interval intersects with another.
   * Equivalent to {@code !this.intersect(I).isEmpty()}.
   * @param I The interval with which to check for an intersection
   * @return A value indicating whether the two intervals overlap.
   * @see #intersect(Interval)
   * @see #isEmpty()
   */
  public boolean intersects(Interval I) {
    return Math.max(minimum, I.minimum) <= Math.min(maximum, I.maximum);
  }

  /**
   * Interpolates between the endpoints of this interval.  If
   * {@code t} is in [0, 1], then the result will fall within
   * the interval, otherwise, the result will fall outside the
   * interval.
   * @param t The value at which to interpolate.
   * @return The interpolated point.
   */
  public double interpolate(double t) {
    return this.minimum + t * (this.maximum - this.minimum);
  }

}