package be.imgn.gaia.docs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SiteGeneratorTest {

    @TempDir
    Path directory;

    private Path markdown;
    private Path theme;
    private Path output;

    @BeforeEach
    void createTheme() throws IOException {
        markdown = Files.createDirectories(directory.resolve("markdown"));
        theme = Files.createDirectories(directory.resolve("theme"));
        output = directory.resolve("output");
        write(theme.resolve("template.html"), "<title>{{title}}</title>\n<nav>{{navigation}}</nav>\n{{content}}");
        write(theme.resolve("style.css"), "body {}");
    }

    @Test
    void writesEachPageInTheTemplate() throws IOException {
        page("index.md", "# Gaia\n\nJava libraries.");
        page("core.md", "# Core & more\n\nExtensions.");
        pages("index.md", "core.md");

        SiteGenerator.generate(markdown, theme, output);

        assertThat(output.resolve("index.html"))
                .content(UTF_8)
                .contains("<title>Gaia</title>")
                .contains("<li><a href=\"index.html\" aria-current=\"page\">Gaia</a></li>\n"
                        + "<li><a href=\"core.html\">Core &amp; more</a></li>")
                .contains("<p>Java libraries.</p>");
        assertThat(output.resolve("core.html"))
                .content(UTF_8)
                .contains("<title>Core &amp; more · Gaia</title>")
                .contains("<li><a href=\"core.html\" aria-current=\"page\">");
        assertThat(output.resolve("style.css")).hasContent("body {}");
        assertThat(output.resolve("template.html")).doesNotExist();
    }

    @Test
    void identifiesHeadingsAsGitHubDoes() throws IOException {
        page("index.md", "# Gaia\n\n## Splitting `text`, fast!\n\n## Splitting `text`, fast!\n\n### Über_alles\n");
        pages("index.md");

        SiteGenerator.generate(markdown, theme, output);

        assertThat(output.resolve("index.html"))
                .content(UTF_8)
                .contains("<h1 id=\"gaia\">Gaia</h1>")
                .contains("<h2 id=\"splitting-text-fast\">")
                .contains("<h2 id=\"splitting-text-fast-1\">")
                .contains("<h3 id=\"über_alles\">");
    }

    @Test
    void resolvesLinks() throws IOException {
        page("index.md", """
                # Gaia

                [Core](core.md), [collections](core.md#collections), [above](#gaia), \
                [web](https://example.com/a.md), [Splitter](api:be.imgn.gaia.core.text.Splitter), \
                [Entry](api:be.imgn.gaia.core.collect.Multiset.Entry)
                """);
        page("core.md", "# Core\n\n## Collections\n");
        pages("index.md", "core.md");

        SiteGenerator.generate(markdown, theme, output);

        assertThat(output.resolve("index.html"))
                .content(UTF_8)
                .contains("<a href=\"core.html\">Core</a>")
                .contains("<a href=\"core.html#collections\">collections</a>")
                .contains("<a href=\"#gaia\">above</a>")
                .contains("<a href=\"https://example.com/a.md\">web</a>")
                .contains("<a href=\"api/latest/be.imgn.gaia.core/be/imgn/gaia/core/text/Splitter.html\">Splitter</a>")
                .contains("<a href=\"api/latest/be.imgn.gaia.core/be/imgn/gaia/core/collect/Multiset.Entry.html\">");
    }

    @Test
    void reportsEveryProblem() throws IOException {
        page("index.md", """
                # Gaia

                [a](missing.md) [b](core.md#nowhere) [c](#nowhere) [d](other.txt)
                [e](api:be.imgn.gaia.core.text.Nothing) [f](api:java.lang.String)
                [g](api:be.imgn.gaia.memfs.internal.MemoryFileSystemProvider)
                ![h](picture.png)
                """);
        page("core.md", "No title.");
        page("unlisted.md", "# Unlisted");
        pages("index.md", "core.md", "core.md", "absent.md", "notes.txt");

        assertThatIllegalStateException()
                .isThrownBy(() -> SiteGenerator.generate(markdown, theme, output))
                .withMessage("""
                        The guide has problems:
                          core.md: no level-1 heading for the title
                          pages.txt: core.md is listed twice
                          pages.txt: no page absent.md
                          pages.txt: no page notes.txt
                          unlisted.md: not listed in pages.txt
                          index.md: broken link to missing.md
                          index.md: no heading nowhere in core.md
                          index.md: no heading nowhere in index.md
                          index.md: broken link to other.txt
                          index.md: no API documentation for be.imgn.gaia.core.text.Nothing
                          index.md: no API documentation for java.lang.String
                          index.md: no API documentation for be.imgn.gaia.memfs.internal.MemoryFileSystemProvider
                          index.md: images are not supported: picture.png""");
        assertThat(output).doesNotExist();
    }

    @Test
    void requiresAHomePage() throws IOException {
        page("core.md", "# Core");
        pages("core.md");

        assertThatIllegalStateException()
                .isThrownBy(() -> SiteGenerator.generate(markdown, theme, output))
                .withMessage("The guide has problems:\n  pages.txt: no index.md");
    }

    private void page(String name, String content) throws IOException {
        write(markdown.resolve(name), content);
    }

    private void pages(String... names) throws IOException {
        write(markdown.resolve("pages.txt"), "# The pages, in order\n\n" + String.join("\n", names) + "\n");
    }

    private static void write(Path file, String content) throws IOException {
        Files.writeString(file, content, UTF_8);
    }
}
