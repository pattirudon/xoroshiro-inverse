package app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DenGenerator {
    public static List<Integer> original(long seed, int flawlessIvs, int abilityType, boolean requireGender) {
        Xoroshiro rng = new Xoroshiro(seed);
        for (int i = 0; i < 3; i++) {
            rng.next();
        }
        int[] ivs = { -1, -1, -1, -1, -1, -1 };
        long start = rng.i;
        for (int i = 0; i < flawlessIvs; i++) {
            int idx;
            do {
                idx = (int) rng.nextInt(6);
            } while (ivs[idx] != -1);
            ivs[idx] = 31;
        }
        for (int i = 0; i < 6; i++) {
            if (ivs[i] == -1)
                ivs[i] = (int) rng.nextInt(32);
        }
        long end = rng.i;
        int ability;
        if (abilityType == 4) {
            ability = (int) rng.nextInt(3);
        } else {
            ability = (int) rng.nextInt(2);
        }
        int gender = -1;
        if (requireGender) {
            gender = (int) rng.nextInt(253) + 1;
        }
        int nature = (int) rng.nextInt(25);

        System.out.printf("seed:0x%016x%n", seed);
        System.out.printf("IVs:%dV - %s (%d rolls)%n", flawlessIvs, Arrays.toString(ivs), end - start);
        System.out.printf("ability:%d/%d%n", ability, abilityType);
        System.out.printf("gender:%d%n", gender);
        System.out.printf("nature:%d%n", nature);
        List<Integer> result = new ArrayList<>(7);
        for (int iv : ivs) {
            result.add(iv);
        }
        result.add(ability);
        result.add(gender);
        result.add(nature);
        return result;
    }

    public static byte[] pure(long s0, long s1, int offsetLastFlawlessIdx, int offsetAdditionalBit) {
        int m = 57;
        byte[] column = new byte[m];
        Xoroshiro rng = new Xoroshiro(s0, s1);
        if (offsetAdditionalBit == 0) {
            int ec = (int) rng.nextInt();
            writeBE(ec, column, 56, 1);
        } else {
            rng.next();
        }
        rng.next();
        rng.next();
        for (int i = 0; i < offsetLastFlawlessIdx; i++) {
            rng.next();
        }
        writeBE(rng.s[0], column, 0, 3);
        writeBE(rng.s[1], column, 3, 3);
        rng.next();
        for (int i = 0; i < 5; i++) {
            writeBE(rng.s[0], column, 6 + 10 * i, 5);
            writeBE(rng.s[1], column, 11 + 10 * i, 5);
            rng.next();
        }
        if (offsetAdditionalBit != 0) {
            int ability = (int) rng.nextInt();
            writeBE(ability, column, 56, 1);
        }
        return column;
    }

    public static int[] series(long seed, int offsetLastFlawlessIdx) {
        int[] result = new int[5];
        Xoroshiro rng = new Xoroshiro(seed);
        rng.next();
        rng.next();
        rng.next();
        for (int i = 0; i < offsetLastFlawlessIdx; i++) {
            rng.next();
        }
        rng.next(); // last flawless iv

        for (int i = 0; i < result.length; i++) {
            result[i] = (int) rng.nextInt(32);
        }
        return result;
    }

    public static byte[] linear(long seed, int offsetLastFlawlessIdx, int offsetAdditionalBit) {
        Matrix f = Matrix.f(offsetLastFlawlessIdx, offsetAdditionalBit);
        byte[] v = f.multVect(seed);
        byte[] o = pure(0, Xoroshiro.XOROSHIRO_CONST, offsetLastFlawlessIdx, offsetAdditionalBit);
        for (int i = 0; i < o.length; i++) {
            v[i] ^= o[i];
        }
        return v;
    }

    public static boolean hasAccidentalFlawless(long seed, int flawlessIvs) {
        Xoroshiro rng = new Xoroshiro(seed);
        rng.nextInt();
        rng.nextInt();
        rng.nextInt();
        int[] ivs = { -1, -1, -1, -1, -1, -1 };
        for (int i = 0; i < flawlessIvs; i++) {
            int idx;
            do {
                idx = (int) rng.nextInt(6);
            } while (ivs[idx] != -1);
            ivs[idx] = 31;
        }

        for (int i = 0; i < 6; i++) {
            if (ivs[i] == -1) {
                int iv = (int) rng.nextInt(32);
                if (iv == 31)
                    return true;
            }
        }
        return false;
    }

    public static boolean hasLastFlawlessIdx(long seed, int flawlessIvs, int offsetLastFlawlessIdx) {
        Xoroshiro rng = new Xoroshiro(seed);
        rng.nextInt();
        rng.nextInt();
        rng.nextInt();
        int[] ivs = { -1, -1, -1, -1, -1, -1 };
        long start = rng.i;
        for (int i = 0; i < flawlessIvs; i++) {
            int idx;
            do {
                idx = (int) rng.nextInt(6);
            } while (ivs[idx] != -1);
            ivs[idx] = 31;
        }
        long end = rng.i;
        if ((int) (end - start) != offsetLastFlawlessIdx + 1) {
            return false;
        }
        return true;
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
}