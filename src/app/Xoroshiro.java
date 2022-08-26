package app;

public class Xoroshiro {
    public static final long XOROSHIRO_CONST = 0x82A2B175229D6A5BL;

    public final long[] s = { 0, 0 };

    long i;

    static long rotl(long x, int k) {
        return (x << k) | (x >>> (64 - k));
    }

    public Xoroshiro(long seed) {
        s[0] = seed;
        s[1] = XOROSHIRO_CONST;
    }

    public Xoroshiro(long s0, long s1) {
        s[0] = s0;
        s[1] = s1;
    }

    public long next() {
        long s0 = s[0];
        long s1 = s[1];
        long result = s0 + s1;

        s1 ^= s0;
        s[0] = rotl(s0, 24) ^ s1 ^ (s1 << 16);
        s[1] = rotl(s1, 37);

        i++;

        return result;
    }

    private static long nextP2(long n) {
        n--;
        for (int i = 0; i < 6; i++) {
            n |= n >>> (1 << i);
        }
        return n;
    }

    public long nextInt(long MOD) {
        long res = 0;
        long p2mod = nextP2(MOD);
        do {
            res = next() & p2mod;
        } while (res >= MOD);
        return res;
    }

    public long nextInt() {
        return nextInt(0xFFFFFFFFL);
    }
}