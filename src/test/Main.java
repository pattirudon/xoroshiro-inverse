package test;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import app.*;

public class Main {

    public static void main(String[] args) {
        List<Integer> flawlessIvsList = Arrays.asList(2, 2, 3);
        List<int[]> lowersList = Arrays.asList(new int[] { 7, 25, 31, 18, 31, 29 }, new int[] { 31, 30, 31, 7, 27, 31 },
                new int[] { 30, 23, 31, 21, 31, 31 });
        List<int[]> uppersList = lowersList;
        List<Integer> abilityTypesList = Arrays.asList(3, 3, 3);
        List<Integer> abilitiesList = Arrays.asList(-1, -1, -1);
        List<Boolean> requireGenderList = Arrays.asList(true, true, true);
        List<Integer> natureList = Arrays.asList(13, 1, 15);
        int offsetLastFlawlessIdx = 1;
        int lastFlawlessIdx = 2;
        int[] flaws = { 7, 25, 18, 29, 14, 2 };
        int additionalBit = 0;
        int offsetAdditionalBit = 0;
        OmotePredicateFor6IVs predicate = new OmotePredicateFor6IVs(flawlessIvsList, lowersList, uppersList,
                abilityTypesList, abilitiesList, requireGenderList, natureList, offsetLastFlawlessIdx, lastFlawlessIdx,
                flaws, additionalBit, offsetAdditionalBit);
        IntStream.rangeClosed(0, 0x3fffffff).parallel().filter(predicate).count();
    }

    public static void specific() {
        long seed = 0xfd15fd5b9d7640fdL;
        int debugIvRecalculation = 2;
        List<Integer> flawlessIvsList = Arrays.asList(1, 1, 1);
        List<Integer> abilityTypesList = Arrays.asList(4, 4, 3);
        List<Boolean> requireGenderList = Arrays.asList(false, false, false);
        test(seed, debugIvRecalculation, flawlessIvsList, abilityTypesList, requireGenderList);
    }

    public static void random() {
        SecureRandom r = null;
        try {
            r = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        if (r == null) {
            return;
        }
        boolean result = false;
        do {
            int debugIvRecalculation = r.nextInt(2) + 2;
            List<Integer> flawlessIvsList = new ArrayList<>();
            List<Integer> abilitiyTypesList = new ArrayList<>();
            List<Boolean> requireGenderList = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                int flawlessIvs = r.nextInt(3) + 1;
                flawlessIvsList.add(flawlessIvs);
                int abilityType = r.nextInt(2) + 3;
                abilitiyTypesList.add(abilityType);
                boolean requireGender = r.nextBoolean();
                requireGenderList.add(requireGender);
            }

            int offsetLastFlawlessIdx = flawlessIvsList.get(0) - 1 + debugIvRecalculation;
            long goodseed;

            while (true) {
                goodseed = r.nextLong();
                if (DenGenerator.hasLastFlawlessIdx(goodseed, flawlessIvsList.get(0), offsetLastFlawlessIdx)) {
                    boolean accidental = false;
                    for (int i = 0; i < flawlessIvsList.size(); i++) {
                        long seed = goodseed + Xoroshiro.XOROSHIRO_CONST * i;
                        if (DenGenerator.hasAccidentalFlawless(seed, flawlessIvsList.get(i))) {
                            accidental = true;
                            break;
                        }
                    }
                    if (!accidental)
                        break;
                }
            }

            result = test(goodseed, offsetLastFlawlessIdx, flawlessIvsList, abilitiyTypesList, requireGenderList);
        } while (result);
    }

    public static boolean test(long seed0, int actualOffsetLastFlawlessIdx, List<Integer> flawlessIvsList,
            List<Integer> abilityTypesList, List<Boolean> requireGenderList) {
        SecureRandom r = null;
        try {
            r = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        if (r == null) {
            return false;
        }

        // List<Integer> flawlessIvsList = new ArrayList<>();
        final List<int[]> lowersList = new ArrayList<>();
        final List<int[]> uppersList = new ArrayList<>();
        // final List<Integer> abilitiyTypeList = new ArrayList<>();
        final List<Integer> abilitiesList = new ArrayList<>();
        // final List<Boolean> requireGenderList = new ArrayList<>();
        final List<Integer> natureList = new ArrayList<>();
        final int[] flaws;

        int minOffset = flawlessIvsList.get(0) - 1;
        int maxOffset = minOffset + 4;

        for (int i = 0; i < flawlessIvsList.size(); i++) {
            long seed = seed0 + Xoroshiro.XOROSHIRO_CONST * i;
            List<Integer> g = DenGenerator.original(seed, flawlessIvsList.get(i), abilityTypesList.get(i),
                    requireGenderList.get(i));
            int[] lower = new int[6];
            int[] upper = new int[6];
            for (int j = 0; j < 6; j++) {
                lower[j] = g.get(j);
                upper[j] = g.get(j);
            }
            lowersList.add(lower);
            uppersList.add(upper);
            abilitiesList.add(g.get(6));
            int nature = g.get(8);
            natureList.add(nature);
        }

        int offsetAdditionalBit = 0;

        flaws = DenGenerator.series(seed0, actualOffsetLastFlawlessIdx);
        byte[] x = DenGenerator.pure(seed0, Xoroshiro.XOROSHIRO_CONST, actualOffsetLastFlawlessIdx,
                offsetAdditionalBit);
        int omote = (int) readBE(x, 0, 3);
        for (int i = 0; i < 5; i++) {
            omote = (omote << 5) | (int) readBE(x, 6 + 10 * i, 5);
        }

        int omote_lower_bound_inclusive = omote & 0xFFFFFFFF;
        int omote_upper_bound_inclusive = omote_lower_bound_inclusive | 0;

        ///////////////////////////////////////////////////////////////////////////////////////////////////

        List<Integer> probableAdditionalBits = new ArrayList<>();
        int offsetAdditionalBits;
        {
            int ec = (int) (new Xoroshiro(seed0)).nextInt() & 1;
            offsetAdditionalBits = 0;
            if (ec != -1) {
                probableAdditionalBits.add(ec);
            } else {
                probableAdditionalBits.add(0);
                probableAdditionalBits.add(1);
            }
        }

        List<Integer> probableFlawlessIdx = new ArrayList<>();
        int[] ivs1 = lowersList.get(0);
        for (int i = 0; i < ivs1.length; i++) {
            if (ivs1[i] == 31)
                probableFlawlessIdx.add(i);
        }

        System.out.println("calculating the seed of xoroshiro rng for the 1st raid pokemon...");
        long start = System.currentTimeMillis();
        List<Integer> omoList = new ArrayList<>();
        boolean quitLoop = false;
        boolean found = false;
        List<Integer> probableBitsAfterFlaws;
        if (flawlessIvsList.get(0) > 1 || abilitiesList.get(0) == -1) {
            probableBitsAfterFlaws = Arrays.asList(0, 1);
        } else {
            // ability1 can be used
            probableBitsAfterFlaws = Arrays.asList(abilitiesList.get(0));
        }
        label_outer: for (int offsetLastFlawlessIdx = minOffset; offsetLastFlawlessIdx <= maxOffset; offsetLastFlawlessIdx++) {
            System.out.printf("suppose the number of seeds before the last flawless IV is %d...%n",
                    offsetLastFlawlessIdx);
            for (int lastFlawlessIdx : probableFlawlessIdx) {
                System.out.printf("- suppose the index of the last flawless IV of 1st pokemon is %d...%n",
                        lastFlawlessIdx);
                for (int bit : probableAdditionalBits) {
                    OmotePredicate.IVPredicateBuilder builder = new OmotePredicate.IVPredicateBuilder();
                    builder.setFlawlessIvsList(flawlessIvsList).setLowersList(lowersList).setUppersList(uppersList)
                            .setAbilityTypesList(abilityTypesList).setAbilitiesList(abilitiesList)
                            .setRequireGenderList(requireGenderList).setNatureList(natureList)
                            .setLastFlawlessIdx(lastFlawlessIdx).setOffsetLastFlawlessIdx(offsetLastFlawlessIdx)
                            .setAdditionalBit(bit).setOffsetAdditionalBit(offsetAdditionalBits).setFlaws(flaws);
                    OmotePredicate predicate = builder.getIncetance();
                    int[] bundle = IntStream.rangeClosed(omote_lower_bound_inclusive, omote_upper_bound_inclusive)
                            .parallel().filter(predicate).toArray();
                    for (int o : bundle) {
                        omoList.add(o);
                    }
                }
            }
        }

        long end = System.currentTimeMillis();
        System.out.printf("finish.[%dms]%n", end - start);

        return omoList.contains(omote);
    }

    private static long readBE(byte[] src, int start, int length) {
        long result = 0;
        for (int i = 0; i < length; i++) {
            result <<= 1;
            result |= src[start + i];
        }
        return result;
    }

}