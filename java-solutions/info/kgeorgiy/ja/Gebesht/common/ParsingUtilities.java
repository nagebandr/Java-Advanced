package info.kgeorgiy.ja.Gebesht.common;

import java.util.List;

public final class ParsingUtilities {
    private ParsingUtilities() {}

    public static int parseIntegerArg(final String[] args, final int index, List<Integer> defaultValues) {
        if (index >= args.length) {
            if (defaultValues == null) {
                throw new IllegalArgumentException("Missing argument number " + (index + 1));
            } else {
                return defaultValues.get(index - 1);
            }
        }
        try {
            int val = Integer.parseInt(args[index]);
            if (val <= 0) throw new IllegalArgumentException("Invalid argument: " + args[index]);
            return val;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid argument: " + args[index]);
        }
    }
}
