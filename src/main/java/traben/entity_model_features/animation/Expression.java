package traben.entity_model_features.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.MathHelper;
import traben.entity_model_features.models.animation.AnimSetupContext;
import traben.entity_model_features.config.EMFConfig;

public final class Expression {

    public interface Node {
        double evaluate(RenderParameters parameters);

        default ValueType type() {
            return ValueType.FLOAT;
        }

        default boolean isConstant() {
            return false;
        }

        default float evaluateApi(RenderParameters parameters) {
            float value = (float) evaluate(parameters);
            return type() == ValueType.BOOLEAN
                    ? traben.entity_model_features.models.animation.math.expression_tree.MathValue.fromBoolean(truth(value)) : value;
        }
    }

    public enum ValueType { FLOAT, BOOLEAN, DYNAMIC }

    public static Node typed(Node evaluator, ValueType type, boolean constant) {
        return new Node() {
            // Upstream evaluates every expression node as a float. Resource packs
            // compare these results exactly with Minecraft's native model angles.
            public double evaluate(RenderParameters parameters) { return (float) evaluator.evaluate(parameters); }
            public ValueType type() { return type; }
            public boolean isConstant() { return constant; }
        };
    }

    private final String source;
    private int position;
    private int tokenStart;
    private Token current;
    private final AnimSetupContext context;

    private Expression(String source) {
        this(source, new AnimSetupContext("expression", null, new java.util.HashMap<>()));
    }

    private Expression(String source, AnimSetupContext context) {
        this.context = context;
        this.source = source == null ? "0" : source;
        current = nextToken();
    }

    static Node compile(String source) {
        return compile(source, new AnimSetupContext("expression", null, new java.util.HashMap<>()));
    }

    static Node compile(String source, AnimSetupContext context) {
        try {
            return compileStrict(source, context);
        } catch (RuntimeException exception) {
            System.err.println("[EMF] Invalid animation expression '" + source + "': " + exception.getMessage());
            return parameters -> 0.0D;
        }
    }

    public static Node compileStrict(String source) {
        return compileStrict(source, new AnimSetupContext("expression", null, new java.util.HashMap<>()));
    }

    public static Node compileStrict(String source, AnimSetupContext context) {
        Expression parser = new Expression(source, context);
        Node result = parser.parseExpression(0);
        if (parser.current.type != TokenType.END) throw new IllegalArgumentException("Unexpected token " + parser.current.text);
        return result;
    }

    static boolean isValid(String source) {
        try {
            Expression parser = new Expression(source);
            parser.parseExpression(0);
            return parser.current.type == TokenType.END;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Node parseExpression(int minimumPrecedence) {
        Node left = parseUnary();
        while (current.type == TokenType.OPERATOR) {
            String operator = current.text;
            int precedence = precedence(operator);
            if (precedence < minimumPrecedence) {
                break;
            }
            advance();
            Node right = parseExpression(precedence + 1);
            Node previous = left;
            left = binary(operator, previous, right);
        }
        return left;
    }

    private Node parseUnary() {
        if (accept("!")) {
            Node value = parseUnary();
            return typed(parameters -> bool(!truth(value.evaluate(parameters))), ValueType.BOOLEAN, value.isConstant());
        }
        if (accept("-")) {
            Node value = parseUnary();
            return typed(parameters -> -value.evaluate(parameters), ValueType.FLOAT, value.isConstant());
        }
        if (accept("+")) {
            return parseUnary();
        }
        if (accept("(")) {
            Node value = parseExpression(0);
            require(")");
            return value;
        }
        if (current.type == TokenType.NUMBER) {
            if (current.text.startsWith(".") && EMFConfig.get().enforceOptiFineAnimSyntaxLimits)
                throw new IllegalArgumentException("OptiFine numbers must start with a digit");
            float value = Float.parseFloat(current.text);
            advance();
            return typed(parameters -> value, ValueType.FLOAT, true);
        }
        if (current.type == TokenType.IDENTIFIER) {
            String name = current.text;
            if (name.startsWith("_") && EMFConfig.get().enforceOptiFineAnimSyntaxLimits)
                throw new IllegalArgumentException("OptiFine variables must not start with an underscore");
            advance();
            if (accept("(")) {
                List<String> raw = rawArguments();
                Node extension = AnimationExtensions.function(name, raw, context);
                if (extension != null) return extension;
                if (name.equals("nbt")) {
                    if (raw.size() != 2) throw new IllegalArgumentException("nbt requires key and query");
                    return typed(p -> bool(p.matchesNbt(unquote(raw.get(0)), unquote(raw.get(1)))), ValueType.BOOLEAN, false);
                }
                List<Node> arguments = new ArrayList<>();
                for (int i = 0; i < raw.size(); i++) {
                    boolean text = (name.equals("print") || name.equals("printb")) && raw.size() == 3 && i == 0
                            || name.equals("catch") && i == 2;
                    arguments.add(text ? zero() : compileStrict(raw.get(i), context));
                }
                ValueType type = switch (name) {
                    case "ifb", "randomb", "printb", "between", "equals", "in" -> ValueType.BOOLEAN;
                    default -> ValueType.FLOAT;
                };
                boolean constant = !List.of("random", "randomb", "print", "printb").contains(name)
                        && arguments.stream().allMatch(Node::isConstant);
                return typed(function(name, arguments, raw), type, constant);
            }
            Node extension = AnimationExtensions.variable(name, context);
            if (extension != null) return extension;
            boolean bool = name.equals("true") || name.equals("false") || name.startsWith("is_")
                    || name.startsWith("varb.") || name.startsWith("global_varb.") || name.endsWith(".visible")
                    || name.endsWith(".visible_boxes");
            boolean constant = List.of("true", "false", "pi", "e", "nan").contains(name);
            return typed(parameters -> (parameters == null ? new RenderParameters(null, 0, 0, 0, 0, 0, 0) : parameters).get(name),
                    bool ? ValueType.BOOLEAN : ValueType.FLOAT, constant);
        }
        throw new IllegalArgumentException("Unexpected token " + current.text);
    }

    private static Node binary(String operator, Node left, Node right) {
        Node evaluator = switch (operator) {
            case "+" -> p -> (float) left.evaluate(p) + (float) right.evaluate(p);
            case "-" -> p -> (float) left.evaluate(p) - (float) right.evaluate(p);
            case "*" -> p -> (float) left.evaluate(p) * (float) right.evaluate(p);
            case "/" -> p -> (float) left.evaluate(p) / (float) right.evaluate(p);
            case "%" -> p -> (float) left.evaluate(p) % (float) right.evaluate(p);
            case ">" -> p -> bool(left.evaluate(p) > right.evaluate(p));
            case ">=" -> p -> bool(left.evaluate(p) >= right.evaluate(p));
            case "<" -> p -> bool(left.evaluate(p) < right.evaluate(p));
            case "<=" -> p -> bool(left.evaluate(p) <= right.evaluate(p));
            case "==" -> p -> bool(left.evaluate(p) == right.evaluate(p));
            case "!=" -> p -> bool(left.evaluate(p) != right.evaluate(p));
            case "&&" -> p -> bool(truth(left.evaluate(p)) && truth(right.evaluate(p)));
            case "||" -> p -> bool(truth(left.evaluate(p)) || truth(right.evaluate(p)));
            default -> p -> 0.0D;
        };
        boolean bool = List.of(">", ">=", "<", "<=", "==", "!=", "&&", "||").contains(operator);
        return typed(evaluator, bool ? ValueType.BOOLEAN : ValueType.FLOAT, left.isConstant() && right.isConstant());
    }

    private static Node function(String rawName, List<Node> values, List<String> raw) {
        String name = rawName.toLowerCase(Locale.ROOT);
        return switch (name) {
            case "sin" -> unary(values, value -> MathHelper.sin((float) value));
            case "cos" -> unary(values, value -> MathHelper.cos((float) value));
            case "asin" -> unary(values, Math::asin);
            case "acos" -> unary(values, Math::acos);
            case "tan" -> unary(values, Math::tan);
            case "atan" -> unary(values, Math::atan);
            case "abs" -> unary(values, Math::abs);
            case "floor" -> unary(values, Math::floor);
            case "ceil" -> unary(values, Math::ceil);
            case "exp" -> unary(values, Math::exp);
            case "log" -> unary(values, Math::log);
            case "round" -> unary(values, value -> Math.round((float) value));
            case "signum" -> unary(values, Math::signum);
            case "sqrt" -> unary(values, value -> MathHelper.sqrt_float((float) value));
            case "torad" -> unary(values, value -> (float) value * 0.017453292F);
            case "todeg" -> unary(values, value -> (float) value * 57.295776F);
            case "frac" -> unary(values, value -> value - Math.floor(value));
            case "atan2" -> binaryFunction(values, Math::atan2);
            case "pow" -> binaryFunction(values, Math::pow);
            case "fmod" -> binaryFunction(values, (a, b) -> (int) b == 0 ? Double.NaN : Math.floorMod((int) a, (int) b));
            case "wrapdeg" -> unary(values, Expression::wrapDegrees);
            case "wraprad" -> unary(values, x -> Math.toRadians(wrapDegrees(Math.toDegrees(x))));
            case "degdiff" -> binaryFunction(values, (a, b) -> Math.abs(wrapDegrees(b - a)));
            case "raddiff" -> binaryFunction(values, (a, b) -> Math.toRadians(Math.abs(wrapDegrees(Math.toDegrees(b - a)))));
            case "catch" -> {
                if (values.size() < 2 || values.size() > 3) throw new IllegalArgumentException("catch requires 2 or 3 arguments");
                yield p -> {
                    try {
                        double result = values.get(0).evaluate(p);
                        if (!Double.isNaN(result)) return result;
                    } catch (RuntimeException ignored) {
                    }
                    return values.get(1).evaluate(p);
                };
            }
            case "keyframe", "keyframeloop" -> {
                if (values.size() < 3) throw new IllegalArgumentException("keyframe requires at least 2 frames");
                yield p -> keyframe(values, p, name.equals("keyframeloop"));
            }
            case "catmullrom" -> exact(values, 5, p -> MathFunctions.catmullrom((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p), (float) values.get(3).evaluate(p), (float) values.get(4).evaluate(p)));
            case "quadbezier" -> exact(values, 4, p -> MathFunctions.quadraticBezier((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p), (float) values.get(3).evaluate(p)));
            case "cubicbezier" -> exact(values, 5, p -> MathFunctions.cubicBezier((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p), (float) values.get(3).evaluate(p), (float) values.get(4).evaluate(p)));
            case "hermite" -> exact(values, 5, p -> MathFunctions.hermiteInterpolation((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p), (float) values.get(3).evaluate(p), (float) values.get(4).evaluate(p)));
            case "easeinquad" -> exact(values, 3, p -> MathFunctions.easeInQuad((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeoutquad" -> exact(values, 3, p -> MathFunctions.easeOutQuad((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeinoutquad" -> exact(values, 3, p -> MathFunctions.easeInOutQuad((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeincubic", "cubiceasein" -> exact(values, 3, p -> MathFunctions.easeInCubic((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeoutcubic", "cubiceaseout" -> exact(values, 3, p -> MathFunctions.easeOutCubic((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeinoutcubic", "cubiceaseinout" -> exact(values, 3, p -> MathFunctions.easeInOutCubic((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeinquart" -> exact(values, 3, p -> MathFunctions.easeInQuart((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeoutquart" -> exact(values, 3, p -> MathFunctions.easeOutQuart((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeinoutquart" -> exact(values, 3, p -> MathFunctions.easeInOutQuart((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeinquint" -> exact(values, 3, p -> MathFunctions.easeInQuint((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeoutquint" -> exact(values, 3, p -> MathFunctions.easeOutQuint((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeinoutquint" -> exact(values, 3, p -> MathFunctions.easeInOutQuint((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeinsine", "easein" -> exact(values, 3, p -> MathFunctions.easeInSine((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeoutsine", "easeout" -> exact(values, 3, p -> MathFunctions.easeOutSine((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeinoutsine", "easeinout" -> exact(values, 3, p -> MathFunctions.easeInOutSine((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeinexpo" -> exact(values, 3, p -> MathFunctions.easeInExpo((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeoutexpo" -> exact(values, 3, p -> MathFunctions.easeOutExpo((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeinoutexpo" -> exact(values, 3, p -> MathFunctions.easeInOutExpo((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeincirc" -> exact(values, 3, p -> MathFunctions.easeInCirc((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeoutcirc" -> exact(values, 3, p -> MathFunctions.easeOutCirc((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeinoutcirc" -> exact(values, 3, p -> MathFunctions.easeInOutCirc((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeinelastic" -> exact(values, 3, p -> MathFunctions.easeInElastic((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeoutelastic" -> exact(values, 3, p -> MathFunctions.easeOutElastic((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeinoutelastic" -> exact(values, 3, p -> MathFunctions.easeInOutElastic((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeinbounce" -> exact(values, 3, p -> MathFunctions.easeInBounce((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeoutbounce" -> exact(values, 3, p -> MathFunctions.easeOutBounce((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeinoutbounce" -> exact(values, 3, p -> MathFunctions.easeInOutBounce((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeinback" -> exact(values, 3, p -> MathFunctions.easeInBack((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeoutback" -> exact(values, 3, p -> MathFunctions.easeOutBack((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "easeinoutback" -> exact(values, 3, p -> MathFunctions.easeInOutBack((float) values.get(0).evaluate(p), (float) values.get(1).evaluate(p), (float) values.get(2).evaluate(p)));
            case "lerp" -> exact(values, 3, p -> {
                float delta = (float) values.get(0).evaluate(p);
                float first = (float) values.get(1).evaluate(p);
                return first + delta * ((float) values.get(2).evaluate(p) - first);
            });
            case "min" -> aggregate(values, true);
            case "max" -> aggregate(values, false);
            case "clamp" -> exact(values, 3, p -> Math.max(values.get(1).evaluate(p),
                    Math.min(values.get(2).evaluate(p), values.get(0).evaluate(p))));
            case "between" -> exact(values, 3, p -> {
                double value = values.get(0).evaluate(p);
                return bool(value >= values.get(1).evaluate(p) && value <= values.get(2).evaluate(p));
            });
            case "equals" -> exact(values, 3, p -> {
                double epsilon = values.get(2).evaluate(p);
                return bool(Math.abs(values.get(0).evaluate(p) - values.get(1).evaluate(p)) <= epsilon);
            });
            case "in" -> atLeast(values, 2, p -> {
                double tested = values.get(0).evaluate(p);
                for (int i = 1; i < values.size(); i++) {
                    if (tested == values.get(i).evaluate(p)) return 1.0D;
                }
                return 0.0D;
            });
            case "if", "ifb" -> conditional(values);
            case "random", "randomb" -> {
                if (values.size() > 1) throw new IllegalArgumentException("random requires 0 or 1 arguments");
                yield p -> {
                double result = values.isEmpty() ? Math.random() : seededRandom(values.get(0).evaluate(p));
                return name.equals("randomb") ? bool(result >= 0.5D) : result;
                };
            }
            case "print", "printb" -> {
                if (values.size() != 1 && values.size() != 3) throw new IllegalArgumentException("print requires 1 or 3 arguments");
                int[] counter = {0};
                yield p -> {
                    double result = values.get(values.size() - 1).evaluate(p);
                    int interval = values.size() == 3 ? (int) values.get(1).evaluate(p) : 1;
                    if (++counter[0] % Math.max(1, interval) == 0 && !p.paused()) {
                        System.out.println("[EMF] print: [" + raw.get(0) + "] = " + (name.equals("printb") ? truth(result) : result));
                    }
                    return result;
                };
            }
            default -> throw new IllegalArgumentException("Unknown function " + rawName);
        };
    }

    private static Node exact(List<Node> values, int count, Node result) {
        if (values.size() != count) throw new IllegalArgumentException("Expected " + count + " arguments");
        return result;
    }

    private static Node atLeast(List<Node> values, int count, Node result) {
        if (values.size() < count) throw new IllegalArgumentException("Expected at least " + count + " arguments");
        return result;
    }

    private static double wrapDegrees(double value) {
        double result = value % 360;
        if (result >= 180) result -= 360;
        if (result < -180) result += 360;
        return result;
    }

    private static double keyframe(List<Node> values, RenderParameters p, boolean loop) {
        double delta = values.get(0).evaluate(p);
        int index = (int) Math.floor(delta);
        int count = values.size() - 1;
        if (!loop) {
            if (index >= count - 1) return values.get(count).evaluate(p);
            if (index <= 0) return values.get(1).evaluate(p);
        }
        float[] frame = new float[4];
        for (int i = 0; i < 4; i++) {
            int n = index + i - 1;
            n = loop ? Math.floorMod(n, count) : Math.max(0, Math.min(count - 1, n));
            frame[i] = (float) values.get(n + 1).evaluate(p);
        }
        return MathFunctions.catmullrom((float) (delta - Math.floor(delta)), frame[0], frame[1], frame[2], frame[3]);
    }

    private List<String> rawArguments() {
        List<String> result = new ArrayList<>();
        int start = tokenStart;
        int depth = 0;
        char quote = 0;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (c == '\\') { i++; continue; }
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') { quote = c; continue; }
            if (c == '(' || c == '[' || c == '{') depth++;
            if (c == ')' && depth == 0) {
                String last = source.substring(start, i).trim();
                if (!last.isEmpty()) result.add(last);
                else if (!result.isEmpty()) throw new IllegalArgumentException("Empty function argument");
                position = i + 1;
                current = nextToken();
                return result;
            }
            if (c == ')' || c == ']' || c == '}') depth--;
            if (c == ',' && depth == 0) {
                String argument = source.substring(start, i).trim();
                if (argument.isEmpty()) throw new IllegalArgumentException("Empty function argument");
                result.add(argument);
                start = i + 1;
            }
        }
        throw new IllegalArgumentException("Unclosed function call");
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && (value.charAt(0) == '"' || value.charAt(0) == '\'')
                && value.charAt(0) == value.charAt(value.length() - 1)) return value.substring(1, value.length() - 1);
        return value;
    }

    private static double seededRandom(double seed) {
        int value = Float.floatToIntBits((float) seed);
        value = value ^ 61 ^ value >> 16;
        value += value << 3;
        value ^= value >> 4;
        value *= 668265261;
        value ^= value >> 15;
        return (float) Math.abs(value) / 2.14748365E9F;
    }

    private static Node conditional(List<Node> values) {
        if (values.size() < 3 || values.size() % 2 != 1)
            throw new IllegalArgumentException("if requires condition/value pairs and a fallback");
        return parameters -> {
            int pairs = values.size() / 2;
            for (int i = 0; i < pairs; i++) {
                if (truth(values.get(i * 2).evaluate(parameters))) {
                    return values.get(i * 2 + 1).evaluate(parameters);
                }
            }
            return values.size() % 2 == 1 ? values.get(values.size() - 1).evaluate(parameters) : 0.0D;
        };
    }

    private static Node aggregate(List<Node> values, boolean minimum) {
        return atLeast(values, 2, parameters -> {
            double result = values.get(0).evaluate(parameters);
            for (int i = 1; i < values.size(); i++) {
                result = minimum ? Math.min(result, values.get(i).evaluate(parameters))
                        : Math.max(result, values.get(i).evaluate(parameters));
            }
            return result;
        });
    }

    private static Node unary(List<Node> values, java.util.function.DoubleUnaryOperator operator) {
        return exact(values, 1, p -> operator.applyAsDouble(values.get(0).evaluate(p)));
    }

    private static Node binaryFunction(List<Node> values, java.util.function.DoubleBinaryOperator operator) {
        return exact(values, 2, p -> operator.applyAsDouble(values.get(0).evaluate(p), values.get(1).evaluate(p)));
    }

    private static Node zero() {
        return parameters -> 0.0D;
    }

    private boolean accept(String text) {
        if (current.text.equals(text)) {
            advance();
            return true;
        }
        return false;
    }

    private void require(String text) {
        if (!accept(text)) {
            throw new IllegalArgumentException("Expected " + text);
        }
    }

    private void advance() {
        current = nextToken();
    }

    private Token nextToken() {
        while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++;
        tokenStart = position;
        if (position >= source.length()) return new Token(TokenType.END, "");
        char first = source.charAt(position);
        if (Character.isDigit(first) || first == '.' && position + 1 < source.length() && Character.isDigit(source.charAt(position + 1))) {
            int start = position++;
            while (position < source.length()) {
                char value = source.charAt(position);
                if (!Character.isDigit(value) && value != '.' && value != 'e' && value != 'E' &&
                        !((value == '+' || value == '-') && (source.charAt(position - 1) == 'e' || source.charAt(position - 1) == 'E'))) break;
                position++;
            }
            return new Token(TokenType.NUMBER, source.substring(start, position));
        }
        if (Character.isLetter(first) || first == '_') {
            int start = position++;
            while (position < source.length()) {
                char value = source.charAt(position);
                if (!Character.isLetterOrDigit(value) && value != '_' && value != '.' && value != ':') break;
                position++;
            }
            return new Token(TokenType.IDENTIFIER, source.substring(start, position));
        }
        if (position + 1 < source.length()) {
            String pair = source.substring(position, position + 2);
            if (pair.equals("&&") || pair.equals("||") || pair.equals(">=") || pair.equals("<=") ||
                    pair.equals("==") || pair.equals("!=")) {
                position += 2;
                return new Token(TokenType.OPERATOR, pair);
            }
        }
        position++;
        return new Token(first == '(' || first == ')' || first == ',' ? TokenType.PUNCTUATION : TokenType.OPERATOR,
                Character.toString(first));
    }

    private static int precedence(String operator) {
        return switch (operator) {
            case "||" -> 1;
            case "&&" -> 2;
            case "==", "!=" -> 3;
            case ">", ">=", "<", "<=" -> 4;
            case "+", "-" -> 5;
            case "*", "/", "%" -> 6;
            default -> -1;
        };
    }

    private static boolean truth(double value) {
        return value != 0.0D && !Double.isNaN(value);
    }

    private static double bool(boolean value) {
        return value ? 1.0D : 0.0D;
    }

    private enum TokenType {NUMBER, IDENTIFIER, OPERATOR, PUNCTUATION, END}

    private record Token(TokenType type, String text) {
    }
}
