/**
 *
 */
package svsutil.jmist;

import java.io.Serializable;
import java.nio.DoubleBuffer;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;

/**
 * Mutable element-storage for a two-dimensional matrix.
 */
public final class MatrixBuffer implements Serializable {

  /** Serialization version ID. */
  private static final long serialVersionUID = -7273426889569165478L;

  /** The buffer containing the elements of this <code>MatrixBuffer</code>. */
  private final DoubleBuffer elements;

  /** The number of rows. */
  private final int rows;

  /** The number of columns. */
  private final int cols;

  /** The index into {@link #elements} of the first element. */
  private final int offset;

  /**
   * The difference between the indices into {@link #elements} of the first
   * element of the first row and the first element of the second row.
   */
  private final int rowStride;

  /**
   * The difference between the indices into {@link #elements} of the first
   * element of the first column and the first element of the second column.
   */
  private final int colStride;

  /**
   * Creates a new array-backed <code>MatrixBuffer</code>.
   * @param elements The array of <code>double</code>s containing the
   *     elements of the matrix (must be large enough so that the index of
   *     the last element [<code>offset + (rows-1) * rowStride + (cols-1) *
   *     colStride</code>] is in the array).
   * @param rows The number of rows in the matrix (must be non-negative).
   * @param cols The number of columns in the matrix (must be non-negative).
   * @param offset The offset into <code>elements</code> of the first element
   *     of the matrix (must be non-negative).
   * @param rowStride The difference between the indices into
   *     <code>elements</code> of the first element of the first row and the
   *     first element of the second row.
   * @param colStride The difference between the indices into
   *     <code>elements</code> of the first element of the first column and
   *     the first element of the second column.
   * @throws IllegalArgumentException if <code>offset</code> is negative.
   * @throws IllegalArgumentException if <code>rows</code> or
   *     <code>cols</code> is negative.
   * @throws IllegalArgumentException if <code>elements</code> is not large
   *     enough to hold all elements of the array).
   */
  public MatrixBuffer(double[] elements, int rows, int cols, int offset,
                      int rowStride, int colStride) {
    this(DoubleBuffer.wrap(elements), rows, cols, offset, rowStride, colStride);
  }

  /**
   * Creates a new <code>MatrixBuffer</code>.
   * @param elements The <code>DoubleBuffer</code> containing the elements of
   *     the matrix (must be large enough so that the index of the last element
   *     [<code>offset + (rows-1) * rowStride + (cols-1) * colStride</code>] is
   *     in the array).
   * @param rows The number of rows in the matrix (must be non-negative).
   * @param cols The number of columns in the matrix (must be non-negative).
   * @param offset The offset into <code>elements</code> of the first element
   *     of the matrix (must be non-negative).
   * @param rowStride The difference between the indices into
   *     <code>elements</code> of the first element of the first row and the
   *     first element of the second row.
   * @param colStride The difference between the indices into
   *     <code>elements</code> of the first element of the first column and
   *     the first element of the second column.
   * @throws IllegalArgumentException if <code>offset</code> is negative.
   * @throws IllegalArgumentException if <code>rows</code> or
   *     <code>cols</code> is negative.
   * @throws IllegalArgumentException if <code>elements</code> is not large
   *     enough to hold all elements of the array).
   */
  public MatrixBuffer(DoubleBuffer elements, int rows, int cols, int offset,
                      int rowStride, int colStride) {
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be non-negative");
    }
    if (rows < 0 || cols < 0) {
      throw new IllegalArgumentException("rows and cols must be non-negative");
    }
    if ((offset + (cols - 1) * colStride + (rows - 1) * rowStride)
        >= elements.limit()) {
      throw new IllegalArgumentException("not enough elements");
    }

    this.elements = elements;
    this.rows = rows;
    this.cols = cols;
    this.offset = offset;
    this.rowStride = rowStride;
    this.colStride = colStride;
  }

  /**
   * Gets the number of rows in this <code>MatrixBuffer</code>.
   * @return The number of rows in this <code>MatrixBuffer</code>.
   */
  public int rows() {
    return this.rows;
  }

  /**
   * Gets the number of columns in this <code>MatrixBuffer</code>.
   * @return The number of columns in this <code>MatrixBuffer</code>.
   */
  public int columns() {
    return this.cols;
  }

  /**
   * Gets the number of elements in this <code>MatrixBuffer</code>.
   * @return The number of elements in this <code>MatrixBuffer</code>.
   */
  public int size() {
    return this.rows * this.cols;
  }

  private Iterator<Double> iterator() {
    return new Iterator<Double>() {
      int r = 0;
      int c = 0;
      int rpos = offset;
      int pos = rpos;

      @Override
      public boolean hasNext() {
        return r < rows;
      }

      @Override
      public Double next() {
        int j = pos;

        if (++c < cols) {
          pos += colStride;
        } else {
          c = 0;
          r++;
          rpos += rowStride;
          pos = rpos;
        }

        return elements.get(j);
      }
    };
  }

  /**
   * Gets a read-only list of the elements of this <code>MatrixBuffer</code>.
   * @return A read-only list of the elements of this
   *     <code>MatrixBuffer</code>.
   */
  public List<Double> elements() {
    return elementsByRow();
  }

  /**
   * Gets a read-only list of the elements of this <code>MatrixBuffer</code>
   * in row-major order.
   * @return A read-only list of the elements of this
   *     <code>MatrixBuffer</code> in row-major order.
   */
  public List<Double> elementsByRow() {
    return new AbstractList<Double>() {

      @Override
      public Iterator<Double> iterator() {
        return MatrixBuffer.this.iterator();
      }

      @Override
      public Double get(int j) {
        int r = j / cols;
        int c = j % cols;
        return at(r, c);
      }

      @Override
      public int size() {
        return MatrixBuffer.this.size();
      }

    };
  }

  /**
   * Gets a read-only list of the elements of this <code>MatrixBuffer</code>
   * in column-major order.
   * @return A read-only list of the elements of this
   *     <code>MatrixBuffer</code> in column-major order.
   */
  public List<Double> elementsByColumn() {
    return transpose().elementsByRow();
  }

  /**
   * Gets the smallest value in this <code>MatrixBuffer</code>.
   * @return The smallest value in this <code>MatrixBuffer</code>.
   */
  public double minimum() {
    double min = Double.POSITIVE_INFINITY;
    for (int r = 0, rpos = offset; r < rows; r++, rpos += rowStride) {
      for (int c = 0, pos = rpos; c < cols; c++, pos += colStride) {
        if (elements.get(pos) < min) {
          min = elements.get(pos);
        }
      }
    }
    return min;
  }

  /**
   * Gets the largest value in this <code>MatrixBuffer</code>.
   * @return The largest value in this <code>MatrixBuffer</code>.
   */
  public double maximum() {
    double max = Double.NEGATIVE_INFINITY;
    for (int r = 0, rpos = offset; r < rows; r++, rpos += rowStride) {
      for (int c = 0, pos = rpos; c < cols; c++, pos += colStride) {
        if (elements.get(pos) > max) {
          max = elements.get(pos);
        }
      }
    }
    return max;
  }

  /**
   * Gets the range of values in this <code>MatrixBuffer</code>.
   * @return The ranges of values in this <code>MatrixBuffer</code>.
   */
  public Interval range() {
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    for (int r = 0, rpos = offset; r < rows; r++, rpos += rowStride) {
      for (int c = 0, pos = rpos; c < cols; c++, pos += colStride) {
        if (elements.get(pos) > max) {
          max = elements.get(pos);
        }
        if (elements.get(pos) < min) {
          min = elements.get(pos);
        }
      }
    }
    return new Interval(min, max);
  }

  /**
   * Gets the sum of all values in this <code>MatrixBuffer</code>.
   * @return The largest value in this <code>MatrixBuffer</code>.
   */
  public double sum() {
    double sum = 0.0;
    for (int r = 0, rpos = offset; r < rows; r++, rpos += rowStride) {
      for (int c = 0, pos = rpos; c < cols; c++, pos += colStride) {
        sum += elements.get(pos);
      }
    }
    return sum;
  }

  /**
   * Gets the element at the specified position in this <code>MatrixBuffer</code>.
   * @param row The row of the element to obtain (must be non-negative and
   *     less than <code>this.rows()</code>).
   * @param col The column of the element to obtain (must be non-negative and
   *     less than <code>this.columns()</code>).
   * @return The element at the specified row and column in this
   *     <code>MatrixBuffer</code>.
   * @throws IllegalArgumentException if <code>row</code> or <code>col</code>
   *     is out of bounds.
   * @see #rows()
   * @see #columns()
   */
  public double at(int row, int col) {
    return this.elements.get(indexOf(row, col));
  }

  /**
   * Sets the element at the specified position in this
   * <code>MatrixBuffer</code>.
   * @param row The row of the element to obtain (must be non-negative and
   *     less than <code>this.rows()</code>).
   * @param col The column of the element to obtain (must be non-negative and
   *     less than <code>this.columns()</code>).
   * @param value The value to set the specified element to.
   * @return A reference to this <code>MatrixBuffer</code>.
   * @throws IllegalArgumentException if <code>row</code> or <code>col</code>
   *     is out of bounds.
   * @see #rows()
   * @see #columns()
   */
  public MatrixBuffer set(int row, int col, double value) {
    this.elements.put(indexOf(row, col), value);
    return this;
  }

  /**
   * Adds to the element at the specified position in this
   * <code>MatrixBuffer</code>.
   * @param row The row of the element to obtain (must be non-negative and
   *     less than <code>this.rows()</code>).
   * @param col The column of the element to obtain (must be non-negative and
   *     less than <code>this.columns()</code>).
   * @param value The value to add to the specified element.
   * @return A reference to this <code>MatrixBuffer</code>.
   * @throws IllegalArgumentException if <code>row</code> or <code>col</code>
   *     is out of bounds.
   * @see #rows()
   * @see #columns()
   */
  public MatrixBuffer add(int row, int col, double value) {
    int index = indexOf(row, col);
    this.elements.put(index, elements.get(index) + value);
    return this;
  }

  /**
   * Gets the index into {@link #elements} of the specified row and column.
   * @param row The row of the element to get the index of (must be
   *     non-negative and less than <code>this.rows()</code>).
   * @param col The column of the element to get the index of (must be
   *     non-negative and less than <code>this.columns()</code>).
   * @return The index of the element at the specified row and column in this
   *     <code>MatrixBuffer</code>.
   * @throws IllegalArgumentException if <code>row</code> or <code>col</code>
   *     is out of bounds.
   * @see #rows()
   * @see #columns()
   */
  private int indexOf(int row, int col) {
    if (0 <= row && row < rows && 0 <= col && col < cols) {
      return offset + row * rowStride + col * colStride;
    } else {
      throw new IllegalArgumentException("Out of bounds.");
    }
  }

  /**
   * Gets the transpose of this <code>MatrixBuffer</code> (i.e., the
   * <code>MatrixBuffer</code> <code>T</code> such that
   * <code>this.at(i, j) == T.at(j, i)</code> for all
   * <code>i, j</code> with <code>0 &lt;= i &lt; this.rows()</code> and
   * <code>0 &lt;= j &lt; this.columns()</code>.
   * @return The transpose of this <code>MatrixBuffer</code>.
   * @see #at(int, int)
   * @see #rows()
   * @see #columns()
   */
  public MatrixBuffer transpose() {
    return new MatrixBuffer(elements, cols, rows, offset, colStride, rowStride);
  }

  /**
   * Gets a view of a sub-matrix of this <code>MatrixBuffer</code> (i.e.,
   * @param row The index of the first row of the sub-matrix (must be
   *     non-negative).
   * @param col The index of the first column of the sub-matrix (must be
   *     non-negative).
   * @param rows The number of rows of the sub-matrix (must be non-negative
   *     and satisfy <code>row + rows &lt;= this.rows()</code>).
   * @param cols The number of columns of the sub-matrix (must be
   *     non-negative and satisfy
   *     <code>col + cols &lt;= this.columns()</code>).
   * @return The <code>MatrixBuffer</code>, <code>T</code>, with <code>rows</code>
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
  public MatrixBuffer slice(int row, int col, int rows, int cols) {
    return new MatrixBuffer(elements, rows, cols, indexOf(row, col),
                            rowStride, colStride);
  }

  /**
   * Gets a column vector of the diagonal elements of this
   * <code>MatrixBuffer</code> (i.e., the elements <code>this.at(i, i)</code> for
   * <code>0 &lt;= i &lt; Math.min(this.rows(), this.columns())</code>).
   * @return The column vector of the diagonal elements of this
   *     <code>MatrixBuffer</code>.
   * @see #at(int, int)
   * @see Math#min(int, int)
   */
  public MatrixBuffer diagonal() {
    return new MatrixBuffer(elements, Math.min(rows, cols), 1, offset,
                            rowStride + colStride, 0);
  }

  /**
   * Gets the specified row of this <code>MatrixBuffer</code>.
   * @param row The row to get (must be non-negative and less than
   *     <code>this.rows()</code>).
   * @return The specified row of this <code>MatrixBuffer</code>.
   * @throws IllegalArgumentException if <code>row</code> is negative or if
   *     <code>row &gt;= this.rows()</code>.
   * @see #rows()
   */
  public MatrixBuffer row(int row) {
    return slice(row, 0, 1, cols);
  }

  /**
   * Gets the specified column of this <code>MatrixBuffer</code>.
   * @param col The column to get (must be non-negative and less than
   *     <code>this.columns()</code>).
   * @return The specified column of this <code>MatrixBuffer</code>.
   * @throws IllegalArgumentException if <code>col</code> is negative or if
   *     <code>col &gt;= this.columns()</code>.
   * @see #columns()
   */
  public MatrixBuffer column(int col) {
    return slice(0, col, rows, 1);
  }

  /**
   * Creates a <code>MatrixBuffer</code> with the specified values in column-major
   * order.
   * @param rows The number of rows in the <code>MatrixBuffer</code> (must be
   *     non-negative).
   * @param cols The number of columns in the <code>MatrixBuffer</code> (must be
   *     non-negative).
   * @param elements The elements of the <code>MatrixBuffer</code> in column-major
   *     order (must have at least <code>rows * cols</code> elements --
   *     additional elements will be ignored).
   * @return The <code>rows</code> by <code>cols</code> <code>MatrixBuffer</code>
   *     consisting of the elements specified.
   * @throws IllegalArgumentException if
   *     <code>elements.length &lt; rows * cols</code>.
   * @throws IllegalArgumentException if <code>rows</code> or
   *     <code>cols</code> is negative.
   */
  public static MatrixBuffer columnMajor(int rows, int cols,
                                         DoubleBuffer elements) {
    return new MatrixBuffer(elements, rows, cols, 0, 1, rows);
  }

  /**
   * Creates a <code>MatrixBuffer</code> with the specified values in column-major
   * order.
   * @param rows The number of rows in the <code>MatrixBuffer</code> (must be
   *     non-negative).
   * @param cols The number of columns in the <code>MatrixBuffer</code> (must be
   *     non-negative).
   * @param elements The elements of the <code>MatrixBuffer</code> in column-major
   *     order (must have at least <code>rows * cols</code> elements --
   *     additional elements will be ignored).
   * @return The <code>rows</code> by <code>cols</code> <code>MatrixBuffer</code>
   *     consisting of the elements specified.
   * @throws IllegalArgumentException if
   *     <code>elements.length &lt; rows * cols</code>.
   * @throws IllegalArgumentException if <code>rows</code> or
   *     <code>cols</code> is negative.
   */
  public static MatrixBuffer columnMajor(int rows, int cols,
                                         double[] elements) {
    return new MatrixBuffer(elements, rows, cols, 0, 1, rows);
  }

  /**
   * Creates a <code>MatrixBuffer</code> with values arranged in column-major
   * order.
   * @param rows The number of rows in the <code>MatrixBuffer</code> (must be
   *     non-negative).
   * @param cols The number of columns in the <code>MatrixBuffer</code> (must be
   *     non-negative).
   * @return A <code>MatrixBuffer</code> for a <code>rows</code> by
   *     <code>cols</code> matrix.
   * @throws IllegalArgumentException if <code>rows</code> or
   *     <code>cols</code> is negative.
   */
  public static MatrixBuffer columnMajor(int rows, int cols) {
    return columnMajor(rows, cols, new double[rows * cols]);
  }

  /**
   * Creates a <code>MatrixBuffer</code> with the specified values in row-major
   * order.
   * @param rows The number of rows in the <code>MatrixBuffer</code> (must be
   *     non-negative).
   * @param cols The number of columns in the <code>MatrixBuffer</code> (must be
   *     non-negative).
   * @param elements The elements of the <code>MatrixBuffer</code> in row-major
   *     order (must have at least <code>rows * cols</code> elements --
   *     additional elements will be ignored).
   * @return The <code>rows</code> by <code>cols</code> <code>MatrixBuffer</code>
   *     consisting of the elements specified.
   * @throws IllegalArgumentException if
   *     <code>elements.length &lt; rows * cols</code>.
   * @throws IllegalArgumentException if <code>rows</code> or
   *     <code>cols</code> is negative.
   */
  public static MatrixBuffer rowMajor(int rows, int cols,
                                      DoubleBuffer elements) {
    return new MatrixBuffer(elements, rows, cols, 0, cols, 1);
  }

  /**
   * Creates a <code>MatrixBuffer</code> with the specified values in row-major
   * order.
   * @param rows The number of rows in the <code>MatrixBuffer</code> (must be
   *     non-negative).
   * @param cols The number of columns in the <code>MatrixBuffer</code> (must be
   *     non-negative).
   * @param elements The elements of the <code>MatrixBuffer</code> in row-major
   *     order (must have at least <code>rows * cols</code> elements --
   *     additional elements will be ignored).
   * @return The <code>rows</code> by <code>cols</code> <code>MatrixBuffer</code>
   *     consisting of the elements specified.
   * @throws IllegalArgumentException if
   *     <code>elements.length &lt; rows * cols</code>.
   * @throws IllegalArgumentException if <code>rows</code> or
   *     <code>cols</code> is negative.
   */
  public static MatrixBuffer rowMajor(int rows, int cols, double[] elements) {
    return new MatrixBuffer(elements, rows, cols, 0, cols, 1);
  }

  /**
   * Creates a <code>MatrixBuffer</code> with values arranged in row-major
   * order.
   * @param rows The number of rows in the <code>MatrixBuffer</code> (must be
   *     non-negative).
   * @param cols The number of columns in the <code>MatrixBuffer</code> (must be
   *     non-negative).
   * @return A <code>MatrixBuffer</code> for a <code>rows</code> by
   *     <code>cols</code> matrix.
   * @throws IllegalArgumentException if <code>rows</code> or
   *     <code>cols</code> is negative.
   */
  public static MatrixBuffer rowMajor(int rows, int cols) {
    return rowMajor(rows, cols, new double[rows * cols]);
  }

}