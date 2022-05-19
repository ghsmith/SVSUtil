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
package svsutil.jmist;

import java.io.Serializable;
import java.util.List;

/**
 * A two dimensional matrix.
 * @author Brad Kimmel
 */
public final class Matrix implements Serializable {

  /** Serialization version ID. */
  private static final long serialVersionUID = 4279455941534491589L;

  /** The array to use for the elements of zero matrices. */
  private static final double[] ZERO = new double[]{ 0.0 };

  /** The array to use for elements of matrices consisting of all ones. */
  private static final double[] ONE = new double[]{ 1.0 };

  /** The <code>MatrixBuffer</code> storing the entries for this matrix. */
  private final MatrixBuffer buffer;

  /**
   * Creates a new <code>Matrix</code>.
   * @param buffer The <code>MatrixBuffer</code> to store the matrix entries.
   */
  public Matrix(MatrixBuffer buffer) {
    this.buffer = buffer;
  }

  /**
   * Gets the number of rows in this <code>Matrix</code>.
   * @return The number of rows in this <code>Matrix</code>.
   */
  public int rows() {
    return buffer.rows();
  }

  /**
   * Gets the number of columns in this <code>Matrix</code>.
   * @return The number of columns in this <code>Matrix</code>.
   */
  public int columns() {
    return buffer.columns();
  }

  /**
   * Gets the number of elements in this <code>Matrix</code>.
   * @return The number of elements in this <code>Matrix</code>.
   */
  public int size() {
    return buffer.size();
  }

  /**
   * Gets a read-only list of the elements of this <code>Matrix</code>.
   * @return A read-only list of the elements of this <code>Matrix</code>.
   */
  public List<Double> elements() {
    return buffer.elements();
  }

  /**
   * Gets a read-only list of the elements of this <code>Matrix</code> in
   * row-major order.
   * @return A read-only list of the elements of this <code>Matrix</code> in
   *     row-major order.
   */
  public List<Double> elementsByRow() {
    return buffer.elementsByRow();
  }

  /**
   * Gets a read-only list of the elements of this <code>Matrix</code> in
   * column-major order.
   * @return A read-only list of the elements of this <code>Matrix</code> in
   *     column-major order.
   */
  public List<Double> elementsByColumn() {
    return buffer.elementsByColumn();
  }

  /**
   * Gets the smallest value in this <code>Matrix</code>.
   * @return The smallest value in this <code>Matrix</code>.
   */
  public double minimum() {
    return buffer.minimum();
  }

  /**
   * Gets the largest value in this <code>Matrix</code>.
   * @return The largest value in this <code>Matrix</code>.
   */
  public double maximum() {
    return buffer.maximum();
  }

  /**
   * Gets the range of values in this <code>Matrix</code>.
   * @return The ranges of values in this <code>Matrix</code>.
   */
  public Interval range() {
    return buffer.range();
  }

  /**
   * Gets the sum of all values in this <code>Matrix</code>.
   * @return The largest value in this <code>Matrix</code>.
   */
  public double sum() {
    return buffer.sum();
  }

  /**
   * Gets the element at the specified position in this <code>Matrix</code>.
   * @param row The row of the element to obtain (must be non-negative and
   *     less than <code>this.rows()</code>).
   * @param col The column of the element to obtain (must be non-negative and
   *     less than <code>this.columns()</code>).
   * @return The element at the specified row and column in this
   *     <code>Matrix</code>.
   * @throws IllegalArgumentException if <code>row</code> or <code>col</code>
   *     is out of bounds.
   * @see #rows()
   * @see #columns()
   */
  public double at(int row, int col) {
    return buffer.at(row, col);
  }

  /**
   * Gets the transpose of this <code>Matrix</code> (i.e., the
   * <code>Matrix</code> <code>T</code> such that
   * <code>this.at(i, j) == T.at(j, i)</code> for all
   * <code>i, j</code> with <code>0 &lt;= i &lt; this.rows()</code> and
   * <code>0 &lt;= j &lt; this.columns()</code>.
   * @return The transpose of this <code>Matrix</code>.
   * @see #at(int, int)
   * @see #rows()
   * @see #columns()
   */
  public Matrix transpose() {
    return new Matrix(buffer.transpose());
  }

  /**
   * Gets a view of a sub-matrix of this <code>Matrix</code> (i.e.,
   * @param row The index of the first row of the sub-matrix (must be
   *     non-negative).
   * @param col The index of the first column of the sub-matrix (must be
   *     non-negative).
   * @param rows The number of rows of the sub-matrix (must be non-negative
   *     and satisfy <code>row + rows &lt;= this.rows()</code>).
   * @param cols The number of columns of the sub-matrix (must be
   *     non-negative and satisfy
   *     <code>col + cols &lt;= this.columns()</code>).
   * @return The <code>Matrix</code>, <code>T</code>, with <code>rows</code>
   *     rows and <code>cols</code> columns satisfying
   *     <code>T.at(i, j) == this.at(row + i, col + j)</code> for all
   *     <code>i, j</code> with <code>0 &lt;= i &lt; rows</code> and
   *     <code>0 &lt;= j &lt; cols</code>.
   * @throws IllegalArgumentException if <code>row</code> is negative,
   *     <code>col</code> is negative, <code>rows</code> is negative,
   *     <code>cols</code> is negative,
   *     <code>row + rows &gt; this.rows()</code>, or
   *     <code>col + cols &gt; this.columns()</code>.
   * @see #at(int, int)
   * @see #rows()
   * @see #columns()
   */
  public Matrix slice(int row, int col, int rows, int cols) {
    return new Matrix(buffer.slice(row, col, rows, cols));
  }

  /**
   * Gets a column vector of the diagonal elements of this
   * <code>Matrix</code> (i.e., the elements <code>this.at(i, i)</code> for
   * <code>0 &lt;= i &lt; Math.min(this.rows(), this.columns())</code>).
   * @return The column vector of the diagonal elements of this
   *     <code>Matrix</code>.
   * @see #at(int, int)
   * @see Math#min(int, int)
   */
  public Matrix diagonal() {
    return new Matrix(buffer.diagonal());
  }

  /**
   * Gets the specified row of this <code>Matrix</code>.
   * @param row The row to get (must be non-negative and less than
   *     <code>this.rows()</code>).
   * @return The specified row of this <code>Matrix</code>.
   * @throws IllegalArgumentException if <code>row</code> is negative or if
   *     <code>row &gt;= this.rows()</code>.
   * @see #rows()
   */
  public Matrix row(int row) {
    return new Matrix(buffer.row(row));
  }

  /**
   * Gets the specified column of this <code>Matrix</code>.
   * @param col The column to get (must be non-negative and less than
   *     <code>this.columns()</code>).
   * @return The specified column of this <code>Matrix</code>.
   * @throws IllegalArgumentException if <code>col</code> is negative or if
   *     <code>col &gt;= this.columns()</code>.
   * @see #columns()
   */
  public Matrix column(int col) {
    return new Matrix(buffer.column(col));
  }

  /**
   * Gets a <code>Matrix</code> consisting of all zeros.
   * @param rows The number of rows in the <code>Matrix</code> (must be
   *     non-negative).
   * @param cols The number of columns in the <code>Matrix</code> (must be
   *     non-negative).
   * @return The zero <code>Matrix</code> with the specified dimensions.
   * @throws IllegalArgumentException if <code>rows</code> or
   *     <code>cols</code> is negative.
   */
  public static Matrix zeros(int rows, int cols) {
    return new Matrix(new MatrixBuffer(ZERO, rows, cols, 0, 0, 0));
  }

  /**
   * Gets a <code>Matrix</code> consisting of all ones.
   * @param rows The number of rows in the <code>Matrix</code> (must be
   *     non-negative).
   * @param cols The number of columns in the <code>Matrix</code> (must be
   *     non-negative).
   * @return The <code>Matrix</code> with the specified dimensions consisting
   *     of all ones.
   * @throws IllegalArgumentException if <code>rows</code> or
   *     <code>cols</code> is negative.
   */
  public static Matrix ones(int rows, int cols) {
    return new Matrix(new MatrixBuffer(ONE, rows, cols, 0, 0, 0));
  }

  /**
   * Gets a <code>Matrix</code> with each element having the same value.
   * @param rows The number of rows in the <code>Matrix</code> (must be
   *     non-negative).
   * @param cols The number of columns in the <code>Matrix</code> (must be
   *     non-negative).
   * @param value The value to use for all of the elements of the matrix.
   * @return The <code>Matrix</code> with all elements having the same value.
   * @throws IllegalArgumentException if <code>rows</code> or
   *     <code>cols</code> is negative.
   */
  public static Matrix constant(int rows, int cols, double value) {
    return new Matrix(new MatrixBuffer(new double[]{ value }, rows, cols, 0, 0, 0));
  }

  /**
   * Gets the identity <code>Matrix</code> of the specified size.
   * @param n The size of the <code>Matrix</code> (must be non-negative).
   * @return The <code>n</code> by <code>n</code> identity
   *     <code>Matrix</code>.
   * @throws IllegalArgumentException if <code>n</code> is negative.
   */
  public static Matrix identity(int n) {
    double[] elements = new double[n * 2 - 1];
    elements[n - 1] = 1.0;
    return new Matrix(new MatrixBuffer(elements, n, n, n - 1, -1, 1));
  }

  /**
   * Creates a <code>Matrix</code> with the specified values in column-major
   * order.
   * @param rows The number of rows in the <code>Matrix</code> (must be
   *     non-negative).
   * @param cols The number of columns in the <code>Matrix</code> (must be
   *     non-negative).
   * @param elements The elements of the <code>Matrix</code> in column-major
   *     order (must have at least <code>rows * cols</code> elements --
   *     additional elements will be ignored).
   * @return The <code>rows</code> by <code>cols</code> <code>Matrix</code>
   *     consisting of the elements specified.
   * @throws IllegalArgumentException if
   *     <code>elements.length &lt; rows * cols</code>.
   * @throws IllegalArgumentException if <code>rows</code> or
   *     <code>cols</code> is negative.
   */
  public static Matrix columnMajor(int rows, int cols, double[] elements) {
    return new Matrix(new MatrixBuffer(elements, rows, cols, 0, 1, rows));
  }

  /**
   * Creates a <code>Matrix</code> with the specified values in row-major
   * order.
   * @param rows The number of rows in the <code>Matrix</code> (must be
   *     non-negative).
   * @param cols The number of columns in the <code>Matrix</code> (must be
   *     non-negative).
   * @param elements The elements of the <code>Matrix</code> in row-major
   *     order (must have at least <code>rows * cols</code> elements --
   *     additional elements will be ignored).
   * @return The <code>rows</code> by <code>cols</code> <code>Matrix</code>
   *     consisting of the elements specified.
   * @throws IllegalArgumentException if
   *     <code>elements.length &lt; rows * cols</code>.
   * @throws IllegalArgumentException if <code>rows</code> or
   *     <code>cols</code> is negative.
   */
  public static Matrix rowMajor(int rows, int cols, double[] elements) {
    return new Matrix(new MatrixBuffer(elements, rows, cols, 0, cols, 1));
  }

  /**
   * Computes the product of two matrices.  It is expected that neither
   * <code>A</code> nor <code>B</code> are not backed by the same array as
   * <code>R</code>, and that the elements of <code>R</code> are backed by
   * distinct array elements.  Results are undefined if these conditions are
   * not met.
   * @param A The left <code>Matrix</code> operand
   * @param B The right <code>Matrix</code> operand
   * @param R The <code>MatrixBuffer</code> to store the result.
   * @throws IllegalArgumentException if <code>A.columns() != B.rows()</code>.
   * @throws IllegalArgumentException if <code>A.rows() != R.rows()</code> or if
   *     <code>R.columns() != B.columns()</code>.
   * @see #rows()
   * @see #columns()
   */
  public static void multiply(Matrix A, Matrix B, MatrixBuffer R) {
    if (A.columns() != B.rows()) {
      throw new IllegalArgumentException("Cannot multiply matrices: wrong dimensions.");
    }

    int rows = A.rows();
    int cols = B.columns();
    int k = A.columns();

    if (rows != R.rows() || cols != R.columns()) {
      throw new IllegalArgumentException("Target MatrixBuffer has wrong dimensions.");
    }

    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        R.set(r, c, 0.0);
        for (int j = 0; j < k; j++) {
          R.add(r, c, A.at(r, j) * B.at(j, c));
        }
      }
    }
  }

  /**
   * Computes the product of two matrices element-wise.  It is expected that
   * the elements of <code>R</code> are backed by distinct array elements
   * whenever the corresponding elements of <code>A</code> or <code>B</code>
   * are backed by distinct array elements.  Results are undefined if these
   * conditions are not met.  Note that <code>R</code> may share its backing
   * array with either <code>A</code>, <code>B</code>, or both, as long as
   * the indexing is the same (e.g., both are in row-major order, both are
   * in column-major order, etc.).
   * @param A The left <code>Matrix</code> operand
   * @param B The right <code>Matrix</code> operand
   * @param R The <code>MatrixBuffer</code> to store the result.
   * @throws IllegalArgumentException if <code>A</code>, <code>B</code>, and
   *     <code>R</code> do not all have the same dimensions.
   * @see #rows()
   * @see #columns()
   */
  public static void multiplyElements(Matrix A, Matrix B, MatrixBuffer R) {
    int rows = A.rows();
    int cols = A.columns();

    if (rows != B.rows() || cols != B.columns()) {
      throw new IllegalArgumentException("Cannot multiply element-wise matrices of different dimensions.");
    }
    if (rows != R.rows() || cols != R.columns()) {
      throw new IllegalArgumentException("Target MatrixBuffer has wrong dimensions.");
    }

    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        R.set(r, c, A.at(r, c) * B.at(r, c));
      }
    }
  }

  /**
   * Computes the quotient of two matrices element-wise.  It is expected that
   * the elements of <code>R</code> are backed by distinct array elements
   * whenever the corresponding elements of <code>A</code> or <code>B</code>
   * are backed by distinct array elements.  Results are undefined if these
   * conditions are not met.  Note that <code>R</code> may share its backing
   * array with either <code>A</code>, <code>B</code>, or both, as long as
   * the indexing is the same (e.g., both are in row-major order, both are
   * in column-major order, etc.).
   * @param A The left <code>Matrix</code> operand
   * @param B The right <code>Matrix</code> operand
   * @param R The <code>MatrixBuffer</code> to store the result.
   * @throws IllegalArgumentException if <code>A</code>, <code>B</code>, and
   *     <code>R</code> do not all have the same dimensions.
   * @see #rows()
   * @see #columns()
   */
  public static void divideElements(Matrix A, Matrix B, MatrixBuffer R) {
    int rows = A.rows();
    int cols = A.columns();

    if (rows != B.rows() || cols != B.columns()) {
      throw new IllegalArgumentException("Cannot divide element-wise matrices of different dimensions.");
    }
    if (rows != R.rows() || cols != R.columns()) {
      throw new IllegalArgumentException("Target MatrixBuffer has wrong dimensions.");
    }

    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        R.set(r, c, A.at(r, c) / B.at(r, c));
      }
    }
  }

  /**
   * Computes the sum of two matrices element-wise.  It is expected that
   * the elements of <code>R</code> are backed by distinct array elements
   * whenever the corresponding elements of <code>A</code> or <code>B</code>
   * are backed by distinct array elements.  Results are undefined if these
   * conditions are not met.  Note that <code>R</code> may share its backing
   * array with either <code>A</code>, <code>B</code>, or both, as long as
   * the indexing is the same (e.g., both are in row-major order, both are
   * in column-major order, etc.).
   * @param A The left <code>Matrix</code> operand
   * @param B The right <code>Matrix</code> operand
   * @param R The <code>MatrixBuffer</code> to store the result.
   * @throws IllegalArgumentException if <code>A</code>, <code>B</code>, and
   *     <code>R</code> do not all have the same dimensions.
   * @see #rows()
   * @see #columns()
   */
  public static void add(Matrix A, Matrix B, MatrixBuffer R) {
    int rows = A.rows();
    int cols = A.columns();

    if (rows != B.rows() || cols != B.columns()) {
      throw new IllegalArgumentException("Cannot add matrices of different dimensions.");
    }
    if (rows != R.rows() || cols != R.columns()) {
      throw new IllegalArgumentException("Target MatrixBuffer has wrong dimensions.");
    }

    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        R.set(r, c, A.at(r, c) + B.at(r, c));
      }
    }
  }

  /**
   * Computes the difference of two matrices element-wise.  It is expected that
   * the elements of <code>R</code> are backed by distinct array elements
   * whenever the corresponding elements of <code>A</code> or <code>B</code>
   * are backed by distinct array elements.  Results are undefined if these
   * conditions are not met.  Note that <code>R</code> may share its backing
   * array with either <code>A</code>, <code>B</code>, or both, as long as
   * the indexing is the same (e.g., both are in row-major order, both are
   * in column-major order, etc.).
   * @param A The left <code>Matrix</code> operand
   * @param B The right <code>Matrix</code> operand
   * @param R The <code>MatrixBuffer</code> to store the result.
   * @throws IllegalArgumentException if <code>A</code>, <code>B</code>, and
   *     <code>R</code> do not all have the same dimensions.
   * @see #rows()
   * @see #columns()
   */
  public static void subtract(Matrix A, Matrix B, MatrixBuffer R) {
    int rows = A.rows();
    int cols = A.columns();

    if (rows != B.rows() || cols != B.columns()) {
      throw new IllegalArgumentException("Cannot subtract matrices of different dimensions.");
    }
    if (rows != R.rows() || cols != R.columns()) {
      throw new IllegalArgumentException("Target MatrixBuffer has wrong dimensions.");
    }

    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        R.set(r, c, A.at(r, c) - B.at(r, c));
      }
    }
  }

  /**
   * Computes the product of two matrices.
   * @param other The <code>Matrix</code> by which to multiply this
   *     <code>Matrix</code> (must have as many rows as this
   *     <code>Matrix</code> has columns).
   * @return The product of this <code>Matrix</code> with <code>other</code>.
   * @throws IllegalArgumentException if
   *     <code>this.columns() != other.rows()</code>.
   * @see #rows()
   * @see #columns()
   */
  public Matrix times(Matrix other) {
    if (this.columns() != other.rows()) {
      throw new IllegalArgumentException("Cannot multiply matrices: wrong dimensions.");
    }

    int rows = this.rows();
    int cols = other.columns();
    int k = this.columns();

    double[] elements = new double[rows * cols];
    for (int r = 0, i = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++, i++) {
        for (int j = 0; j < k; j++) {
          elements[i] += this.at(r, j) * other.at(j, c);
        }
      }
    }

    return Matrix.rowMajor(rows, cols, elements);
  }

  /**
   * Computes the sum of two matrices.
   * @param other The <code>Matrix</code> to which to add this
   *     <code>Matrix</code> (must have the same dimensions as this
   *     <code>Matrix</code>).
   * @return The sum of this <code>Matrix</code> and <code>other</code>.
   * @throws IllegalArgumentException if <code>other</code> has different
   *     dimensions than <code>this</code>.
   */
  public Matrix plus(Matrix other) {
    int rows = this.rows();
    int cols = this.columns();

    if (rows != other.rows() || cols != other.columns()) {
      throw new IllegalArgumentException("Cannot add matrices of different dimensions.");
    }

    double[] elements = new double[rows * cols];
    for (int r = 0, i = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++, i++) {
        elements[i] = this.at(r, c) + other.at(r, c);
      }
    }

    return Matrix.rowMajor(rows, cols, elements);
  }

  /**
   * Computes the difference of two matrices.
   * @param other The <code>Matrix</code> subtract from this
   *     <code>Matrix</code> (must have the same dimensions as this
   *     <code>Matrix</code>).
   * @return The difference between this <code>Matrix</code> and
   *     <code>other</code>.
   * @throws IllegalArgumentException if <code>other</code> has different
   *     dimensions than <code>this</code>.
   */
  public Matrix minus(Matrix other) {
    int rows = this.rows();
    int cols = this.columns();

    if (rows != other.rows() || cols != other.columns()) {
      throw new IllegalArgumentException("Cannot subtract matrices of different dimensions.");
    }

    double[] elements = new double[rows * cols];
    for (int r = 0, i = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++, i++) {
        elements[i] = this.at(r, c) - other.at(r, c);
      }
    }

    return Matrix.rowMajor(rows, cols, elements);
  }

}