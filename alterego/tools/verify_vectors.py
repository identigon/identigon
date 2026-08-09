#!/usr/bin/env python3
"""Independently verifies the frozen conformance vectors under src/test/resources/vectors/.

This is a from-scratch reimplementation of Appendix A (SPECIFICATION.md) using Python's stdlib
hmac/hashlib, deliberately not sharing any code with the Java implementation. It is what backed
the M1 independent review gate (docs/tasks/M1.md) before the vectors were frozen: every case in all
four vector files was checked against this script and matched exactly.

Not part of the Gradle build. Re-run it any time you want an independent second opinion on the
frozen vectors, or as a starting point when a future major version adds a new vector set:

    python3 tools/verify_vectors.py
"""

import hashlib
import hmac
import json
import struct
import sys
from pathlib import Path

VECTORS_DIR = Path(__file__).resolve().parent.parent / "src/test/resources/vectors"

PURPOSE_MAPKEY = "alterego/1/mapkey"


def derive_key(salt: bytes, purpose: str, domain: str, canonical: str, counter: int) -> bytes:
    """Appendix A.1."""
    message = (
        purpose.encode("utf-8")
        + b"\x00"
        + domain.encode("utf-8")
        + b"\x00"
        + canonical.encode("utf-8")
        + b"\x00"
        + struct.pack(">I", counter & 0xFFFFFFFF)
    )
    return hmac.new(salt, message, hashlib.sha256).digest()


def stream_bytes(key: bytes, count: int) -> bytes:
    """Appendix A.2."""
    out = b""
    i = 0
    while len(out) < count:
        out += hmac.new(key, struct.pack(">I", i), hashlib.sha256).digest()
        i += 1
    return out[:count]


class Stream:
    """Appendix A.2 lazy block consumption plus A.3 sampling primitives."""

    def __init__(self, key: bytes):
        self.key = key
        self.block_index = 0
        self.buf = b""
        self.pos = 0

    def _next8(self) -> int:
        v = 0
        for _ in range(8):
            if self.pos == len(self.buf):
                self.buf = hmac.new(self.key, struct.pack(">I", self.block_index), hashlib.sha256).digest()
                self.block_index += 1
                self.pos = 0
            v = (v << 8) | self.buf[self.pos]
            self.pos += 1
        return v

    def next_long(self, bound: int) -> int:
        limit = (0x7FFFFFFFFFFFFFFF // bound) * bound
        while True:
            v = self._next8() & 0x7FFFFFFFFFFFFFFF
            if v < limit:
                return v % bound

    def next_int(self, bound: int) -> int:
        return self.next_long(bound)

    def next_boolean(self) -> bool:
        return self.next_long(2) == 1

    def digit(self) -> str:
        return str(self.next_int(10))

    def letter_upper(self) -> str:
        return chr(ord("A") + self.next_int(26))

    def letter_lower(self) -> str:
        return chr(ord("a") + self.next_int(26))

    def pick_index(self, size: int) -> int:
        return self.next_int(size)


def load(name: str):
    with open(VECTORS_DIR / name) as f:
        return json.load(f)


def verify_derivation() -> bool:
    ok = True
    for v in load("derivation.json"):
        salt = bytes.fromhex(v["saltHex"])
        got = derive_key(salt, v["purpose"], v["domain"], v["canonical"], v["counter"]).hex()
        matched = got == v["keyHex"]
        ok &= matched
        print(f"  derivation  {v['name']:32s} {'OK' if matched else 'MISMATCH'}")
    return ok


def verify_stream() -> bool:
    ok = True
    for v in load("stream.json"):
        key = bytes.fromhex(v["keyHex"])
        got = stream_bytes(key, len(v["streamHex"]) // 2).hex()
        matched = got == v["streamHex"]
        ok &= matched
        print(f"  stream      {v['name']:32s} {'OK' if matched else 'MISMATCH'}")
    return ok


def verify_mapkey() -> bool:
    ok = True
    for v in load("mapkey.json"):
        salt = bytes.fromhex(v["saltHex"])
        got = derive_key(salt, PURPOSE_MAPKEY, v["domain"], v["canonical"], 0).hex()
        matched = got == v["keyHex"]
        ok &= matched
        print(f"  mapkey      {v['name']:32s} {'OK' if matched else 'MISMATCH'}")
    return ok


def verify_sampling() -> bool:
    ok = True
    for v in load("sampling.json"):
        stream = Stream(bytes.fromhex(v["keyHex"]))
        case_ok = True
        for call in v["calls"]:
            op = call["op"]
            expected = call["result"]
            if op == "nextInt":
                got = stream.next_int(call["bound"])
            elif op == "nextLong":
                got = stream.next_long(call["bound"])
            elif op == "nextBoolean":
                got = stream.next_boolean()
            elif op == "digit":
                got = stream.digit()
            elif op == "letterUpper":
                got = stream.letter_upper()
            elif op == "letterLower":
                got = stream.letter_lower()
            elif op == "pick":
                got = stream.pick_index(call["size"])
            else:
                raise ValueError(f"unknown op: {op}")
            if got != expected:
                case_ok = False
                print(f"    {op} expected={expected} got={got}")
        ok &= case_ok
        print(f"  sampling    {v['name']:32s} {'OK' if case_ok else 'MISMATCH'}")
    return ok


def main() -> int:
    print(f"Verifying vectors in {VECTORS_DIR}")
    results = [verify_derivation(), verify_stream(), verify_mapkey(), verify_sampling()]
    if all(results):
        print("\nAll vectors independently verified.")
        return 0
    print("\nMISMATCH DETECTED - do not trust the frozen vectors until this is resolved.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
