package info.kgeorgiy.ja.Gebesht.walk;

public class FNVHasher {
    private static final int OFFSET = 0x811c9dc5;
    private static final int PRIME = 0x01000193;

    private int current = OFFSET;

    public void update(byte[] buffer, int n) {
        for (int i = 0; i < n; i++) {
            current *= PRIME;
            current ^= Byte.toUnsignedInt(buffer[i]);
        }
    }

    public int hash() {
        return current;
    }
}
