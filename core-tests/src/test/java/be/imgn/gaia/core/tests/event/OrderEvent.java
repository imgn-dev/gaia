package be.imgn.gaia.core.tests.event;

/** An event of the tests, with a sealed hierarchy as users may write theirs. */
sealed interface OrderEvent permits OrderPlaced, OrderCancelled {}
