package be.imgn.gaia.processing.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class NameAllocatorTest {

    @Test
    void handsOutTheSuggestionWhenItIsFree() {
        NameAllocator names = new NameAllocator();

        assertThat(names.newName("count", "first")).isEqualTo("count");
        assertThat(names.newName("total", "second")).isEqualTo("total");
        assertThat(names.get("first")).isEqualTo("count");
        assertThat(names.get("second")).isEqualTo("total");
    }

    @Test
    void appendsUnderscoresUntilTheNameIsFree() {
        NameAllocator names = new NameAllocator();

        assertThat(names.newName("value")).isEqualTo("value");
        assertThat(names.newName("value", 1)).isEqualTo("value_");
        assertThat(names.newName("value")).isEqualTo("value__");
        assertThat(names.newName("value_")).isEqualTo("value___");
        assertThat(names.get(1)).isEqualTo("value_");
    }

    @Test
    void avoidsKeywords() {
        NameAllocator names = new NameAllocator();

        assertThat(names.newName("class", "type")).isEqualTo("class_");
        assertThat(names.newName("new")).isEqualTo("new_");
        assertThat(names.newName("_")).isEqualTo("__");
        assertThat(names.get("type")).isEqualTo("class_");
    }

    @Test
    void makesSuggestionsValidIdentifiers() {
        assertThat(NameAllocator.toJavaIdentifier("first-name")).isEqualTo("first_name");
        assertThat(NameAllocator.toJavaIdentifier("2nd")).isEqualTo("_2nd");
        assertThat(NameAllocator.toJavaIdentifier("#tag")).isEqualTo("_tag");
        assertThat(NameAllocator.toJavaIdentifier("x 1")).isEqualTo("x_1");
        // One underscore for a character outside the Basic Multilingual Plane, not one per char.
        assertThat(NameAllocator.toJavaIdentifier("rocket🚀launch")).isEqualTo("rocket_launch");
        assertThat(NameAllocator.toJavaIdentifier("été")).isEqualTo("été");
        assertThat(NameAllocator.toJavaIdentifier("")).isEqualTo("_");
        assertThat(new NameAllocator().newName("2nd-try")).isEqualTo("_2nd_try");
    }

    @Test
    void refusesATagTwice() {
        NameAllocator names = new NameAllocator();
        names.newName("left", "side");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> names.newName("right", "side"))
                .withMessageContaining("left")
                .withMessageContaining("right");
    }

    @Test
    void refusesAnUnknownTag() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new NameAllocator().get("missing"))
                .withMessageContaining("missing");
    }

    @Test
    void givesNestedScopesTheirOwnNames() {
        NameAllocator outer = new NameAllocator();
        outer.newName("value", "outer");

        NameAllocator first = outer.copy();
        NameAllocator second = outer.copy();

        assertThat(first.newName("value", "inner")).isEqualTo("value_");
        assertThat(first.newName("index", "loop")).isEqualTo("index");
        assertThat(second.newName("index", "inner")).isEqualTo("index");
        assertThat(second.newName("value", "loop")).isEqualTo("value_");
        assertThat(first.get("outer")).isEqualTo("value");
        assertThat(outer.newName("index")).isEqualTo("index");
        assertThatIllegalArgumentException().isThrownBy(() -> outer.get("inner"));
    }
}
