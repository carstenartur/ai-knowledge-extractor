package org.aiknowledge.core;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dependency-free JSON reader with duplicate-field and trailing-token rejection. */
final class StrictJsonReader {
    private StrictJsonReader() {
    }

    static Object read(Path path) throws IOException {
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    static Object parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("JSON input must not be null");
        }
        return new Parser(input).parseDocument();
    }

    private static final class Parser {
        private final String input;
        private int position;

        private Parser(String input) {
            this.input = input;
        }

        private Object parseDocument() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (!atEnd()) {
                throw error("trailing token");
            }
            return value;
        }

        private Object parseValue() {
            if (atEnd()) {
                throw error("expected value");
            }
            return switch (current()) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            skipWhitespace();
            Map<String, Object> result = new LinkedHashMap<>();
            if (consume('}')) {
                return result;
            }
            while (true) {
                if (atEnd() || current() != '"') {
                    throw error("object key must be a string");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                Object value = parseValue();
                if (result.containsKey(key)) {
                    throw error("duplicate object field " + quote(key));
                }
                result.put(key, value);
                skipWhitespace();
                if (consume('}')) {
                    return result;
                }
                expect(',');
                skipWhitespace();
            }
        }

        private List<Object> parseArray() {
            expect('[');
            skipWhitespace();
            List<Object> result = new ArrayList<>();
            if (consume(']')) {
                return result;
            }
            while (true) {
                result.add(parseValue());
                skipWhitespace();
                if (consume(']')) {
                    return result;
                }
                expect(',');
                skipWhitespace();
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (!atEnd()) {
                char value = input.charAt(position++);
                if (value == '"') {
                    return result.toString();
                }
                if (value == '\\') {
                    appendEscape(result);
                    continue;
                }
                if (value < 0x20) {
                    throw error("unescaped control character in string");
                }
                if (Character.isHighSurrogate(value)) {
                    if (atEnd() || !Character.isLowSurrogate(current())) {
                        throw error("unpaired high surrogate in string");
                    }
                    result.append(value).append(input.charAt(position++));
                } else if (Character.isLowSurrogate(value)) {
                    throw error("unpaired low surrogate in string");
                } else {
                    result.append(value);
                }
            }
            throw error("unterminated string");
        }

        private void appendEscape(StringBuilder result) {
            if (atEnd()) {
                throw error("unterminated string escape");
            }
            char escaped = input.charAt(position++);
            switch (escaped) {
                case '"' -> result.append('"');
                case '\\' -> result.append('\\');
                case '/' -> result.append('/');
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> appendUnicodeEscape(result);
                default -> throw error("unsupported string escape \\" + escaped);
            }
        }

        private void appendUnicodeEscape(StringBuilder result) {
            char first = parseHexCodeUnit();
            if (Character.isHighSurrogate(first)) {
                if (position + 1 >= input.length()
                        || input.charAt(position) != '\\'
                        || input.charAt(position + 1) != 'u') {
                    throw error("high surrogate must be followed by a low surrogate escape");
                }
                position += 2;
                char second = parseHexCodeUnit();
                if (!Character.isLowSurrogate(second)) {
                    throw error("high surrogate is not followed by a low surrogate");
                }
                result.append(first).append(second);
            } else if (Character.isLowSurrogate(first)) {
                throw error("unpaired low surrogate escape");
            } else {
                result.append(first);
            }
        }

        private char parseHexCodeUnit() {
            if (position + 4 > input.length()) {
                throw error("incomplete unicode escape");
            }
            int value = 0;
            for (int index = 0; index < 4; index++) {
                char current = input.charAt(position++);
                int digit = Character.digit(current, 16);
                if (digit < 0) {
                    throw error("invalid unicode escape");
                }
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private Object parseLiteral(String literal, Object value) {
            if (!input.startsWith(literal, position)) {
                throw error("invalid literal");
            }
            position += literal.length();
            return value;
        }

        private BigDecimal parseNumber() {
            int start = position;
            consume('-');
            if (atEnd()) {
                throw error("incomplete number");
            }
            if (consume('0')) {
                if (!atEnd() && Character.isDigit(current())) {
                    throw error("leading zero in number");
                }
            } else {
                requireDigit("expected integer digits");
                while (!atEnd() && Character.isDigit(current())) {
                    position++;
                }
            }
            if (consume('.')) {
                requireDigit("fraction requires at least one digit");
                while (!atEnd() && Character.isDigit(current())) {
                    position++;
                }
            }
            if (!atEnd() && (current() == 'e' || current() == 'E')) {
                position++;
                if (!atEnd() && (current() == '+' || current() == '-')) {
                    position++;
                }
                requireDigit("exponent requires at least one digit");
                while (!atEnd() && Character.isDigit(current())) {
                    position++;
                }
            }
            if (start == position) {
                throw error("expected JSON value");
            }
            String token = input.substring(start, position);
            try {
                return new BigDecimal(token);
            } catch (NumberFormatException exception) {
                throw error("invalid number " + token);
            }
        }

        private void requireDigit(String message) {
            if (atEnd() || !Character.isDigit(current())) {
                throw error(message);
            }
        }

        private void skipWhitespace() {
            while (!atEnd()) {
                char value = current();
                if (value == ' ' || value == '\t' || value == '\n' || value == '\r') {
                    position++;
                } else {
                    return;
                }
            }
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw error("expected '" + expected + "'");
            }
        }

        private boolean consume(char expected) {
            if (!atEnd() && current() == expected) {
                position++;
                return true;
            }
            return false;
        }

        private char current() {
            return input.charAt(position);
        }

        private boolean atEnd() {
            return position >= input.length();
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(
                "Invalid JSON at offset " + position + ": " + message);
        }

        private static String quote(String value) {
            return "'" + value.replace("'", "\\'") + "'";
        }
    }
}
