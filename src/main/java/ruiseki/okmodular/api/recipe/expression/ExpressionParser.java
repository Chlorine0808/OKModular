package ruiseki.okmodular.api.recipe.expression;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ComparisonCondition;
import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.condition.ICondition;
import ruiseki.okmodular.api.condition.OpAnd;
import ruiseki.okmodular.api.condition.OpNot;
import ruiseki.okmodular.api.condition.OpOr;

/**
 * A simple recursive descent parser for expressions and conditions.
 * Supports arithmetic, comparison, variables (day, time, moon), and nbt('key')
 * function.
 */
public class ExpressionParser {

    private final String input;
    private int pos = -1, ch;

    public ExpressionParser(String input) {
        this.input = input;
    }

    private void nextChar() {
        ch = (++pos < input.length()) ? input.charAt(pos) : -1;
    }

    private boolean isSpace(int c) {
        return c == ' ' || c == '\n' || c == '\r' || c == '\t';
    }

    private boolean eat(int charToEat) {
        while (isSpace(ch)) nextChar();
        if (ch == charToEat) {
            nextChar();
            return true;
        }
        return false;
    }

    public RecipeScriptException error(String message) {
        return new RecipeScriptException(input, Math.max(0, pos), message);
    }

    public Object parse() {
        nextChar();
        Object x = parseLogicalOr();
        while (isSpace(ch)) nextChar();
        if (pos < input.length()) throw error("Unexpected token: '" + (char) ch + "'");
        return x;
    }

    public IExpression parseExpression() {
        Object res = parse();
        if (res instanceof IExpression expression) return expression;
        throw error("Expected numeric expression, got action or condition");
    }

    public IAction parseAction() {
        Object res = parse();
        if (res instanceof IAction action) return action;
        if (res instanceof IExpression expression) {
            return new IAction() {

                @Override
                public void execute(ConditionContext context) {
                    expression.evaluate(context);
                }
            };
        }
        throw error("Expected action or expression");
    }

    // 1. OR: x || y
    private Object parseLogicalOr() {
        Object x = parseLogicalAnd();
        while (eat('|')) {
            if (!eat('|')) throw error("Expected '||'");
            Object y = parseLogicalAnd();
            if (x instanceof ICondition cx && y instanceof ICondition cy) {
                List<ICondition> children = new ArrayList<>();
                children.add(cx);
                children.add(cy);
                x = new OpOr(children);
            } else {
                throw error("OR (||) requires condition operands");
            }
        }
        return x;
    }

    // 2. AND: x && y
    private Object parseLogicalAnd() {
        Object x = parseComparison();
        while (eat('&')) {
            if (!eat('&')) throw error("Expected '&&'");
            Object y = parseComparison();
            if (x instanceof ICondition cx && y instanceof ICondition cy) {
                List<ICondition> children = new ArrayList<>();
                children.add(cx);
                children.add(cy);
                x = new OpAnd(children);
            } else {
                throw error("AND (&&) requires condition operands");
            }
        }
        return x;
    }

    private IExpression asExpression(Object obj) {
        if (obj instanceof IExpression expr) return expr;
        if (obj instanceof ICondition cond) {
            return new IExpression() {

                @Override
                public EvaluationValue evaluate(ConditionContext context) {
                    return cond.isMet(context) ? EvaluationValue.TRUE : EvaluationValue.FALSE;
                }

                @Override
                public String toString() {
                    return cond.toString();
                }
            };
        }
        throw error("Expected numeric expression or condition");
    }

    private ICondition asCondition(Object obj) {
        if (obj instanceof ICondition cond) return cond;
        if (obj instanceof IExpression expr) {
            return new ICondition() {

                @Override
                public boolean isMet(ConditionContext context) {
                    return expr.evaluate(context)
                        .asBoolean();
                }

                @Override
                public String getDescription() {
                    return expr.toString();
                }

                @Override
                public void write(JsonObject json) {}

                @Override
                public String toString() {
                    return expr.toString();
                }
            };
        }
        throw error("Expected condition or numeric expression");
    }

    // 3. Comparison: x == y, x != y, ...
    private Object parseComparison() {
        Object x = parseAdditiveExpression();
        while (true) {
            String op = "";
            if (eat('=')) {
                if (eat('=')) {
                    op = "==";
                } else {
                    // Check for assignment (single '=')
                    return parseAssignment(x, "=");
                }
            } else if (eat('!')) {
                if (eat('=')) {
                    op = "!=";
                } else {
                    pos--; // Backtrack '!'
                    nextChar();
                    return x;
                }
            } else if (eat('+')) {
                if (eat('=')) {
                    return parseAssignment(x, "+=");
                } else {
                    pos--; // Backtrack '+'
                    nextChar();
                    return x;
                }
            } else if (eat('-')) {
                if (eat('=')) {
                    return parseAssignment(x, "-=");
                } else {
                    pos--; // Backtrack '-'
                    nextChar();
                    return x;
                }
            } else if (eat('*')) {
                if (eat('=')) {
                    return parseAssignment(x, "*=");
                } else {
                    pos--; // Backtrack '*'
                    nextChar();
                    return x;
                }
            } else if (eat('/')) {
                if (eat('=')) {
                    return parseAssignment(x, "/=");
                } else {
                    pos--; // Backtrack '/'
                    nextChar();
                    return x;
                }
            } else if (eat('>')) {
                if (eat('=')) op = ">=";
                else op = ">";
            } else if (eat('<')) {
                if (eat('=')) op = "<=";
                else op = "<";
            } else {
                return x;
            }

            if (!op.isEmpty()) {
                Object y = parseAdditiveExpression();
                x = new ComparisonCondition(asExpression(x), asExpression(y), op);
            }
        }
    }

    /**
     * Parse assignment operator (=, +=, -=, *=, /=).
     * <p>
     * The target must be an {@link NbtExpression}, i.e. written as
     * <code>nbt('key')</code>. That is the only thing an assignment can name, so
     * anything else is a mistake worth reporting rather than a form to support.
     */
    private Object parseAssignment(Object left, String operation) {
        // Parse right-hand side
        Object right = parseAdditiveExpression();

        if (left instanceof NbtExpression nbtExpr) {
            if (nbtExpr.getSymbol() != '\0') {
                // Writes land on whatever NBT the surrounding field owns - the output
                // stack, or the block being placed. A symbol names a block to read
                // from, so assigning through one has no target.
                throw error(
                    "Cannot assign through a symbol. nbt('" + nbtExpr.getSymbol()
                        + "', ...) reads another block; write to this output's own NBT instead");
            }
            return new NBTAssignmentExpression(
                nbtExpr.getNbtKey(),
                nbtExpr.getPathSegments(),
                asExpression(right),
                operation);
        }

        throw error("Assignment target must be an NBT access, e.g. nbt('display.Name') = 'Sword'");
    }

    // expression = term ( ( "+" | "-" ) term )*
    private Object parseAdditiveExpression() {
        Object x = parseTerm();
        for (;;) {
            if (eat('+')) x = new ArithmeticExpression(asExpression(x), asExpression(parseTerm()), "+");
            else if (eat('-')) x = new ArithmeticExpression(asExpression(x), asExpression(parseTerm()), "-");
            else return x;
        }
    }

    // term = factor ( ( "*" | "/" | "%" ) factor )*
    private Object parseTerm() {
        Object x = parseFactor();
        for (;;) {
            if (eat('*')) x = new ArithmeticExpression(asExpression(x), asExpression(parseFactor()), "*");
            else if (eat('/')) x = new ArithmeticExpression(asExpression(x), asExpression(parseFactor()), "/");
            else if (eat('%')) x = new ArithmeticExpression(asExpression(x), asExpression(parseFactor()), "%");
            else return x;
        }
    }

    private Object parseFactor() {
        if (eat('+')) return parseFactor(); // unary plus
        if (eat('-')) return new ArithmeticExpression(new ConstantExpression(0), asExpression(parseFactor()), "-");
        if (eat('!')) {
            return new OpNot(asCondition(parseFactor()));
        }

        int startPos = this.pos;
        // Array literals: ['item1', 'item2', ...]
        if (ch == '[') {
            nextChar(); // skip '['
            List<IExpression> elements = new ArrayList<>();

            while (isSpace(ch)) nextChar();

            // Parse array elements
            while (ch != ']' && ch != -1) {
                // Parse element (can be string or expression)
                Object element = parseLogicalOr();
                elements.add(asExpression(element));

                while (isSpace(ch)) nextChar();

                // Check for comma or end of array
                if (ch == ',') {
                    nextChar(); // skip comma
                    while (isSpace(ch)) nextChar();
                } else if (ch != ']') {
                    throw error("Expected ',' or ']' in array literal");
                }
            }

            if (!eat(']')) {
                throw error("Expected closing ']' for array literal");
            }

            return new ArrayLiteralExpression(elements);
        }
        // String literals: 'text' or "text"
        else if (ch == '\'' || ch == '"') {
            char quote = (char) ch;
            nextChar(); // skip opening quote
            int strStart = this.pos;
            while (ch != quote && ch != -1) {
                nextChar();
            }
            if (ch != quote) {
                throw error("Expected closing quote '" + quote + "'");
            }
            String stringValue = input.substring(strStart, this.pos);
            nextChar(); // skip closing quote
            return new StringLiteralExpression(stringValue);
        } else if (eat('(') || eat('{')) { // parentheses or braces
            char close = (input.charAt(pos - 1) == '(') ? ')' : '}';
            Object res = parseLogicalOr();
            if (!eat(close)) throw error("Expected closing '" + close + "'");
            return res;
        } else if ((ch >= '0' && ch <= '9') || ch == '.') { // numbers
            while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
            return new ConstantExpression(Double.parseDouble(input.substring(startPos, this.pos)));
        } else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) { // variables, functions, or NBT paths
            // Parse first segment
            List<String> pathSegments = new ArrayList<>();
            StringBuilder segment = new StringBuilder();

            while ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == '_' || (ch >= '0' && ch <= '9')) {
                segment.append((char) ch);
                nextChar();
            }
            pathSegments.add(segment.toString());

            // Check for dot notation (NBT path)
            while (ch == '.') {
                nextChar(); // skip '.'
                segment = new StringBuilder();
                while ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == '_' || (ch >= '0' && ch <= '9')) {
                    segment.append((char) ch);
                    nextChar();
                }
                if (segment.length() == 0) {
                    throw error("Expected identifier after '.'");
                }
                pathSegments.add(segment.toString());
            }
            if (pathSegments.isEmpty()) {
                throw error("Expected identifier");
            }
            String name = pathSegments.get(0);

            // Check if this is a function call
            if (eat('(')) {
                List<IExpression> args = new ArrayList<>();
                if (!eat(')')) {
                    while (true) {
                        Object arg = parseLogicalOr();
                        args.add(asExpression(arg));
                        if (eat(',')) {
                            continue;
                        }
                        if (eat(')')) {
                            break;
                        }
                        throw error("Expected ',' or ')' after function argument");
                    }
                }

                IExpression funcExpr = ExpressionRegistry.createFunction(name, args, this);
                if (funcExpr != null) {
                    return funcExpr;
                }

                throw error("Unknown function: '" + name + "'");
            }

            // Dotted names: only tier.component remains. NBT paths go through nbt(),
            // where the target is an argument - see below.
            if (pathSegments.size() > 1) {
                if (pathSegments.get(0)
                    .equalsIgnoreCase("tier")) {
                    // tier.component must have exactly 2 segments
                    if (pathSegments.size() != 2) {
                        throw error(
                            "Invalid tier expression: expected 'tier.componentName', got "
                                + String.join(".", pathSegments));
                    }
                    String componentName = pathSegments.get(1);
                    return new ComponentTierExpression(componentName);
                }

                // Bare dot notation used to mean "an NBT path on this machine". It was
                // dropped because it cannot express any other target: S.energy reads
                // identically to a two-level path on the machine itself, so a symbol
                // and a nested key were indistinguishable.
                String fullPath = String.join(".", pathSegments);
                throw error(
                    "Dotted names are no longer NBT paths. Write nbt('" + fullPath
                        + "') for this machine, or nbt('<symbol>', '<key>') for another block");
            }

            // Single segment - check for known variables
            IExpression varExpr = ExpressionRegistry.getVariable(name);
            if (varExpr != null) {
                return varExpr;
            } else {
                throw error("Unknown variable: '" + name + "'");
            }
        } else {
            throw error("Unexpected character: '" + (char) ch + "'");
        }
    }

    public static IExpression parseExpression(String input) {
        Object res = new ExpressionParser(input).parse();
        if (res instanceof IExpression expr) return expr;
        if (res instanceof ICondition cond) {
            return new IExpression() {

                @Override
                public EvaluationValue evaluate(ConditionContext context) {
                    return cond.isMet(context) ? EvaluationValue.TRUE : EvaluationValue.FALSE;
                }

                @Override
                public String toString() {
                    return cond.toString();
                }
            };
        }
        throw new RuntimeException("Input is not a numeric expression: " + input);
    }

    public static ICondition parseCondition(String input) {
        Object res = new ExpressionParser(input).parse();
        if (res instanceof ICondition cond) return cond;
        if (res instanceof IExpression expr) {
            return new ICondition() {

                @Override
                public boolean isMet(ConditionContext context) {
                    return expr.evaluate(context)
                        .asBoolean();
                }

                @Override
                public String getDescription() {
                    return expr.toString();
                }

                @Override
                public void write(JsonObject json) {
                    // Not needed for dynamic conditions generated during parsing
                }

                @Override
                public String toString() {
                    return expr.toString();
                }
            };
        }
        throw new RuntimeException("Input is not a condition: " + input);
    }
}
