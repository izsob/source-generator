package org.sourcegenerator.converter;

import com.github.javaparser.ASTHelper;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.sourcegenerator.Converter;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.sourcegenerator.Util.firstLower;
import static org.sourcegenerator.Util.firstUpper;

public class FieldToBuilderMethodConverter extends VoidVisitorAdapter implements Converter {

    private ClassOrInterfaceDeclaration resultClazz;
    private ClassOrInterfaceType resultClazzType;
    private List<MethodDeclaration> methodDeclarations;
    private TypeDeclaration sourceClazz;
    private CompilationUnit sourceCompilationUnit;
    private CompilationUnit resultCompilationUnit;
    private ClassOrInterfaceType sourceClazzType;

    public FieldToBuilderMethodConverter() {
        this.methodDeclarations = new ArrayList<>();
    }

    @Override
    public void convert(CompilationUnit sourceCompilationUnit, CompilationUnit resultCompilationUnit) {
        this.resultCompilationUnit = resultCompilationUnit;
        this.resultClazz = (ClassOrInterfaceDeclaration) resultCompilationUnit.getTypes().get(0);
        this.resultClazzType = new ClassOrInterfaceType(resultClazz.getName());
        this.sourceCompilationUnit = sourceCompilationUnit;
        this.sourceClazz = sourceCompilationUnit.getTypes().get(0);
        this.sourceClazzType = new ClassOrInterfaceType(sourceClazz.getName());

        resultCompilationUnit.setImports(sourceCompilationUnit.getImports());

        createDataField(sourceCompilationUnit);
        ASTHelper.addMember(resultClazz, createBuildMethod());


        visit(sourceCompilationUnit, null);

        for (MethodDeclaration methodDeclaration : methodDeclarations) {
            ASTHelper.addMember(resultClazz, methodDeclaration);
        }

    }

    @Override
    public String getName() {
        return "Builder";
    }

    @Override
    public void visit(FieldDeclaration fieldDeclaration, Object arg) {
        List<VariableDeclarator> variables = fieldDeclaration.getVariables();

        for (VariableDeclarator variable : variables) {
            String withName = variable.getId().getName();
            Type withType = fieldDeclaration.getType();

//            NameExpr importClass = getImportForType(withType);
//            if (importClass != null) {
//                ImportDeclaration importDeclaration = new ImportDeclaration(importClass, false, false);
//                resultCompilationUnit.getImports().add(importDeclaration);
//            }

            MethodDeclaration methodDeclaration = createMethod(withName, withType);
            methodDeclarations.add(methodDeclaration);

        }

    }

    private NameExpr getImportForType(Type withType) {
        NameExpr importClass = null;
        if ("BigDecimal".equals(withType.toString())) {
            importClass = new NameExpr("java.math.BigDecimal");
        } else if (withType.toString().startsWith("List<")) {
            importClass = new NameExpr("java.util.List");
        }
        return importClass;
    }

    private void createDataField(CompilationUnit sourceCompilationUnit) {
        FieldDeclaration builderField = new FieldDeclaration(
                ModifierSet.PRIVATE,
                sourceClazzType,
                asList(new VariableDeclarator(new VariableDeclaratorId(firstLower(sourceClazz.getName())))));
        ASTHelper.addMember(resultClazz, builderField);
    }

    private MethodDeclaration createBuildMethod() {
        // create a method
        MethodDeclaration method = new MethodDeclaration(ModifierSet.PUBLIC, sourceClazzType, "build");

        // add a body to the method
        BlockStmt block = new BlockStmt();
        method.setBody(block);

        // return with the builder
        ThisExpr thisExpr = new ThisExpr();
        FieldAccessExpr fieldAccessExpr = new FieldAccessExpr(thisExpr, firstLower(sourceClazz.getName()));
        ReturnStmt returnStatement = new ReturnStmt(fieldAccessExpr);
        ASTHelper.addStmt(block, returnStatement);

        return method;
    }

    private MethodDeclaration createMethod(String name, Type type) {
        // create a method
        MethodDeclaration method = new MethodDeclaration(ModifierSet.PUBLIC, resultClazzType, "with" + firstUpper(name));


        // add parameter to the method
        Parameter param = ASTHelper.createParameter(type, name);
        ASTHelper.addParameter(method, param);

        // add body to the method
        BlockStmt block = new BlockStmt();
        method.setBody(block);

        // add variable assignment
        NameExpr dataClassVariable = new NameExpr(firstLower(sourceClazz.getName()));
        FieldAccessExpr field = new FieldAccessExpr(dataClassVariable, name);
        NameExpr target = new NameExpr(name);
        AssignExpr assignExpr = new AssignExpr(field, target, AssignExpr.Operator.assign);
        ASTHelper.addStmt(block, assignExpr);

        // return with the builder
        ThisExpr thisExpr = new ThisExpr();
        ReturnStmt returnStatement = new ReturnStmt(thisExpr);
        ASTHelper.addStmt(block, returnStatement);

        return method;
    }

}
