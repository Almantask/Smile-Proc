import Foundation

/// CPython 3.12 `random.Random` for an integer seed, enough for `randint`.
final class PythonRandom {
    private var state = [UInt32](repeating: 0, count: 624)
    private var index = 624

    init(seed: UInt32) {
        var mt = [UInt32](repeating: 0, count: 624)
        mt[0] = 19650218
        for offset in 1..<624 {
            mt[offset] = 1812433253 &* (mt[offset - 1] ^ (mt[offset - 1] >> 30)) &+ UInt32(offset)
        }
        let key = [seed]
        var i = 1
        var j = 0
        for _ in 0..<624 {
            mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >> 30)) &* 1664525)) &+ key[j] &+ UInt32(j)
            i += 1
            j += 1
            if i >= 624 {
                mt[0] = mt[623]
                i = 1
            }
            if j >= key.count { j = 0 }
        }
        for _ in 0..<(624 - 1) {
            mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >> 30)) &* 1566083941)) &- UInt32(i)
            i += 1
            if i >= 624 {
                mt[0] = mt[623]
                i = 1
            }
        }
        mt[0] = 0x80000000
        state = mt
        index = 624
    }

    func randint(from lower: Int, through upper: Int) -> Int {
        lower + randbelow(upper - lower + 1)
    }

    private func randbelow(_ limit: Int) -> Int {
        var bits = 0
        var value = limit
        while value > 0 {
            bits += 1
            value >>= 1
        }
        while true {
            let drawn = Int(next() >> UInt32(32 - bits))
            if drawn < limit { return drawn }
        }
    }

    private func next() -> UInt32 {
        if index >= 624 {
            twist()
        }
        var y = state[index]
        index += 1
        y ^= y >> 11
        y ^= (y << 7) & 0x9d2c5680
        y ^= (y << 15) & 0xefc60000
        y ^= y >> 18
        return y
    }

    private func twist() {
        let n = 624
        let m = 397
        let mag01: [UInt32] = [0, 0x9908b0df]
        for kk in 0..<(n - m) {
            let y = (state[kk] & 0x80000000) | (state[kk + 1] & 0x7fffffff)
            state[kk] = state[kk + m] ^ (y >> 1) ^ mag01[Int(y & 1)]
        }
        for kk in (n - m)..<(n - 1) {
            let y = (state[kk] & 0x80000000) | (state[kk + 1] & 0x7fffffff)
            state[kk] = state[kk + (m - n)] ^ (y >> 1) ^ mag01[Int(y & 1)]
        }
        let y = (state[n - 1] & 0x80000000) | (state[0] & 0x7fffffff)
        state[n - 1] = state[m - 1] ^ (y >> 1) ^ mag01[Int(y & 1)]
        index = 0
    }
}
