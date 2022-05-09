/* ww w.  ja va 2 s  . com
 * Java Modular Image Synthesis Toolkit (JMIST)
 * Copyright (C) 2008-2013 Bradley W. Kimmel
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class Interpolater {
    /**
     * Performs a trilinear interpolation between eight values.
     * @param _000 The value at <code>(t, u, v) = (0, 0, 0)</code>.
     * @param _100 The value at <code>(t, u, v) = (1, 0, 0)</code>.
     * @param _010 The value at <code>(t, u, v) = (0, 1, 0)</code>.
     * @param _110 The value at <code>(t, u, v) = (1, 1, 0)</code>.
     * @param _001 The value at <code>(t, u, v) = (0, 0, 1)</code>.
     * @param _101 The value at <code>(t, u, v) = (1, 0, 1)</code>.
     * @param _011 The value at <code>(t, u, v) = (0, 1, 1)</code>.
     * @param _111 The value at <code>(t, u, v) = (1, 1, 1)</code>.
     * @param t The first value at which to interpolate.
     * @param u The second value at which to interpolate.
     * @param v The third value at which to interpolate.
     * @return The interpolated value at <code>(t, u, v)</code>.
     */
    public static double trilinearInterpolate(double _000, double _100,
            double _010, double _110, double _001, double _101,
            double _011, double _111, double t, double u, double v) {

        return interpolate(
                bilinearInterpolate(_000, _001, _010, _011, u, v),
                bilinearInterpolate(_100, _101, _110, _111, u, v), t);

    }
    /**
     * Interpolates between two end points.
     * @param a The end point at <code>t = 0</code>.
     * @param b The end point at <code>t = 1</code>.
     * @param t The value at which to interpolate.
     * @return The value that is the fraction <code>t</code> of the way from
     *     <code>a</code> to <code>b</code>: <code>(1-t)a + tb</code>.
     */
    public static double interpolate(double a, double b, double t) {
        return a + t * (b - a);
    }
    /**
     * Interpolates between two points on a line.
     * @param x0 The x-coordinate of the first point.
     * @param y0 The y-coordinate of the first point.
     * @param x1 The x-coordinate of the second point.
     * @param y1 The y-coordinate of the second point.
     * @param x The x-coordinate at which to interpolate.
     * @return The y-coordinate corresponding to <code>x</code>.
     */
    public static double interpolate(double x0, double y0, double x1,
            double y1, double x) {
        double t = (x - x0) / (x1 - x0);
        return interpolate(y0, y1, t);
    }
    /**
     * Interpolates a piecewise linear curve.
     * @param xs An array of x-coordinates (this must be sorted in ascending
     *     order).
     * @param ys An array of the y-coordinates (must be of the same length as
     *     <code>xs</code>, or one less if <code>wrap == true</code>).
     * @param x The x-coordinate at which to interpolate.
     * @param wrap A value indicating whether the curve is periodic.
     * @return The y-coordinate corresponding to <code>x0</code>.
     */
    public static double interpolate(double[] xs, double[] ys, double x,
            boolean wrap) {
        if (wrap) {
            return interpolateWrapped(xs, ys, x);
        } else {
            return interpolate(xs, ys, x);
        }
    }
    /**
     * Interpolates a piecewise linear curve.
     * @param xs An array of x-coordinates (this must be sorted in ascending
     *     order).
     * @param ys An array of the y-coordinates (must be of the same length as
     *     <code>xs</code>).
     * @param x The x-coordinate at which to interpolate.
     * @return The y-coordinate corresponding to <code>x0</code>.
     */
    public static double interpolate(double[] xs, double[] ys, double x) {

        if (x <= xs[0]) {
            return ys[0];
        }
        if (x >= xs[xs.length - 1]) {
            return ys[xs.length - 1];
        }

        int index = Arrays.binarySearch(xs, x);
        if (index < 0) {
            index = -(index + 1);
        }
        while (index < xs.length - 1 && !(x < xs[index + 1])) {
            index++;
        }

        assert (index < xs.length - 1);

        return interpolate(xs[index - 1], ys[index - 1], xs[index],
                ys[index], x);

    }
    /**
     * Interpolates a piecewise linear curve.
     * @param xs An array of x-coordinates (this must be sorted in ascending
     *     order).
     * @param ys An array of the y-coordinates (must be of the same length as
     *     <code>xs</code>, or one less if <code>wrap == true</code>).
     * @param x The x-coordinate at which to interpolate.
     * @param wrap A value indicating whether the curve is periodic.
     * @return The y-coordinate corresponding to <code>x0</code>.
     */
    public static double interpolate(List<Double> xs, List<Double> ys,
            double x, boolean wrap) {
        if (wrap) {
            return interpolateWrapped(xs, ys, x);
        } else {
            return interpolate(xs, ys, x);
        }
    }
    /**
     * Interpolates a piecewise linear curve.
     * @param xs An array of x-coordinates (this must be sorted in ascending
     *     order).
     * @param ys An array of the y-coordinates (must of length of
     *     <code>xs.size() - 1</code>).
     * @param x The x-coordinate at which to interpolate.
     * @return The y-coordinate corresponding to <code>x</code>.
     */
    public static double interpolate(List<Double> xs, List<Double> ys,
            double x) {

        if (x <= xs.get(0)) {
            return ys.get(0);
        }

        int n = xs.size();
        if (x >= xs.get(n - 1)) {
            return ys.get(n - 1);
        }

        int index = Collections.binarySearch(xs, x);
        if (index < 0) {
            index = -(index + 1);
        }
        while (index < n - 1 && !(x < xs.get(index + 1))) {
            index++;
        }

        assert (index < n - 1);

        return interpolate(xs.get(index - 1), ys.get(index - 1),
                xs.get(index), ys.get(index), x);

    }
    /**
     * Interpolates a piecewise linear curve.
     * @param x0 The minimum value in the domain.
     * @param x1 The maximum value in the domain (must not be less than
     *     <code>x0</code>).
     * @param y An array of the y-coordinates (must have at least two elements).
     * @param x The x-coordinate at which to interpolate.
     * @param wrap A value indicating whether the curve is periodic.
     * @return The y-coordinate corresponding to <code>x</code>.
     */
    public static double interpolate(double x0, double x1, double[] y,
            double x, boolean wrap) {
        if (wrap) {
            return interpolateWrapped(x0, x1, y, x);
        } else {
            return interpolate(x0, x1, y, x);
        }
    }
    /**
     * Interpolates a piecewise linear curve.
     * @param x0 The minimum value in the domain.
     * @param x1 The maximum value in the domain (must not be less than
     *     <code>x0</code>).
     * @param y An array of the y-coordinates (must have at least two elements).
     * @param x The x-coordinate at which to interpolate.
     * @return The y-coordinate corresponding to <code>x</code>.
     */
    public static double interpolate(double x0, double x1, double[] y,
            double x) {

        if (x <= x0) {
            return y[0];
        }
        if (x >= x1) {
            return y[y.length - 1];
        }

        double t = (y.length - 1) * ((x - x0) / (x1 - x0));
        int i = (int) Math.floor(t);

        return interpolate(y[i], y[i + 1], t - i);

    }
    /**
     * Interpolates a piecewise linear curve.
     * @param x0 The minimum value in the domain.
     * @param x1 The maximum value in the domain (must not be less than
     *     <code>x0</code>).
     * @param y An array of the y-coordinates (must have at least two elements
     *     if <code>wrap == false</code>).
     * @param x The x-coordinate at which to interpolate.
     * @param wrap A value indicating whether the curve is periodic.
     * @return The y-coordinate corresponding to <code>x</code>.
     */
    public static double interpolate(double x0, double x1, List<Double> y,
            double x, boolean wrap) {
        if (wrap) {
            return interpolateWrapped(x0, x1, y, x);
        } else {
            return interpolate(x0, x1, y, x);
        }
    }
    /**
     * Interpolates a piecewise linear curve.
     * @param x0 The minimum value in the domain.
     * @param x1 The maximum value in the domain (must not be less than
     *     <code>x0</code>).
     * @param y An array of the y-coordinates (must have at least two elements).
     * @param x The x-coordinate at which to interpolate.
     * @return The y-coordinate corresponding to <code>x</code>.
     */
    public static double interpolate(double x0, double x1, List<Double> y,
            double x) {

        if (x <= x0) {
            return y.get(0);
        }
        if (x > x1) {
            return y.get(y.size() - 1);
        }

        double t = (y.size() - 1) * ((x - x0) / (x1 - x0));
        int i = (int) Math.floor(t);

        return interpolate(y.get(i), y.get(i + 1), t - i);

    }
    /**
     * Performs a bilinear interpolation between four values.
     * @param _00 The value at <code>(t, u) = (0, 0)</code>.
     * @param _10 The value at <code>(t, u) = (1, 0)</code>.
     * @param _01 The value at <code>(t, u) = (0, 1)</code>.
     * @param _11 The value at <code>(t, u) = (1, 1)</code>.
     * @param t The first value at which to interpolate.
     * @param u The second value at which to interpolate.
     * @return The interpolated value at <code>(t, u)</code>.
     */
    public static double bilinearInterpolate(double _00, double _10,
            double _01, double _11, double t, double u) {

        return interpolate(interpolate(_00, _10, t),
                interpolate(_01, _11, t), u);

    }
    /**
     * Performs bilinear interpolation over a non-uniform grid.
     * @param xs The array of grid points along the x-axis (must have the same
     *     length as the number of rows in <code>z</code>).
     * @param ys The array of grid points along the y-axis (must have the same
     *     length as the number of columns in <code>z</code>).
     * @param z The <code>Matrix</code> of values to interpolate (must have
     *     <code>xs.length</code> rows and <code>ys.length</code> columns).
     * @param x The x-coordinate at which to interpolate.
     * @param y The y-coordinate at which to interpolate.
     * @return The interpolated value at <code>(x, y)</code>.
     * @throws IllegalArgumentException if <code>z</code> does not have
     *     dimensions of <code>xs.length</code> by <code>ys.length</code>.
     */
    public static double bilinearInterpolate(double xs[], double ys[],
            Matrix z, double x, double y) {
        return bilinearInterpolate(xs, ys, z, x, y, false, false);
    }
    /**
     * Performs bilinear interpolation over a non-uniform grid.
     * @param xs The array of grid points along the x-axis (must have the same
     *     length as the number of rows in <code>z</code>).
     * @param ys The array of grid points along the y-axis (must have the same
     *     length as the number of columns in <code>z</code>).
     * @param z The <code>Matrix</code> of values to interpolate (must have
     *     <code>xs.length - (wrapX ? 1 : 0)</code> rows and
     *     <code>ys.length - (wrapY ? 1 : 0)</code> columns).
     * @param x The x-coordinate at which to interpolate.
     * @param y The y-coordinate at which to interpolate.
     * @param wrapX A value indicating whether to wrap in the x-direction.  If
     *     <code>true</code>, then <code>xs</code> should have an extra entry
     *     at the end which maps to row <code>0</code>.
     * @param wrapY A value indicating whether to wrap in the y-direction.  If
     *     <code>true</code>, then <code>ys</code> should have an extra entry
     *     at the end which maps to column <code>0</code>.
     * @return The interpolated value at <code>(x, y)</code>.
     * @throws IllegalArgumentException if <code>z</code> does not have
     *     the correct dimensions.
     */
    public static double bilinearInterpolate(double xs[], double ys[],
            Matrix z, double x, double y, boolean wrapX, boolean wrapY) {
        return bilinearInterpolate(Matrix.rowMajor(1, xs.length, xs)
                .elementsByRow(), Matrix.rowMajor(1, ys.length, ys)
                .elementsByRow(), z, x, y, wrapX, wrapY);
    }
    /**
     * Performs bilinear interpolation over a non-uniform grid.
     * @param xs The list of grid points along the x-axis (must have the same
     *     length as the number of rows in <code>z</code>).
     * @param ys The list of grid points along the y-axis (must have the same
     *     length as the number of columns in <code>z</code>).
     * @param z The <code>Matrix</code> of values to interpolate (must have
     *     <code>xs.size()</code> rows and <code>ys.size()</code> columns).
     * @param x The x-coordinate at which to interpolate.
     * @param y The y-coordinate at which to interpolate.
     * @return The interpolated value at <code>(x, y)</code>.
     * @throws IllegalArgumentException if <code>z</code> does not have
     *     dimensions of <code>xs.size()</code> by <code>ys.size()</code>.
     */
    public static double bilinearInterpolate(List<Double> xs,
            List<Double> ys, Matrix z, double x, double y) {
        return bilinearInterpolate(xs, ys, z, x, y, false, false);
    }
    /**
     * Performs bilinear interpolation over a non-uniform grid.
     * @param xs The list of grid points along the x-axis (must have the same
     *     length as the number of rows in <code>z</code>).
     * @param ys The list of grid points along the y-axis (must have the same
     *     length as the number of columns in <code>z</code>).
     * @param z The <code>Matrix</code> of values to interpolate (must have
     *     <code>xs.length - (wrapX ? 1 : 0)</code> rows and
     *     <code>ys.length - (wrapY ? 1 : 0)</code> columns).
     * @param x The x-coordinate at which to interpolate.
     * @param y The y-coordinate at which to interpolate.
     * @param wrapX A value indicating whether to wrap in the x-direction.  If
     *     <code>true</code>, then <code>xs</code> should have an extra entry
     *     at the end which maps to row <code>0</code>.
     * @param wrapY A value indicating whether to wrap in the y-direction.  If
     *     <code>true</code>, then <code>ys</code> should have an extra entry
     *     at the end which maps to column <code>0</code>.
     * @return The interpolated value at <code>(x, y)</code>.
     * @throws IllegalArgumentException if <code>z</code> does not have
     *     the correct dimensions.
     */
    public static double bilinearInterpolate(List<Double> xs,
            List<Double> ys, Matrix z, double x, double y, boolean wrapX,
            boolean wrapY) {

        int nx = xs.size();
        int ny = ys.size();
        if (nx != z.rows() + (wrapX ? 1 : 0)
                || ny != z.columns() + (wrapY ? 1 : 0)) {
            throw new IllegalArgumentException(
                    "Matrix z must be xs.size() by ys.size()");
        }

        if (x <= xs.get(0)) {
            return interpolate(ys, z.row(0).elements(), y, wrapY);
        }
        if (x >= xs.get(nx - 1)) {
            return interpolate(ys, z.row(nx - 1).elements(), y, wrapY);
        }
        if (y <= ys.get(0)) {
            return interpolate(xs, z.column(0).elements(), x, wrapX);
        }
        if (y >= ys.get(ny - 1)) {
            return interpolate(xs, z.column(ny - 1).elements(), x, wrapX);
        }

        int ix = Collections.binarySearch(xs, x);
        if (ix < 0) {
            ix = -(ix + 1);
        }
        while (ix < nx - 1 && !(x < xs.get(ix + 1))) {
            ix++;
        }

        int iy = Collections.binarySearch(ys, y);
        if (iy < 0) {
            iy = -(iy + 1);
        }
        while (iy < ny - 1 && !(y < ys.get(iy + 1))) {
            iy++;
        }

        assert (ix < nx - 1 && iy < ny - 1);

        double x0 = xs.get(ix);
        double x1 = xs.get(ix + 1);
        double y0 = ys.get(iy);
        double y1 = ys.get(iy + 1);

        double tx = (x - x0) / (x1 - x0);
        double ty = (y - y0) / (y1 - y0);

        int jx = ix + 1;
        int jy = iy + 1;
        if (jx == z.rows()) {
            jx = 0;
        }
        if (jy == z.columns()) {
            jy = 0;
        }

        double _00 = z.at(ix, iy);
        double _01 = z.at(ix, jy);
        double _10 = z.at(jx, iy);
        double _11 = z.at(jx, jy);

        return bilinearInterpolate(_00, _10, _01, _11, tx, ty);

    }
    /**
     * Performs bilinear interpolation over a uniform grid.
     * @param x0 The minimum value along the x-axis.
     * @param x1 The maximum value along the x-axis.
     * @param y0 The minimum value along the y-axis.
     * @param y1 The maximum value along the y-axis.
     * @param z The <code>Matrix</code> of values to interpolate (must have at
     *     least two rows and at least two columns).
     * @param x The x-coordinate at which to interpolate.
     * @param y The y-coordinate at which to interpolate.
     * @return The interpolated value at <code>(x, y)</code>.
     * @throws IllegalArgumentException if <code>z</code> has fewer than two
     *     rows or fewer than two columns.
     */
    public static double bilinearInterpolate(double x0, double x1,
            double y0, double y1, Matrix z, double x, double y) {
        return bilinearInterpolate(x0, x1, y0, y1, z, x, y, false, false);
    }
    /**
     * Performs bilinear interpolation over a uniform grid.
     * @param x0 The minimum value along the x-axis.
     * @param x1 The maximum value along the x-axis.
     * @param y0 The minimum value along the y-axis.
     * @param y1 The maximum value along the y-axis.
     * @param z The <code>Matrix</code> of values to interpolate.  It must have
     *     at least two rows (unless wrapping in X) and at least two columns
     *     (unless wrapping in Y).
     * @param x The x-coordinate at which to interpolate.
     * @param y The y-coordinate at which to interpolate.
     * @param wrapX A value indicating whether to wrap in the x-direction.
     * @param wrapY A value indicating whether to wrap in the y-direction.
     * @return The interpolated value at <code>(x, y)</code>.
     * @throws IllegalArgumentException if <code>z</code> has fewer than two
     *     rows (and does not wrap in X) or fewer than two columns (and does
     *     not wrap in Y).
     */
    public static double bilinearInterpolate(double x0, double x1,
            double y0, double y1, Matrix z, double x, double y,
            boolean wrapX, boolean wrapY) {

        if ((!wrapX && z.rows() < 2) || (!wrapY && z.columns() < 2)) {
            throw new IllegalArgumentException(
                    "Matrix z must have length 2 in each non-wrapping dimension");
        }

        if (x <= x0) {
            return interpolate(y0, y1, z.row(0).elements(), y, wrapY);
        }
        if (x >= x1) {
            int j = z.rows() - 1;
            return interpolate(y0, y1, z.row(j).elements(), y, wrapY);
        }
        if (y <= y0) {
            return interpolate(x0, x1, z.column(0).elements(), x, wrapX);
        }
        if (y >= y1) {
            int j = z.columns() - 1;
            return interpolate(x0, x1, z.column(j).elements(), x, wrapX);
        }

        int nx = z.rows();
        int ny = z.columns();
        double tx = (nx - (wrapX ? 0 : 1)) * ((x - x0) / (x1 - x0));
        double ty = (ny - (wrapY ? 0 : 1)) * ((y - y0) / (y1 - y0));
        int ix = (int) Math.floor(tx);
        int iy = (int) Math.floor(ty);
        int jx = ix + 1;
        int jy = iy + 1;
        jx = jx < nx ? jx : 0;
        jy = jy < ny ? jy : 0;

        double _00 = z.at(ix, iy);
        double _01 = z.at(ix, jy);
        double _10 = z.at(jx, iy);
        double _11 = z.at(jx, jy);

        return bilinearInterpolate(_00, _10, _01, _11, tx - ix, ty - iy);

    }
    /**
     * Interpolates a periodic piecewise linear curve.
     * @param xs An array of x-coordinates (this must be sorted in ascending
     *     order).
     * @param ys An array of the y-coordinates (must have length
     *     <code>xs.length - 1</code>).
     * @param x The x-coordinate at which to interpolate.
     * @return The y-coordinate corresponding to <code>x</code>.
     */
    public static double interpolateWrapped(double[] xs, double[] ys,
            double x) {

        int n = xs.length;
        double x0 = xs[0];
        double x1 = xs[n - 1];

        x -= (x1 - x0) * Math.floor((x - x0) / (x1 - x0));
        assert (inRangeCO(x, x0, x1));

        int index = Arrays.binarySearch(xs, x);
        if (index < 0) {
            index = -(index + 1);
        }
        while (index < n - 1 && !(x < xs[index + 1])) {
            index++;
        }

        assert (index < n - 1);

        int i = index - 1;
        int j = index;
        if (j == n - 1) {
            j = 0;
        }

        return interpolate(xs[i], ys[i], xs[index], ys[j], x);

    }
    /**
     * Interpolates a piecewise linear curve.
     * @param xs An array of x-coordinates (this must be sorted in ascending
     *     order).
     * @param ys An array of the y-coordinates (must be of the same length as
     *     <code>xs</code>, or one less if <code>wrap == true</code>).
     * @param x The x-coordinate at which to interpolate.
     * @return The y-coordinate corresponding to <code>x</code>.
     */
    public static double interpolateWrapped(List<Double> xs,
            List<Double> ys, double x) {

        int n = xs.size();
        double x0 = xs.get(0);
        double x1 = xs.get(n - 1);

        x -= (x1 - x0) * Math.floor((x - x0) / (x1 - x0));
        assert (inRangeCO(x, x0, x1));

        int index = Collections.binarySearch(xs, x);
        if (index < 0) {
            index = -(index + 1);
        }
        while (index < n - 1 && !(x < xs.get(index + 1))) {
            index++;
        }

        assert (index < n - 1);

        int i = index - 1;
        int j = index;
        if (j == n - 1) {
            j = 0;
        }

        return interpolate(xs.get(i), ys.get(i), xs.get(index), ys.get(j),
                x);

    }
    /**
     * Interpolates a periodic piecewise linear curve.
     * @param x0 The minimum value in the domain.
     * @param x1 The maximum value in the domain (must not be less than
     *     <code>x0</code>).
     * @param y An array of the y-coordinates.
     * @param x The x-coordinate at which to interpolate.
     * @param wrap A value indicating whether the curve is periodic.
     * @return The y-coordinate corresponding to <code>x</code>.
     */
    public static double interpolateWrapped(double x0, double x1,
            double[] y, double x) {

        double t = (x - x0) / (x1 - x0);
        t -= Math.floor(t);
        t *= y.length;

        int i = (int) Math.floor(t);
        int j = i + 1;
        if (j == y.length) {
            j = 0;
        }

        assert (0 <= i && i < y.length);

        return interpolate(y[i], y[j], t - i);

    }
    /**
     * Interpolates a periodic piecewise linear curve.
     * @param x0 The minimum value in the domain.
     * @param x1 The maximum value in the domain (must not be less than
     *     <code>x0</code>).
     * @param y An array of the y-coordinates.
     * @param x The x-coordinate at which to interpolate.
     * @return The y-coordinate corresponding to <code>x</code>.
     */
    public static double interpolateWrapped(double x0, double x1,
            List<Double> y, double x) {

        double t = (x - x0) / (x1 - x0);
        t -= Math.floor(t);
        t *= y.size();

        int i = (int) Math.floor(t);
        int j = i + 1;
        if (j == y.size()) {
            j = 0;
        }

        assert (0 <= i && i < y.size());

        return interpolate(y.get(i), y.get(j), t - i);

    }
    /**
     * Determines whether {@code x} falls within the interval
     * {@code [minimum, maximum)}.
     * @param x The value to check.
     * @param minimum The lower bound of the interval to check against.
     * @param maximum The upper bound of the interval to check against.
     * @return A value indicating whether {@code x} is contained in the
     *     interval {@code [minimum, maximum)}.
     */
    public static boolean inRangeCO(double x, double minimum, double maximum) {
        return minimum <= x && x < maximum;
    }
}
