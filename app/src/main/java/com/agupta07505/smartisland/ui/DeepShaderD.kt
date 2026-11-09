package com.agupta07505.smartisland.ui

internal object DeepShaderD {
    const val SRC = """
half4 main(float2 fc) {
    float2 u = float2(fc.x / uRes.x, 1.0 - fc.y / uRes.y);
    float ia = uK.x;
    float sd0 = uK.y;
    float e1 = min(uK.z * exp(-ia * 14.0), 1.0);
    float e2 = min(uImp.y * 1.2, 1.0);
    float e3 = uK.z * exp(-ia * 2.2);
    float2 ic = uImp.zw;
    float gs = sd0 * 7.0 + floor(uT.x * 24.0);
    float2 sa = float2(uRes.x / uRes.y, 1.0);
    u = ic + (u - ic) * (1.0 - 0.05 * e1);
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
    if (uAux.w > 0.55 || e2 > 0.03) chroma = 0.0015 + uAux.w * 0.004 + e2 * 0.07;
    float bsr = e2 * 0.16;
    float3 col = float3(0.0);
    if (bsr > 0.012) {
        float2 rv = (ic - u) * bsr;
        float ns = 2.0 + step(0.75, uQ);
        float3 wsum = float3(0.0);
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            if (fi < ns) {
                float2 q = u + rv * fi;
                float3 s = scene(float2(q.x, 1.0 - q.y), 0.0);
                float3 wg = float3(1.0 - 0.38 * fi, 1.0, 0.24 + 0.38 * fi);
                col += s * wg;
                wsum += wg;
            }
        }
        col = col / wsum;
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
        float ca = hash(float2(sd0, 9.0)) * 3.14159;
        float2 cd = float2(cos(ca), sin(ca));
        float al = dot(dc, cd);
        float pr = dot(dc, float2(-cd.y, cd.x));
        pr += (vnz(float2(al * 9.0, sd0)) - 0.5) * 0.07 + (vnz(float2(al * 38.0, sd0 + 3.0)) - 0.5) * 0.015;
        float reach = clamp(ia * 14.0, 0.0, 3.4);
        float ms = smoothstep(reach, reach - 0.3, abs(al));
        float c1 = exp(-pr * pr / 0.0002);
        float c2 = exp(-abs(pr) * 30.0) * 0.5;
        col += (float3(1.0, 0.95, 1.0) * c1 + float3(0.62, 0.35, 1.0) * c2) * ms * e2 * 1.8;
        float sp = exp(-abs(dc.x) * 60.0) * exp(-abs(dc.y) * 4.0) + exp(-abs(dc.y) * 60.0) * exp(-abs(dc.x) * 4.0);
        col += (float3(0.8, 0.6, 1.0) * (sp * 0.9 + exp(-sd * sd * 160.0) * 2.5) + float3(1.0) * exp(-sd * sd * 900.0) * 3.0) * e2;
    }
    col = float3(1.0) - exp(-col * 1.35);
    // halftone / ink / rays look of the .html (lingers ~1 s after a hard hit)
    float k3 = smoothstep(0.15, 0.7, e3);
    float ks = clamp(max(k3, e1 * 0.8), 0.0, 0.94);
    if (ks > 0.01) {
        float3 base = col;
        float an = atan(dc.y, dc.x);
        float L = dot(base, float3(0.3, 0.6, 0.1));
        float rays = vnz(float2(an * 9.0 + 3.0, sd0 * 0.37));
        float tone = clamp(0.06 + 1.25 * L + 0.5 * exp(-sd * 2.4) * (0.5 + 0.5 * e3) + 0.4 * (rays - 0.5), 0.0, 1.0);
        float2 g = fc / (uRes.y * 0.045);
        float2 gp = float2(0.7071 * g.x - 0.7071 * g.y, 0.7071 * g.x + 0.7071 * g.y);
        float rad = 0.74 * (1.0 - tone);
        float dark = 1.0 - smoothstep(rad - 0.08, rad + 0.08, length(fract(gp) - 0.5));
        float3 ink = float3(0.012, 0.0, 0.05);
        float3 st = mix(float3(0.64, 0.47, 1.0), ink, dark);
        st = mix(st, float3(0.97, 0.93, 1.0), smoothstep(0.8, 1.0, tone) * (1.0 - dark) * 0.8);
        float br = vnz(float2(an * 11.0 + sd0 * 2.0, sd * 2.6)) * smoothstep(0.12, 0.55, sd);
        st = mix(st, ink, smoothstep(0.55, 0.62, br) * k3);
        float ai = an * 64.0 / 6.2832 + sd0;
        float id = floor(ai);
        float fr = fract(ai);
        float r1 = hash(float2(id, sd0 + 1.0));
        float r2 = hash(float2(id, sd0 + 2.0));
        float hw = 0.04 + 0.18 * r2;
        float ln = 1.0 - smoothstep(hw * 0.5, hw, abs(fr - 0.5));
        float lv = ln * step(0.42, r1) * smoothstep(0.1, 0.24, sd) * (1.0 - smoothstep(0.25 + 0.8 * r1, 0.55 + 1.1 * r1, sd));
        st = mix(st, float3(0.98, 0.95, 1.0), clamp(lv, 0.0, 1.0) * k3);
        float wv = clamp((L - 0.42) * 2.1, 0.0, 1.0);
        col = mix(col, mix(st, base * 1.1 + 0.03, wv * 0.9), ks);
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
