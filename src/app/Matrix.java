package app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Matrix {
    public final long[] rows;
    public final int m;

    private Matrix(int m) {
        rows = new long[m];
        this.m = m;
    }

    public Matrix(long... rows) {
        this.rows = Arrays.copyOf(rows, rows.length);
        this.m = rows.length;
    }

    public Matrix(Matrix f) {
        this.rows = Arrays.copyOf(f.rows, f.rows.length);
        this.m = f.m;
    }

    public static Matrix zeros(int m) {
        return new Matrix(m);
    }

    public static Matrix ones(int m) {
        Matrix f = zeros(m);
        for (int i = 0; i < m; i++) {
            f.setEntry(i, i, (byte) 1);
        }
        return f;
    }

    public Matrix swapRows(int i, int j) {
        long ri = rows[i];
        long rj = rows[j];
        rows[j] = ri;
        rows[i] = rj;
        return this;
    }

    public Matrix addRows(int src, int dst) {
        rows[dst] ^= rows[src];
        return this;
    }

    public static void swapRows(int i, int j, int[] x) {
        int ri = x[i];
        int rj = x[j];
        x[j] = ri;
        x[i] = rj;
    }

    public static void addRows(int src, int dst, int[] x) {
        x[dst] ^= x[src];
    }

    public byte[] getColumn(int j) {
        byte[] column = new byte[m];
        long b = 1L << (63 - j);
        for (int i = 0; i < m; i++) {
            if ((rows[i] & b) != 0)
                column[i] = 1;
        }
        return column;
    }

    public Matrix swapColumns(int i, int j) {
        byte[] ci = getColumn(i);
        byte[] cj = getColumn(j);
        this.setColumn(j, ci);
        this.setColumn(i, cj);
        return this;
    }

    public Matrix addColumns(int src, int dst) {
        byte[] s = getColumn(src);
        byte[] d = getColumn(dst);
        for (int i = 0; i < m; i++) {
            d[i] ^= s[i];
        }
        setColumn(dst, d);
        return this;
    }

    public Meta getEchelon() {
        Matrix f = new Matrix(this); /* deep copy */
        int rank = 0;
        List<Integer> pivotslist = new ArrayList<>();
        for (int j = 0; j < 64; j++) {
            long b = 1L << (63 - j);
            for (int i = rank; i < f.m; i++) {
                if ((f.rows[i] & b) != 0) {
                    /* erase other rows */
                    for (int k = i + 1; k < f.m; k++) {
                        if ((f.rows[k] & b) != 0)
                            f.addRows(i, k);
                    }
                    f.swapRows(i, rank);
                    pivotslist.add(j);
                    rank++;
                    break;
                }
            }
        }
        int[] pivots = new int[pivotslist.size()];
        for (int i = 0; i < pivots.length; i++) {
            pivots[i] = pivotslist.get(i);
        }
        return new Meta(f, rank, pivots);
    }

    public int rank() {
        return getEchelon().rank();
    }

    public int size() {
        return m;
    }

    public Matrix setEntry(int i, int j, byte value) {
        if (value != 0) {
            long b = 0x8000000000000000L >>> j;
            rows[i] |= b;
        } else {
            long b = ~(0x8000000000000000L >>> j);
            rows[i] &= b;
        }
        return this;
    }

    public Matrix setColumn(int j, byte[] column) {
        for (int i = 0; i < m; i++) {
            setEntry(i, j, column[i]);
        }
        return this;
    }

    public byte[] multVect(long v) {
        byte[] result = new byte[m];
        for (int i = 0; i < m; i++) {
            long p = rows[i] & v;
            byte q = 0;
            for (int j = 0; j < 64; j++) {
                if (((1L << j) & p) != 0) {
                    q ^= 1;
                }
            }
            result[i] = q;
        }
        return result;
    }

    public List<Long> kernelBasis() {
        Meta f = getEchelon(); /* triangulation doesn't affect the kernel */
        Matrix q = Matrix.ones(64);

        int rank = 0;
        for (int i = 0; i < f.m; i++) {
            for (int j = rank; j < 64; j++) {
                long b = 1L << (63 - j);
                if ((f.rows[i] & b) != 0) {
                    /* erase other columns */
                    for (int k = j + 1; k < 64; k++) {
                        long bb = 1L << (63 - k);
                        if ((f.rows[i] & bb) != 0) {
                            f.addColumns(j, k);
                            q.addRows(j, k);
                        }
                    }
                    /* move pivot to diagonal */
                    f.swapColumns(j, rank);
                    q.swapRows(j, rank);
                    rank++;
                    break;
                }
            }
        }
        List<Long> basis = new ArrayList<>();
        for (int i = rank; i < 64; i++) {
            basis.add(q.rows[i]);
        }
        return basis;
    }

    public static Matrix f(int offsetLastFlawlessIdx, int offsetAdditionalBit) {
        int m = DenGenerator.pure(0, 0, offsetLastFlawlessIdx, offsetAdditionalBit).length;
        Matrix f = Matrix.zeros(m);
        for (int j = 0; j < 64; j++) {
            long b = 1L << (63 - j);
            byte[] column = DenGenerator.pure(b, 0, offsetLastFlawlessIdx, offsetAdditionalBit);
            f.setColumn(j, column);
        }
        return f;
    }

    public static Matrix finv(int offsetLastFlawlessIdx, int offsetAdditionalBit) {
        /*
         * left inverse like matrix
         * 
         * (finv)^t*f=[diagonal 1 or 0; *] <- number of diagonal 1 equals to rank f
         */
        Matrix f = f(offsetLastFlawlessIdx, offsetAdditionalBit);
        Matrix f0 = Matrix.zeros(64);
        for (int i = 0; i < f.m; i++) {
            f0.rows[i] = f.rows[i];
        }
        Matrix finv = Matrix.ones(64);
        int rank = 0;
        List<Integer> pivotslist = new ArrayList<>();
        for (int j = 0; j < 64; j++) {
            long b = 1L << (63 - j);
            for (int i = rank; i < f0.m; i++) {
                if ((f0.rows[i] & b) != 0) {
                    /* erase other rows */
                    for (int k = 0; k < f0.m; k++) {
                        if ((k != i) && (f0.rows[k] & b) != 0) {
                            f0.addRows(i, k);
                            finv.addColumns(i, k);
                        }
                    }
                    f0.swapRows(i, rank);
                    finv.swapColumns(i, rank);
                    pivotslist.add(j);
                    rank++;
                    break;
                }
            }
        }
        for (int i = pivotslist.size() - 1; i >= 0; i--) {
            int j = pivotslist.get(i);
            f0.swapRows(i, j);
            finv.swapColumns(i, j);
        }
        int generate = pivotslist.get(pivotslist.size() - 1);
        long mask = -(1L << (63 - generate));
        // long mask = -1L;
        Matrix small = Matrix.zeros(f.m);
        for (int i = 0; i < small.m; i++) {
            small.rows[i] = finv.rows[i] & mask;
        }
        return small;
    }

    public static class Meta extends Matrix {
        public final int rank;
        public final int generate;
        public final int[] pivots;

        Meta(Matrix f, int rank, int[] pivots) {
            super(f);
            this.rank = rank;
            this.pivots = Arrays.copyOf(pivots, pivots.length);
            if (this.pivots.length > 0) {
                generate = pivots[pivots.length - 1];
            } else {
                generate = 0;
            }
        }

        @Override
        public int rank() {
            return rank;
        }
    }
}