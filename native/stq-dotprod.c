// STQ dotprod kernel from llama.cpp PR 22836 (MIT). Separate ISA translation unit.
#define GGML_COMMON_IMPL_C
#include "ggml-common.h"
#include "ggml-quants.h"
#include "ggml-impl.h"
#include "ggml-cpu.h"
#include "simd-mappings.h"

#include "ggml-cpu/quants.h"
#include "ggml-cpu/ggml-cpu-impl.h"

#include <math.h>
#include <string.h>
#include <assert.h>
#include <float.h>
#include <stdlib.h> // for qsort
#include <stdio.h>  // for GGML_ASSERT

#define GROUP_MAX_EPS 1e-15f
#define GROUP_MAX_EPS_IQ3_XXS 1e-8f
#define GROUP_MAX_EPS_IQ2_S 1e-8f
#define GROUP_MAX_EPS_IQ1_M 1e-7f
#define GROUP_MAX_EPS_IQ1_S 1e-12f

#define UNUSED GGML_UNUSED

#if defined(__ARM_NEON)
#define B1(c,s,n)  0x ## n ## c ,  0x ## n ## s
#define B2(c,s,n) B1(c,s,n ## c), B1(c,s,n ## s)
#define B3(c,s,n) B2(c,s,n ## c), B2(c,s,n ## s)
#define B4(c,s,n) B3(c,s,n ## c), B3(c,s,n ## s)
#define B5(c,s,n) B4(c,s,n ## c), B4(c,s,n ## s)
#define B6(c,s,n) B5(c,s,n ## c), B5(c,s,n ## s)
#define B7(c,s,n) B6(c,s,n ## c), B6(c,s,n ## s)
#define B8(c,s  ) B7(c,s,     c), B7(c,s,     s)

// precomputed tables for expanding 8bits to 8 bytes:
static const uint64_t table_b2b_0[1 << 8] = { B8(00, 10) }; // ( b) << 4
static const uint64_t table_b2b_1[1 << 8] = { B8(10, 00) }; // (!b) << 4
#endif

void xup_vec_dot_stq1_dotprod(int n, float * GGML_RESTRICT s, size_t bs, const void * GGML_RESTRICT vx, size_t bx, const void * GGML_RESTRICT vy, size_t by, int nrc) {
    assert(nrc == 1);
    UNUSED(nrc);
    UNUSED(bx);
    UNUSED(by);
    UNUSED(bs);

    const block_stq1_0 * GGML_RESTRICT x = vx;
    const block_q8_K  * GGML_RESTRICT y = vy;

    const int nb = n / QK_K;

#if defined(__ARM_NEON)
    float sumf = 0.0f;

    const uint8x16_t m3 = vdupq_n_u8(3);
    const uint8x16_t mask_0f = vdupq_n_u8(0x0F);
    const uint8_t * sign_lut_16 = (const uint8_t *)(const void *) table_b2b_0;

#if defined(__ARM_FEATURE_DOTPROD)
    // dotprod path: single 32-byte tbl lookup + native vdotq.
    const uint8x16x2_t codebook2 = { { vld1q_u8(stq1_0_codebook), vld1q_u8(stq1_0_codebook + 16) } };
    #define STQ1_0_DOT(acc, sx, sy)  vdotq_s32((acc), (sx), (sy))
    #define STQ1_0_LOOKUP(idx)       vqtbl2q_u8(codebook2, (idx))
#else
    // ARMv8.0 NEON without dotprod: emulate vdotq_s32 (vmull_s8 + vpaddlq_s16)
    // and split the 32-byte codebook lookup into two vqtbl1q_u8 calls. vqtbl1q_u8
    // returns 0 for out-of-range indices, so OR-ing the low and high halves
    // (the high half is indexed by idx-16, which underflows to >=240 for idx<16)
    // reproduces vqtbl2q_u8 byte-for-byte.
    const uint8x16_t cb_lo = vld1q_u8(stq1_0_codebook);
    const uint8x16_t cb_hi = vld1q_u8(stq1_0_codebook + 16);
    const uint8x16_t v16   = vdupq_n_u8(16);
    #define STQ1_0_DOT(acc, sx, sy)  ggml_vdotq_s32((acc), (sx), (sy))
    #define STQ1_0_LOOKUP(idx)       vorrq_u8(vqtbl1q_u8(cb_lo, (idx)), \
                                            vqtbl1q_u8(cb_hi, vsubq_u8((idx), v16)))
#endif

    // Each half processes 16 bytes of x.qs (32 codes), 4 bytes of x.sign,
    // and 128 bytes of y.qs (2 chunks of 64 B). Stride-16 grouping lets each
    // lane plane be a contiguous 16-byte slice of y (offsets 0/16/32/48 within
    // a chunk), so we read it with plain vld1q_s8 — no deinterleave needed.
#define STQ1_0_DOT_HALF(QS_PTR, SIGN_PTR, YP_PTR) do {                                \
    const uint8x16_t packed = vld1q_u8(QS_PTR);                                     \
    const uint8x16_t lo     = vandq_u8(packed, mask_0f);                            \
    const uint8x16_t hi     = vshrq_n_u8(packed, 4);                                \
    const uint8x16_t idx0   = vzip1q_u8(lo, hi);                                    \
    const uint8x16_t idx1   = vzip2q_u8(lo, hi);                                    \
    const uint8_t * sp      = (SIGN_PTR);                                           \
    const uint8x16_t s0 = vcombine_u8(vld1_u8(sign_lut_16 + 8*sp[0]),               \
                                      vld1_u8(sign_lut_16 + 8*sp[1]));              \
    const uint8x16_t s1 = vcombine_u8(vld1_u8(sign_lut_16 + 8*sp[2]),               \
                                      vld1_u8(sign_lut_16 + 8*sp[3]));              \
    const uint8x16_t sel_0 = STQ1_0_LOOKUP(vorrq_u8(idx0, s0));                       \
    const uint8x16_t sel_1 = STQ1_0_LOOKUP(vorrq_u8(idx1, s1));                       \
    const int8x16_t sqx0 = vreinterpretq_s8_u8(vandq_u8(sel_0, m3));                \
    const int8x16_t sqx1 = vreinterpretq_s8_u8(vandq_u8(vshrq_n_u8(sel_0, 2), m3)); \
    const int8x16_t sqx2 = vreinterpretq_s8_u8(vandq_u8(vshrq_n_u8(sel_0, 4), m3)); \
    const int8x16_t sqx3 = vreinterpretq_s8_u8(vshrq_n_u8(sel_0, 6));               \
    const int8x16_t sqx4 = vreinterpretq_s8_u8(vandq_u8(sel_1, m3));                \
    const int8x16_t sqx5 = vreinterpretq_s8_u8(vandq_u8(vshrq_n_u8(sel_1, 2), m3)); \
    const int8x16_t sqx6 = vreinterpretq_s8_u8(vandq_u8(vshrq_n_u8(sel_1, 4), m3)); \
    const int8x16_t sqx7 = vreinterpretq_s8_u8(vshrq_n_u8(sel_1, 6));               \
    const int8_t * yp = (YP_PTR);                                                   \
    sumi0 = STQ1_0_DOT(sumi0, sqx0, vld1q_s8(yp +   0));                            \
    sumi1 = STQ1_0_DOT(sumi1, sqx1, vld1q_s8(yp +  16));                            \
    sumi2 = STQ1_0_DOT(sumi2, sqx2, vld1q_s8(yp +  32));                            \
    sumi3 = STQ1_0_DOT(sumi3, sqx3, vld1q_s8(yp +  48));                            \
    sumi0 = STQ1_0_DOT(sumi0, sqx4, vld1q_s8(yp +  64));                            \
    sumi1 = STQ1_0_DOT(sumi1, sqx5, vld1q_s8(yp +  80));                            \
    sumi2 = STQ1_0_DOT(sumi2, sqx6, vld1q_s8(yp +  96));                            \
    sumi3 = STQ1_0_DOT(sumi3, sqx7, vld1q_s8(yp + 112));                            \
} while (0)

    for (int i = 0; i < nb; ++i) {
        // 4 accumulators
        int32x4_t sumi0 = vdupq_n_s32(0);
        int32x4_t sumi1 = vdupq_n_s32(0);
        int32x4_t sumi2 = vdupq_n_s32(0);
        int32x4_t sumi3 = vdupq_n_s32(0);

        STQ1_0_DOT_HALF(x[i].qs,      x[i].sign,     y[i].qs);
        STQ1_0_DOT_HALF(x[i].qs + 16, x[i].sign + 4, y[i].qs + 128);

        const int16x8_t ysum0 = vld1q_s16(y[i].bsums);
        const int16x8_t ysum1 = vld1q_s16(y[i].bsums + 8);
        const float d = GGML_CPU_FP16_TO_FP32(x[i].d) * y[i].d;

        sumi0 = vaddq_s32(vaddq_s32(sumi0, sumi1), vaddq_s32(sumi2, sumi3));
        sumi0 = vsubq_s32(sumi0, vpaddlq_s16(vaddq_s16(ysum0, ysum1)));

        sumf += d * (float) vaddvq_s32(sumi0);
    }

#undef STQ1_0_DOT
#undef STQ1_0_LOOKUP
#undef STQ1_0_DOT_HALF

    *s = sumf;
#else
    UNUSED(x);
    UNUSED(y);
    UNUSED(nb);
    ggml_vec_dot_stq1_0_q8_K_generic(n, s, bs, vx, bx, vy, by, nrc);
#endif
}
