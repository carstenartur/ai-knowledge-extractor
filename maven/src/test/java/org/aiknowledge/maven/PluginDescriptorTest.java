package org.aiknowledge.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class PluginDescriptorTest {
    @Test
    void descriptorDefinesAllExpectedGoals() throws Exception {
        Set<String> goals = goalNames(document());
        assertEquals(Set.of("generate", "analyze", "optimize", "benchmark", "check", "help"), goals);
    }

    @Test
    void operationGoalsExposeAllSharedParameters() throws Exception {
        Document document = document();
        Set<String> expectedParameters = sharedParameters();
        for (String goal : Set.of("generate", "analyze", "optimize", "benchmark", "check")) {
            Set<String> actual = parametersForGoal(document, goal);
            assertEquals(expectedParameters, actual, "parameter mismatch for goal " + goal);
        }
    }

    @Test
    void helpGoalExposesGoalAndDetailParameters() throws Exception {
        Set<String> parameters = parametersForGoal(document(), "help");
        assertEquals(Set.of("goal", "detail"), parameters);
    }

    private static Set<String> sharedParameters() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/aiknowledge/maven/AbstractAiKnowledgeMojo.java"));
        return source
                .lines()
                .map(String::trim)
                .filter(line -> line.startsWith("protected "))
                .filter(line -> line.endsWith(";"))
                .filter(line -> !line.contains("("))
                .map(line -> line.replace(";", ""))
                .map(line -> line.substring(line.lastIndexOf(' ') + 1))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> goalNames(Document document) {
        NodeList nodes = document.getElementsByTagName("mojo");
        Set<String> goals = new LinkedHashSet<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            goals.add(textOf(node, "goal"));
        }
        return goals;
    }

    private static Set<String> parametersForGoal(Document document, String goalName) {
        NodeList nodes = document.getElementsByTagName("mojo");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            if (!goalName.equals(textOf(node, "goal"))) continue;
            NodeList parameterNodes = node.getElementsByTagName("parameter");
            Set<String> parameters = new LinkedHashSet<>();
            for (int j = 0; j < parameterNodes.getLength(); j++) {
                Element parameter = (Element) parameterNodes.item(j);
                parameters.add(textOf(parameter, "name"));
            }
            return parameters;
        }
        throw new AssertionError("Goal not found in descriptor: " + goalName);
    }

    private static Document document() throws Exception {
        try (InputStream stream = PluginDescriptorTest.class.getResourceAsStream("/META-INF/maven/plugin.xml")) {
            assertTrue(stream != null, "plugin descriptor should be available on test classpath");
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
        }
    }

    private static String textOf(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0) == null) return "";
        return nodes.item(0).getTextContent();
    }
}
