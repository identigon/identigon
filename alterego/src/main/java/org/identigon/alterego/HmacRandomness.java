package org.identigon.alterego;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Appendix A.2/A.3: an HMAC-SHA256 counter-mode byte stream over a derived key, with the
 * rejection-sampling primitives layered on top. Stateful and single-threaded: each call
 * consumes the next bytes of the stream.
 */
final class HmacRandomness implements Randomness {

  private final byte[] key;
  private int blockIndex;
  private byte[] currentBlock = new byte[0];
  private int posInBlock;

  HmacRandomness(byte[] key) {
    this.key = key;
  }

  private byte[] nextBlock() {
    byte[] counterBytes = ByteBuffer.allocate(4).putInt(blockIndex).array();
    blockIndex++;
    return Derivation.hmac(key, counterBytes);
  }

  private long next8() {
    long v = 0;
    for (int i = 0; i < 8; i++) {
      if (posInBlock == currentBlock.length) {
        currentBlock = nextBlock();
        posInBlock = 0;
      }
      v = (v << 8) | (currentBlock[posInBlock++] & 0xFFL);
    }
    return v;
  }

  @Override
  public long nextLong(long bound) {
    if (bound <= 0) {
      throw new IllegalArgumentException("bound must be positive, got: " + bound);
    }
    long limit = (Long.MAX_VALUE / bound) * bound;
    long v;
    do {
      v = next8() & Long.MAX_VALUE;
    } while (v >= limit);
    return v % bound;
  }

  @Override
  public int nextInt(int bound) {
    return (int) nextLong(bound);
  }

  @Override
  public boolean nextBoolean() {
    return nextLong(2) == 1;
  }

  @Override
  public <T> T pick(List<T> choices) {
    if (choices.isEmpty()) {
      throw new IllegalArgumentException("choices must not be empty");
    }
    return choices.get(nextInt(choices.size()));
  }

  @Override
  public char digit() {
    return (char) ('0' + nextInt(10));
  }

  @Override
  public char letterUpper() {
    return (char) ('A' + nextInt(26));
  }

  @Override
  public char letterLower() {
    return (char) ('a' + nextInt(26));
  }
}
