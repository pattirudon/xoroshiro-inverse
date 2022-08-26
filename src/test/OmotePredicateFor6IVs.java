package test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

import app.*;

public class OmotePredicateFor6IVs extends OmotePredicate implements IntPredicate {

    final Matrix finv;

    protected final long xoroshiroequiv;

    final long[] kernel;

    public OmotePredicateFor6IVs(List<Integer> flawlessIvsList, List<int[]> lowersList, List<int[]> uppersList,
            List<Integer> abilityTypesList, List<Integer> abilitiesList, List<Boolean> requireGenderList,
            List<Integer> natureList, int offsetLastFlawlessIdx, int lastFlawlessIdx, int[] flaws, int additionalBit,
            int offsetAdditionalBit) {
        super(flawlessIvsList, lowersList, uppersList, abilityTypesList, abilitiesList, requireGenderList, natureList,
                offsetLastFlawlessIdx, lastFlawlessIdx, flaws, additionalBit, offsetAdditionalBit);
        {
            finv = f6inv(offsetLastFlawlessIdx);
        }
        {
            final Matrix f = f6(offsetLastFlawlessIdx);
            List<Long> basislist = f.kernelBasis();
            int dim = basislist.size();
            int card = 1 << dim;
            List<Long> tmp = new ArrayList<>(card);
            for (int i = 0; i < card; i++) {
                long k = 0;
                for (int j = 0; j < dim; j++) {
                    if ((i & (1 << j)) != 0) {
                        k ^= basislist.get(j);
                    }
                }
                tmp.add(k);
            }
            this.kernel = new long[tmp.size()];
            for (int i = 0; i < tmp.size(); i++) {
                this.kernel[i] = tmp.get(i);
            }
        }
        {
            byte[] x = column(0, Xoroshiro.XOROSHIRO_CONST, offsetLastFlawlessIdx);
            long tmp = 0;
            for (int i = 0; i < x.length; i++) {
                if (x[i] != 0) {
                    tmp ^= finv.rows[i];
                }
            }
            xoroshiroequiv = tmp;
        }
    }

    @Override
    public boolean test(int omote) {
        int[] iv0 = new int[6];
        int[] iv1 = new int[6];
        for (int i = iv0.length - 1; i >= 0; i--) {
            int iv = omote & 0x1f;
            iv0[i] = iv;
            iv1[i] = (flaws[i] - iv) & 0x1f;
            omote >>>= 5;
        }
        // int flawlessIdx0 = omote & 0x7;
        // int flawlessIdx1 = (lastFlawlessIdx - flawlessIdx0) & 0x7;

        byte[] x = new byte[60];
        // writeBE(flawlessIdx0, x, 0, 3);
        // writeBE(flawlessIdx1, x, 3, 3);
        for (int i = 0; i < iv0.length; i++) {
            writeBE(iv0[i], x, 10 * i, 5);
            writeBE(iv1[i], x, 5 + 10 * i, 5);
        }
        // writeBE(additionalBit, x, 56, 1);
        long finvx = 0;
        for (int i = 0; i < x.length; i++) {
            if (x[i] != 0) {
                finvx ^= finv.rows[i];
            }
        }
        for (int i = 0; i < kernel.length; i++) {
            long seed0 = xoroshiroequiv ^ finvx ^ kernel[i];
            int size = flawlessIvsList.size();
            boolean result = true;
            for (int j = size - 1; j >= 0; j--) {
                long seed = seed0 + Xoroshiro.XOROSHIRO_CONST * j;
                int flawlessIvs = flawlessIvsList.get(j);
                int[] lower = lowersList.get(j);
                int[] upper = uppersList.get(j);
                int abilityType = abilityTypesList.get(j);
                int ability = abilitiesList.get(j);
                boolean requireGender = requireGenderList.get(j);
                int nature = natureList.get(j);
                result &= ivAbilityNature(seed, flawlessIvs, lower, upper, abilityType, ability, requireGender, nature);
                if (!result) {
                    break;
                }
            }
            if (result) {
                System.out.printf("0x%016x%n", seed0);
                return true;
            }
        }
        return false;
    }

    static byte[] column(long s0, long s1, int offsetLastFlawlessIdx) {
        int m = 60;
        byte[] column = new byte[m];
        Xoroshiro rng = new Xoroshiro(s0, s1);
        rng.next();
        rng.next();
        rng.next();
        for (int i = 0; i <= offsetLastFlawlessIdx; i++) {
            rng.next();
        }
        for (int i = 0; i < 6; i++) {
            writeBE(rng.s[0], column, 10 * i, 5);
            writeBE(rng.s[1], column, 5 + 10 * i, 5);
            rng.next();
        }
        return column;
    }

    private static Matrix f6(int offsetLastFlawlessIdx) {
        int m = 60;
        Matrix f = Matrix.zeros(m);
        for (int j = 0; j < 64; j++) {
            long b = 1L << (63 - j);
            byte[] column = column(b, 0, offsetLastFlawlessIdx);
            f.setColumn(j, column);
        }
        return f;
    }

    private static void writeBE(long src, byte[] dst, int start, int length) {
        for (int i = 0; i < length; i++) {
            int b = 1 << (length - 1 - i);
            if ((src & b) != 0) {
                dst[start + i] = 1;
            } else {
                dst[start + i] = 0;
            }
        }
    }

    private static Matrix f6inv(int offsetLastFlawlessIdx) {
        /*
         * left inverse like matrix
         * 
         * (finv)^t*f=[diagonal 1 or 0; *] <- number of diagonal 1 equals to rank f
         */
        Matrix f = f6(offsetLastFlawlessIdx);
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
}