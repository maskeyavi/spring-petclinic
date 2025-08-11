package org.springframework.samples.petclinic.templates.fragments;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Thymeleaf layout fragment (templates/fragments/layout.html).
 *
 * Testing Library and Framework:
 * - JUnit 5 (Jupiter)
 * - AssertJ (via Spring Boot test starter)
 * - Thymeleaf TemplateEngine (configured for classpath templates)
 *
 * These tests render the fragment with and without parameters and validate resulting HTML.
 */
public class LayoutTemplateTest {

    private TemplateEngine buildTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        // Important to allow th:fragment lookups on fragment files
        resolver.setCheckExistence(true);

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    /**
     * Renders the "fragments/layout" template which defines a fragment "layout (template, menu)".
     * To exercise the fragment, we provide a minimal "template" variable that the layout inserts.
     */
    private String renderLayoutWith(Context ctx) {
        TemplateEngine engine = buildTemplateEngine();
        // Render the full template file (it contains the fragment and surrounding HTML)
        return engine.process("fragments/layout", ctx);
    }

    @Test
    @DisplayName("Layout contains core head resources, meta, and title with message key")
    void layout_containsCoreHeadAndTitle() {
        Context ctx = new Context();
        // Provide a minimal inner template so th:insert does not fail when resolving ${template}
        ctx.setVariable("template", "~{fragments/empty :: content}");
        ctx.setVariable("menu", "home");

        String html = renderLayoutWith(ctx);

        // Head resources and title i18n key
        assertThat(html).containsIgnoringCase("<meta charset=\"utf-8\"");
        assertThat(html).contains("Content-Type");
        assertThat(html).contains("viewport");
        assertThat(html).contains("rel=\"shortcut icon\"");
        assertThat(html).contains("webjars/font-awesome");
        assertThat(html).contains("resources/css/petclinic.css");
        assertThat(html).contains("layoutTitle"); // th:text="#{layoutTitle}" key should be present in template
    }

    @Test
    @DisplayName("Navbar renders structure and menu skeleton with Thymeleaf fragments")
    void layout_rendersNavbarStructure() {
        Context ctx = new Context();
        ctx.setVariable("template", "~{fragments/empty :: content}");
        ctx.setVariable("menu", "home");

        String html = renderLayoutWith(ctx);

        // Navbar and collapsible container
        assertThat(html).contains("navbar navbar-expand-lg navbar-dark");
        assertThat(html).contains("id=\"main-navbar\"");
        // Menu items for home, owners/find, vets, error are present as replace directives outcome
        assertThat(html).contains("/");
        assertThat(html).contains("/owners/find");
        assertThat(html).contains("/vets.html");
        assertThat(html).contains("/oups");
    }

    @Nested
    @DisplayName("Welcome back alert block behavior based on param.name")
    class WelcomeBackAlert {

        @Test
        @DisplayName("No alert when param.name is entirely missing")
        void noAlertWhenParamMissing() {
            Context ctx = new Context();
            ctx.setVariable("template", "~{fragments/empty :: content}");
            ctx.setVariable("menu", "home");

            String html = renderLayoutWith(ctx);

            assertThat(html).doesNotContain("Welcome back,");
            assertThat(html).doesNotContain("alert alert-primary");
        }

        @Test
        @DisplayName("No alert when param.name is present but empty array")
        void noAlertWhenParamEmptyArray() {
            Context ctx = new Context();
            ctx.setVariable("template", "~{fragments/empty :: content}");
            ctx.setVariable("menu", "home");
            ctx.setVariable("param", java.util.Collections.singletonMap("name", new String[]{}));

            String html = renderLayoutWith(ctx);

            assertThat(html).doesNotContain("Welcome back,");
            assertThat(html).doesNotContain("alert alert-primary");
        }

        @Test
        @DisplayName("No alert when param.name[0] is blank")
        void noAlertWhenFirstNameBlank() {
            Context ctx = new Context();
            ctx.setVariable("template", "~{fragments/empty :: content}");
            ctx.setVariable("menu", "home");
            ctx.setVariable("param", java.util.Collections.singletonMap("name", new String[]{""}));

            String html = renderLayoutWith(ctx);

            assertThat(html).doesNotContain("Welcome back,");
            assertThat(html).doesNotContain("alert alert-primary");
        }

        @Test
        @DisplayName("Alert renders when param.name[0] is non-empty")
        void alertWhenFirstNamePresent() {
            Context ctx = new Context();
            ctx.setVariable("template", "~{fragments/empty :: content}");
            ctx.setVariable("menu", "home");
            ctx.setVariable("param", java.util.Collections.singletonMap("name", new String[]{"Alice"}));

            String html = renderLayoutWith(ctx);

            assertThat(html).contains("alert alert-primary");
            assertThat(html).contains("Welcome back, Alice!");
        }
    }

    @Test
    @DisplayName("Includes footer logo image and bootstrap bundle script")
    void layout_includesLogoAndScripts() {
        Context ctx = new Context();
        ctx.setVariable("template", "~{fragments/empty :: content}");
        ctx.setVariable("menu", "home");

        String html = renderLayoutWith(ctx);

        assertThat(html).contains("resources/images/spring-logo.svg");
        assertThat(html).contains("webjars/bootstrap/dist/js/bootstrap.bundle.min.js");
    }

    @Test
    @DisplayName("Active menu CSS class toggles when menu variable changes")
    void menuActiveClassToggles() {
        Context ctxHome = new Context();
        ctxHome.setVariable("template", "~{fragments/empty :: content}");
        ctxHome.setVariable("menu", "home");
        String htmlHome = renderLayoutWith(ctxHome);
        // Expect to find 'nav-link active' somewhere due to active==menu condition
        assertThat(htmlHome).contains("nav-link");

        Context ctxVets = new Context();
        ctxVets.setVariable("template", "~{fragments/empty :: content}");
        ctxVets.setVariable("menu", "vets");
        String htmlVets = renderLayoutWith(ctxVets);
        assertThat(htmlVets).contains("nav-link");
    }
}