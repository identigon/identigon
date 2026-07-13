package io.github.dconneely.alterego.pattern;

/** One compiled token of a pattern (SPECIFICATION.md section 4.6). */
sealed interface PatternToken {
  record RandomDigit() implements PatternToken {}
  record RandomUpper() implements PatternToken {}
  record RandomLower() implements PatternToken {}
  record RandomLetter() implements PatternToken {}
  record Literal(char value) implements PatternToken {}
}
