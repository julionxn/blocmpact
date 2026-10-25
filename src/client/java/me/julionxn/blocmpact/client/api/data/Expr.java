package me.julionxn.blocmpact.client.api.data;

import me.julionxn.blocmpact.api.FxMath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small math expression language used by data-driven effects (and usable from Java).
 *
 * <pre>
 * numbers 1, 2.5, 1e3 · variables radius, t, s · constants pi, tau, e
 * operators + - * / % ^ (power), comparisons &lt; &lt;= &gt; &gt;= == !=, logic &amp;&amp; || !, ternary c ? a : b
 * functions sin cos tan asin acos atan atan2 sqrt abs sign floor ceil round fract min max clamp sat mix lerp step
 *   smoothstep pow exp log log2 log10 mod hypot remap progress between if
 *   window swindow smooth smoother ease_in ease_out ease_in_out ease_out_back ease_out_elastic ease_out_bounce
 *   decay impulse spring pulse beat wave saw tri square noise fbm rand randr hash arrival
 * </pre>
 * Booleans are numbers: 0 is false, anything else is true.
 */
public final class Expr {
    /** Variable values for evaluation. */
    public interface Env {
        /** Value of a variable; return NaN if unknown (reported once by the caller). */
        double get(String name);

        /** Seed used by {@code noise}, {@code fbm}, {@code rand}. */
        default long seed() {
            return 0;
        }
    }

    private final Node root;
    private final String source;

    private Expr(Node root, String source) {
        this.root = root;
        this.source = source;
    }

    public static Expr constant(double v) {
        return new Expr(new Num(v), Double.toString(v));
    }

    /** Parses an expression; throws {@link IllegalArgumentException} with the position of the error. */
    public static Expr parse(String text) {
        Parser p = new Parser(text);
        Node n = p.ternary();
        p.skip();
        if (p.pos < text.length()) throw p.error("unexpected '" + text.charAt(p.pos) + "'");
        return new Expr(fold(n), text);
    }

    public double eval(Env env) {
        return root.eval(env);
    }

    public float evalf(Env env) {
        return (float) root.eval(env);
    }

    public boolean test(Env env) {
        double v = root.eval(env);
        return v != 0 && !Double.isNaN(v);
    }

    /** True if the expression contains no variables (its value never changes). */
    public boolean isConstant() {
        return root instanceof Num;
    }

    /** Names of the variables the expression reads. */
    public List<String> variables() {
        List<String> out = new ArrayList<>();
        root.collect(out);
        return out;
    }

    @Override
    public String toString() {
        return source;
    }

    // ------------------------------------------------------------------ nodes

    private interface Node {
        double eval(Env e);

        default void collect(List<String> vars) {
        }
    }

    private record Num(double v) implements Node {
        public double eval(Env e) {
            return v;
        }
    }

    private record Var(String name) implements Node {
        public double eval(Env e) {
            return e.get(name);
        }

        public void collect(List<String> vars) {
            vars.add(name);
        }
    }

    private record Unary(char op, Node a) implements Node {
        public double eval(Env e) {
            double x = a.eval(e);
            return op == '-' ? -x : (x == 0 ? 1 : 0);
        }

        public void collect(List<String> vars) {
            a.collect(vars);
        }
    }

    private record Binary(String op, Node a, Node b) implements Node {
        public double eval(Env e) {
            switch (op) {
                case "&&":
                    return a.eval(e) != 0 && b.eval(e) != 0 ? 1 : 0;
                case "||":
                    return a.eval(e) != 0 || b.eval(e) != 0 ? 1 : 0;
                default:
                    break;
            }
            double x = a.eval(e), y = b.eval(e);
            return switch (op) {
                case "+" -> x + y;
                case "-" -> x - y;
                case "*" -> x * y;
                case "/" -> x / y;
                case "%" -> x - Math.floor(x / y) * y;
                case "^" -> Math.pow(x, y);
                case "<" -> x < y ? 1 : 0;
                case "<=" -> x <= y ? 1 : 0;
                case ">" -> x > y ? 1 : 0;
                case ">=" -> x >= y ? 1 : 0;
                case "==" -> Math.abs(x - y) < 1e-9 ? 1 : 0;
                case "!=" -> Math.abs(x - y) >= 1e-9 ? 1 : 0;
                default -> throw new IllegalStateException(op);
            };
        }

        public void collect(List<String> vars) {
            a.collect(vars);
            b.collect(vars);
        }
    }

    private record Ternary(Node c, Node a, Node b) implements Node {
        public double eval(Env e) {
            return c.eval(e) != 0 ? a.eval(e) : b.eval(e);
        }

        public void collect(List<String> vars) {
            c.collect(vars);
            a.collect(vars);
            b.collect(vars);
        }
    }

    @FunctionalInterface
    private interface Fn {
        double apply(double[] a, Env e);
    }

    private record Call(String name, Fn fn, Node[] args, boolean pure) implements Node {
        public double eval(Env e) {
            double[] v = new double[args.length];
            for (int i = 0; i < v.length; i++) v[i] = args[i].eval(e);
            return fn.apply(v, e);
        }

        public void collect(List<String> vars) {
            for (Node n : args) n.collect(vars);
        }
    }

    /** Pre-computes constant sub-expressions. */
    private static Node fold(Node n) {
        if (n instanceof Unary u) {
            Node a = fold(u.a);
            return a instanceof Num ? new Num(new Unary(u.op, a).eval(null)) : new Unary(u.op, a);
        }
        if (n instanceof Binary b) {
            Node x = fold(b.a), y = fold(b.b);
            Binary nb = new Binary(b.op, x, y);
            return x instanceof Num && y instanceof Num ? new Num(nb.eval(null)) : nb;
        }
        if (n instanceof Ternary t) {
            Node c = fold(t.c), a = fold(t.a), b = fold(t.b);
            if (c instanceof Num cn) return cn.v != 0 ? a : b;
            return new Ternary(c, a, b);
        }
        if (n instanceof Call c) {
            Node[] args = new Node[c.args.length];
            boolean allConst = c.pure;
            for (int i = 0; i < args.length; i++) {
                args[i] = fold(c.args[i]);
                allConst &= args[i] instanceof Num;
            }
            Call nc = new Call(c.name, c.fn, args, c.pure);
            return allConst ? new Num(nc.eval(null)) : nc;
        }
        return n;
    }

    // ------------------------------------------------------------------ functions

    private record FnDef(int minArgs, int maxArgs, boolean pure, Fn fn) {
    }

    private static final Map<String, FnDef> FUNCTIONS = new HashMap<>();
    private static final Map<String, Double> CONSTANTS = Map.of("pi", Math.PI, "tau", Math.PI * 2, "e", Math.E, "true", 1.0, "false", 0.0);

    private static void fn(String name, int args, Fn f) {
        FUNCTIONS.put(name, new FnDef(args, args, true, f));
    }

    private static void fn(String name, int min, int max, Fn f) {
        FUNCTIONS.put(name, new FnDef(min, max, true, f));
    }

    private static void seeded(String name, int min, int max, Fn f) {
        FUNCTIONS.put(name, new FnDef(min, max, false, f));
    }

    private static float f(double d) {
        return (float) d;
    }

    static {
        fn("sin", 1, (a, e) -> Math.sin(a[0]));
        fn("cos", 1, (a, e) -> Math.cos(a[0]));
        fn("tan", 1, (a, e) -> Math.tan(a[0]));
        fn("asin", 1, (a, e) -> Math.asin(a[0]));
        fn("acos", 1, (a, e) -> Math.acos(a[0]));
        fn("atan", 1, (a, e) -> Math.atan(a[0]));
        fn("atan2", 2, (a, e) -> Math.atan2(a[0], a[1]));
        fn("sqrt", 1, (a, e) -> Math.sqrt(Math.max(0, a[0])));
        fn("abs", 1, (a, e) -> Math.abs(a[0]));
        fn("sign", 1, (a, e) -> Math.signum(a[0]));
        fn("floor", 1, (a, e) -> Math.floor(a[0]));
        fn("ceil", 1, (a, e) -> Math.ceil(a[0]));
        fn("round", 1, (a, e) -> Math.rint(a[0]));
        fn("fract", 1, (a, e) -> a[0] - Math.floor(a[0]));
        fn("min", 2, 16, (a, e) -> {
            double m = a[0];
            for (double x : a) m = Math.min(m, x);
            return m;
        });
        fn("max", 2, 16, (a, e) -> {
            double m = a[0];
            for (double x : a) m = Math.max(m, x);
            return m;
        });
        fn("clamp", 3, (a, e) -> Math.max(a[1], Math.min(a[2], a[0])));
        fn("sat", 1, (a, e) -> FxMath.clamp01(a[0]));
        fn("mix", 3, (a, e) -> a[0] + (a[1] - a[0]) * a[2]);
        fn("lerp", 3, (a, e) -> a[0] + (a[1] - a[0]) * a[2]);
        fn("step", 2, (a, e) -> a[1] < a[0] ? 0 : 1);
        fn("smoothstep", 3, (a, e) -> FxMath.smoothstep(f(a[0]), f(a[1]), f(a[2])));
        fn("pow", 2, (a, e) -> Math.pow(a[0], a[1]));
        fn("exp", 1, (a, e) -> Math.exp(a[0]));
        fn("log", 1, (a, e) -> Math.log(a[0]));
        fn("log2", 1, (a, e) -> Math.log(a[0]) / Math.log(2));
        fn("log10", 1, (a, e) -> Math.log10(a[0]));
        fn("mod", 2, (a, e) -> a[0] - Math.floor(a[0] / a[1]) * a[1]);
        fn("hypot", 2, 3, (a, e) -> Math.sqrt(a[0] * a[0] + a[1] * a[1] + (a.length > 2 ? a[2] * a[2] : 0)));
        fn("remap", 5, (a, e) -> FxMath.remap(f(a[0]), f(a[1]), f(a[2]), f(a[3]), f(a[4])));
        fn("progress", 3, (a, e) -> FxMath.progress(f(a[0]), f(a[1]), f(a[2])));
        fn("between", 3, (a, e) -> a[0] >= a[1] && a[0] < a[2] ? 1 : 0);
        fn("if", 3, (a, e) -> a[0] != 0 ? a[1] : a[2]);
        fn("window", 5, (a, e) -> FxMath.window(f(a[0]), f(a[1]), f(a[2]), f(a[3]), f(a[4])));
        fn("swindow", 5, (a, e) -> FxMath.smoothWindow(f(a[0]), f(a[1]), f(a[2]), f(a[3]), f(a[4])));
        fn("smooth", 1, (a, e) -> FxMath.smooth(f(a[0])));
        fn("smoother", 1, (a, e) -> FxMath.smoother(f(a[0])));
        fn("ease_in", 1, (a, e) -> FxMath.easeInCubic(f(a[0])));
        fn("ease_out", 1, (a, e) -> FxMath.easeOutCubic(f(a[0])));
        fn("ease_in_out", 1, (a, e) -> FxMath.easeInOutCubic(f(a[0])));
        fn("ease_out_back", 1, 2, (a, e) -> FxMath.easeOutBack(f(a[0]), a.length > 1 ? f(a[1]) : 1.70158f));
        fn("ease_out_elastic", 1, (a, e) -> FxMath.easeOutElastic(f(a[0])));
        fn("ease_out_bounce", 1, (a, e) -> FxMath.easeOutBounce(f(a[0])));
        fn("decay", 2, 3, (a, e) -> a.length == 2 ? Math.exp(-Math.max(a[0], 0) * a[1]) : FxMath.decay(f(a[0]), f(a[1]), f(a[2])));
        fn("impulse", 3, (a, e) -> FxMath.impulse(f(a[0]), f(a[1]), f(a[2])));
        fn("spring", 3, (a, e) -> FxMath.spring(f(a[0]), f(a[1]), f(a[2])));
        fn("pulse", 5, 6, (a, e) -> FxMath.pulsePhase(f(a[0]), f(a[1]), f(a[2]), f(a[3]), f(a[4]), a.length > 5 ? f(a[5]) : 2f));
        fn("beat", 2, (a, e) -> FxMath.beat(f(a[0]), f(a[1])));
        fn("wave", 2, (a, e) -> FxMath.wave(f(a[0]), f(a[1])));
        fn("saw", 1, (a, e) -> FxMath.saw(f(a[0])));
        fn("tri", 1, (a, e) -> FxMath.triangle(f(a[0])));
        fn("square", 1, 2, (a, e) -> FxMath.square(f(a[0]), a.length > 1 ? f(a[1]) : 0.5f));
        fn("arrival", 1, (a, e) -> Math.max(0, a[0]) / FxMath.SOUND_BLOCKS_PER_TICK);
        seeded("noise", 1, 2, (a, e) -> FxMath.noise(e.seed() + (a.length > 1 ? (long) a[1] * 7919L : 0), f(a[0])));
        seeded("fbm", 1, 2, (a, e) -> FxMath.fbm(e.seed(), f(a[0]), a.length > 1 ? (int) a[1] : 4));
        seeded("rand", 1, 1, (a, e) -> FxMath.hash(e.seed(), (long) Math.floor(a[0])));
        seeded("randr", 3, 3, (a, e) -> FxMath.hash(e.seed(), (long) Math.floor(a[0]), f(a[1]), f(a[2])));
        fn("hash", 1, 2, (a, e) -> FxMath.hash(a.length > 1 ? (long) a[1] : 0, (long) Math.floor(a[0])));
    }

    // ------------------------------------------------------------------ parser

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) {
            this.s = s;
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException(msg + " at " + pos + " in \"" + s + "\"");
        }

        void skip() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        boolean eat(String token) {
            skip();
            if (s.startsWith(token, pos)) {
                pos += token.length();
                return true;
            }
            return false;
        }

        Node ternary() {
            Node c = or();
            if (eat("?")) {
                Node a = ternary();
                if (!eat(":")) throw error("expected ':'");
                Node b = ternary();
                return new Ternary(c, a, b);
            }
            return c;
        }

        Node or() {
            Node n = and();
            while (eat("||")) n = new Binary("||", n, and());
            return n;
        }

        Node and() {
            Node n = comparison();
            while (eat("&&")) n = new Binary("&&", n, comparison());
            return n;
        }

        Node comparison() {
            Node n = additive();
            while (true) {
                String op = null;
                for (String o : new String[]{"<=", ">=", "==", "!=", "<", ">"}) {
                    skip();
                    if (s.startsWith(o, pos)) {
                        op = o;
                        break;
                    }
                }
                if (op == null) return n;
                pos += op.length();
                n = new Binary(op, n, additive());
            }
        }

        Node additive() {
            Node n = multiplicative();
            while (true) {
                skip();
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                    String op = String.valueOf(s.charAt(pos++));
                    n = new Binary(op, n, multiplicative());
                } else return n;
            }
        }

        Node multiplicative() {
            Node n = unary();
            while (true) {
                skip();
                if (pos < s.length() && "*/%".indexOf(s.charAt(pos)) >= 0) {
                    String op = String.valueOf(s.charAt(pos++));
                    n = new Binary(op, n, unary());
                } else return n;
            }
        }

        Node unary() {
            skip();
            if (eat("-")) return new Unary('-', unary());
            if (eat("+")) return unary();
            if (pos < s.length() && s.charAt(pos) == '!' && !s.startsWith("!=", pos)) {
                pos++;
                return new Unary('!', unary());
            }
            return power();
        }

        Node power() {
            Node base = primary();
            if (eat("^")) return new Binary("^", base, unary());
            return base;
        }

        Node primary() {
            skip();
            if (pos >= s.length()) throw error("unexpected end");
            char c = s.charAt(pos);
            if (c == '(') {
                pos++;
                Node n = ternary();
                if (!eat(")")) throw error("expected ')'");
                return n;
            }
            if (Character.isDigit(c) || c == '.') return number();
            if (Character.isLetter(c) || c == '_') {
                int start = pos;
                while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_' || s.charAt(pos) == '.')) pos++;
                String name = s.substring(start, pos);
                String lower = name.toLowerCase(Locale.ROOT);
                if (eat("(")) {
                    FnDef def = FUNCTIONS.get(lower);
                    if (def == null) throw error("unknown function " + name);
                    List<Node> args = new ArrayList<>();
                    if (!eat(")")) {
                        do args.add(ternary());
                        while (eat(","));
                        if (!eat(")")) throw error("expected ')'");
                    }
                    if (args.size() < def.minArgs || args.size() > def.maxArgs)
                        throw error(name + " takes " + (def.minArgs == def.maxArgs ? def.minArgs : def.minArgs + "-" + def.maxArgs) + " arguments");
                    return new Call(lower, def.fn, args.toArray(new Node[0]), def.pure);
                }
                Double k = CONSTANTS.get(lower);
                return k != null ? new Num(k) : new Var(name);
            }
            throw error("unexpected '" + c + "'");
        }

        Node number() {
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                int save = pos;
                pos++;
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                if (pos < s.length() && Character.isDigit(s.charAt(pos))) {
                    while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                } else pos = save;
            }
            try {
                return new Num(Double.parseDouble(s.substring(start, pos)));
            } catch (NumberFormatException e) {
                throw error("bad number");
            }
        }
    }
}
