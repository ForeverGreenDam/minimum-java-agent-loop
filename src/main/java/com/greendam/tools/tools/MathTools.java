package com.greendam.tools.tools;

import com.greendam.tools.annotation.Param;
import com.greendam.tools.annotation.Tool;

import java.util.Random;

/**
 * 数学计算工具集 — 提供数学表达式计算和随机数生成能力.
 *
 * <p>表达式计算支持: 加减乘除(+ - * /)、取模(%)、幂运算(^)、括号、以及常用数学函数
 * (sqrt, abs, sin, cos, tan, log, exp, floor, ceil, round, min, max, pow).
 */
public class MathTools {

    private static final Random RANDOM = new Random();
    private String expr;
    private int pos;

    // ==================== 表达式解析器 ====================
    private int len;

    private static void checkArgs(String func, double[] args, int expected) {
        if (args.length != expected) {
            throw new IllegalArgumentException(
                    "函数 " + func + " 需要 " + expected + " 个参数，但传入了 " + args.length + " 个");
        }
    }

    /**
     * 计算数学表达式.
     */
    @Tool(name = "calculate", description = "计算数学表达式并返回结果。支持加减乘除、取模、幂运算、括号以及常用数学函数(sqrt/abs/sin/cos/tan/log/exp/floor/ceil/round/min/max/pow)。例如: '2+3*4', 'sqrt(16)+abs(-5)', 'pow(2,8)'")
    public String calculate(
            @Param(name = "expression", description = "数学表达式字符串，例如 '2+3*(4-1)', 'sqrt(144)', 'pow(2,10)+sin(0)'") String expression
    ) {
        try {
            double result = evaluate(expression);
            // 智能格式化：整数不显示小数点
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return String.valueOf((long) result);
            }
            return String.valueOf(result);
        } catch (Exception e) {
            return "[ERROR] 表达式计算失败: " + e.getMessage();
        }
    }

    /**
     * 生成指定范围内的随机数.
     */
    @Tool(name = "randomNumber", description = "生成一个指定范围内的随机浮点数。区间为 [min, max)，即包含最小值，不包含最大值。")
    public String randomNumber(
            @Param(name = "min", description = "最小值（包含）") Double min,
            @Param(name = "max", description = "最大值（不包含）") Double max
    ) {
        if (min == null || max == null) {
            return "[ERROR] min 和 max 不能为空";
        }
        if (min >= max) {
            return "[ERROR] min 必须小于 max";
        }
        double value = min + RANDOM.nextDouble() * (max - min);
        return String.valueOf(value);
    }

    private double evaluate(String expression) {
        this.expr = expression.replaceAll("\\s+", ""); // 去除空白
        this.pos = 0;
        this.len = expr.length();
        double result = parseExpression();
        if (pos < len) {
            throw new IllegalArgumentException("表达式在位置 " + pos + " 附近有语法错误，剩余: '" + expr.substring(pos) + "'");
        }
        return result;
    }

    // expression = term (('+' | '-') term)*
    private double parseExpression() {
        double left = parseTerm();
        while (pos < len) {
            char op = expr.charAt(pos);
            if (op == '+') {
                pos++;
                left += parseTerm();
            } else if (op == '-') {
                pos++;
                left -= parseTerm();
            } else {
                break;
            }
        }
        return left;
    }

    // term = factor (('*' | '/' | '%') factor)*
    private double parseTerm() {
        double left = parsePower();
        while (pos < len) {
            char op = expr.charAt(pos);
            if (op == '*') {
                pos++;
                left *= parsePower();
            } else if (op == '/') {
                pos++;
                double divisor = parsePower();
                if (divisor == 0) throw new ArithmeticException("除数不能为零");
                left /= divisor;
            } else if (op == '%') {
                pos++;
                double divisor = parsePower();
                if (divisor == 0) throw new ArithmeticException("取模运算除数不能为零");
                left %= divisor;
            } else {
                break;
            }
        }
        return left;
    }

    // power = unary ('^' unary)*  (右结合)
    private double parsePower() {
        double left = parseUnary();
        while (pos < len && expr.charAt(pos) == '^') {
            pos++;
            double right = parseUnary();
            left = Math.pow(left, right);
        }
        return left;
    }

    // unary = ('+' | '-')? atom
    private double parseUnary() {
        if (pos < len && expr.charAt(pos) == '-') {
            pos++;
            return -parseAtom();
        }
        if (pos < len && expr.charAt(pos) == '+') {
            pos++;
        }
        return parseAtom();
    }

    // atom = number | '(' expression ')' | function '(' args ')'
    private double parseAtom() {
        if (pos >= len) {
            throw new IllegalArgumentException("表达式意外结束");
        }

        char ch = expr.charAt(pos);

        // 数字
        if (Character.isDigit(ch) || ch == '.') {
            return parseNumber();
        }

        // 括号
        if (ch == '(') {
            pos++; // skip '('
            double value = parseExpression();
            if (pos >= len || expr.charAt(pos) != ')') {
                throw new IllegalArgumentException("缺少右括号 ')'");
            }
            pos++; // skip ')'
            return value;
        }

        // 函数或常量
        if (Character.isLetter(ch)) {
            String name = parseName();
            if (name.equals("pi") || name.equals("PI") || name.equals("π")) {
                return Math.PI;
            }
            if (name.equals("e") || name.equals("E")) {
                return Math.E;
            }
            // 函数调用
            if (pos < len && expr.charAt(pos) == '(') {
                pos++; // skip '('
                double[] args = parseArgList();
                if (pos >= len || expr.charAt(pos) != ')') {
                    throw new IllegalArgumentException("函数 " + name + " 缺少右括号 ')'");
                }
                pos++; // skip ')'
                return evalFunction(name, args);
            }
            throw new IllegalArgumentException("未知符号: " + name);
        }

        throw new IllegalArgumentException("位置 " + pos + " 处的字符 '" + ch + "' 无法解析");
    }

    private double parseNumber() {
        int start = pos;
        while (pos < len && (Character.isDigit(expr.charAt(pos)) || expr.charAt(pos) == '.')) {
            pos++;
        }
        // 支持科学计数法: e/E + 可选符号 + 数字
        if (pos < len && (expr.charAt(pos) == 'e' || expr.charAt(pos) == 'E')) {
            pos++;
            if (pos < len && (expr.charAt(pos) == '+' || expr.charAt(pos) == '-')) {
                pos++;
            }
            while (pos < len && Character.isDigit(expr.charAt(pos))) {
                pos++;
            }
        }
        return Double.parseDouble(expr.substring(start, pos));
    }

    private String parseName() {
        int start = pos;
        while (pos < len && (Character.isLetter(expr.charAt(pos)) || expr.charAt(pos) == '_')) {
            pos++;
        }
        return expr.substring(start, pos);
    }

    private double[] parseArgList() {
        if (pos < len && expr.charAt(pos) == ')') {
            return new double[0]; // 无参数函数
        }
        java.util.List<Double> args = new java.util.ArrayList<>();
        do {
            if (pos < len && expr.charAt(pos) == ',') {
                pos++; // skip ','
            }
            args.add(parseExpression());
        } while (pos < len && expr.charAt(pos) == ',');

        double[] result = new double[args.size()];
        for (int i = 0; i < args.size(); i++) result[i] = args.get(i);
        return result;
    }

    private double evalFunction(String name, double[] args) {
        return switch (name.toLowerCase()) {
            case "sqrt" -> {
                checkArgs(name, args, 1);
                yield Math.sqrt(args[0]);
            }
            case "abs" -> {
                checkArgs(name, args, 1);
                yield Math.abs(args[0]);
            }
            case "sin" -> {
                checkArgs(name, args, 1);
                yield Math.sin(args[0]);
            }
            case "cos" -> {
                checkArgs(name, args, 1);
                yield Math.cos(args[0]);
            }
            case "tan" -> {
                checkArgs(name, args, 1);
                yield Math.tan(args[0]);
            }
            case "asin", "arcsin" -> {
                checkArgs(name, args, 1);
                yield Math.asin(args[0]);
            }
            case "acos", "arccos" -> {
                checkArgs(name, args, 1);
                yield Math.acos(args[0]);
            }
            case "atan", "arctan" -> {
                checkArgs(name, args, 1);
                yield Math.atan(args[0]);
            }
            case "log", "ln" -> {
                checkArgs(name, args, 1);
                yield Math.log(args[0]);
            }
            case "log10" -> {
                checkArgs(name, args, 1);
                yield Math.log10(args[0]);
            }
            case "exp" -> {
                checkArgs(name, args, 1);
                yield Math.exp(args[0]);
            }
            case "floor" -> {
                checkArgs(name, args, 1);
                yield Math.floor(args[0]);
            }
            case "ceil" -> {
                checkArgs(name, args, 1);
                yield Math.ceil(args[0]);
            }
            case "round" -> {
                checkArgs(name, args, 1);
                yield (double) Math.round(args[0]);
            }
            case "pow" -> {
                checkArgs(name, args, 2);
                yield Math.pow(args[0], args[1]);
            }
            case "min" -> {
                if (args.length < 2) throw new IllegalArgumentException("min 函数至少需要2个参数");
                double m = args[0];
                for (int i = 1; i < args.length; i++) m = Math.min(m, args[i]);
                yield m;
            }
            case "max" -> {
                if (args.length < 2) throw new IllegalArgumentException("max 函数至少需要2个参数");
                double m = args[0];
                for (int i = 1; i < args.length; i++) m = Math.max(m, args[i]);
                yield m;
            }
            case "deg" -> {
                checkArgs(name, args, 1);
                yield Math.toDegrees(args[0]);
            }
            case "rad" -> {
                checkArgs(name, args, 1);
                yield Math.toRadians(args[0]);
            }
            default -> throw new IllegalArgumentException("未知函数: " + name);
        };
    }
}
