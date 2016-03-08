package org.sourcegenerator;

import org.sourcegenerator.converter.FieldToMatcherConverter;

import java.nio.file.*;

public class Main {
    public static void main(String[] args) {
        try {
            String sourcePath = "/home/benedek/IdeaProjects/test/src/org/example/test/MainData.java";

            Converter converter = new FieldToMatcherConverter("customMatchers.properties");
            // Converter converter = new FieldToBuilderMethodConverter();

            SourceGenerator sourceGenerator = new SourceGenerator(converter);
            String convertedJavaContent = sourceGenerator.generate(sourcePath);

            Files.write(getPath(sourcePath, converter.getName()), convertedJavaContent.getBytes(), StandardOpenOption.CREATE);

            System.out.println(convertedJavaContent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Path getPath(String sourcePath, String type) {
        return Paths.get(sourcePath.substring(0, sourcePath.length()-5) + type + ".java");
    }
}
