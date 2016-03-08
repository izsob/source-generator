package org.sourcegenerator.converter;

import com.github.javaparser.ASTHelper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.sourcegenerator.Converter;
import org.sourcegenerator.util.VariableSwapVisitor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static java.util.Arrays.asList;
import static org.sourcegenerator.Util.firstLower;
import static org.sourcegenerator.Util.firstUpper;

public class FieldToMatcherConverter extends VoidVisitorAdapter implements Converter {

    private ClassOrInterfaceDeclaration resultClazz;
    private ClassOrInterfaceType resultClazzType;
    private TypeDeclaration sourceClazz;
    private CompilationUnit sourceCompilationUnit;
    private CompilationUnit resultCompilationUnit;
    private ClassOrInterfaceType sourceClazzType;
    private List<MethodDeclaration> methodDeclarations;
    private List<FieldDeclaration> fieldDeclarations;
    private List<IfStmt> safelyMatchConditions;
    private List<ExpressionStmt> descriptionStmts;
    private List<MethodCallExpr> withStmtsObject;
    private List<Parameter> fieldParameters;
    private List<IfStmt> withStmtsFields;
    private Map<String, Expression> customMatchers;

    public FieldToMatcherConverter(String specialMatchers) {
        this.methodDeclarations = new ArrayList<>();
        this.fieldDeclarations = new ArrayList<>();
        this.safelyMatchConditions = new ArrayList<>();
        this.descriptionStmts = new ArrayList<>();
        this.withStmtsObject = new ArrayList<>();
        this.fieldParameters = new ArrayList<>();
        this.withStmtsFields = new ArrayList<>();

        customMatchers = new HashMap<>();
        readCustomMatchers(specialMatchers);
    }

    private void readCustomMatchers(String specialMatchers) {
        if (specialMatchers == null) {
            return;
        }

        InputStream customMatchersInputStream = getClass().getClassLoader().getResourceAsStream(specialMatchers);
        try {
            Properties customMatcherProperties = new Properties();
            customMatchersInputStream = new FileInputStream(specialMatchers);
            customMatcherProperties.load(customMatchersInputStream);
            for (String typeName : customMatcherProperties.stringPropertyNames()) {
                String matcherStr = customMatcherProperties.getProperty(typeName);
                Expression matcherExpression = JavaParser.parseExpression(matcherStr);
                customMatchers.put(typeName, matcherExpression);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        } finally {
            if (customMatchersInputStream != null) {
                try {
                    customMatchersInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    @Override
    public void convert(CompilationUnit sourceCompilationUnit, CompilationUnit resultCompilationUnit) {
        this.resultCompilationUnit = resultCompilationUnit;
        this.resultClazz = (ClassOrInterfaceDeclaration) resultCompilationUnit.getTypes().get(0);
        this.resultClazzType = new ClassOrInterfaceType(resultClazz.getName());
        this.sourceCompilationUnit = sourceCompilationUnit;
        this.sourceClazz = sourceCompilationUnit.getTypes().get(0);
        this.sourceClazzType = new ClassOrInterfaceType(sourceClazz.getName());

        resultClazz.setExtends(getTypeSafeDiagnosingMatcherExtendsList());

        resultCompilationUnit.setImports(sourceCompilationUnit.getImports());
        List<ImportDeclaration> hamcrestImports = getHamcrestImports();
        for (ImportDeclaration hamcrestImport : hamcrestImports) {
            resultCompilationUnit.getImports().add(hamcrestImport);
        }

        visit(sourceCompilationUnit, null);

        for (FieldDeclaration fieldDeclaration : fieldDeclarations) {
            ASTHelper.addMember(resultClazz, fieldDeclaration);
        }

        ASTHelper.addMember(resultClazz, createFactoryMethodWithObject());

        ASTHelper.addMember(resultClazz, createFactoryMethodWithFields());

        ASTHelper.addMember(resultClazz, createBuilderCreatorMethod());

        for (MethodDeclaration methodDeclaration : methodDeclarations) {
            ASTHelper.addMember(resultClazz, methodDeclaration);
        }

        ASTHelper.addMember(resultClazz, createMatchesSafelyMethod());

        ASTHelper.addMember(resultClazz, createDescribeToMethod());

    }

    private List<ClassOrInterfaceType> getTypeSafeDiagnosingMatcherExtendsList() {
        ClassOrInterfaceType tsdm = new ClassOrInterfaceType("TypeSafeDiagnosingMatcher");
        tsdm.getTypeArgs().add(sourceClazzType);
        return asList(tsdm);
    }

    private List<ImportDeclaration> getHamcrestImports() {
        List<ImportDeclaration> imports = new ArrayList<>();

        List<String> hamcrestImportsStr = asList(
                "org.hamcrest.Description",
                "org.hamcrest.Factory",
                "org.hamcrest.Matcher",
                "org.hamcrest.TypeSafeDiagnosingMatcher",
                "org.hamcrest.core.IsAnything",
                "org.hamcrest.core.IsNull");
        for (String importStr : hamcrestImportsStr) {
            imports.add(new ImportDeclaration(new NameExpr(importStr), false, false));
        }

        List<String> hamcrestStaticImportsStr = asList(
                "org.hamcrest.Matchers"
        );
        for (String importStr : hamcrestStaticImportsStr) {
            imports.add(new ImportDeclaration(new NameExpr(importStr), true, true));
        }

        return imports;
    }

    @Override
    public String getName() {
        return "Matcher";
    }

    private MethodDeclaration createFactoryMethodWithObject() {
        // create a method
        MethodDeclaration method = new MethodDeclaration(
                ModifierSet.PUBLIC | ModifierSet.STATIC,
                resultClazzType,
                "is"+firstUpper(sourceClazz.getName()));
        AnnotationExpr factoryAnnotation = new MarkerAnnotationExpr(new NameExpr("Factory"));
        method.getAnnotations().add(factoryAnnotation);

        Parameter expected = new Parameter(sourceClazzType, new VariableDeclaratorId("expected"));
        ASTHelper.addParameter(method, expected);

        // add a body to the method
        BlockStmt block = new BlockStmt();
        method.setBody(block);


        // create new matcher
        String matcherName = firstLower(sourceClazz.getName()) + "Matcher";
        MethodCallExpr newMatcher = new MethodCallExpr(null, "is" + firstUpper(sourceClazz.getName()));
        VariableDeclarator matcherDeclaration = new VariableDeclarator(new VariableDeclaratorId(matcherName), newMatcher);
        VariableDeclarationExpr matcherDeclarationExpression = new VariableDeclarationExpr(resultClazzType, asList(matcherDeclaration));
        ASTHelper.addStmt(block, matcherDeclarationExpression);

        // set expected values
        for (MethodCallExpr withStmt : withStmtsObject) {
            ASTHelper.addStmt(block, withStmt);
        }


        // return with the matcher
        NameExpr matcher = new NameExpr(matcherName);
        ReturnStmt returnStatement = new ReturnStmt(matcher);
        ASTHelper.addStmt(block, returnStatement);

        return method;
    }

    private MethodDeclaration createFactoryMethodWithFields() {
        // create a method
        MethodDeclaration method = new MethodDeclaration(
                ModifierSet.PUBLIC | ModifierSet.STATIC,
                resultClazzType,
                "is"+firstUpper(sourceClazz.getName()));
        AnnotationExpr factoryAnnotation = new MarkerAnnotationExpr(new NameExpr("Factory"));
        method.getAnnotations().add(factoryAnnotation);

        // add parameters
        for (Parameter fieldParameter : fieldParameters) {
            ASTHelper.addParameter(method, fieldParameter);
        }

        // add a body to the method
        BlockStmt block = new BlockStmt();
        method.setBody(block);

        // create new matcher
        String matcherName = firstLower(sourceClazz.getName()) + "Matcher";
        MethodCallExpr newMatcher = new MethodCallExpr(null, "is" + firstUpper(sourceClazz.getName()));
        VariableDeclarator matcherDeclaration = new VariableDeclarator(new VariableDeclaratorId(matcherName), newMatcher);
        VariableDeclarationExpr matcherDeclarationExpression = new VariableDeclarationExpr(resultClazzType, asList(matcherDeclaration));
        ASTHelper.addStmt(block, matcherDeclarationExpression);

        // set expected values
        for (IfStmt withStmtsField : withStmtsFields) {
            ASTHelper.addStmt(block, withStmtsField);
        }

        // return with the matcher
        NameExpr matcher = new NameExpr(matcherName);
        ReturnStmt returnStatement = new ReturnStmt(matcher);
        ASTHelper.addStmt(block, returnStatement);

        return method;
    }

    private MethodDeclaration createBuilderCreatorMethod() {
        // create a method
        MethodDeclaration method = new MethodDeclaration(
                ModifierSet.PUBLIC | ModifierSet.STATIC,
                resultClazzType,
                "is"+firstUpper(sourceClazz.getName()));

        // add a body to the method
        BlockStmt block = new BlockStmt();
        method.setBody(block);

        // return with the builder
        ObjectCreationExpr newMatcher = new ObjectCreationExpr(null, resultClazzType, null);
        ReturnStmt returnStatement = new ReturnStmt(newMatcher);
        ASTHelper.addStmt(block, returnStatement);

        return method;
    }

    private MethodDeclaration createMatchesSafelyMethod() {
        // create a method
        MethodDeclaration method = new MethodDeclaration(
                ModifierSet.PROTECTED,
                new PrimitiveType(PrimitiveType.Primitive.Boolean),
                "matchesSafely");
        AnnotationExpr factoryAnnotation = new MarkerAnnotationExpr(new NameExpr("Override"));
        method.getAnnotations().add(factoryAnnotation);

        Parameter actual = new Parameter(sourceClazzType, new VariableDeclaratorId("actual"));
        ASTHelper.addParameter(method, actual);
        Parameter description = new Parameter(new ClassOrInterfaceType("Description"), new VariableDeclaratorId("description"));
        ASTHelper.addParameter(method, description);

        // add a body to the method
        BlockStmt block = new BlockStmt();
        method.setBody(block);

        for (IfStmt safelyMatchCondition : safelyMatchConditions) {
            ASTHelper.addStmt(block, safelyMatchCondition);
        }

        // return with the builder
        ReturnStmt returnStatement = new ReturnStmt(new BooleanLiteralExpr(true));
        ASTHelper.addStmt(block, returnStatement);

        return method;
    }

    private MethodDeclaration createDescribeToMethod() {
        // create a method
        MethodDeclaration method = new MethodDeclaration(
                ModifierSet.PUBLIC,
                ASTHelper.VOID_TYPE,
                "describeTo");
        AnnotationExpr factoryAnnotation = new MarkerAnnotationExpr(new NameExpr("Override"));
        method.getAnnotations().add(factoryAnnotation);

        Parameter description = new Parameter(new ClassOrInterfaceType("Description"), new VariableDeclaratorId("description"));
        ASTHelper.addParameter(method, description);

        // add a body to the method
        BlockStmt block = new BlockStmt();
        method.setBody(block);

        for (ExpressionStmt descriptionStmt : descriptionStmts) {
            ASTHelper.addStmt(block, descriptionStmt);
        }

        return method;
    }

    @Override
    public void visit(FieldDeclaration fieldDeclaration, Object arg) {
        List<VariableDeclarator> variables = fieldDeclaration.getVariables();

        for (VariableDeclarator variable : variables) {
            String withName = variable.getId().getName();
            Type withType = fieldDeclaration.getType();

            FieldDeclaration matcherField = createMatcherVariable(withName, withType);
            fieldDeclarations.add(matcherField);

            MethodCallExpr withObjectCall = createWithObjectCall(withName);
            withStmtsObject.add(withObjectCall);

            Parameter parameter = createParameters(withName, withType);
            fieldParameters.add(parameter);

            IfStmt withFieldCall = createWithFieldCall(withName, withType);
            withStmtsFields.add(withFieldCall);

            MethodDeclaration matcherBuilderMethodDeclaration = createMatcherBuilderMethod(withName, withType);
            methodDeclarations.add(matcherBuilderMethodDeclaration);

            IfStmt safelyMatchCondition = createSafelyMatchCondition(withName);
            safelyMatchConditions.add(safelyMatchCondition);

        }

    }

    private Parameter createParameters(String withName, Type withType) {
        VariableDeclaratorId variable = new VariableDeclaratorId(withName);
        return new Parameter(withType, variable);
    }

    private MethodCallExpr createWithObjectCall(String withName) {
        String matcherName = firstLower(sourceClazz.getName()) + "Matcher";
        NameExpr matcherNameExpr = new NameExpr(matcherName);

        Expression expected = new FieldAccessExpr(new NameExpr("expected"), withName);
        MethodCallExpr setExpected = new MethodCallExpr(matcherNameExpr, "with" + firstUpper(withName), asList(expected));
        return setExpected;
    }

    private IfStmt createWithFieldCall(String withName, Type withType) {
        String matcherName = firstLower(sourceClazz.getName()) + "Matcher";
        NameExpr matcherNameExpr = new NameExpr(matcherName);

        BinaryExpr condition = new BinaryExpr(new NameExpr(withName), new NullLiteralExpr(), BinaryExpr.Operator.notEquals);
        MethodCallExpr thenExpr = new MethodCallExpr(matcherNameExpr, "with" + firstUpper(withName), Arrays.<Expression>asList(new NameExpr(withName)));
        BlockStmt thenStmt = new BlockStmt(Arrays.<Statement>asList(new ExpressionStmt(thenExpr)));
        IfStmt ifStmt = new IfStmt(condition, thenStmt, null);

        return ifStmt;
    }

    private IfStmt createSafelyMatchCondition(String withName) {
        FieldAccessExpr actualField = new FieldAccessExpr(new NameExpr("actual"), withName);
        MethodCallExpr matchesExpression = new MethodCallExpr(new NameExpr(withName + "Matcher"), "matches", Arrays.<Expression>asList(actualField));
        UnaryExpr matcherCondition = new UnaryExpr(matchesExpression, UnaryExpr.Operator.not);

        generateExpectedDescribeToStatement(withName);
        MethodCallExpr appendText = generateVariableDescriptionStatement(withName);
        ExpressionStmt actualAppendTextStmt = new ExpressionStmt(appendText);
        ExpressionStmt actualDescriptionStmt = generateActualDescribeToStatement(withName);

        ReturnStmt returnFalseStmt = new ReturnStmt(new BooleanLiteralExpr(false));
        BlockStmt matcherThen = new BlockStmt(asList(actualAppendTextStmt, actualDescriptionStmt, returnFalseStmt));
        return new IfStmt(matcherCondition, matcherThen, null);
    }

    private ExpressionStmt generateActualDescribeToStatement(String withName) {
        NameExpr fieldMatcher = new NameExpr(withName + "Matcher");
        MethodCallExpr appendDescriptionOf = new MethodCallExpr(fieldMatcher, "describeMismatch", Arrays.<Expression>asList(new NameExpr("actual."+withName), new NameExpr("description")));
        ExpressionStmt descriptionStmt = new ExpressionStmt(appendDescriptionOf);
        return descriptionStmt;
    }

    private void generateExpectedDescribeToStatement(String withName) {
        MethodCallExpr appendText = generateVariableDescriptionStatement(withName);
        MethodCallExpr appendDescriptionOf = new MethodCallExpr(appendText, "appendDescriptionOf", Arrays.<Expression>asList(new NameExpr(withName + "Matcher")));
        ExpressionStmt descriptionStmt = new ExpressionStmt(appendDescriptionOf);
        descriptionStmts.add(descriptionStmt);
    }

    private MethodCallExpr generateVariableDescriptionStatement(String withName) {
        NameExpr description = new NameExpr("description");
        StringLiteralExpr variableNameStr = new StringLiteralExpr(" " + withName + ": ");
        return new MethodCallExpr(description, "appendText", Arrays.<Expression>asList(variableNameStr));
    }

    private FieldDeclaration createMatcherVariable(String withName, Type withType) {
        ClassOrInterfaceType matcherVariableType = new ClassOrInterfaceType(null, "Matcher");
        matcherVariableType.getTypeArgs().add(withType);
        ClassOrInterfaceType anythingMatcherType = new ClassOrInterfaceType("IsAnything");
        anythingMatcherType.getTypeArgs().add(withType);
        VariableDeclarator matcherVariable = new VariableDeclarator(
                new VariableDeclaratorId(withName + "Matcher"),
                new ObjectCreationExpr(null, anythingMatcherType, null));
        return new FieldDeclaration(
                ModifierSet.PRIVATE,
                matcherVariableType,
                asList(matcherVariable));
    }

    public MethodDeclaration createMatcherBuilderMethod(String name, Type type) {
        // create method
        MethodDeclaration method = new MethodDeclaration(ModifierSet.PUBLIC, resultClazzType, "with" + firstUpper(name));

        // add parameter to method
        Parameter param = ASTHelper.createParameter(type, name);
        ASTHelper.addParameter(method, param);

        // add body to method
        BlockStmt block = new BlockStmt();
        method.setBody(block);

        // add matcher variable assignment
        NameExpr fieldVariableExpression = new NameExpr(name);
        Expression value = null;
        if (customMatchers.containsKey(type.toStringWithoutComments())) {
            value = customMatchers.get(type.toStringWithoutComments());
            swapVariable(value, fieldVariableExpression);
        } else {
            value = new MethodCallExpr(null, "is", Arrays.<Expression>asList(fieldVariableExpression));
        }
        NameExpr variable = new NameExpr(name + "Matcher");
        AssignExpr assignExpr = new AssignExpr(variable, value, AssignExpr.Operator.assign);
        ASTHelper.addStmt(block, assignExpr);

        // return with the builder matcher
        ThisExpr thisExpr = new ThisExpr();
        ReturnStmt returnStatement = new ReturnStmt(thisExpr);
        ASTHelper.addStmt(block, returnStatement);

        return method;
    }

    private void swapVariable(Expression customExpression, NameExpr fieldVariableExpression) {
        if (customExpression instanceof MethodCallExpr) {
            new VariableSwapVisitor(fieldVariableExpression).visit((MethodCallExpr) customExpression, null);
        } else if (customExpression instanceof ConditionalExpr) {
            new VariableSwapVisitor(fieldVariableExpression).visit((ConditionalExpr) customExpression, null);
        }

    }

}
