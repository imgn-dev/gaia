package be.imgn.gaia.core.random.dice;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Thrown when a dice expression is rolled, or its chances computed, while some of its variables have no value.
 *
 * @since 0.2.0
 * @see DiceExpression#bind(String, int)
 */
public final class UnresolvedVariableException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    // An unmodifiable LinkedHashSet of strings, which is serializable.
    @SuppressWarnings("serial")
    private final Set<String> variables;

    UnresolvedVariableException(Set<String> variables) {
        super("Unresolved variables: " + String.join(", ", variables));
        this.variables = Collections.unmodifiableSet(new LinkedHashSet<>(variables));
    }

    /**
     * Returns the names of the variables that have no value, in the order they first appear in the expression.
     *
     * @return the names of the variables that have no value, in the order they first appear in the expression
     */
    public Set<String> variables() {
        return variables;
    }
}
