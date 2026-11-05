package com.agupta07505.smartisland.ui

internal object DeepShaderA {
    const val SRC = """
uniform float2 uRes;
uniform float2 uT;
uniform float4 uAux;
uniform float2 uEnv[40];
uniform float uBand[24];
uniform float uBeat[40];
uniform float4 uImp;
uniform float3 uK;
uniform float uFrac;

float3 pal(float t) {
    t = 1.0 - abs(fract(t * 0.5) * 2.0 - 1.0);
    float3 a = float3(0.10, 0.95, 1.00);
    float3 b = float3(0.22, 0.42, 1.00);
    float3 c = float3(0.68, 0.28, 1.00);
    float3 d = float3(1.00, 0.22, 0.78);
    float3 r = mix(a, b, smoothstep(0.0, 0.34, t));
    r = mix(r, c, smoothstep(0.30, 0.64, t));
    r = mix(r, d, smoothstep(0.60, 0.95, t));
    return r;
}

float hash(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

// x = PCM mid line, y = smoothed half amplitude (tide), z = beat marker, w = beat halo
float4 envAt(float x) {
    float cx = x * 39.0 + uFrac;
    float c = 0.0;
    float sm = 0.0;
    float ws = 0.0;
    float bm = 0.0;
    float bw = 0.0;
    for (int i = 0; i < 40; i++) {
        float d = cx - float(i);
        float w = max(0.0, 1.0 - abs(d));
        c += 0.5 * (uEnv[i].x + uEnv[i].y) * w;
        float g = exp(-d * d / 12.5);
        sm += 0.5 * (uEnv[i].y - uEnv[i].x) * g;
        ws += g;
        float ab = abs(d);
        bm = max(bm, uBeat[i] * max(0.0, 1.0 - ab * 0.8));
        bw = max(bw, uBeat[i] * max(0.0, 1.0 - ab / 16.0));
    }
    return float4(c, sm / max(ws, 0.001), bm, bw);
}

float bandAt(float x) {
    float bx = x * 23.0;
    float v = 0.0;
    for (int i = 0; i < 24; i++) {
        v += uBand[i] * max(0.0, 1.0 - abs(bx - float(i)));
    }
    return v;
}

// background PCM line + tide filaments + reflection + needles
float3 layers(float x, float y, float4 e, float bv) {
    float t = uT.x;
    float pxy = 2.0 / uRes.y;
    float3 acc = float3(0.0);

    // background wave: thin raw PCM line
    float yl = 0.62 + e.x * 0.05 + sin(x * 31.0 + t * 2.2) * 0.008 * (0.3 + uAux.z * 2.0);
    float dl = (y - yl) / pxy;
    acc += float3(0.86, 0.94, 1.0) * (exp(-dl * dl * 1.3) * (0.7 + 0.5 * uAux.z) + exp(-dl * dl * 0.05) * 0.10);

    // tide (front waves)
    float sl = sin(x * 3.1 + t * 0.55 + 1.3) * 0.9 + 0.6 * sin(x * 5.4 - t * 0.8) + 0.35 * sin(x * 9.0 + t * 1.3);
    float lv = 0.5 + 0.9 * uAux.x + 0.5 * uAux.y;
    float yc = 0.05 + 0.10 * sl * lv;
    float hw = clamp(0.09 + 0.7 * e.y * (0.6 + 0.8 * uAux.y), 0.09, 0.38);
    float yM = -0.30;
    float yr = 2.0 * yM - y;
    float below = step(y, yM);
    float rn = 0.55 + 0.45 * hash(floor(float2(x * uRes.x * 0.6, (y + 1.0) * uRes.y * 0.5)));
    float fadeR = exp((y - yM) * 1.8) * below;
    for (int k = 0; k < 14; k++) {
        float fk = float(k);
        float tk = fk / 13.0 * 2.0 - 1.0;
        float yk = yc + tk * hw * (1.0 + 0.22 * sin(x * 14.0 + fk * 0.9 + t * 1.6) * (0.4 + uAux.z));
        float ed = 1.0 - abs(tk) * 0.9;
        float bead = 0.45 + 0.55 * smoothstep(-0.2, 1.0, sin(x * uRes.x * 1.7 + fk * 2.7 - t * 2.5));
        float3 col = mix(pal(x * 0.6 + fk * 0.012 + t * 0.03 + uAux.x * 0.15), float3(0.92, 0.96, 1.0), 0.5 * ed * ed);
        float wgt = 0.35 + 0.9 * ed * ed;
        float d = (y - yk) / pxy;
        float d2 = (yr - yk) / pxy;
        acc += col * wgt * (exp(-d * d * 0.9) * bead + exp(-d * d * 0.06) * 0.07);
        acc += col * wgt * exp(-d2 * d2 * 0.45) * 0.38 * fadeR * rn * bead;
    }
    float dy = (y - yc) / (hw + 0.06);
    acc += pal(x * 0.5 + 0.7) * exp(-dy * dy * 2.0) * 0.09;

    // needles (band driven), up from the tide and mirrored down
    float idx = floor(x * 72.0);
    float hs = hash(float2(idx, 3.0));
    float hs2 = hash(float2(idx, 11.0));
    float sh = bv * (0.25 + 0.75 * hs2) * 0.6 * step(0.42, hs);
    float dxp = (fract(x * 72.0) - 0.5) * uRes.x / 72.0;
    float thin = exp(-dxp * dxp * 1.6);
    float top = yc + hw;
    float up = step(top, y) * step(y, top + sh) * (1.0 - (y - top) / max(sh, 0.02));
    float dn = below * step(y, yM - 0.04) * step(yM - 0.04 - sh * 0.7, y) * (1.0 - (yM - 0.04 - y) / max(sh * 0.7, 0.02)) * 0.45;
    acc += float3(0.80, 0.86, 1.0) * thin * (up + dn) * 0.85;
    return acc;
}

float3 scene(float2 uv, float off) {
    float x = uv.x;
    float y = (0.5 - uv.y) * 2.0;
    float beat = uAux.w;
    float gl = step(0.5, beat) * beat;
    float sl = floor(uv.y * 9.0);
    float xg = clamp(x + (hash(float2(sl, floor(uT.x * 24.0))) - 0.5) * 0.07 * gl, 0.0, 1.0);
    float4 e = envAt(xg);
    float bv = bandAt(xg);
    float3 rc = layers(xg + off, y, e, bv);
    float3 gc = layers(xg, y, e, bv);
    float3 bc = layers(xg - off, y, e, bv);
    float3 col = float3(rc.x, gc.y, bc.z);

    // beat markers: tick through the whole pill + wide halo that lights it up
    float mk = e.z * e.z * smoothstep(1.0, 0.7, abs(y));
    col += float3(1.0, 0.92, 0.75) * mk * 1.1;
    col += pal(x * 0.8 + 0.3) * e.w * e.w * (0.55 + 0.5 * exp(-y * y * 1.5));
    col += float3(1.0) * e.z * exp(-((y - 0.05) * uRes.y * 0.5) * ((y - 0.05) * uRes.y * 0.5) * 0.35) * 0.8;

    // deep field: faint stars + glitter
    float2 sp = floor(uv * uRes * 0.5);
    float sh2 = hash(sp);
    float stw = 0.5 + 0.5 * sin(uT.x * (1.0 + sh2 * 4.0) + sh2 * 60.0);
    col += float3(0.7, 0.8, 1.0) * step(0.985, sh2) * stw * 0.35;
    float2 gp = floor(float2(uv.x * 64.0, uv.y * 36.0));
    float hs = hash(gp);
    float tw = 0.5 + 0.5 * sin(uT.x * (2.0 + hs * 5.0) + hs * 40.0);
    float near = exp(-(y - 0.1) * (y - 0.1) * 1.69 * 2.0);
    col += pal(hs * 3.0 + x) * step(0.965, hs) * tw * near * (0.5 + beat * 2.2);

    col += pal(x * 0.5 + uT.x * 0.05) * (beat * beat) * 0.5 * exp(-y * y * 0.8);
    col *= 0.94 + 0.06 * sin(uv.y * uRes.y * 3.14159);
    return col;
}

"""
}
