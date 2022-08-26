package app;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

public class OmotePredicate implements IntPredicate {
    protected final List<Integer> flawlessIvsList;
    protected final List<int[]> lowersList;
    protected final List<int[]> uppersList;
    protected final List<Integer> abilityTypesList;
    protected final List<Integer> abilitiesList;
    protected final List<Boolean> requireGenderList;
    protected final List<Integer> natureList;
    protected final int offsetLastFlawlessIdx;
    protected final int lastFlawlessIdx;
    protected final int[] flaws;
    protected final int additionalBit;

    final Matrix finv;

    protected final long xoroshiroequiv;

    final long[] kernel;

    /*
     * find x (64 bit)
     * f(seed,Xoroshiroconst) = {flawlessIdx0, flawlessIdx1, ... , ability}
     * f(seed) = f(Xoroshiroconst) + {flawlessIdx0, flawlessIdx1, ... , ability}
     * f:surj
     * seed = f^-1(f(Xoroshiroconst)) + f^-1({flawlessIdx0, flawlessIdx1, ... , ability})
     *      + kernel
     */

    public OmotePredicate(List<Integer> flawlessIvsList, List<int[]> lowersList, List<int[]> uppersList,
            List<Integer> abilityTypesList, List<Integer> abilitiesList, List<Boolean> requireGenderList,
            List<Integer> natureList, int offsetLastFlawlessIdx, int lastFlawlessIdx, int[] flaws, int additionalBit,
            int offsetAdditionalBit) {
        this.flawlessIvsList = flawlessIvsList;
        this.lowersList = lowersList;
        this.uppersList = uppersList;
        this.abilitiesList = abilitiesList;
        this.abilityTypesList = abilityTypesList;
        this.requireGenderList = requireGenderList;
        this.natureList = natureList;
        this.offsetLastFlawlessIdx = offsetLastFlawlessIdx;
        this.lastFlawlessIdx = lastFlawlessIdx;
        this.flaws = flaws;
        this.additionalBit = additionalBit;
        {
            finv = Matrix.finv(offsetLastFlawlessIdx, offsetAdditionalBit);
        }
        {
            final Matrix f = Matrix.f(offsetLastFlawlessIdx, offsetAdditionalBit);
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
            byte[] x = DenGenerator.linear(0, offsetLastFlawlessIdx, offsetAdditionalBit);
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
        int[] iv0 = new int[5];
        int[] iv1 = new int[5];
        for (int i = iv0.length - 1; i >= 0; i--) {
            int iv = omote & 0x1f;
            iv0[i] = iv;
            iv1[i] = (flaws[i] - iv) & 0x1f;
            omote >>>= 5;
        }
        int flawlessIdx0 = omote & 0x7;
        int flawlessIdx1 = (lastFlawlessIdx - flawlessIdx0) & 0x7;

        byte[] x = new byte[57];
        writeBE(flawlessIdx0, x, 0, 3);
        writeBE(flawlessIdx1, x, 3, 3);
        for (int i = 0; i < 5; i++) {
            writeBE(iv0[i], x, 6 + 10 * i, 5);
            writeBE(iv1[i], x, 11 + 10 * i, 5);
        }
        writeBE(additionalBit, x, 56, 1);
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

    protected static boolean ivAbilityNature(long seed, int flawlessIvs, int[] lower, int[] upper, int abilityType,
            int ability, boolean requireGender, int nature) {
        long s0 = seed;
        long s1 = Xoroshiro.XOROSHIRO_CONST;
        for (int i = 0; i < 3; i++) {
            s1 ^= s0;
            s0 = rotl(s0, 24) ^ s1 ^ (s1 << 16);
            s1 = rotl(s1, 37);
        }
        int[] tmpivs = { -1, -1, -1, -1, -1, -1 };
        for (int i = 0; i < flawlessIvs; i++) {
            int idx;
            do {
                do {
                    int temper = (int) (s0 + s1);
                    idx = temper & 0x7;
                    s1 ^= s0;
                    s0 = rotl(s0, 24) ^ s1 ^ (s1 << 16);
                    s1 = rotl(s1, 37);
                } while (idx >= 6);
            } while (tmpivs[idx] != -1);
            tmpivs[idx] = 31;
            if (upper[idx] < 31) {
                return false;
            }
        }
        for (int i = 0; i < 6; i++) {
            if (tmpivs[i] == -1) {
                int temper = (int) (s0 + s1);
                int iv = temper & 0x1f;
                tmpivs[i] = iv;
                if (iv < lower[i] || iv > upper[i]) {
                    return false;
                }
                s1 ^= s0;
                s0 = rotl(s0, 24) ^ s1 ^ (s1 << 16);
                s1 = rotl(s1, 37);
            }
        }
        { // ability
            int tmpability;
            if (abilityType == 4) { // hidden
                do {
                    int temper = (int) (s0 + s1);
                    tmpability = temper & 0x3;
                    s1 ^= s0;
                    s0 = rotl(s0, 24) ^ s1 ^ (s1 << 16);
                    s1 = rotl(s1, 37);
                } while (tmpability >= 3);
            } else {
                int temper = (int) (s0 + s1);
                tmpability = temper & 0x1;
                s1 ^= s0;
                s0 = rotl(s0, 24) ^ s1 ^ (s1 << 16);
                s1 = rotl(s1, 37);
            }
            boolean match;
            if (ability == -1) { // ordinary
                match = (tmpability == 0) || (tmpability == 1);
            } else {
                match = tmpability == ability;
            }
            if (!match) {
                return false;
            }
        }
        if (requireGender) {
            int tmp;
            do { // gender
                int temper = (int) (s0 + s1);
                tmp = temper & 0xff;
                s1 ^= s0;
                s0 = rotl(s0, 24) ^ s1 ^ (s1 << 16);
                s1 = rotl(s1, 37);
            } while (tmp >= 253);
        }
        {
            int tmpnature;
            do {
                int temper = (int) (s0 + s1);
                tmpnature = temper & 0x1f;
                s1 ^= s0;
                s0 = rotl(s0, 24) ^ s1 ^ (s1 << 16);
                s1 = rotl(s1, 37);
            } while (tmpnature >= 25);
            if (nature != tmpnature) {
                return false;
            }
        }
        return true;
    }

    protected static void writeBE(int src, byte[] dst, int start, int length) {
        for (int i = 0; i < length; i++) {
            int b = 1 << (length - 1 - i);
            if ((src & b) != 0) {
                dst[start + i] = 1;
            } else {
                dst[start + i] = 0;
            }
        }
    }

    static long rotl(long x, int k) {
        return (x << k) | (x >>> (64 - k));
    }

    public static class IVPredicateBuilder {
        List<Integer> flawlessIvsList;
        List<int[]> lowersList;
        List<int[]> uppersList;
        List<Integer> abilityTypesList;
        List<Integer> abilitiesList;
        List<Boolean> requireGenderList;
        List<Integer> natureList;
        int offsetLastFlawlessIdx;
        int lastFlawlessIdx;
        int[] flaws;
        int additionalBit;
        int offsetAdditionalBit;

        public OmotePredicate getIncetance() {
            return new OmotePredicate(flawlessIvsList, lowersList, uppersList, abilityTypesList, abilitiesList,
                    requireGenderList, natureList, offsetLastFlawlessIdx, lastFlawlessIdx, flaws, additionalBit,
                    offsetAdditionalBit);
        }

        public IVPredicateBuilder setLastFlawlessIdx(int lastFlawlessIdx) {
            this.lastFlawlessIdx = lastFlawlessIdx;
            return this;
        }

        public IVPredicateBuilder setOffsetLastFlawlessIdx(int offsetLastFlawlessIdx) {
            this.offsetLastFlawlessIdx = offsetLastFlawlessIdx;
            return this;
        }

        public IVPredicateBuilder setFlaws(int[] flaws) {
            this.flaws = flaws;
            return this;
        }

        public IVPredicateBuilder setAdditionalBit(int bit) {
            this.additionalBit = bit;
            return this;
        }

        public IVPredicateBuilder setFlawlessIvsList(List<Integer> flawlessIvsList) {
            this.flawlessIvsList = flawlessIvsList;
            return this;
        }

        public IVPredicateBuilder setLowersList(List<int[]> lowersList) {
            this.lowersList = lowersList;
            return this;
        }

        public IVPredicateBuilder setUppersList(List<int[]> uppersList) {
            this.uppersList = uppersList;
            return this;
        }

        public IVPredicateBuilder setAbilitiesList(List<Integer> abilitiesList) {
            this.abilitiesList = abilitiesList;
            return this;
        }

        public IVPredicateBuilder setRequireGenderList(List<Boolean> requireGenderList) {
            this.requireGenderList = requireGenderList;
            return this;
        }

        public IVPredicateBuilder setNatureList(List<Integer> natureList) {
            this.natureList = natureList;
            return this;
        }

        public IVPredicateBuilder setAbilityTypesList(List<Integer> abilityTypesList) {
            this.abilityTypesList = abilityTypesList;
            return this;
        }

        public IVPredicateBuilder setOffsetAdditionalBit(int offsetAdditionalBits) {
            this.offsetAdditionalBit = offsetAdditionalBits;
            return this;
        }
    }
}