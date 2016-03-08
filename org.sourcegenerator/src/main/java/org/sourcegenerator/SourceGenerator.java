package org.sourcegenerator;

import com.github.javaparser.ASTHelper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.google.common.base.Preconditions;

import java.io.FileInputStream;
import java.util.List;

public class SourceGenerator {

    private Converter converter;
    private CompilationUnit sourceCompilationUnit;
    private CompilationUnit resultCompilationUnit;
    private ClassOrInterfaceDeclaration resultClazz;

    public SourceGenerator(Converter converter) {
        this.converter = converter;
    }

    public String generate(String path) throws Exception {
        parseFile(path);

        createResultCompilationUnit();

        convertSource();

        return resultCompilationUnit.toString();
    }

    private void parseFile(String path) throws Exception {
        FileInputStream in = new FileInputStream(path);

        try {
            sourceCompilationUnit = JavaParser.parse(in);
        } finally {
            in.close();
        }

    }

    private void createResultCompilationUnit() {
        resultCompilationUnit = new CompilationUnit();

        resultCompilationUnit.setPackage(sourceCompilationUnit.getPackage());

        List<TypeDeclaration> clazzes = sourceCompilationUnit.getTypes();
        Preconditions.checkState(clazzes.size() == 1);
        String className = clazzes.get(0).getName();

        resultClazz = new ClassOrInterfaceDeclaration(ModifierSet.PUBLIC, false, className + converter.getName());
        ASTHelper.addTypeDeclaration(resultCompilationUnit, resultClazz);
    }

    private void convertSource() {
        converter.convert(sourceCompilationUnit, resultCompilationUnit);
    }

}
