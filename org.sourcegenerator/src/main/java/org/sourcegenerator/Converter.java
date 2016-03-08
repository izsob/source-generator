package org.sourcegenerator;

import com.github.javaparser.ast.CompilationUnit;

public interface Converter {

    public void convert(CompilationUnit sourceCompilationUnit, CompilationUnit resultClazz);
    public String getName();

}
