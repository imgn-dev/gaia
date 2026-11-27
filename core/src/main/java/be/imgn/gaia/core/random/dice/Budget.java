package be.imgn.gaia.core.random.dice;

/** How much work the computation of chances may still take, counted in steps of about the same cost. */
final class Budget {

    private final long limit;
    private long left;

    Budget(long limit) {
        this.limit = limit;
        this.left = limit;
    }

    /** Takes steps off this budget, and fails if there aren't enough left. */
    void spend(long steps) {
        left -= steps;
        if (left < 0) {
            throw new ArithmeticException(
                    "The chances of this expression take more than " + limit + " steps to compute");
        }
    }
}
