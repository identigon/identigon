package org.identigon.alterego;

import java.nio.ByteBuffer;

/** Reads raw Appendix A.2 stream bytes directly, independent of {@link HmacRandomness}. */
final class RawStream {

  private RawStream() {}

  static byte[] firstBytes(byte[] key, int count) {
    ByteBuffer out = ByteBuffer.allocate(count);
    int blockIndex = 0;
    while (out.hasRemaining()) {
      byte[] block = Derivation.hmac(key, ByteBuffer.allocate(4).putInt(blockIndex).array());
      int take = Math.min(block.length, out.remaining());
      out.put(block, 0, take);
      blockIndex++;
    }
    return out.array();
  }
}
