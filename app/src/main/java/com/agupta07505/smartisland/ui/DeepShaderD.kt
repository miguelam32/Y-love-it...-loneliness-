package com.agupta07505.smartisland.ui

internal object DeepShaderD {
    const val SRC = """
half4 main(float2 fc) {
    float2 u = float2(fc.x / uRes.x, 1.0 - fc.y / uRes.y);
    float e1 = min(uImp.x * 1.1, 1.0);
    float e2 = min(uImp.y * 1.2, 1.0);
    float2 ic = uImp.zw;
    float ia = uK.x;
    float gs = uK.y * 7.0 + floor(uT.x * 20.0);
    float2 sa = float2(uRes.x / uRes.y, 1.0);
    float2 dv = (u - float2(0.5, 0.4)) * sa;
    float rr = length(dv);
    float rd = (rr - 0.06 - ia * 1.1) / (0.05 + 0.05 * ia);
    float ring = exp(-rd * rd) * exp(-ia * 3.5) * uK.z * step(0.001, e2);
    float2 rw = dv / (rr + 0.001) / sa * ring;
    u += rw * 0.05;
    if (e2 > 0.01) {
        float2 k1 = floor(u * float2(5.0, 11.0));
        float2 k2 = floor(u * float2(11.0, 29.0));
        float b1 = step(1.0 - 0.6 * e2, hash(k1 + gs * 3.1));
        float b2 = step(1.0 - 0.55 * e2, hash(k2 + gs * 7.7));
        u.x += b1 * (hash(k1 + gs) - 0.5) * 0.8 * e2 + b2 * (hash(k2 + gs + 2.0) - 0.5) * 0.5 * e2;
        u.y += b2 * (hash(k2 + gs + 4.0) - 0.5) * 0.16 * e2;
    }
    float chroma = 0.0;
    if (uAux.w > 0.08 || e2 > 0.01) chroma = 0.0015 + uAux.w * 0.004 + e2 * 0.07;
    float bsr = e2 * 0.12;
    float3 col = float3(0.0);
    if (bsr > 0.004) {
        float2 rv = (ic - u) * bsr;
        for (int i = 0; i < 4; i++) {
            float fi = float(i);
            float2 q = u + rv * fi;
            float3 s = scene(float2(q.x, 1.0 - q.y), 0.0);
            col += s * float3(1.0 - 0.28 * fi, 1.0, 0.16 + 0.28 * fi);
        }
        col = col / float3(2.32, 4.0, 2.32);
    } else {
        col = scene(float2(u.x, 1.0 - u.y), chroma);
    }
    float2 dc = (u - ic) * sa;
    float sd = length(dc);
    col += float3(0.35, 0.12, 0.8) * e2 * exp(-sd * 1.1) * 0.9;
    col += float3(0.55, 0.3, 1.0) * e1 * 0.18;
    float fq = (abs(u.x - ic.x) * sa.x - ia * 3.2) / 0.14;
    float front = exp(-fq * fq) * exp(-ia * 2.2) * uK.z;
    col += float3(0.75, 0.55, 1.0) * front * 0.7 * step(0.001, e2);
    col += float3(0.7, 0.5, 1.0) * ring * 0.8;
    if (e2 > 0.02) {
        float ca = hash(float2(gs, 9.0)) * 3.14159;
        float2 cd = float2(cos(ca), sin(ca));
        float al = dot(dc, cd);
        float pr = dot(dc, float2(-cd.y, cd.x));
        float c1 = exp(-pr * pr / 0.0006);
        float c2 = exp(-abs(pr) * 10.0) * 0.6;
        float ms = 1.0 - smoothstep(1.8, 3.2, abs(al));
        col += (float3(1.0, 0.95, 1.0) * c1 + float3(0.62, 0.35, 1.0) * c2) * ms * e2 * 1.6;
        col += float3(1.0, 0.9, 1.0) * exp(-sd * sd * 40.0) * 2.6 * e2;
    }
    col = float3(1.0) - exp(-col * 1.35);
    float ks = clamp(e1 * 0.75, 0.0, 0.7);
    if (ks > 0.01) {
        float L = dot(col, float3(0.3, 0.6, 0.1));
        float tone = clamp(0.1 + 1.5 * L + 0.5 * exp(-sd * 2.4), 0.0, 1.0);
        float2 g = fc / (uRes.y * 0.07);
        float2 gp = float2(0.7071 * g.x - 0.7071 * g.y, 0.7071 * g.x + 0.7071 * g.y);
        float rad = 0.74 * (1.0 - tone);
        float dark = 1.0 - smoothstep(rad - 0.08, rad + 0.08, length(fract(gp) - 0.5));
        float3 st = mix(float3(0.64, 0.47, 1.0), float3(0.012, 0.0, 0.05), dark);
        st = mix(st, float3(0.97, 0.93, 1.0), smoothstep(0.8, 1.0, tone) * (1.0 - dark) * 0.8);
        float wv = clamp(L * 1.7, 0.0, 1.0);
        col = mix(col, mix(st, col * 1.1 + 0.03, wv * 0.9), ks);
    }
    float2 pc = fc - uRes * 0.5;
    float prad = uRes.y * 0.5;
    float2 qd = abs(pc) - (uRes * 0.5 - float2(prad));
    float dd = length(max(qd, float2(0.0))) + min(max(qd.x, qd.y), 0.0) - prad;
    float edge = smoothstep(0.0, -5.0, dd);
    col *= edge * uT.y;
    float a = clamp(max(col.x, max(col.y, col.z)), 0.0, 1.0);
    return half4(half3(col), half(a));
}
"""
}
