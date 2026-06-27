package org.aiknowledge.maven;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class HelpGoal extends AbstractMojo {
    @Parameter(property = "goal")
    private String goal;

    @Parameter(property = "detail", defaultValue = "false")
    private boolean detail;

    @Override
    public void execute() throws MojoExecutionException {
        List<MojoMetadata> mojos = readMojos();
        String requestedGoal = firstNonBlank(goal, System.getProperty("goal"));
        boolean showDetail = detail || Boolean.parseBoolean(System.getProperty("detail", "false"));
        if (requestedGoal != null) {
            String normalizedGoal = requestedGoal.trim().toLowerCase(Locale.ROOT);
            MojoMetadata selected = null;
            for (MojoMetadata mojo : mojos) {
                if (mojo.goal.equalsIgnoreCase(normalizedGoal)) {
                    selected = mojo;
                    break;
                }
            }
            if (selected == null) {
                throw new MojoExecutionException("Unknown goal: " + requestedGoal);
            }
            printMojo(selected, true);
            return;
        }
        getLog().info("AI Knowledge Maven Plugin goals:");
        for (MojoMetadata mojo : mojos) {
            if ("help".equals(mojo.goal)) continue;
            printMojo(mojo, showDetail);
        }
        getLog().info("Use -Dgoal=<goal> -Ddetail=true to show one goal with parameters.");
    }

    static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return null;
    }

    private List<MojoMetadata> readMojos() throws MojoExecutionException {
        try (InputStream stream = HelpGoal.class.getResourceAsStream("/META-INF/maven/plugin.xml")) {
            if (stream == null) throw new MojoExecutionException("Plugin descriptor not found");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder().parse(stream);
            NodeList mojoNodes = document.getElementsByTagName("mojo");
            List<MojoMetadata> result = new ArrayList<>();
            for (int i = 0; i < mojoNodes.getLength(); i++) {
                Element mojo = (Element) mojoNodes.item(i);
                MojoMetadata metadata = new MojoMetadata();
                metadata.goal = textOf(mojo, "goal");
                metadata.description = textOf(mojo, "description");
                metadata.phase = textOf(mojo, "phase");
                metadata.parameters = readParameters(mojo);
                result.add(metadata);
            }
            return result;
        } catch (MojoExecutionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MojoExecutionException("Failed to read plugin descriptor", ex);
        }
    }

    private static List<ParameterMetadata> readParameters(Element mojo) {
        Map<String, String> defaults = new LinkedHashMap<>();
        NodeList configuration = mojo.getElementsByTagName("configuration");
        if (configuration.getLength() > 0) {
            NodeList defaultNodes = ((Element) configuration.item(0)).getChildNodes();
            for (int i = 0; i < defaultNodes.getLength(); i++) {
                if (!(defaultNodes.item(i) instanceof Element element)) continue;
                defaults.put(element.getTagName(), element.getAttribute("default-value"));
            }
        }

        List<ParameterMetadata> parameters = new ArrayList<>();
        NodeList parameterNodes = mojo.getElementsByTagName("parameter");
        for (int i = 0; i < parameterNodes.getLength(); i++) {
            Element parameter = (Element) parameterNodes.item(i);
            ParameterMetadata metadata = new ParameterMetadata();
            metadata.name = textOf(parameter, "name");
            metadata.type = textOf(parameter, "type");
            metadata.required = Boolean.parseBoolean(textOf(parameter, "required"));
            metadata.defaultValue = defaults.get(metadata.name);
            parameters.add(metadata);
        }
        return parameters;
    }

    private void printMojo(MojoMetadata mojo, boolean includeParameters) {
        String phase = (mojo.phase == null || mojo.phase.isBlank()) ? "none" : mojo.phase;
        getLog().info("  " + mojo.goal + " (phase: " + phase + ") - " + mojo.description);
        if (!includeParameters) return;
        if (mojo.parameters.isEmpty()) {
            getLog().info("    (no configurable parameters)");
            return;
        }
        for (ParameterMetadata parameter : mojo.parameters) {
            String defaultValue = parameter.defaultValue == null || parameter.defaultValue.isBlank() ? "-" : parameter.defaultValue;
            getLog().info("    - " + parameter.name + " [" + parameter.type + "] required=" + parameter.required + ", default=" + defaultValue);
        }
    }

    private static String textOf(Element element, String name) {
        NodeList nodes = element.getElementsByTagName(name);
        if (nodes.getLength() == 0 || nodes.item(0) == null) return "";
        return nodes.item(0).getTextContent();
    }

    private static final class MojoMetadata {
        private String goal;
        private String description;
        private String phase;
        private List<ParameterMetadata> parameters;
    }

    private static final class ParameterMetadata {
        private String name;
        private String type;
        private boolean required;
        private String defaultValue;
    }
}
