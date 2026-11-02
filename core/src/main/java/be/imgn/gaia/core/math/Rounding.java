package be.imgn.gaia.core.math;

import java.math.RoundingMode;

/** How a rounding mode rounds an inexact result. */
final class Rounding {

    private Rounding() {}

    /**
     * Returns whether an inexact result, rounded toward zero, is to be moved one away from zero.
     *
     * @param mode the rounding mode
     * @param negative whether the exact result is negative
     * @param halfComparison the sign of the distance from the exact result to the one rounded toward zero, minus one
     *     half: negative if it's less than one half, zero if it's one half, positive if it's more
     * @param odd whether the result rounded toward zero is odd
     * @throws ArithmeticException if {@code mode} is {@link RoundingMode#UNNECESSARY}
     */
    static boolean awayFromZero(RoundingMode mode, boolean negative, int halfComparison, boolean odd) {
        return switch (mode) {
            case UNNECESSARY -> throw new ArithmeticException("Rounding necessary");
            case DOWN -> false;
            case UP -> true;
            case FLOOR -> negative;
            case CEILING -> !negative;
            case HALF_DOWN -> halfComparison > 0;
            case HALF_UP -> halfComparison >= 0;
            case HALF_EVEN -> halfComparison > 0 || (halfComparison == 0 && odd);
        };
    }
}
