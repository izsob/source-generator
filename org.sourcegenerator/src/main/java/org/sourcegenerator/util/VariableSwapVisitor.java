package org.sourcegenerator.util;

import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class VariableSwapVisitor extends VoidVisitorAdapter {

    private final NameExpr newExpression;

    public VariableSwapVisitor(NameExpr newExpression) {
        this.newExpression = newExpression;
    }

    @Override
    public void visit(NameExpr nameExpr, Object arg) {
        nameExpr.setName(newExpression.getName());
    }
}
